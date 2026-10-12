package com.ping.app

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

fun batteryPercent(level: Int, scale: Int): Int? =
    if (scale <= 0 || level < 0 || level > scale) null else (level.toLong() * 100 / scale).toInt()

fun batteryUpdate(context: Context): Map<String, Any> {
    val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return emptyMap()
    val percent = batteryPercent(battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1),
        battery.getIntExtra(BatteryManager.EXTRA_SCALE, -1)) ?: return emptyMap()
    val status = battery.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
    return mapOf("battery_percent" to percent, "battery_charging" to
        (status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL))
}

@Composable
fun BatteryPill(percent: Int?, charging: Boolean, fresh: Boolean, modifier: Modifier = Modifier) {
    val label = if (percent == null) "BAT --" else "${if (charging) "\u26a1" else "BAT"} $percent%"
    Box(modifier = modifier.alpha(if (fresh) 1f else 0.55f)
            .semantics { contentDescription = "Broadcaster battery: ${percent?.let { "$it percent" } ?: "unavailable"}" +
                (if (charging) ", charging" else "") + (if (!fresh) ", last reported" else "") }
            .background(Color.Black.copy(alpha = 0.9f), RoundedCornerShape(28.dp))
            .border(1.dp, PingColors.cardBorder, RoundedCornerShape(28.dp))
            .padding(horizontal = 8.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center) {
        Text(label, fontFamily = Mono, fontSize = 11.sp, maxLines = 1,
            color = if (percent != null && percent <= 20) PingColors.red else PingColors.white)
    }
}
