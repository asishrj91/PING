package com.ping.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.ConsoleMessage
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebResourceError
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.firebase.database.*
import com.ping.app.services.LocationService
import com.ping.app.monitoring.*
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    companion object {
        const val TAG = "PingNative"
        const val EXTRA_OPEN_BROADCASTER = "open_broadcaster"
        const val EXTRA_OPEN_OBSERVER = "open_observer"
        const val PREFS_NAME = "PingPrefs"
        const val KEY_FIREBASE_CONFIG = "firebase_config"
    }

    private var openBroadcastRequest by mutableIntStateOf(0)
    private var openObserverRequest by mutableIntStateOf(0)

    private val fgPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val fineGranted = results[Manifest.permission.ACCESS_FINE_LOCATION] == true
        if (fineGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            bgPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }
    }

    private val bgPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        Log.d(TAG, "Background location granted: $granted")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestAllPermissions()
        if (intent.getBooleanExtra(EXTRA_OPEN_BROADCASTER, false)) {
            openBroadcastRequest++
            intent.removeExtra(EXTRA_OPEN_BROADCASTER)
        }
        if (intent.getBooleanExtra(EXTRA_OPEN_OBSERVER, false)) {
            openObserverRequest++
            intent.removeExtra(EXTRA_OPEN_OBSERVER)
        }

        setContent {
            PingTheme {
                PingApp(
                    context = this,
                    openBroadcastRequest = openBroadcastRequest,
                    openObserverRequest = openObserverRequest,
                    onStartBroadcast = { pin -> startBroadcasting(pin) },
                    onStopBroadcast = { stopBroadcasting() },
                    onToggleSos = { toggleSos() }
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val config = prefs.getString(KEY_FIREBASE_CONFIG, null)
        SessionNavigation(prefs).observerPin(config)?.let { pin ->
            runCatching { ObserverMonitoring.start(this, pin) }
                .onFailure { Log.w(TAG, "Could not resume observer monitoring", it) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(EXTRA_OPEN_BROADCASTER, false)) {
            openBroadcastRequest++
            intent.removeExtra(EXTRA_OPEN_BROADCASTER)
        }
        if (intent.getBooleanExtra(EXTRA_OPEN_OBSERVER, false)) {
            openObserverRequest++
            intent.removeExtra(EXTRA_OPEN_OBSERVER)
        }
    }

    private fun requestAllPermissions() {
        val fgPermissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            fgPermissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val missing = fgPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            fgPermissionLauncher.launch(missing.toTypedArray())
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            bgPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }
    }

    fun initFirebaseFromConfig(): Boolean {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getString(KEY_FIREBASE_CONFIG, null) == null) return false
        try {
            if (initializeSavedFirebase(this) == null) return false

            // Sign in anonymously (required by Firebase security rules)
            val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
            if (auth.currentUser == null) {
                auth.signInAnonymously().addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Log.d(TAG, "Anonymous auth successful")
                    } else {
                        Log.e(TAG, "Anonymous auth failed: ${task.exception?.message}")
                    }
                }
            }
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Firebase init error: ${e.message}", e)
            return false
        }
    }

    private fun startBroadcasting(pin: String) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(this, "Location permission required!", Toast.LENGTH_LONG).show()
            requestAllPermissions()
            return
        }
        if (!initFirebaseFromConfig()) {
            Toast.makeText(this, "Firebase not configured!", Toast.LENGTH_LONG).show()
            return
        }
        val intent = Intent(this, LocationService::class.java).apply {
            putExtra("pin", pin)
            putExtra("uid", "native-${System.currentTimeMillis()}")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        SessionNavigation(getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)).select("broadcaster")
    }

    private fun stopBroadcasting() {
        val intent = Intent(this, LocationService::class.java).apply { action = "STOP" }
        startService(intent)
    }

    private fun toggleSos() {
        LocationService.isSosActive = !LocationService.isSosActive
        com.ping.app.services.BroadcastSessionStore(
            getSharedPreferences(LocationService.SESSION_PREFS_NAME, Context.MODE_PRIVATE)
        ).setSos(LocationService.isSosActive)
        try {
            val pin = LocationService.activePin
            if (pin.isNotEmpty()) {
                val sessionPrefs = getSharedPreferences(LocationService.SESSION_PREFS_NAME, Context.MODE_PRIVATE)
                val eventId = if (LocationService.isSosActive) java.util.UUID.randomUUID().toString()
                    else sessionPrefs.getString("sos_event_id", "").orEmpty().ifBlank { java.util.UUID.randomUUID().toString() }
                sessionPrefs.edit().putString("sos_event_id", eventId).apply()
                val update = mapOf<String, Any>(
                    "owner_uid" to com.google.firebase.auth.FirebaseAuth.getInstance().currentUser!!.uid,
                    "session_id" to LocationService.activeUid,
                    "sos_active" to LocationService.isSosActive,
                    "sos" to mapOf("active" to LocationService.isSosActive, "event_id" to eventId,
                        "session_id" to LocationService.activeUid, "at" to ServerValue.TIMESTAMP))
                FirebaseDatabase.getInstance().getReference("active_sessions/$pin").updateChildren(update)
                    .addOnFailureListener { Toast.makeText(this, "SOS could not be sent: "+it.message, Toast.LENGTH_LONG).show() }
            }
        } catch (e: Exception) {
            Log.e(TAG, "SOS error: ${e.message}")
        }
    }
}

// ---
// Composables
// ---

val Mono = FontFamily.Monospace

@Composable
fun PingApp(
    context: Context,
    openBroadcastRequest: Int = 0,
    openObserverRequest: Int = 0,
    onStartBroadcast: (String) -> Unit,
    onStopBroadcast: () -> Unit,
    onToggleSos: () -> Unit
) {
    val prefs = context.getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
    val config = prefs.getString(MainActivity.KEY_FIREBASE_CONFIG, null)
    val hasConfig = config != null
    val sessions = remember { SessionNavigation(prefs) }
    val savedBroadcast = com.ping.app.services.BroadcastSessionStore(
        context.getSharedPreferences(LocationService.SESSION_PREFS_NAME, Context.MODE_PRIVATE)).load()
    var observerPin by rememberSaveable(config) { mutableStateOf(sessions.observerPin(config)) }
    var configRevision by remember { mutableIntStateOf(0) }
    val databaseConnected = rememberDatabaseConnection(context, config, configRevision)
    var screen by rememberSaveable { mutableStateOf(resumeScreen(hasConfig, sessions.lastMode(),
        observerPin != null, LocationService.isServiceRunning || savedBroadcast != null)) }

    LaunchedEffect(screen) {
        if (screen == "observer" && observerPin != null) sessions.select("observer")
        if (screen == "broadcaster" && (LocationService.isServiceRunning || savedBroadcast != null)) {
            sessions.select("broadcaster")
        }
    }

    BackHandler(enabled = screen == "observer" || screen == "broadcaster") {
        if (screen != "broadcaster" || !LocationService.isSosActive) screen = "home"
    }

    LaunchedEffect(openObserverRequest) {
        if (openObserverRequest > 0 && hasConfig && observerPin != null) screen = "observer"
    }

    LaunchedEffect(openBroadcastRequest) {
        if (openBroadcastRequest > 0 && hasConfig) screen = "broadcaster"
    }

    Surface(modifier = Modifier.fillMaxSize(), color = PingColors.black) {
        when (screen) {
            "setup" -> SetupScreen(context) { configRevision++; screen = "home" }
            "home" -> HomeScreen(
                databaseConnected = databaseConnected,
                onBroadcast = { screen = "broadcaster" },
                onObserve = { screen = "observer" },
                onReconfigure = { screen = "setup" }
            )
            "broadcaster" -> BroadcasterScreen(
                databaseConnected = databaseConnected,
                context = context,
                onStartBroadcast = onStartBroadcast,
                onStopBroadcast = onStopBroadcast,
                onToggleSos = onToggleSos,
                onBack = { screen = "home" }
            )
            "observer" -> ObserverScreen(
                context = context,
                initialPin = observerPin,
                databaseConnected = databaseConnected,
                onConnect = { observerPin = it; config?.let { saved -> sessions.rememberObserver(it, saved) } },
                onDisconnect = { observerPin = null; sessions.disconnect(); ObserverMonitoring.disconnect(context) },
                onBack = { screen = "home" }
            )
        }
    }
}

// --- Home Screen (Mode Selection) ---
@Composable
fun HomeScreen(databaseConnected: Boolean, onBroadcast: () -> Unit, onObserve: () -> Unit, onReconfigure: () -> Unit) {
    // Pulsing dots animation
    val infiniteTransition = rememberInfiniteTransition(label = "dots")

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Title
        DotHeading("PING")
        Spacer(Modifier.height(4.dp))
        Text("STAY CONNECTED", color = PingColors.textMuted,
            fontSize = 10.sp, fontFamily = Mono, letterSpacing = 2.sp)

        // Database status badge
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier
                .background(PingColors.cardBg, RoundedCornerShape(20.dp))
                .border(1.dp, PingColors.cardBorder, RoundedCornerShape(20.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(if (databaseConnected) PingColors.green else PingColors.textMuted))
            Spacer(Modifier.width(8.dp))
            Text(if (databaseConnected) "DATABASE CONNECTED" else "DATABASE DISCONNECTED", color = PingColors.textSecondary, fontSize = 9.sp,
                fontFamily = Mono, letterSpacing = 2.sp)
        }

        // Animated dot strip
        Spacer(Modifier.height(28.dp))
        Row(horizontalArrangement = Arrangement.Center) {
            repeat(12) { i ->
                val alpha by infiniteTransition.animateFloat(
                    initialValue = 0.15f, targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(600, delayMillis = i * 80, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ), label = "dot$i"
                )
                Box(
                    Modifier.padding(horizontal = 3.dp).size(6.dp).clip(CircleShape)
                        .background(PingColors.red.copy(alpha = alpha))
                )
            }
        }

        Spacer(Modifier.height(28.dp))

        // Share Location Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(PingColors.cardBg, RoundedCornerShape(24.dp))
                .border(1.dp, PingColors.cardBorder, RoundedCornerShape(24.dp))
                .clickable { onBroadcast() }
                .padding(22.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(PingColors.red))
                    Spacer(Modifier.width(10.dp))
                }
                Spacer(Modifier.height(14.dp))
                Text("SHARE LOCATION", color = PingColors.white, fontSize = 18.sp, fontFamily = Mono,
                    fontWeight = FontWeight.SemiBold, letterSpacing = 3.sp)
                Spacer(Modifier.height(8.dp))
                Text("Broadcast your GPS coordinates and generate a session PIN.",
                    color = PingColors.textSecondary, fontSize = 12.sp, fontFamily = Mono, lineHeight = 18.sp)
            }
            Text("\u2192", color = PingColors.textMuted, fontSize = 20.sp,
                modifier = Modifier.align(Alignment.TopEnd))
        }

        Spacer(Modifier.height(14.dp))

        // Track Someone Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(PingColors.cardBg, RoundedCornerShape(24.dp))
                .border(1.dp, PingColors.cardBorder, RoundedCornerShape(24.dp))
                .clickable { onObserve() }
                .padding(22.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(PingColors.active))
                    Spacer(Modifier.width(10.dp))
                }
                Spacer(Modifier.height(14.dp))
                Text("TRACK SOMEONE", color = PingColors.white, fontSize = 18.sp, fontFamily = Mono,
                    fontWeight = FontWeight.SemiBold, letterSpacing = 3.sp)
                Spacer(Modifier.height(8.dp))
                Text("Enter a 6-digit PIN to observe movement on a live map.",
                    color = PingColors.textSecondary, fontSize = 12.sp, fontFamily = Mono, lineHeight = 18.sp)
            }
            Text("\u2192", color = PingColors.textMuted, fontSize = 20.sp,
                modifier = Modifier.align(Alignment.TopEnd))
        }

        Spacer(Modifier.height(40.dp))

        Text("DATABASE SETTINGS", color = PingColors.textMuted, fontSize = 10.sp,
            fontFamily = Mono, letterSpacing = 2.sp,
            modifier = Modifier.clickable { onReconfigure() }.padding(16.dp))
    }
}

// --- Broadcaster Screen ---
@Composable
fun BroadcasterScreen(
    context: Context,
    databaseConnected: Boolean,
    onStartBroadcast: (String) -> Unit,
    onStopBroadcast: () -> Unit,
    onToggleSos: () -> Unit,
    onBack: () -> Unit
) {
    var showStopConfirmation by rememberSaveable { mutableStateOf(false) }
    var isBroadcasting by remember { mutableStateOf(LocationService.isServiceRunning) }
    var isSos by remember { mutableStateOf(LocationService.isSosActive) }
    var pin by rememberSaveable {
        mutableStateOf(LocationService.activePin.ifEmpty {
            com.ping.app.services.BroadcastSessionStore(context.getSharedPreferences(
                LocationService.SESSION_PREFS_NAME, Context.MODE_PRIVATE)).load()?.pin
                ?: (100000..999999).random().toString()
        })
    }
    LaunchedEffect(Unit) {
        while (true) {
            isBroadcasting = LocationService.isServiceRunning
            isSos = LocationService.isSosActive
            if (LocationService.activePin.isNotEmpty()) pin = LocationService.activePin
            delay(1000)
        }
    }

    // Pulse animation for broadcasting indicator
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 2.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ), label = "scale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f, targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ), label = "alpha"
    )

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Back button
        Text("\u2190 BACK", color = PingColors.textMuted, fontSize = 12.sp, fontFamily = Mono,
            letterSpacing = 3.sp,
            modifier = Modifier.align(Alignment.Start).clickable {
                if (!isSos) onBack()
            })

        Spacer(Modifier.weight(1f))

        if (isBroadcasting) {
            // Pulse rings
            Box(contentAlignment = Alignment.Center) {
                Box(
                    Modifier.size(80.dp).scale(pulseScale).alpha(pulseAlpha)
                        .clip(CircleShape).background((if (databaseConnected) PingColors.green else PingColors.textMuted).copy(alpha = 0.3f))
                )
                Box(
                    Modifier.size(80.dp).clip(CircleShape)
                        .background(PingColors.activeSurface)
                        .border(1.dp, PingColors.active, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("\u25c9", color = if (databaseConnected) PingColors.green else PingColors.textMuted, fontSize = 28.sp)
                }
            }

            Spacer(Modifier.height(24.dp))
            ScreenHeading("BROADCASTING")
            Spacer(Modifier.height(16.dp))
            Text(pin, color = PingColors.white, fontSize = 36.sp, fontFamily = Mono,
                fontWeight = FontWeight.Bold, letterSpacing = 8.sp)
            Text("SHARE THIS PIN", color = PingColors.textMuted, fontSize = 10.sp,
                fontFamily = Mono, letterSpacing = 3.sp)

            Spacer(Modifier.height(48.dp))

            // SOS Button
            Button(
                onClick = { onToggleSos(); isSos = !isSos },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isSos) PingColors.red else PingColors.cardBg
                ),
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth().height(60.dp)
                    .then(if (!isSos) Modifier.border(1.dp, PingColors.cardBorder, RoundedCornerShape(28.dp)) else Modifier)
            ) {
                Text(if (isSos) "\u26a0 CANCEL SOS" else "\u26a0 SOS",
                    color = PingColors.white, fontFamily = Mono, fontSize = 16.sp,
                    fontWeight = FontWeight.Bold, letterSpacing = 4.sp)
            }

            Spacer(Modifier.height(12.dp))

            // Stop Button (locked during SOS)
            OutlinedButton(
                onClick = {
                    if (!isSos) {
                        showStopConfirmation = true
                    }
                },
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Text(
                    if (isSos) "LOCKED DURING SOS" else "STOP BROADCASTING",
                    color = if (isSos) PingColors.textMuted else PingColors.white,
                    fontFamily = Mono, letterSpacing = 1.sp
                )
            }
        } else {
            // Not broadcasting - show start
            LocationIntro(sharing = true)

            Spacer(Modifier.height(64.dp))

            Button(
                onClick = { onStartBroadcast(pin); isBroadcasting = true },
                colors = ButtonDefaults.buttonColors(containerColor = PingColors.white),
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth().height(60.dp)
            ) {
                Text("START SHARING", color = PingColors.black, fontFamily = Mono,
                    fontSize = 15.sp, fontWeight = FontWeight.Bold, letterSpacing = 4.sp)
            }
        }

        Spacer(Modifier.weight(1f))
    }
    if (showStopConfirmation) AlertDialog(
        onDismissRequest = { showStopConfirmation = false },
        title = { Text("Stop broadcasting?") },
        text = { Text("Are you sure? Your location and heartbeat will no longer be shared.") },
        dismissButton = {
            TextButton(onClick = { showStopConfirmation = false }) { Text("CANCEL") }
        },
        confirmButton = {
            TextButton(enabled = !isSos, onClick = {
                showStopConfirmation = false
                if (!LocationService.isSosActive) {
                    onStopBroadcast()
                    isBroadcasting = false
                }
            }) { Text("STOP BROADCASTING", color = PingColors.red) }
        }
    )

}

// --- SOS Alarm Helper ---
// --- Observer Screen ---
@Composable
fun ObserverScreen(context: Context, initialPin: String?, databaseConnected: Boolean,
    onConnect: (String) -> Unit, onDisconnect: () -> Unit, onBack: () -> Unit) {
    var pinInput by rememberSaveable { mutableStateOf(initialPin.orEmpty()) }
    var connected by rememberSaveable { mutableStateOf(initialPin != null) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var sosActive by remember { mutableStateOf(false) }
    var sosAcknowledged by rememberSaveable { mutableStateOf(false) }
    var showDisconnectConfirmation by rememberSaveable { mutableStateOf(false) }
    var currentTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var serverTimeOffset by remember { mutableLongStateOf(0L) }
    var lastMovementTime by remember { mutableStateOf<Long?>(null) }
    var movementStatus by remember { mutableStateOf(MovementStatus()) }
    var hasFirstLocation by remember { mutableStateOf(false) }
    var latestLat by remember { mutableStateOf<Double?>(null) }
    var latestLng by remember { mutableStateOf<Double?>(null) }
    var isMapLoaded by remember { mutableStateOf(false) }
    var mapError by remember { mutableStateOf<String?>(null) }
    val webViewRef = remember { mutableStateOf<WebView?>(null) }
    var lastUpdateTime by remember { mutableLongStateOf(0L) }
    var battery by remember { mutableStateOf<Int?>(null) }
    var charging by remember { mutableStateOf(false) }
    var sessionId by remember { mutableStateOf("") }
    var sosEventId by remember { mutableStateOf("") }
    var sosEventTime by remember { mutableLongStateOf(0L) }
    var monitorStartError by remember { mutableStateOf<String?>(null) }
    var monitorRetry by remember { mutableIntStateOf(0) }
    var showMonitorDetails by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current
    var screenVisible by remember { mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, _ ->
            screenVisible = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
            if (screenVisible) webViewRef.value?.onResume() else webViewRef.value?.onPause()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(connected, pinInput, screenVisible, monitorRetry) {
        if (connected && screenVisible) {
            monitorStartError = null
            try { ObserverMonitoring.start(context, pinInput) }
            catch (e: Exception) { monitorStartError = e.message ?: "Could not start SOS monitoring." }
        }
    }
    val monitorError = monitorStartError ?: ObserverService.lastError
    LaunchedEffect(ObserverService.accessDenied) {
        if (connected && ObserverService.accessDenied) {
            errorMsg = ObserverService.lastError
            connected = false
            onDisconnect()
        }
    }
    val notificationsEnabled = androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()
    if (showMonitorDetails) AlertDialog(onDismissRequest = { showMonitorDetails = false },
        title = { Text("Background SOS monitoring") },
        text = { Text(monitorError ?: if (!notificationsEnabled)
            "Allow PING notifications in phone settings so SOS alerts and monitoring status are visible."
            else "Monitoring stays on when you leave the map or close PING from Recents. Set PING battery usage to Unrestricted and keep background network access enabled. Disconnect stops monitoring.") },
        confirmButton = { TextButton(onClick = { showMonitorDetails = false; monitorRetry++ }) { Text("RETRY") } },
        dismissButton = { TextButton(onClick = {
            showMonitorDetails = false
            context.startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                android.net.Uri.parse("package:" + context.packageName)))
        }) { Text("PHONE SETTINGS") } })

    if (showDisconnectConfirmation) AlertDialog(
        onDismissRequest = { showDisconnectConfirmation = false },
        title = { Text("Disconnect?") },
        text = { Text("Are you sure? You will need to enter the PIN again to reconnect.") },
        dismissButton = {
            TextButton(onClick = { showDisconnectConfirmation = false }) { Text("CANCEL") }
        },
        confirmButton = {
            TextButton(onClick = {
                showDisconnectConfirmation = false

                connected = false
                pinInput = ""
                sosActive = false
                sosAcknowledged = false
                onDisconnect()
            }) { Text("DISCONNECT", color = PingColors.red) }
        }
    )

    // Firebase listener - waits for auth before attaching
    DisposableEffect(connected, screenVisible) {
        var disposed = false
        var listener: ValueEventListener? = null
        var dbRef: DatabaseReference? = null
        var offsetRef: DatabaseReference? = null
        var offsetListener: ValueEventListener? = null

        if (connected && screenVisible && pinInput.length == 6) {
            try {
                (context as? MainActivity)?.initFirebaseFromConfig()
                val auth = com.google.firebase.auth.FirebaseAuth.getInstance()

                val attachListener = attach@{
                    if (disposed) return@attach
                    Log.d("PingObserver", "Attaching session listener")
                    val db = FirebaseDatabase.getInstance()
                    dbRef = db.getReference("active_sessions/$pinInput")
                    offsetRef = db.getReference(".info/serverTimeOffset")
                    offsetListener = object : ValueEventListener {
                        override fun onDataChange(snapshot: DataSnapshot) {
                            if (!disposed) serverTimeOffset = snapshot.getValue(Long::class.java) ?: 0L
                        }
                        override fun onCancelled(error: DatabaseError) = Unit
                    }
                    offsetRef?.addValueEventListener(offsetListener!!)

                    listener = object : ValueEventListener {
                        override fun onDataChange(snapshot: DataSnapshot) {
                            if (disposed) return
                            val lat = snapshot.child("lat").getValue(Double::class.java)
                            val lng = snapshot.child("lng").getValue(Double::class.java)
                            val sos = snapshot.child("sos_active").getValue(Boolean::class.java) ?: false
                            battery = snapshot.child("battery_percent").getValue(Int::class.java)?.takeIf { it in 0..100 }
                            charging = snapshot.child("battery_charging").getValue(Boolean::class.java) ?: false
                            sessionId = snapshot.child("session_id").getValue(String::class.java).orEmpty()
                            val newEventId = snapshot.child("sos/event_id").getValue(String::class.java).orEmpty()
                            if (newEventId != sosEventId) sosAcknowledged = false
                            sosEventId = newEventId
                            sosEventTime = snapshot.child("sos/at").getValue(Long::class.java) ?: 0L
                            val ts = snapshot.child("heartbeat_at").getValue(Long::class.java)
                                ?: snapshot.child("timestamp").getValue(Long::class.java) ?: 0L
                            lastMovementTime = snapshot.child("last_moved_at").getValue(Long::class.java)
                            movementStatus = movementStatus.observe(lastMovementTime)

                            // If SOS was deactivated by broadcaster, reset acknowledge
                            if (!sos && sosActive) {
                                sosAcknowledged = false
                            }
                            sosActive = sos
                            lastUpdateTime = ts

                            if (lat != null && lng != null && lat.isFinite() && lng.isFinite() &&
                                lat in -90.0..90.0 && lng in -180.0..180.0) {
                                val locationChanged = latestLat != lat || latestLng != lng
                                latestLat = lat
                                latestLng = lng
                                hasFirstLocation = true
                                if (isMapLoaded && locationChanged) {
                                    val js = "updateLocation($lat, $lng);"
                                    webViewRef.value?.post {
                                        webViewRef.value?.evaluateJavascript(js, null)
                                    }
                                }
                            } else {
                                Log.d("PingObserver", "Snapshot has no lat/lng")
                            }
                        }

                        override fun onCancelled(error: DatabaseError) {
                            if (disposed) return
                            Log.e("PingObserver", "Firebase listener cancelled: ${error.message}")
                            errorMsg = if (error.code == DatabaseError.PERMISSION_DENIED)
                                "Session access denied. It may already have an observer; check the PIN and database rules."
                                else "Cannot read session: ${error.message}"
                            connected = false
                        }
                    }

                    dbRef?.addValueEventListener(listener!!)
                }

                if (auth.currentUser != null) {
                    Log.d("PingObserver", "Already authenticated")
                    attachListener()
                } else {
                    Log.d("PingObserver", "Waiting for anonymous auth...")
                    auth.signInAnonymously().addOnCompleteListener { task ->
                        if (disposed) return@addOnCompleteListener
                        if (task.isSuccessful) {
                            Log.d("PingObserver", "Auth successful")
                            attachListener()
                        } else {
                            Log.e("PingObserver", "Auth failed: ${task.exception?.message}")
                            errorMsg = "Sign-in failed. Check your connection and Firebase anonymous authentication."
                            connected = false
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("PingObserver", "Error connecting: ${e.message}")
                errorMsg = "Connection error: ${e.message}"
                connected = false
            }
        }

        onDispose {
            disposed = true
            listener?.let { dbRef?.removeEventListener(it) }
            offsetListener?.let { offsetRef?.removeEventListener(it) }

        }
    }

    // Age labels tick locally; this does not read or write Firebase every second.
    LaunchedEffect(connected, screenVisible) {
        while (connected && screenVisible) {
            currentTime = System.currentTimeMillis()
            if (sosEventId.isNotEmpty() && ObserverMonitoring.store(context).acknowledged("$sessionId:$sosEventId")) sosAcknowledged = true
            delay(1000)
        }
    }
    val serverNow = currentTime + serverTimeOffset
    val overdue = heartbeatOverdue(lastUpdateTime, serverNow)
    val heartbeatFresh = databaseConnected && lastUpdateTime > 0L && !overdue
    val isMoving = movementStatus.isMoving(serverNow, heartbeatFresh)
    val heartbeatPulse = remember { Animatable(0f) }
    LaunchedEffect(lastUpdateTime) {
        if (heartbeatFresh) {
            heartbeatPulse.snapTo(1f)
            heartbeatPulse.animateTo(0f, animationSpec = tween(1200))
        }
    }

    if (!connected) {
        // PIN Entry Screen
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(60.dp))
            Text("\u2190 BACK", color = PingColors.textMuted, fontSize = 12.sp, fontFamily = Mono,
                letterSpacing = 3.sp,
                modifier = Modifier.align(Alignment.Start).clickable { onBack() })

            Spacer(Modifier.weight(1f))

            LocationIntro(sharing = false)

            Spacer(Modifier.height(40.dp))

            val focusRequester = remember { FocusRequester() }

            Box(contentAlignment = Alignment.Center) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(6) { i ->
                        val filled = pinInput.length > i
                        Box(
                            modifier = Modifier
                                .weight(1f).height(58.dp)
                                .background(
                                    if (filled) PingColors.activeSurface else PingColors.cardBg,
                                    RoundedCornerShape(10.dp)
                                )
                                .border(
                                    1.dp,
                                    if (filled) PingColors.active else PingColors.cardBorder,
                                    RoundedCornerShape(10.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                if (i < pinInput.length) pinInput[i].toString() else "",
                                color = PingColors.white, fontSize = 24.sp,
                                fontFamily = Mono, fontWeight = FontWeight.Light
                            )
                        }
                    }
                }

                BasicTextField(
                    value = pinInput,
                    onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) pinInput = it },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    textStyle = TextStyle(color = Color.Transparent, fontSize = 20.sp, letterSpacing = 36.sp),
                    cursorBrush = SolidColor(Color.Transparent),
                    singleLine = true,
                    modifier = Modifier
                        .matchParentSize()
                        .alpha(0f)
                        .focusRequester(focusRequester)
                )
            }

            LaunchedEffect(Unit) {
                focusRequester.requestFocus()
            }

            errorMsg?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, color = PingColors.red, fontSize = 11.sp, fontFamily = Mono, letterSpacing = 1.sp)
            }

            Spacer(Modifier.height(32.dp))

            val isReady = pinInput.length == 6
            Button(
                onClick = {
                    if (isReady) {
                        errorMsg = null
                        mapError = null
                        battery = null
                        charging = false
                        sessionId = ""
                        sosEventId = ""
                        isMapLoaded = false
                        hasFirstLocation = false
                        latestLat = null
                        latestLng = null
                        lastUpdateTime = 0L
                        lastMovementTime = null
                        movementStatus = MovementStatus()
                        currentTime = System.currentTimeMillis()
                        serverTimeOffset = 0L
                        sosActive = false
                        sosAcknowledged = false
                        connected = true
                        onConnect(pinInput)
                    } else {
                        errorMsg = "Enter a valid 6-digit PIN"
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isReady) PingColors.activeSurface else PingColors.cardBg
                ),
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth().height(56.dp)
                    .border(
                        1.dp,
                        if (isReady) PingColors.active else PingColors.cardBorder,
                        RoundedCornerShape(28.dp)
                    )
            ) {
                Text("CONNECT", color = if (isReady) PingColors.active else PingColors.textMuted,
                    fontFamily = Mono, fontSize = 14.sp, letterSpacing = 4.sp, fontWeight = FontWeight.SemiBold)
            }

            Spacer(Modifier.weight(1f))
        }
    } else {
        // Map Screen
        Box(modifier = Modifier.fillMaxSize()) {
            // WebView with Leaflet map - loaded from assets for reliable external resource loading
            AndroidView(
                factory = { ctx ->
                    val assetLoader = WebViewAssetLoader.Builder()
                        .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(ctx))
                        .build()
                    WebView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        settings.javaScriptEnabled = true
                        settings.allowContentAccess = false
                        settings.allowFileAccess = false
                        settings.userAgentString = settings.userAgentString + " PING/1.1.4 (com.ping.app)"
                        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
                            WebSettingsCompat.setAlgorithmicDarkeningAllowed(settings, false)
                        }
                        webChromeClient = object : WebChromeClient() {
                            override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                                Log.d("PingMap", "${message.messageLevel()}: ${message.message()}")
                                return true
                            }
                        }
                        webViewClient = object : WebViewClient() {
                            override fun shouldInterceptRequest(
                                view: WebView?, request: WebResourceRequest
                            ): WebResourceResponse? = assetLoader.shouldInterceptRequest(request.url)

                            override fun onPageFinished(view: WebView, url: String?) {
                                view.evaluateJavascript("window.mapReady === true") { ready ->
                                    if (webViewRef.value !== view) return@evaluateJavascript
                                    isMapLoaded = ready == "true"
                                    mapError = if (isMapLoaded) null else "Map could not start. Tap to retry."
                                    val lat = latestLat
                                    val lng = latestLng
                                    if (isMapLoaded && lat != null && lng != null) {
                                        view.evaluateJavascript("updateLocation($lat, $lng);", null)
                                    }
                                }
                            }

                            override fun onReceivedError(
                                view: WebView?, request: WebResourceRequest, error: WebResourceError
                            ) {
                                if (request.isForMainFrame) {
                                    isMapLoaded = false
                                    mapError = "Map could not load. Tap to retry."
                                }
                                Log.e("PingObserver", "Map resource error: " + error.description)
                            }
                        }
                        setBackgroundColor(android.graphics.Color.rgb(232, 230, 223))
                        addOnLayoutChangeListener { view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
                            if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop) {
                                (view as WebView).evaluateJavascript(
                                    "if (window.mapReady) { map.invalidateSize(); recenterLocation(); }", null
                                )
                            }
                        }
                        webViewRef.value = this
                        loadUrl("https://appassets.androidplatform.net/assets/map.html")
                    }
                },
                onRelease = { view ->
                    if (webViewRef.value === view) {
                        webViewRef.value = null
                        isMapLoaded = false
                    }
                    view.stopLoading()
                    view.destroy()
                },
                modifier = Modifier.fillMaxSize()
            )

            // Keep the map visible while waiting for a broadcaster's first GPS fix.
            if (mapError != null || !isMapLoaded || !hasFirstLocation) {
                Text(
                    text = mapError ?: if (!isMapLoaded) "LOADING MAP" else "AWAITING LOCATION - CHECK BROADCASTER AND PIN",
                    color = PingColors.white, fontSize = 11.sp, fontFamily = Mono,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 195.dp)
                        .padding(horizontal = 24.dp)
                        .background(Color.Black.copy(alpha = 0.85f), RoundedCornerShape(12.dp))
                        .clickable(enabled = mapError != null) {
                            mapError = null
                            isMapLoaded = false
                            webViewRef.value?.reload()
                        }.padding(14.dp)
                )
            }

            ObserverStatusPill(
                pin = pinInput,
                status = if (sosActive) "SOS ACTIVE" else heartbeatStatus(lastUpdateTime, serverNow, databaseConnected),
                movement = if (isMoving) "MOVING" else compactMovementAge(lastMovementTime, serverNow),
                isMoving = isMoving,
                color = when {
                    sosActive -> PingColors.red
                    heartbeatFresh -> PingColors.green
                    overdue -> PingColors.red
                    else -> PingColors.textMuted
                },
                pulse = if (heartbeatFresh) heartbeatPulse.value else 0f,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 50.dp)
                    .padding(horizontal = 16.dp)
            )

            // Bottom bar
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 24.dp, end = 24.dp, bottom = 40.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!ObserverService.ready || monitorError != null || !notificationsEnabled) {
                    Text(if (monitorError != null || !notificationsEnabled) "SOS MONITORING · TAP FOR DETAILS" else "SOS MONITORING · CONNECTING",
                        color = PingColors.white, fontFamily = Mono, fontSize = 10.sp, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.85f), RoundedCornerShape(12.dp))
                            .clickable { showMonitorDetails = true }.padding(10.dp))
                }
                // Location remains readable even if map tiles cannot be downloaded.
                if (hasFirstLocation && latestLat != null && latestLng != null) {
                    Text(
                        text = String.format(java.util.Locale.US, "%.6f, %.6f  |  RECENTER", latestLat, latestLng),
                        color = PingColors.white, fontFamily = Mono, fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.85f), RoundedCornerShape(12.dp))
                            .clickable {
                                webViewRef.value?.evaluateJavascript(
                                    "if (window.mapReady) recenterLocation();", null
                                )
                            }.padding(14.dp)
                    )
                }
                // SOS Acknowledge button (only when SOS is active and not yet acknowledged)
                if (sosActive && !sosAcknowledged) {
                    // Flashing SOS banner
                    val infiniteTransition = rememberInfiniteTransition(label = "sos_flash")
                    val flashAlpha by infiniteTransition.animateFloat(
                        initialValue = 1f, targetValue = 0.3f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(500, easing = LinearEasing),
                            repeatMode = RepeatMode.Reverse
                        ), label = "flash"
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .alpha(flashAlpha)
                            .background(PingColors.red, RoundedCornerShape(28.dp))
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("\u26a0 SOS TRIGGERED BY BROADCASTER", color = PingColors.white,
                            fontFamily = Mono, fontSize = 12.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            sosAcknowledged = true

                            if (sosEventId.isNotEmpty()) ObserverMonitoring.store(context).acknowledge("$sessionId:$sosEventId")
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A1A1A)),
                        shape = RoundedCornerShape(28.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .border(2.dp, PingColors.red, RoundedCornerShape(28.dp))
                    ) {
                        Text("\u2713 ACKNOWLEDGE SOS", color = PingColors.white, fontFamily = Mono,
                            fontSize = 14.sp, letterSpacing = 3.sp, fontWeight = FontWeight.Bold)
                    }
                } else if (sosActive && sosAcknowledged) {
                    // SOS acknowledged but still active
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF331111), RoundedCornerShape(28.dp))
                            .border(1.dp, PingColors.red, RoundedCornerShape(28.dp))
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("SOS ACKNOWLEDGED \u00b7 STILL ACTIVE", color = PingColors.red,
                            fontFamily = Mono, fontSize = 11.sp, letterSpacing = 2.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                // Navigation buttons (always show)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .width(48.dp).height(52.dp)
                            .background(Color.Black.copy(alpha = 0.85f), RoundedCornerShape(28.dp))
                            .border(1.dp, PingColors.textMuted, RoundedCornerShape(28.dp))
                            .semantics { contentDescription = "Back" }
                            .clickable(role = Role.Button, onClick = onBack),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("\u2190", color = PingColors.textMuted, fontFamily = Mono,
                            fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                    }
                    BatteryPill(battery, charging, heartbeatFresh,
                        Modifier.width(92.dp).height(52.dp))
                    Box(
                        modifier = Modifier
                            .weight(1f).height(52.dp)
                            .background(Color.Black.copy(alpha = 0.85f), RoundedCornerShape(28.dp))
                            .border(1.dp, PingColors.red, RoundedCornerShape(28.dp))
                            .clickable {
                                showDisconnectConfirmation = true
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("DISCONNECT", color = PingColors.red, fontFamily = Mono,
                            fontSize = 12.sp, letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold,
                            maxLines = 1)
                    }
                }
            }
        }
    }
}
