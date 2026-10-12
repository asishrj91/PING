package com.ping.app.services

import android.content.SharedPreferences

data class SavedBroadcast(
    val pin: String,
    val uid: String,
    val sos: Boolean = false,
    val published: Boolean = false,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val accuracy: Float = 0f
)

/** Separate from Firebase configuration; cleared only by an explicit broadcast stop. */
class BroadcastSessionStore(private val preferences: SharedPreferences) {
    fun load(): SavedBroadcast? {
        if (!preferences.getBoolean("enabled", false)) return null
        val pin = preferences.getString("pin", null) ?: return null
        val uid = preferences.getString("uid", null) ?: return null
        if (pin.length != 6 || !pin.all { it in '0'..'9' } || uid.isBlank()) return null
        val lat = preferences.getString("latitude", null)?.toDoubleOrNull()
        val lng = preferences.getString("longitude", null)?.toDoubleOrNull()
        val validLocation = lat != null && lng != null && lat.isFinite() && lng.isFinite() &&
            lat in -90.0..90.0 && lng in -180.0..180.0
        return SavedBroadcast(pin, uid, preferences.getBoolean("sos", false),
            preferences.getBoolean("published", false),
            if (validLocation) lat else null, if (validLocation) lng else null,
            preferences.getFloat("accuracy", 0f))
    }

    fun save(state: SavedBroadcast) {
        preferences.edit().putBoolean("enabled", true)
            .putString("pin", state.pin).putString("uid", state.uid)
            .putBoolean("sos", state.sos).putBoolean("published", state.published)
            .putString("latitude", state.latitude?.toString())
            .putString("longitude", state.longitude?.toString())
            .putFloat("accuracy", state.accuracy).apply()
    }

    fun setSos(active: Boolean) { preferences.edit().putBoolean("sos", active).apply() }
    fun clear() { preferences.edit().clear().apply() }
}
