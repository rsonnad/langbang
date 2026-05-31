package com.sponic.langbang.ui.theme

import com.sponic.langbang.ui.theme.LbColors

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object LbColors {
    val Primary = Color(0xFF155E63)
    val PrimaryDeep = Color(0xFF0B3438)
    val PrimarySoft = Color(0xFFE3F1F0)
    val OnPrimary = Color.White

    val Accent = Color(0xFFE05E46)
    val AccentSoft = Color(0xFFFFE7DF)
    val Gold = Color(0xFFD99A2B)
    val GoldSoft = Color(0xFFFFF1D6)

    val Canvas = Color(0xFFF4F7F2)
    val Surface = Color(0xFFFFFFFF)
    val SurfaceRaised = Color(0xFFFAFCF8)
    val SurfaceTint = Color(0xFFEAF1ED)
    val Sheet = Color(0xFFFBFCF7)
    val Scrim = Color(0xCC0B3438)

    val TextPrimary = Color(0xFF172322)
    val TextSecondary = Color(0xFF586765)
    val TextMuted = Color(0xFF7A8784)
    val Label = Color(0xFF5C6F2C)

    val ChipIdle = Color(0xFFF0F4EE)
    val ChipBorder = Color(0xFFC8D4CF)
    val ChipRing = Color(0xFF8EA19A)

    val Success = Color(0xFF1F7A4D)
    val SuccessSoft = Color(0xFFE4F4EA)
    val Warning = Color(0xFFA86117)
    val WarningSoft = Color(0xFFFFF1D6)
    val Danger = Color(0xFFB53B2D)
    val DangerSoft = Color(0xFFFFE7DF)
}

private val langbangScheme = lightColorScheme(
    primary = LbColors.Primary,
    onPrimary = LbColors.OnPrimary,
    primaryContainer = LbColors.PrimarySoft,
    onPrimaryContainer = LbColors.PrimaryDeep,
    secondary = LbColors.Accent,
    onSecondary = LbColors.OnPrimary,
    secondaryContainer = LbColors.AccentSoft,
    tertiary = LbColors.Gold,
    background = LbColors.Canvas,
    onBackground = LbColors.TextPrimary,
    surface = LbColors.Surface,
    onSurface = LbColors.TextPrimary,
    surfaceVariant = LbColors.SurfaceTint,
    onSurfaceVariant = LbColors.TextSecondary,
    error = LbColors.Danger
)

private val langbangTypography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        color = LbColors.TextPrimary
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        color = LbColors.TextPrimary
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 13.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.SemiBold
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 18.sp,
        lineHeight = 23.sp,
        fontWeight = FontWeight.SemiBold
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 26.sp,
        lineHeight = 31.sp,
        fontWeight = FontWeight.Bold
    )
)

@Composable
fun LangbangTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = langbangScheme,
        typography = langbangTypography,
        content = content
    )
}
