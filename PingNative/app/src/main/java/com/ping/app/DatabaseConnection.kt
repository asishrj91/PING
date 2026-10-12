package com.ping.app

import android.content.Context
import androidx.compose.runtime.*
import com.google.firebase.database.*

@Composable
fun rememberDatabaseConnection(context: Context, config: String?, revision: Int): Boolean {
    var connected by remember(config, revision) { mutableStateOf(false) }
    DisposableEffect(config, revision) {
        var disposed = false
        var reference: DatabaseReference? = null
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!disposed) connected = snapshot.getValue(Boolean::class.java) == true
            }
            override fun onCancelled(error: DatabaseError) {
                if (!disposed) connected = false
            }
        }
        if (config != null) {
            try {
                if ((context as? MainActivity)?.initFirebaseFromConfig() == true) {
                    reference = FirebaseDatabase.getInstance().getReference(".info/connected")
                    reference.addValueEventListener(listener)
                }
            } catch (_: Exception) {
                connected = false
            }
        }
        onDispose {
            disposed = true
            reference?.removeEventListener(listener)
        }
    }
    return connected
}
