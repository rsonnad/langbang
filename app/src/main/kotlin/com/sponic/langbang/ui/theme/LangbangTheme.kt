package com.sponic.langbang.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sponic.langbang.R

object LbColors {
    val Primary = Color(0xFF2F6FE6)
    val PrimaryDeep = Color(0xFF102230)
    val PrimarySoft = Color(0xFFE9F1FF)
    val OnPrimary = Color.White

    val Audio = Color(0xFF0FAE89)
    val AudioDark = Color(0xFF0C9676)
    val AudioBright = Color(0xFF27D3A8)
    val Stop = Color(0xFFE0533A)
    val Star = Color(0xFFF2B13C)
    val Chrome = Color(0xFF102230)
    val Bar = Chrome
    val OnDark = Color(0xFFEAF0F3)
    val OnDark2 = Color(0xFF9FB3BF)
    val DarkScope = Color(0xFF4F86F7)

    val Accent = Audio
    val AccentSoft = Color(0xFFE4F8F3)
    val Gold = Star
    val GoldSoft = Color(0xFFFFF4DE)

    val Canvas = Color(0xFFEEF1F5)
    val Surface = Color(0xFFFFFFFF)
    val SurfaceRaised = Color(0xFFFFFFFF)
    val SurfaceTint = Color(0xFFF7F9FB)
    val Sheet = Color(0xFFFFFFFF)
    val Scrim = Color(0xCC102230)
    val Line = Color(0xFFE1E7EB)

    val TextPrimary = Color(0xFF16222C)
    val TextSecondary = Color(0xFF586772)
    val TextMuted = Color(0xFF8B99A3)
    val Label = TextMuted

    val ChipIdle = Color(0xFFFFFFFF)
    val ChipBorder = Line
    val ChipRing = Color(0xFFC8D4DC)

    val Success = AudioDark
    val SuccessSoft = Color(0xFFE4F8F3)
    val Warning = Color(0xFFA86F00)
    val WarningSoft = Color(0xFFFFF4DE)
    val Danger = Stop
    val DangerSoft = Color(0xFFFFE8E3)
}

object LbShapes {
    val Card = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
    val Button = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
    val Inset = androidx.compose.foundation.shape.RoundedCornerShape(11.dp)
    val Pill = androidx.compose.foundation.shape.RoundedCornerShape(999.dp)
}

object LbType {
    val Ui = FontFamily(
        Font(R.font.hanken_grotesk_variable, FontWeight.Normal),
        Font(R.font.hanken_grotesk_variable, FontWeight.Medium),
        Font(R.font.hanken_grotesk_variable, FontWeight.SemiBold),
        Font(R.font.hanken_grotesk_variable, FontWeight.Bold),
        Font(R.font.hanken_grotesk_variable, FontWeight.ExtraBold)
    )
    val Mono = FontFamily(
        Font(R.font.jetbrains_mono_variable, FontWeight.Medium),
        Font(R.font.jetbrains_mono_variable, FontWeight.SemiBold)
    )
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
        fontFamily = LbType.Ui,
        fontSize = 15.sp,
        lineHeight = 21.sp,
        fontWeight = FontWeight.Medium,
        color = LbColors.TextPrimary
    ),
    bodyMedium = TextStyle(
        fontFamily = LbType.Ui,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Medium,
        color = LbColors.TextPrimary
    ),
    labelLarge = TextStyle(
        fontFamily = LbType.Ui,
        fontSize = 15.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Bold
    ),
    titleMedium = TextStyle(
        fontFamily = LbType.Ui,
        fontSize = 18.sp,
        lineHeight = 23.sp,
        fontWeight = FontWeight.Bold
    ),
    headlineSmall = TextStyle(
        fontFamily = LbType.Ui,
        fontSize = 25.sp,
        lineHeight = 31.sp,
        fontWeight = FontWeight.ExtraBold
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
