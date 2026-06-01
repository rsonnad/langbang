package com.sponic.langbang.ui.common

import com.sponic.langbang.ui.theme.LbColors

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

const val EnglishTranslationDelayMillis = 2_000L

@Composable
fun rememberDelayedTranslationVisible(
    key: Any?,
    delayMillis: Long = EnglishTranslationDelayMillis
): Boolean {
    var visible by remember(key, delayMillis) { mutableStateOf(false) }
    LaunchedEffect(key, delayMillis) {
        visible = false
        delay(delayMillis)
        visible = true
    }
    return visible
}

@Composable
fun DelayedEnglishTranslation(
    text: String,
    modifier: Modifier = Modifier,
    delayMillis: Long = EnglishTranslationDelayMillis,
    fontSize: TextUnit = 12.sp,
    color: Color = LbColors.TextSecondary,
    fontWeight: FontWeight? = null,
    fontStyle: FontStyle? = null,
    maxLines: Int = Int.MAX_VALUE,
    reserveSpace: Boolean = true
) {
    if (text.isBlank()) return

    val visible = rememberDelayedTranslationVisible(text, delayMillis)
    if (visible || reserveSpace) {
        Text(
            text,
            modifier = modifier,
            fontSize = fontSize,
            color = if (visible) color else Color.Transparent,
            fontWeight = fontWeight,
            fontStyle = fontStyle,
            maxLines = maxLines
        )
    }
}
