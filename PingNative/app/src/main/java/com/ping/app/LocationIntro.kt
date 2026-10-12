package com.ping.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun LocationIntro(sharing: Boolean) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        // Decorative outline symbols; the adjacent heading supplies the accessible label.
        Canvas(Modifier.size(40.dp)) {
            scale(size.width / 40f, size.height / 40f, pivot = Offset.Zero) {
                val color = PingColors.white
                val stroke = Stroke(width = 1.7f, cap = StrokeCap.Round)
                val pin = Path().apply {
                    moveTo(20f, 29f)
                    cubicTo(18f, 26f, 13f, 21f, 13f, 17f)
                    cubicTo(13f, 8f, 27f, 8f, 27f, 17f)
                    cubicTo(27f, 21f, 22f, 26f, 20f, 29f)
                    close()
                }
                if (sharing) {
                    drawPath(pin, color, style = stroke)
                    drawCircle(color, 2.5f, Offset(20f, 17f), style = stroke)
                    drawArc(color, 145f, 70f, false, Offset(7f, 5f), Size(26f, 26f), style = stroke)
                    drawArc(color, -35f, 70f, false, Offset(7f, 5f), Size(26f, 26f), style = stroke)
                    drawArc(color, 145f, 70f, false, Offset(1f, -1f), Size(38f, 38f), style = stroke)
                    drawArc(color, -35f, 70f, false, Offset(1f, -1f), Size(38f, 38f), style = stroke)
                } else {
                    drawCircle(color, 13f, Offset(18f, 17f), style = stroke)
                    drawLine(color, Offset(27.5f, 26.5f), Offset(37f, 36f), 2f, StrokeCap.Round)
                    scale(0.65f, 0.65f, pivot = Offset(14f, 13f)) {
                        drawPath(pin, color, style = Stroke(2.4f))
                        drawCircle(color, 2.5f, Offset(20f, 17f), style = Stroke(2.4f))
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        ScreenHeading(if (sharing) "SHARE LOCATION" else "TRACK SOMEONE")
        Spacer(Modifier.height(10.dp))
        Text(if (sharing) "Share your live location with a PIN." else "Enter a PIN to view their live location.",
            color = PingColors.textMuted, fontSize = 12.sp, lineHeight = 18.sp,
            textAlign = TextAlign.Center)
    }
}
