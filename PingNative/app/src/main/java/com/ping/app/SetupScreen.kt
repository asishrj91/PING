package com.ping.app

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.ping.app.services.BroadcastSessionStore
import com.ping.app.services.LocationService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

private val configObjectSaver = Saver<JSONObject, String>(
    save = { it.toString() }, restore = { JSONObject(it) })
private val configFieldsSaver = listSaver<Map<String, String>, String>(
    save = { values -> FirebaseConfig.keys.map { values[it].orEmpty() } },
    restore = { values -> FirebaseConfig.keys.zip(values).toMap() })

internal fun configOptions(json: JSONObject): FirebaseOptions {
    val values = FirebaseConfig.fields(json)
    return FirebaseOptions.Builder()
        .setApplicationId(values["appId"].orEmpty().ifBlank { "1:000000000000:android:0000000000000000" })
        .setApiKey(values.getValue("apiKey"))
        .setDatabaseUrl(values.getValue("databaseURL"))
        .apply {
            values["projectId"]?.takeIf { it.isNotBlank() }?.let { setProjectId(it) }
        }.build()
}

@Composable
fun SetupScreen(context: Context, onComplete: () -> Unit) {
    val prefs = remember { context.getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE) }
    var savedConfig by remember { mutableStateOf(prefs.getString(MainActivity.KEY_FIREBASE_CONFIG, null)) }
    var configText by rememberSaveable { mutableStateOf(savedConfig?.let {
        runCatching { FirebaseConfig.parse(it).toString(2) }.getOrDefault(it)
    } ?: "") }
    var fieldMode by rememberSaveable { mutableStateOf(false) }
    var base by rememberSaveable(stateSaver = configObjectSaver) { mutableStateOf(JSONObject()) }
    var values by rememberSaveable(stateSaver = configFieldsSaver) { mutableStateOf(FirebaseConfig.fields(JSONObject())) }
    var error by remember { mutableStateOf<String?>(null) }
    var connecting by remember { mutableStateOf(false) }
    var showReset by rememberSaveable { mutableStateOf(false) }
    var showSample by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun switchMode(useFields: Boolean) {
        if (fieldMode == useFields) return
        try {
            if (useFields) {
                base = if (configText.isBlank()) JSONObject() else FirebaseConfig.parse(configText)
                values = FirebaseConfig.fields(base)
            } else configText = FirebaseConfig.mergeFields(base, values).toString(2)
            fieldMode = useFields
            error = null
        } catch (e: Exception) {
            error = "Fix the config before switching: ${e.message}"
        }
    }

    Column(Modifier.fillMaxSize().imePadding().padding(horizontal = 24.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (savedConfig != null) {
            Text("\u2190 BACK", color = PingColors.textMuted, fontSize = 12.sp, fontFamily = Mono,
                letterSpacing = 3.sp, modifier = Modifier.align(Alignment.Start)
                    .clickable(enabled = !connecting, onClick = onComplete).padding(vertical = 8.dp))
        }
        ScreenHeading("DATABASE SETUP")
        Text(if (savedConfig != null) "Edit your saved config below." else "Paste JSON or enter individual values.",
            color = PingColors.textSecondary, fontSize = 12.sp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf("JSON" to false, "FIELDS" to true).forEach { (label, mode) ->
                OutlinedButton(onClick = { switchMode(mode) }, enabled = !connecting,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (fieldMode == mode) PingColors.white else PingColors.cardBg,
                        contentColor = if (fieldMode == mode) PingColors.black else PingColors.white)) {
                    Text(label, fontFamily = Mono)
                }
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
        if (fieldMode) {
            val columns = if (maxWidth >= 300.dp) 2 else 1
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
            FirebaseConfig.keys.chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { key ->
                OutlinedTextField(value = values[key].orEmpty(),
                    onValueChange = { values = values + (key to it); error = null },
                    enabled = !connecting, singleLine = true, modifier = Modifier.weight(1f),
                    label = { Text(key + if (key in listOf("apiKey", "databaseURL")) " *" else "", fontSize = 10.sp) },
                    textStyle = TextStyle(fontFamily = Mono, fontSize = 13.sp))
                }
                }
            }
            Text("* Required",
                color = PingColors.textMuted, fontSize = 12.sp)
            }
        } else {
            OutlinedTextField(value = configText, onValueChange = { configText = it; error = null },
                enabled = !connecting, modifier = Modifier.fillMaxSize(), minLines = 1,
                label = { Text("Firebase config", fontFamily = Mono) },
                placeholder = { Text(FirebaseConfig.sample, fontFamily = Mono, fontSize = 12.sp) },
                textStyle = TextStyle(fontFamily = Mono, fontSize = 13.sp))
        }
        }
        TextButton(onClick = { showSample = true }, modifier = Modifier.height(40.dp)) { Text("VIEW SAMPLE", fontFamily = Mono) }
        error?.let { Text(it, color = PingColors.red, fontSize = 12.sp,
            modifier = Modifier.heightIn(max = 64.dp).verticalScroll(rememberScrollState())) }
        Button(enabled = !connecting, modifier = Modifier.fillMaxWidth().height(48.dp), onClick = {
            if (LocationService.isServiceRunning) {
                error = "Stop broadcasting before changing the database config."
                return@Button
            }
            try {
                val json = if (fieldMode) FirebaseConfig.mergeFields(base, values) else FirebaseConfig.parse(configText)
                FirebaseConfig.validate(json)
                val options = configOptions(json)
                connecting = true
                error = null
                scope.launch {
                    var verificationApp: FirebaseApp? = null
                    try {
                        verificationApp = FirebaseApp.initializeApp(context, options, "verify_${System.nanoTime()}")
                        withTimeout(30_000) { FirebaseAuth.getInstance(verificationApp).signInAnonymously().await() }
                        check(!LocationService.isServiceRunning) { "Stop broadcasting before changing the database config." }
                        context.stopService(Intent(context, LocationService::class.java))
                        BroadcastSessionStore(context.getSharedPreferences(LocationService.SESSION_PREFS_NAME, Context.MODE_PRIVATE)).clear()
                        val previous = FirebaseApp.getApps(context).firstOrNull { it.name == FirebaseApp.DEFAULT_APP_NAME }
                        val previousOptions = previous?.options
                        com.ping.app.monitoring.ObserverMonitoring.disconnect(context)
                        previous?.delete()
                        try {
                            FirebaseApp.initializeApp(context, options)
                        } catch (e: Exception) {
                            previousOptions?.let { FirebaseApp.initializeApp(context, it) }
                            throw e
                        }
                        prefs.edit().putString(MainActivity.KEY_FIREBASE_CONFIG, json.toString()).apply()
                        if (savedConfig != json.toString()) SessionNavigation(prefs).clear()
                        savedConfig = json.toString()
                        onComplete()
                    } catch (e: TimeoutCancellationException) {
                        error = "Verification timed out. Check your connection and try again."
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        error = "Could not save config: ${e.message}"
                    } finally {
                        verificationApp?.delete()
                        connecting = false
                    }
                }
            } catch (e: Exception) { error = "Check your config: ${e.message}" }
        }) {
            Text(if (connecting) "VERIFYING..." else "VERIFY & SAVE CONFIG", fontFamily = Mono)
        }
        OutlinedButton(enabled = !connecting, modifier = Modifier.fillMaxWidth().height(48.dp),
            onClick = {
                if (LocationService.isServiceRunning) error = "Stop broadcasting before resetting the config."
                else showReset = true
            }) { Text("RESET CONFIG", color = PingColors.red, fontFamily = Mono) }
    }

    if (showSample) AlertDialog(onDismissRequest = { showSample = false },
        title = { Text("Sample Firebase config") },
        text = { SelectionContainer { Text(FirebaseConfig.sample, fontFamily = Mono, fontSize = 12.sp,
            modifier = Modifier.verticalScroll(rememberScrollState())) } },
        confirmButton = { TextButton(onClick = { showSample = false }) { Text("CLOSE") } })

    if (showReset) AlertDialog(onDismissRequest = { showReset = false },
        title = { Text("Reset database config?") },
        text = { Text("This removes the saved config and your edits from this phone. Your Firebase project and its data will not be deleted.") },
        dismissButton = { TextButton(onClick = { showReset = false }) { Text("CANCEL") } },
        confirmButton = { TextButton(onClick = {
            showReset = false
            if (LocationService.isServiceRunning) {
                error = "Stop broadcasting before resetting the config."
            } else {
                context.stopService(Intent(context, LocationService::class.java))
                BroadcastSessionStore(context.getSharedPreferences(LocationService.SESSION_PREFS_NAME, Context.MODE_PRIVATE)).clear()
                com.ping.app.monitoring.ObserverMonitoring.disconnect(context)
                FirebaseApp.getApps(context).firstOrNull { it.name == FirebaseApp.DEFAULT_APP_NAME }?.delete()
                prefs.edit().remove(MainActivity.KEY_FIREBASE_CONFIG).apply()
                SessionNavigation(prefs).clear()
                savedConfig = null
                configText = ""
                base = JSONObject()
                values = FirebaseConfig.fields(base)
                error = null
            }
        }) { Text("RESET", color = PingColors.red) } })
}
