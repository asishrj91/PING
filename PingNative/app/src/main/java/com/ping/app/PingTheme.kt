package com.ping.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

object PingColors {
    val black = Color(0xFF080808)
    val cardBg = Color(0xFF171717)
    val cardBorder = Color(0xFF383838)
    val white = Color(0xFFF2F2EE)
    val textSecondary = Color(0xFFB8B8B3)
    val textMuted = Color(0xFF999995)
    val active = white
    val green = Color(0xFF4ADE80)
    val activeSurface = Color(0xFF252525)
    val red = Color(0xFFD71921)
}

@Composable
fun PingTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = PingColors.white, onPrimary = PingColors.black,
            primaryContainer = PingColors.activeSurface, onPrimaryContainer = PingColors.white,
            secondary = PingColors.red, onSecondary = Color.White,
            background = PingColors.black, onBackground = PingColors.white,
            surface = PingColors.cardBg, onSurface = PingColors.white,
            surfaceVariant = PingColors.activeSurface, onSurfaceVariant = PingColors.textSecondary,
            outline = PingColors.cardBorder, error = PingColors.red
        ),
        typography = Typography(
            bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 16.sp, lineHeight = 24.sp),
            bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp, lineHeight = 21.sp),
            labelLarge = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp,
                fontWeight = FontWeight.Medium, letterSpacing = 1.5.sp)
        ),
        shapes = Shapes(
            small = RoundedCornerShape(16.dp), medium = RoundedCornerShape(24.dp),
            large = RoundedCornerShape(28.dp)
        ),
        content = content
    )
}

// Original 5x7 dot lettering. No external or proprietary font is required.
private val dotLetters = mapOf(
    'A' to "01110/10001/10001/11111/10001/10001/10001",
    'B' to "11110/10001/10001/11110/10001/10001/11110",
    'C' to "01111/10000/10000/10000/10000/10000/01111",
    'D' to "11110/10001/10001/10001/10001/10001/11110",
    'E' to "11111/10000/10000/11110/10000/10000/11111",
    'G' to "01111/10000/10000/10111/10001/10001/01111",
    'I' to "11111/00100/00100/00100/00100/00100/11111",
    'N' to "10001/11001/11001/10101/10011/10011/10001",
    'O' to "01110/10001/10001/10001/10001/10001/01110",
    'P' to "11110/10001/10001/11110/10000/10000/10000",
    'R' to "11110/10001/10001/11110/10100/10010/10001",
    'S' to "01111/10000/10000/01110/00001/00001/11110",
    'T' to "11111/00100/00100/00100/00100/00100/00100",
    'U' to "10001/10001/10001/10001/10001/10001/01110",
    'V' to "10001/10001/10001/10001/10001/01010/00100"
).mapValues { it.value.split('/') }

@Composable
fun ScreenHeading(text: String) {
    androidx.compose.material3.Text(text, modifier = Modifier.fillMaxWidth().semantics { heading() },
        color = PingColors.white, fontSize = 26.sp, fontWeight = FontWeight.SemiBold,
        fontFamily = FontFamily.SansSerif, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
}

@Composable
fun DotHeading(text: String, modifier: Modifier = Modifier, color: Color = PingColors.white) {
    val label = text.uppercase(Locale.ROOT)
    Canvas(modifier.fillMaxWidth().height(56.dp).semantics {
        contentDescription = text
        heading()
    }) {
        val columns = (label.length * 6 - 1).coerceAtLeast(1)
        val cell = minOf(size.width / columns, size.height / 7)
        val startX = (size.width - columns * cell) / 2
        val startY = (size.height - 7 * cell) / 2
        label.forEachIndexed { index, char ->
            dotLetters[char]?.forEachIndexed { row, bits ->
                bits.forEachIndexed { col, bit ->
                    if (bit == '1') drawCircle(color, cell * 0.32f,
                        Offset(startX + (index * 6 + col + 0.5f) * cell, startY + (row + 0.5f) * cell))
                }
            }
        }
    }
}
