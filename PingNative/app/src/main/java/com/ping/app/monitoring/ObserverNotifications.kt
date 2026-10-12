package com.ping.app.monitoring

import android.app.*
import android.content.*
import android.media.AudioAttributes
import android.media.RingtoneManager
import androidx.core.app.NotificationCompat
import com.ping.app.MainActivity
import com.ping.app.R

object ObserverNotifications {
    const val MONITOR_ID = 2
    const val SOS_ID = 42
    const val MONITOR_CHANNEL = "ping_observer_monitor"
    const val SOS_CHANNEL = "ping_sos_alerts"
    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(MONITOR_CHANNEL, "SOS monitoring",
            NotificationManager.IMPORTANCE_LOW).apply {
            description = "Keeps your observer connected for SOS alerts"
            setSound(null, null); enableVibration(false); setShowBadge(false)
        })
        manager.createNotificationChannel(NotificationChannel(SOS_CHANNEL, "SOS alerts",
            NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Urgent SOS alerts from your broadcaster"
            enableVibration(true)
            setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
        })
    }
    private fun open(context: Context) = PendingIntent.getActivity(context, 42,
        Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN_OBSERVER, true)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    fun monitoring(context: Context, pin: String, connected: Boolean): Notification =
        NotificationCompat.Builder(context, MONITOR_CHANNEL).setSmallIcon(R.drawable.ic_stat_ping)
            .setContentTitle(if (connected) "Monitoring SOS" else "SOS monitoring · reconnecting")
            .setContentText("PIN $pin · Tap to open PING")
            .setContentIntent(open(context)).setOngoing(true).setSilent(true).setOnlyAlertOnce(true)
            .setShowWhen(false).setPriority(NotificationCompat.PRIORITY_LOW).build()
    fun sos(context: Context, pin: String, key: String): Notification {
        val ack = PendingIntent.getBroadcast(context, 43,
            Intent(context, ObserverAcknowledgeReceiver::class.java).putExtra("key", key),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(context, SOS_CHANNEL).setSmallIcon(R.drawable.ic_stat_ping)
            .setContentTitle("SOS · PIN $pin").setContentText("Your broadcaster needs attention.")
            .setCategory(NotificationCompat.CATEGORY_ALARM).setPriority(NotificationCompat.PRIORITY_HIGH)
            // MonitorAlarm owns sound/vibration. Keep the channel's user-selected tone
            // available to it, but suppress Android's separate notification playback.
            .setSilent(true).setOngoing(true).setOnlyAlertOnce(true).setContentIntent(open(context))
            .addAction(0, "ACKNOWLEDGE SOS", ack).addAction(0, "OPEN MAP", open(context)).build()
    }
}

class ObserverAcknowledgeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val key = intent.getStringExtra("key") ?: return
        ObserverMonitoring.store(context).acknowledge(key)
        // The service observes acknowledgement preferences; no new background service is started here.
        if (ObserverMonitoring.prefs(context).getString("active_sos_key", null) == key)
            context.getSystemService(NotificationManager::class.java).cancel(ObserverNotifications.SOS_ID)
    }
}
