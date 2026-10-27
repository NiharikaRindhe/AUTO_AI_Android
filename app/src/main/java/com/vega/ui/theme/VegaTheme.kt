package com.vega.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Values from the guide's "UI theme" section.

data class VegaPalette(
    val listening: Color, val thinkingStart: Color, val thinkingEnd: Color,
    val speaking: Color, val success: Color, val warning: Color, val danger: Color,
    val surface: Color, val text: Color, val textSecondary: Color, val outline: Color,
)

val VegaNight = VegaPalette(
    listening = Color(0xFF22D3EE), thinkingStart = Color(0xFF8B5CF6), thinkingEnd = Color(0xFF3B82F6),
    speaking = Color(0xFF14B8A6), success = Color(0xFF22C55E), warning = Color(0xFFF59E0B),
    danger = Color(0xFFEF4444), surface = Color(0xEB111827) /* 92% */, text = Color(0xFFF9FAFB),
    textSecondary = Color(0xFF9CA3AF), outline = Color(0x14FFFFFF) /* 8% white */,
)

val VegaDay = VegaPalette(
    listening = Color(0xFF0891B2), thinkingStart = Color(0xFF7C3AED), thinkingEnd = Color(0xFF2563EB),
    speaking = Color(0xFF0D9488), success = Color(0xFF16A34A), warning = Color(0xFFD97706),
    danger = Color(0xFFDC2626), surface = Color(0xF0F8FAFC) /* 94% */, text = Color(0xFF0F172A),
    textSecondary = Color(0xFF475569), outline = Color(0x140F172A) /* 8% ink */,
)

val VegaBackground = Color(0xFF0B1120)

// The guide uses Noto Sans. Drop noto_sans_regular/medium/semibold.ttf into res/font and switch to
// FontFamily(Font(R.font.noto_sans_regular, FontWeight.Normal), …); until then the system sans is used.
val NotoSans: FontFamily = FontFamily.SansSerif

object VegaType {
    val Greeting = TextStyle(fontFamily = NotoSans, fontWeight = FontWeight.SemiBold, fontSize = 40.sp, lineHeight = 48.sp)
    val CardBody = TextStyle(fontFamily = NotoSans, fontWeight = FontWeight.Normal, fontSize = 32.sp, lineHeight = 42.sp)
    val Caption = TextStyle(fontFamily = NotoSans, fontWeight = FontWeight.Medium, fontSize = 30.sp, lineHeight = 38.sp)
    val CardTitle = TextStyle(fontFamily = NotoSans, fontWeight = FontWeight.SemiBold, fontSize = 28.sp)
    val Button = TextStyle(fontFamily = NotoSans, fontWeight = FontWeight.SemiBold, fontSize = 28.sp)
    val Toast = TextStyle(fontFamily = NotoSans, fontWeight = FontWeight.Medium, fontSize = 24.sp)
    val Hint = TextStyle(fontFamily = NotoSans, fontWeight = FontWeight.Normal, fontSize = 22.sp)
}

object VegaShape {
    val Card = RoundedCornerShape(28.dp)
    val Pill = RoundedCornerShape(50)
}

object VegaSize {
    val OrbIdle = 56.dp
    val OrbActive = 120.dp
    val OrbCompanion = 300.dp
    val EdgeMargin = 24.dp
    val CardMaxWidth = 760.dp
    val CardPadding = 24.dp
    val MinTouch = 76.dp
    val ButtonMinWidth = 160.dp
    val Gap = 16.dp
}

object VegaMotion {
    const val WakeMs = 250
    const val CardMs = 220
    const val ThinkingTurnMs = 2200
    const val SuccessFlashMs = 300
    const val ToastVisibleMs = 2500
    const val CaptionHideMs = 3000
}

@Composable
fun VegaAppTheme(content: @Composable () -> Unit) {
    val p = VegaNight
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = p.listening,
            onPrimary = VegaBackground,
            secondary = p.speaking,
            background = VegaBackground,
            onBackground = p.text,
            surface = Color(0xFF111827),
            onSurface = p.text,
            onSurfaceVariant = p.textSecondary,
            error = p.danger,
            outline = p.textSecondary,
        ),
        content = content,
    )
}
