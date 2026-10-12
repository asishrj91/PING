package com.ping.app.monitoring

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.ping.app.*

object ObserverMonitoring {
    fun prefs(context: Context) = context.getSharedPreferences("PingObserverMonitor", Context.MODE_PRIVATE)
    fun store(context: Context) = ObserverMonitorStore(prefs(context))
    fun current(context: Context): MonitorRequest? {
        val request = store(context).load() ?: return null
        val prefs = context.getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
        val config = prefs.getString(MainActivity.KEY_FIREBASE_CONFIG, null)
        return request.takeIf { it.config == config && SessionNavigation(prefs).observerPin(config) == it.pin }
    }
    /** Call from a visible activity, before it goes into the background. */
    fun start(context: Context, pin: String) {
        val prefs = context.getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
        val config = prefs.getString(MainActivity.KEY_FIREBASE_CONFIG, null) ?: error("Configure Firebase first.")
        check(SessionNavigation(prefs).observerPin(config) == pin) { "Connect to a session first." }
        val saved = current(context)
        if (saved == null || saved.pin != pin) {
            disconnect(context)
            store(context).save(MonitorRequest(pin, config, java.util.UUID.randomUUID().toString()))
        }
        ObserverService.prepareStart()
        ContextCompat.startForegroundService(context, Intent(context, ObserverService::class.java))
    }
    fun disconnect(context: Context) {
        val request = store(context).load()
        // Clear first so pending callbacks and sticky restarts cannot bring this session back.
        store(context).clear()
        context.stopService(Intent(context, ObserverService::class.java))
        if (request != null) release(context, request)
    }
    fun release(context: Context, request: MonitorRequest) {
        val prefs = context.getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getString(MainActivity.KEY_FIREBASE_CONFIG, null) != request.config) return
        val app = initializeSavedFirebase(context) ?: return
        val uid = FirebaseAuth.getInstance(app).currentUser?.uid ?: return
        FirebaseDatabase.getInstance(app).getReference("active_sessions/${request.pin}/observer")
            .runTransaction(object : Transaction.Handler {
                override fun doTransaction(data: MutableData): Transaction.Result {
                    if (!canReleaseObserver(data.child("uid").getValue(String::class.java),
                            data.child("connection_id").getValue(String::class.java), uid, request.connectionId))
                        return Transaction.abort()
                    data.value = null
                    return Transaction.success(data)
                }
                override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) = Unit
            }, false)
    }
}
