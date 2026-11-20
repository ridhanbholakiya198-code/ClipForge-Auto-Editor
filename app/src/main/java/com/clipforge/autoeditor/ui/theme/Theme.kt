package com.clipforge.autoeditor.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object ClipForgeColors {
    val AmoledBlack = Color(0xFF000000)
    val CardSurface = Color(0xFF121212)
    val ElevatedSurface = Color(0xFF1C1C1E)
    val BorderSubtle = Color(0xFF2A2A2E)
    val CrimsonAccent = Color(0xFFFF2E4C)
    val CrimsonContainer = Color(0xFF3D0A14)
    val SurvivedGreen = Color(0xFF00E676)
    val SurvivedBg = Color(0xFF062E19)
    val TragicRed = Color(0xFFFF3B30)
    val TragicBg = Color(0xFF380D0B)
    val TextPrimary = Color(0xFFF5F5F7)
    val TextSecondary = Color(0xFF9A9AA0)
    val TextMuted = Color(0xFF636368)
}

private val AmoledDarkColorScheme = darkColorScheme(
    primary = ClipForgeColors.CrimsonAccent,
    onPrimary = Color.White,
    primaryContainer = ClipForgeColors.CrimsonContainer,
    onPrimaryContainer = Color(0xFFFFB3BE),
    secondary = Color(0xFF29B6F6),
    onSecondary = Color.Black,
    background = ClipForgeColors.AmoledBlack,
    onBackground = ClipForgeColors.TextPrimary,
    surface = ClipForgeColors.CardSurface,
    onSurface = ClipForgeColors.TextPrimary,
    surfaceVariant = ClipForgeColors.ElevatedSurface,
    onSurfaceVariant = ClipForgeColors.TextSecondary,
    outline = ClipForgeColors.BorderSubtle,
    error = ClipForgeColors.TragicRed,
    onError = Color.White
)

private val ClipForgeTypography = Typography(
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        letterSpacing = (-0.3).sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        letterSpacing = 0.4.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp
    )
)

@Composable
fun ClipForgeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AmoledDarkColorScheme,
        typography = ClipForgeTypography,
        content = content
    )
}
