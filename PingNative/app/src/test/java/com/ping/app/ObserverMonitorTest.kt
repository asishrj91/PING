package com.ping.app

import android.content.SharedPreferences
import com.ping.app.monitoring.*
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Proxy

class ObserverMonitorTest {
    private fun prefs(): SharedPreferences {
        val values = mutableMapOf<String, Any?>()
        return Proxy.newProxyInstance(SharedPreferences::class.java.classLoader,
            arrayOf(SharedPreferences::class.java)) { _, method, args ->
            when (method.name) {
                "edit" -> {
                    val pending = mutableMapOf<String, Any?>()
                    Proxy.newProxyInstance(SharedPreferences.Editor::class.java.classLoader,
                        arrayOf(SharedPreferences.Editor::class.java)) { editor, op, input ->
                        when {
                            op.name.startsWith("put") -> { pending[input!![0] as String] = input[1]; editor }
                            op.name == "remove" -> { pending[input!![0] as String] = null; editor }
                            op.name == "apply" || op.name == "commit" -> {
                                pending.forEach { (k, v) -> if (v == null) values.remove(k) else values[k] = v }
                                if (op.name == "commit") true else null
                            }
                            else -> error(op.name)
                        }
                    }
                }
                "getString", "getStringSet" -> values[args!![0] as String] ?: args[1]
                else -> error(method.name)
            }
        } as SharedPreferences
    }
    @Test fun monitoringIdentityAndBoundSessionSurviveProcessRestart() {
        val prefs = prefs()
        val request = MonitorRequest("123456", "saved-config", "connection")
        ObserverMonitorStore(prefs).apply { save(request); bind("broadcast-session") }
        assertEquals(request, ObserverMonitorStore(prefs).load())
        assertEquals("broadcast-session", ObserverMonitorStore(prefs).session())
    }
    @Test fun disconnectPreventsStickyRestore() {
        val prefs = prefs()
        ObserverMonitorStore(prefs).apply {
            save(MonitorRequest("123456", "config", "connection")); bind("session"); clear()
        }
        assertNull(ObserverMonitorStore(prefs).load())
        assertNull(ObserverMonitorStore(prefs).session())
    }
    @Test fun acknowledgementSurvivesRestartAndDoesNotMuteNewSos() {
        val prefs = prefs()
        ObserverMonitorStore(prefs).acknowledge("session:event-a")
        assertTrue(ObserverMonitorStore(prefs).acknowledged("session:event-a"))
        assertFalse(ObserverMonitorStore(prefs).acknowledged("session:event-b"))
        assertFalse(ObserverMonitorStore(prefs).acknowledged("other:event-a"))
    }
    @Test fun anOldDisconnectCannotReleaseANewConnection() {
        assertTrue(canReleaseObserver("user", "a", "user", "a"))
        assertFalse(canReleaseObserver("user", "b", "user", "a"))
        assertFalse(canReleaseObserver("other", "a", "user", "a"))
        assertFalse(canReleaseObserver(null, null, "user", "a"))
    }
    @Test fun rejectsSosFromAnotherSessionAndMissingIdentity() {
        assertNull(monitorSos("s", "other", "event", true))
        assertNull(monitorSos("s", "s", "", true))
        assertNull(monitorSos("s", "s", "event", null))
        assertEquals(MonitorSos("s", "event", false), monitorSos("s", "s", "event", false))
        assertEquals("s:event", monitorSos("s", "s", "event", true)!!.key)
    }
    @Test fun rejectsCorruptSavedConnections() {
        val store = ObserverMonitorStore(prefs())
        assertNull(store.load())
        for (request in listOf(MonitorRequest("12345", "c", "id"), MonitorRequest("123456", "", "id"),
            MonitorRequest("123456", "c", ""))) {
            store.save(request)
            assertNull(store.load())
        }
    }
}
