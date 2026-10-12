package com.ping.app.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import com.google.firebase.FirebaseApp
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.ping.app.MainActivity
import com.ping.app.R
import com.ping.app.batteryUpdate
import com.ping.app.initializeSavedFirebase

class LocationService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private var lastSentLocation: Location? = null
    private var latestLocation: Location? = null
    private var writeInFlight = false
    private var generation = 0
    private var hasPublishedSession = false
    private val heartbeatHandler = Handler(Looper.getMainLooper())
    private val heartbeat = Runnable { publishUpdate(forceHeartbeat = true) }
    private var wakeLock: PowerManager.WakeLock? = null
    private val sessionStore by lazy {
        BroadcastSessionStore(getSharedPreferences(SESSION_PREFS_NAME, Context.MODE_PRIVATE))
    }

    companion object {
        const val TAG = "PingLocationService"
        const val CHANNEL_ID = "ping_location_channel"
        const val NOTIFICATION_ID = 1
        const val SESSION_PREFS_NAME = "PingBroadcastSession"
        var isServiceRunning = false
        var activePin = ""
        var activeUid = ""
        var isSosActive = false
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        createNotificationChannel()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                Log.d(TAG, "Got ${locationResult.locations.size} location(s)")
                locationResult.lastLocation?.let { processLocation(it) }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        Log.d(TAG, "onStartCommand action=$action")

        if (action == "STOP") {
            Log.d(TAG, "Stopping service...")
            stopLocationUpdates()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        val requestedPin = intent?.getStringExtra("pin")
        if (isServiceRunning && requestedPin == activePin) return START_STICKY
        // Android can restart a sticky service with a null intent in a fresh process.
        // An explicit app/OS stop never schedules a restart here.
        val restored = if (intent == null) sessionStore.load() else null
        val nextPin = requestedPin ?: restored?.pin
        val nextUid = intent?.getStringExtra("uid") ?: restored?.uid
        if (nextPin.isNullOrEmpty() || nextUid.isNullOrEmpty()) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (isServiceRunning) stopLocationUpdates()
        activePin = nextPin
        activeUid = nextUid
        isSosActive = restored?.sos ?: false
        generation++
        heartbeatHandler.removeCallbacks(heartbeat)
        latestLocation = null
        lastSentLocation = restored?.let { saved ->
            if (saved.latitude == null || saved.longitude == null) null
            else Location("saved_broadcast").apply {
                latitude = saved.latitude
                longitude = saved.longitude
                accuracy = saved.accuracy
            }
        }
        writeInFlight = false
        hasPublishedSession = restored?.published ?: false
        Log.d(TAG, "Starting broadcast service")

        // Open the in-app broadcast controls; stopping remains an explicit in-app action.
        val openIntent = Intent(this, MainActivity::class.java).apply {
            this.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(MainActivity.EXTRA_OPEN_BROADCASTER, true)
        }
        val openPendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Location sharing active")
            .setContentText("PING · Tap to manage broadcasting")
            .setSmallIcon(R.drawable.ic_stat_ping)
            .setContentIntent(openPendingIntent)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)
        isServiceRunning = true
        persistSession()
        ensureFirebaseInitialized()
        acquireWakeLock()

        requestLocationUpdates()
        heartbeatHandler.post(heartbeat)

        return START_STICKY
    }

    private fun ensureFirebaseInitialized() {
        try {
            val prefs = getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
            val configJson = prefs.getString(MainActivity.KEY_FIREBASE_CONFIG, null)
            if (configJson == null) {
                Log.e(TAG, "No Firebase config in prefs!")
                return
            }

            if (initializeSavedFirebase(this) == null) return

            // Ensure Anonymous Auth (required by Firebase Rules)
            val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
            if (auth.currentUser == null) {
                auth.signInAnonymously().addOnSuccessListener {
                    if (isServiceRunning) publishUpdate(forceHeartbeat = true)
                }.addOnFailureListener { Log.e(TAG, "Anonymous sign-in failed", it) }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Firebase init in service failed: ${e.message}", e)
        }
    }

    private fun persistSession() {
        sessionStore.save(SavedBroadcast(activePin, activeUid, isSosActive, hasPublishedSession,
            lastSentLocation?.latitude, lastSentLocation?.longitude, lastSentLocation?.accuracy ?: 0f))
    }

    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "ping:location_wakelock"
            )
            wakeLock?.acquire()
            Log.d(TAG, "WakeLock acquired")
        }
    }

    private fun requestLocationUpdates() {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, TrackingPolicy.LOCATION_INTERVAL_MS)
            .setMinUpdateIntervalMillis(TrackingPolicy.LOCATION_INTERVAL_MS)
            // Apply distance filtering once, against the last published fix, not two baselines.
            .setMinUpdateDistanceMeters(0f)
            .setWaitForAccurateLocation(false)
            .build()

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
            Log.d(TAG, "Location updates requested")
        } catch (e: SecurityException) {
            Log.e(TAG, "No location permission: ${e.message}")
        }
    }

    private fun processLocation(location: Location) {
        if (!location.latitude.isFinite() || !location.longitude.isFinite() ||
            location.latitude !in -90.0..90.0 || location.longitude !in -180.0..180.0) return
        latestLocation = Location(location)
        publishUpdate(forceHeartbeat = false)
    }

    private fun scheduleHeartbeat() {
        heartbeatHandler.removeCallbacks(heartbeat)
        if (isServiceRunning) heartbeatHandler.postDelayed(heartbeat, TrackingPolicy.HEARTBEAT_INTERVAL_MS)
    }

    private fun publishUpdate(forceHeartbeat: Boolean) {
        if (!isServiceRunning || activePin.isEmpty() || writeInFlight) return
        try {
            if (FirebaseApp.getApps(this).isEmpty() ||
                com.google.firebase.auth.FirebaseAuth.getInstance().currentUser == null) {
                scheduleHeartbeat()
                return
            }
            val location = latestLocation
            val previous = lastSentLocation
            val shouldSendCoordinates = location != null && TrackingPolicy.shouldPublishLocation(
                previous?.distanceTo(location)
            )
            if (!shouldSendCoordinates && !forceHeartbeat) return

            val database = FirebaseDatabase.getInstance()
            val sessionRef = database.getReference("active_sessions/$activePin")
            val writeGeneration = generation
            val updateData = HashMap<String, Any>()
            updateData.putAll(batteryUpdate(this))
            updateData["owner_uid"] = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser!!.uid
            updateData["session_id"] = activeUid
            // Every movement publication also counts as a heartbeat. Server time avoids clock drift.
            updateData["timestamp"] = ServerValue.TIMESTAMP // Compatibility with older observers.
            updateData["heartbeat_at"] = ServerValue.TIMESTAMP
            if (!hasPublishedSession) {
                updateData["last_moved_at"] = 0L
                updateData["heartbeat_interval_ms"] = TrackingPolicy.HEARTBEAT_INTERVAL_MS
                updateData["sos_active"] = isSosActive
            }
            if (shouldSendCoordinates && location != null) {
                updateData["lat"] = location.latitude
                updateData["lng"] = location.longitude
                updateData["accuracy_m"] = location.accuracy
                updateData["location_updated_at"] = ServerValue.TIMESTAMP
                if (previous != null) updateData["last_moved_at"] = ServerValue.TIMESTAMP
            }
            writeInFlight = true
            heartbeatHandler.removeCallbacks(heartbeat)
            // Keep only one pending write while offline; latestLocation retains the newest fix.
            sessionRef.updateChildren(updateData)
                .addOnSuccessListener {
                    if (generation != writeGeneration || !isServiceRunning) return@addOnSuccessListener
                    writeInFlight = false
                    val firstPublication = !hasPublishedSession
                    hasPublishedSession = true
                    if (shouldSendCoordinates) lastSentLocation = location?.let { Location(it) }
                    // Heartbeats alone do not cause storage writes every 10 seconds.
                    if (firstPublication || shouldSendCoordinates) persistSession()
                    scheduleHeartbeat()
                    // A newer fix may have arrived while the write was pending.
                    publishUpdate(forceHeartbeat = false)
                }
                .addOnFailureListener { e ->
                    if (generation != writeGeneration || !isServiceRunning) return@addOnFailureListener
                    writeInFlight = false
                    // Do not advance the movement baseline when publication failed.
                    Log.e(TAG, "Firebase update failed: ${e.message}")
                    scheduleHeartbeat()
                }
        } catch (e: Exception) {
            writeInFlight = false
            Log.e(TAG, "Error publishing tracking update", e)
            scheduleHeartbeat()
        }
    }

    private fun stopLocationUpdates(endSession: Boolean = true) {
        if (endSession) sessionStore.clear()
        generation++
        heartbeatHandler.removeCallbacks(heartbeat)
        writeInFlight = false
        latestLocation = null
        lastSentLocation = null
        hasPublishedSession = false
        try {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        } catch (e: Exception) {
            Log.e(TAG, "Error removing location updates: ${e.message}")
        }

        isServiceRunning = false
        isSosActive = false

        // Clean up Firebase session
        try {
            if (endSession && activePin.isNotEmpty() && FirebaseApp.getApps(this).isNotEmpty()) {
                val database = FirebaseDatabase.getInstance()
                val sessionRef = database.getReference("active_sessions/$activePin")
                sessionRef.removeValue()
                Log.d(TAG, "Broadcast session removed")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning session: ${e.message}")
        }

        activePin = ""
        activeUid = ""

        // Release wake lock
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
                Log.d(TAG, "WakeLock released")
            }
        }
        wakeLock = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "PING Location Tracking",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps PING broadcasting your location in the background"
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
                enableLights(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service destroyed")
        stopLocationUpdates(endSession = false)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // Removing the activity does not request that the location service stop.
        Log.d(TAG, "Recent-app task removed; broadcast service remains active")
    }
}
