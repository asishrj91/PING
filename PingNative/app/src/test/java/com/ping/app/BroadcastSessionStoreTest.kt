package com.ping.app

import android.content.SharedPreferences
import com.ping.app.services.BroadcastSessionStore
import com.ping.app.services.SavedBroadcast
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Proxy

class BroadcastSessionStoreTest {
    private fun preferences(): SharedPreferences {
        val values = mutableMapOf<String, Any?>()
        return Proxy.newProxyInstance(SharedPreferences::class.java.classLoader,
            arrayOf(SharedPreferences::class.java)) { _, method, args ->
            when (method.name) {
                "edit" -> {
                    val pending = mutableMapOf<String, Any?>()
                    var clear = false
                    Proxy.newProxyInstance(SharedPreferences.Editor::class.java.classLoader,
                        arrayOf(SharedPreferences.Editor::class.java)) { editor, operation, input ->
                        when {
                            operation.name.startsWith("put") -> {
                                pending[input!![0] as String] = input[1]
                                editor
                            }
                            operation.name == "clear" -> { clear = true; editor }
                            operation.name == "apply" || operation.name == "commit" -> {
                                if (clear) values.clear()
                                pending.forEach { (key, value) ->
                                    if (value == null) values.remove(key) else values[key] = value
                                }
                                if (operation.name == "commit") true else null
                            }
                            else -> error("Unexpected editor operation: ${operation.name}")
                        }
                    }
                }
                "getString", "getBoolean", "getFloat" -> values[args!![0] as String] ?: args[1]
                else -> error("Unexpected preference operation: ${method.name}")
            }
        } as SharedPreferences
    }

    @Test fun systemRestartRestoresIdentitySosAndMovementBaseline() {
        val prefs = preferences()
        val state = SavedBroadcast("123456", "native-session", true, true, 12.3, 77.4, 6f)
        BroadcastSessionStore(prefs).save(state)
        // New store instance represents a new service/process without companion-object state.
        assertEquals(state, BroadcastSessionStore(prefs).load())
    }

    @Test fun explicitStopLeavesNothingToRestore() {
        val prefs = preferences()
        val store = BroadcastSessionStore(prefs)
        store.save(SavedBroadcast("123456", "native-session"))
        store.clear()
        assertNull(BroadcastSessionStore(prefs).load())
    }

    @Test fun freshInstallationDoesNotResumeBroadcasting() {
        assertNull(BroadcastSessionStore(preferences()).load())
    }

    @Test fun sosChangePreservesPublishedSessionAndCoordinates() {
        val store = BroadcastSessionStore(preferences())
        val state = SavedBroadcast("123456", "native-session", false, true, 12.3, 77.4, 6f)
        store.save(state)
        store.setSos(true)
        assertEquals(state.copy(sos = true), store.load())
    }

    @Test fun corruptSessionIsNotResumedAndInvalidCoordinatesAreDiscarded() {
        val store = BroadcastSessionStore(preferences())
        store.save(SavedBroadcast("", "native-session"))
        assertNull(store.load())
        store.save(SavedBroadcast("123456", "native-session", latitude = Double.NaN, longitude = 77.4))
        assertNull(store.load()!!.latitude)
        assertNull(store.load()!!.longitude)
    }
}
