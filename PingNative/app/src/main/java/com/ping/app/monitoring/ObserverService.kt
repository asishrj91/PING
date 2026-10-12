package com.ping.app.monitoring

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.os.*
import androidx.compose.runtime.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.ping.app.initializeSavedFirebase

/** User-started, continuous SOS monitoring. No GPS requests, polling timer or cloud functions. */
class ObserverService : Service() {
    companion object {
        var running by mutableStateOf(false); private set
        var ready by mutableStateOf(false); private set
        var lastError by mutableStateOf<String?>(null); private set
        var accessDenied by mutableStateOf(false); private set
        fun prepareStart() {
            if (!running) { accessDenied = false; lastError = null }
        }
    }
    private var request: MonitorRequest? = null
    private var reference: DatabaseReference? = null
    private var sessionListener: ValueEventListener? = null
    private var connectionRef: DatabaseReference? = null
    private var connectionListener: ValueEventListener? = null
    private var networkConnected = false
    private var claimPending = false
    private var claimedSession: String? = null
    private var latestSnapshot: DataSnapshot? = null
    private var activeKey: String? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private lateinit var alarm: MonitorAlarm
    private val store by lazy { ObserverMonitoring.store(this) }
    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "handled" && activeKey?.let(store::acknowledged) == true) stopAlarm()
    }

    override fun onCreate() {
        super.onCreate()
        alarm = MonitorAlarm(this)
        ObserverNotifications.createChannels(this)
        ObserverMonitoring.prefs(this).registerOnSharedPreferenceChangeListener(preferenceListener)
    }

    @SuppressLint("WakelockTimeout") // This user-started monitoring session ends at Disconnect/onDestroy.
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val next = ObserverMonitoring.current(this)
        if (next == null) { stopSelf(); return START_NOT_STICKY }
        if (running && request == next) return START_STICKY
        detach()
        request = next
        lastError = null
        accessDenied = false
        running = true
        try {
            updateForeground()
            wakeLock = getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ping:observer_monitor").apply { acquire() }
            val app = initializeSavedFirebase(this) ?: error("Check your database configuration.")
            val auth = FirebaseAuth.getInstance(app)
            val attach = {
                if (isCurrent(next)) attachSession(FirebaseDatabase.getInstance(app), auth.currentUser!!.uid, next)
            }
            if (auth.currentUser != null) attach()
            else auth.signInAnonymously().addOnSuccessListener { attach() }
                .addOnFailureListener { if (isCurrent(next)) fail("SOS monitoring sign-in failed. Reopen PING to retry.") }
        } catch (e: Exception) {
            fail(e.message ?: "Could not start SOS monitoring.")
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    private fun isCurrent(expected: MonitorRequest) = running && request == expected &&
        ObserverMonitoring.current(this) == expected

    private fun attachSession(db: FirebaseDatabase, uid: String, expected: MonitorRequest) {
        connectionRef = db.getReference(".info/connected")
        connectionListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!isCurrent(expected)) return
                networkConnected = snapshot.getValue(Boolean::class.java) == true
                ready = networkConnected && claimedSession != null
                updateForeground()
            }
            override fun onCancelled(error: DatabaseError) = Unit
        }
        connectionRef!!.addValueEventListener(connectionListener!!)
        reference = db.getReference("active_sessions/${expected.pin}")
        sessionListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!isCurrent(expected)) return
                latestSnapshot = snapshot
                val session = snapshot.child("session_id").getValue(String::class.java)
                val bound = store.session()
                if (session.isNullOrBlank()) {
                    if (bound != null) fail("Broadcast ended. Disconnect and enter a new PIN.")
                    else lastError = "Waiting for the broadcaster. Use the updated PING on both phones."
                    return
                }
                if (bound != null && bound != session) {
                    fail("This broadcast has changed. Disconnect and enter its PIN again.")
                    return
                }
                if (claimedSession == null) {
                    if (!claimPending) claim(uid, session, expected)
                    return
                }
                val observer = snapshot.child("observer")
                if (!canReleaseObserver(observer.child("uid").getValue(String::class.java),
                        observer.child("connection_id").getValue(String::class.java), uid, expected.connectionId)) {
                    fail("Observer connection changed. Disconnect and reconnect.")
                    return
                }
                lastError = null
                handleSos(snapshot, session)
            }
            override fun onCancelled(error: DatabaseError) {
                if (isCurrent(expected)) {
                    accessDenied = true
                    fail("SOS monitoring denied. Check the PIN, existing observer and database rules.")
                }
            }
        }
        reference!!.addValueEventListener(sessionListener!!)
    }

    private fun claim(uid: String, session: String, expected: MonitorRequest) {
        claimPending = true
        reference!!.child("observer").runTransaction(object : Transaction.Handler {
            override fun doTransaction(data: MutableData): Transaction.Result {
                if (!isCurrent(expected)) return Transaction.abort()
                val owner = data.child("uid").getValue(String::class.java)
                if (owner != null && owner != uid) return Transaction.abort()
                data.value = mapOf("uid" to uid, "session_id" to session, "connection_id" to expected.connectionId)
                return Transaction.success(data)
            }
            override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {
                if (!isCurrent(expected)) {
                    if (committed) ObserverMonitoring.release(this@ObserverService, expected)
                    return
                }
                claimPending = false
                if (error != null || !committed) {
                    accessDenied = true
                    fail(if (error != null) "Cannot enable SOS monitoring. Check database rules and retry."
                        else "This session already has an observer.")
                    return
                }
                claimedSession = session
                store.bind(session)
                ready = networkConnected
                lastError = null
                updateForeground()
                latestSnapshot?.let { current ->
                    if (current.child("session_id").getValue(String::class.java) == session) handleSos(current, session)
                    else {
                        ObserverMonitoring.release(this@ObserverService, expected)
                        fail("Broadcast ended while connecting. Disconnect and retry.")
                    }
                }
            }
        }, false)
    }

    private fun handleSos(snapshot: DataSnapshot, session: String) {
        val node = snapshot.child("sos")
        val signal = monitorSos(session, node.child("session_id").getValue(String::class.java),
            node.child("event_id").getValue(String::class.java), node.child("active").getValue(Boolean::class.java))
        if (signal == null || !signal.active) {
            activeKey?.let(store::acknowledge)
            stopAlarm()
            return
        }
        if (store.acknowledged(signal.key)) { stopAlarm(); return }
        if (activeKey == signal.key) return
        stopAlarm()
        activeKey = signal.key
        ObserverMonitoring.prefs(this).edit().putString("active_sos_key", signal.key).apply()
        updateForeground()
        getSystemService(NotificationManager::class.java).notify(ObserverNotifications.SOS_ID,
            ObserverNotifications.sos(this, request!!.pin, signal.key))
        alarm.start()
    }

    private fun stopAlarm() {
        activeKey = null
        ObserverMonitoring.prefs(this).edit().remove("active_sos_key").apply()
        alarm.stop()
        getSystemService(NotificationManager::class.java).cancel(ObserverNotifications.SOS_ID)
        if (running && request != null) updateForeground()
    }
    private fun updateForeground() {
        val current = request ?: return
        val notification = ObserverNotifications.monitoring(this, current.pin, ready)
        if (Build.VERSION.SDK_INT >= 34) startForeground(ObserverNotifications.MONITOR_ID, notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE or
                (if (activeKey != null) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0))
        else startForeground(ObserverNotifications.MONITOR_ID, notification)
    }
    private fun fail(message: String) {
        lastError = message
        running = false
        ready = false
        stopAlarm()
        stopSelf()
    }
    private fun detach() {
        sessionListener?.let { reference?.removeEventListener(it) }
        connectionListener?.let { connectionRef?.removeEventListener(it) }
        sessionListener = null; connectionListener = null
        reference = null; connectionRef = null
        claimPending = false; claimedSession = null; latestSnapshot = null
        networkConnected = false; ready = false
        wakeLock?.let { if (it.isHeld) it.release() }; wakeLock = null
        stopAlarm()
    }
    override fun onDestroy() {
        running = false
        detach()
        request = null
        ObserverMonitoring.prefs(this).unregisterOnSharedPreferenceChangeListener(preferenceListener)
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
    override fun onTaskRemoved(rootIntent: Intent?) {
        // Back/Recents dismissal is not Disconnect. Keep the user-started monitoring session.
        super.onTaskRemoved(rootIntent)
    }
    override fun onBind(intent: Intent?): IBinder? = null
}
