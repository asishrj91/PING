package com.ping.app

import android.content.SharedPreferences

class SessionNavigation(private val prefs: SharedPreferences) {
    fun observerPin(config: String?): String? {
        if (config == null || prefs.getString("observer_config", null) != config) return null
        return prefs.getString("observer_pin", null)?.takeIf { it.length == 6 && it.all { c -> c in '0'..'9' } }
    }
    fun rememberObserver(pin: String, config: String) {
        prefs.edit().putString("observer_pin", pin).putString("observer_config", config)
            .putString("last_session_mode", "observer").apply()
    }
    fun select(mode: String) { prefs.edit().putString("last_session_mode", mode).apply() }
    fun lastMode(): String? = prefs.getString("last_session_mode", null)
    fun disconnect() {
        prefs.edit().remove("observer_pin").remove("observer_config").apply()
    }
    fun clear() {
        prefs.edit().remove("observer_pin").remove("observer_config").remove("last_session_mode").apply()
    }
}

fun resumeScreen(hasConfig: Boolean, lastMode: String?, hasObserver: Boolean, hasBroadcast: Boolean): String = when {
    !hasConfig -> "setup"
    lastMode == "observer" && hasObserver -> "observer"
    hasBroadcast -> "broadcaster"
    hasObserver -> "observer"
    else -> "home"
}
