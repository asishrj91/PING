package com.ping.app.monitoring

import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.*

/** Owned by the existing monitoring service, so SOS doesn't need to launch a new background service. */
class MonitorAlarm(private val context: Context) {
    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    fun start() {
        stop()
        val channel = context.getSystemService(NotificationManager::class.java)
            .getNotificationChannel(ObserverNotifications.SOS_CHANNEL) ?: return
        if (channel.importance < NotificationManager.IMPORTANCE_DEFAULT) return
        channel.sound?.let { uri ->
            runCatching {
                player = MediaPlayer().apply {
                    setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                    setWakeMode(context, PowerManager.PARTIAL_WAKE_LOCK)
                    setDataSource(context, uri)
                    isLooping = true
                    setOnPreparedListener { if (player === it) it.start() }
                    setOnErrorListener { _, _, _ -> true }
                    prepareAsync()
                }
            }
        }
        if (channel.shouldVibrate()) runCatching {
            vibrator = if (Build.VERSION.SDK_INT >= 31)
                context.getSystemService(VibratorManager::class.java).defaultVibrator
            else @Suppress("DEPRECATION") (context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator)
            vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 800, 200, 800, 400), 0))
        }
    }
    fun stop() {
        runCatching { player?.release() }; player = null
        runCatching { vibrator?.cancel() }; vibrator = null
    }
}
