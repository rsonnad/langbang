package com.sponic.langbang.ui.common

import com.sponic.langbang.ui.theme.LbColors

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sponic.langbang.data.model.SentenceExample

/**
 * One row in any phrase / sentence card: Polish tokens laid out in a FlowRow with
 * the matching English gloss directly underneath each Polish word. Replaces the old
 * pattern of "Polish on one line, literal as one separate text line below" — keeps
 * the eye lined up across the two languages.
 *
 * Token source priority:
 *   1. `sentence.words` — the per-token Polish↔English map populated by Gemini and
 *      the lesson-05 hand-crafted entries. Use this when present.
 *   2. Whitespace-split `sentence.pl` zipped with whitespace-split `sentence.literal`.
 *      Falls out when the literal exists but has no structured words. Mismatched
 *      counts → leave the extra slot's gloss blank.
 *   3. Just `sentence.pl` tokens with no gloss row if neither alignment source exists.
 *
 * Designed to drop into Card content; provide your own background. Padding is up to
 * the caller.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WordAlignedPolish(
    sentence: SentenceExample,
    modifier: Modifier = Modifier,
    plFontSize: TextUnit = 16.sp,
    glossFontSize: TextUnit = 10.sp,
    plColor: Color = LbColors.Primary,
    glossColor: Color = LbColors.Label,
    plFontWeight: FontWeight = FontWeight.Medium,
    horizontalSpacing: androidx.compose.ui.unit.Dp = 8.dp
) {
    val plTokens: List<String>
    val glossTokens: List<String>
    val explicit = sentence.words?.takeIf { it.isNotEmpty() }
    if (explicit != null) {
        plTokens = explicit.map { it.pl }
        glossTokens = explicit.map { it.en }
    } else {
        plTokens = sentence.pl.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        glossTokens = sentence.literal
            ?.trim()
            ?.split(Regex("\\s+"))
            ?.filter { it.isNotEmpty() }
            ?: emptyList()
    }

    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(horizontalSpacing),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        plTokens.forEachIndexed { i, plTok ->
            val gloss = glossTokens.getOrNull(i).orEmpty()
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    plTok,
                    fontSize = plFontSize,
                    fontWeight = plFontWeight,
                    color = plColor
                )
                if (gloss.isNotEmpty()) {
                    Text(
                        gloss,
                        fontSize = glossFontSize,
                        color = glossColor,
                        fontStyle = FontStyle.Italic
                    )
                } else {
                    // Reserve the gloss row height so adjacent columns stay aligned.
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
    }
}
