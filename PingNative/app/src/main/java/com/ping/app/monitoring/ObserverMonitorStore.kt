package com.ping.app.monitoring

import android.content.SharedPreferences

data class MonitorRequest(val pin: String, val config: String, val connectionId: String)

class ObserverMonitorStore(private val prefs: SharedPreferences) {
    fun load(): MonitorRequest? {
        val pin = prefs.getString("pin", null) ?: return null
        val config = prefs.getString("config", null) ?: return null
        val id = prefs.getString("connection_id", null) ?: return null
        return if (pin.length == 6 && pin.all { it in '0'..'9' } && config.isNotBlank() && id.isNotBlank())
            MonitorRequest(pin, config, id) else null
    }
    fun save(request: MonitorRequest) {
        prefs.edit().putString("pin", request.pin).putString("config", request.config)
            .putString("connection_id", request.connectionId).commit()
    }
    fun bind(session: String) { prefs.edit().putString("session", session).apply() }
    fun session(): String? = prefs.getString("session", null)
    fun clear() { prefs.edit().remove("pin").remove("config").remove("connection_id").remove("session").commit() }
    fun acknowledged(key: String): Boolean = prefs.getStringSet("handled", emptySet())!!.contains(key)
    fun acknowledge(key: String) {
        if (key.isBlank()) return
        val handled = prefs.getStringSet("handled", emptySet())!!.toMutableSet()
        if (handled.size >= 100) handled.clear()
        handled.add(key)
        prefs.edit().putStringSet("handled", handled).commit()
    }
}

/** Only a matching connection may release a claim; an older disconnect cannot remove a new connection. */
fun canReleaseObserver(uid: String?, connection: String?, expectedUid: String, expectedConnection: String) =
    uid == expectedUid && connection == expectedConnection

data class MonitorSos(val session: String, val id: String, val active: Boolean) {
    val key get() = "$session:$id"
}

fun monitorSos(expectedSession: String, session: String?, id: String?, active: Boolean?): MonitorSos? =
    if (session != expectedSession || id.isNullOrBlank() || active == null) null
    else MonitorSos(session, id, active)
