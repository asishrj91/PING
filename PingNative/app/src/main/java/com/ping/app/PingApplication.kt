package com.ping.app

import android.app.Application
import android.content.Context
import com.google.firebase.FirebaseApp

class PingApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initializeSavedFirebase(this)
    }
}

fun initializeSavedFirebase(context: Context): FirebaseApp? = runCatching {
    FirebaseApp.getApps(context).firstOrNull { it.name == FirebaseApp.DEFAULT_APP_NAME } ?: run {
        val text = context.getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
            .getString(MainActivity.KEY_FIREBASE_CONFIG, null) ?: return@run null
        FirebaseApp.initializeApp(context, configOptions(FirebaseConfig.parse(text)))
    }
}.getOrNull()
