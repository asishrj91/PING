package com.ping.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ObserverStatusPill(pin: String, status: String, movement: String, isMoving: Boolean, color: Color,
    pulse: Float, modifier: Modifier = Modifier) {
    // Invisible sizing text reserves the longest supported values, independent of live data.
    // It uses the same font and user font scale as the visible rows.
    Box(modifier.width(IntrinsicSize.Max)
        .background(Color.Black.copy(alpha = 0.9f), RoundedCornerShape(22.dp))
        .border(1.dp, color, RoundedCornerShape(22.dp))
        .padding(horizontal = 12.dp, vertical = 8.dp)) {
        Column(Modifier.alpha(0f).clearAndSetSemantics {}) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.size(18.dp))
                Text("SOS ACTIVE", fontFamily = Mono, fontSize = 11.sp, maxLines = 1)
                Spacer(Modifier.width(12.dp))
                Text("PIN 123456", fontFamily = Mono, fontSize = 11.sp, maxLines = 1)
            }
            Spacer(Modifier.height(4.dp))
            Row {
                Text("Last movement", fontFamily = Mono, fontSize = 11.sp, maxLines = 1)
                Spacer(Modifier.width(12.dp))
                Text("UNAVAILABLE", fontFamily = Mono, fontSize = 11.sp, maxLines = 1)
            }
        }
        Column(Modifier.matchParentSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth().heightIn(min = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(18.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(7.dp).scale(1f + (1f - pulse) * 1.4f)
                        .alpha(pulse).clip(CircleShape).background(color))
                    Box(Modifier.size(5.dp).clip(CircleShape).background(color))
                }
                Text(status, color = color, fontFamily = Mono, fontSize = 11.sp, maxLines = 1)
                Spacer(Modifier.weight(1f))
                Text("PIN $pin", color = PingColors.textSecondary, fontFamily = Mono, fontSize = 11.sp, maxLines = 1)
            }
            Row(Modifier.fillMaxWidth()) {
                Text(if (isMoving) "Movement" else "Last movement", color = PingColors.textSecondary, fontFamily = Mono, fontSize = 11.sp, maxLines = 1)
                Spacer(Modifier.weight(1f))
                Text(movement, color = if (isMoving) PingColors.green else PingColors.white, fontFamily = Mono, fontSize = 11.sp, maxLines = 1)
            }
        }
    }
}
