package com.sponic.langbang.ui.common

import com.sponic.langbang.ui.theme.LbColors

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sponic.langbang.domain.NowVoicing

/**
 * Single source of truth for the Now Voicing card body — the status header, the English
 * grammatical line, and the big centered Polish row with per-token gloss columns.
 *
 * Two surfaces render this:
 *   1. The sticky header panel at the top of the app (LangbangApp.NowVoicingContent).
 *   2. The in-sheet panel inside the Play Phrases config sheet
 *      (RandomConfigSheet.NowPlayingPanel).
 *
 * Both delegate here so any visual change happens in ONE place. If you need to tweak
 * font size, contrast, spacing, gloss styling, or the masked-letters behaviour during
 * a quiz pause, edit this file — both surfaces pick it up automatically.
 *
 * @param pinned the sentence to display (sticky in the header; the live value in the
 *   sheet). Pass null to render the idle placeholder.
 * @param live the currently-active NowVoicing (drives active-language bolding +
 *   plHidden masking during quiz reveal). When null, nothing is bolded.
 * @param statusText pre-formatted status line shown above the EN (e.g. "NOW VOICING
 *   · 3/12 · PL" or "NOW PLAYING · 4 / 20"). Caller owns the format because each
 *   surface has slightly different status semantics.
 * @param onPlWordClick invoked when the user taps a Polish token (drill-down hook).
 *   Pass {} if the surface doesn't support drill-down.
 * @param idlePlaceholder text shown when pinned is null. Defaults to the header
 *   wording; the sheet overrides with a shorter dash.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NowVoicingBody(
    pinned: NowVoicing?,
    live: NowVoicing?,
    statusText: String,
    onPlWordClick: (String) -> Unit = {},
    idlePlaceholder: String = "Tap “Play Phrases” to start drilling phrases.",
    modifier: Modifier = Modifier
) {
    if (pinned == null) {
        Text(
            idlePlaceholder,
            fontSize = 14.sp,
            color = LbColors.TextMuted,
            modifier = modifier
        )
        return
    }

    val activeLang = live?.lang
    val plActive = activeLang == "pl"
    val slowActive = activeLang == "pl-slow"
    val enActive = activeLang == "en"
    val pausing = activeLang == "pause"
    val plHidden = live?.plHidden == true

    // Prefer the structured per-token field when present. Fall back to whitespace-
    // zipping `literal` for older cached sentences; if literal is also missing,
    // pad with empty strings so every Polish token still gets a (blank) gloss
    // slot — keeps the row height stable.
    val plTokens: List<String>
    val glossTokens: List<String>
    val structuredWords = pinned.words
    if (pinned.words != null && pinned.words.isNotEmpty()) {
        plTokens = pinned.words.map { it.pl }
        glossTokens = pinned.words.map { it.en }
    } else {
        plTokens = pinned.pl.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        glossTokens = pinned.literal
            ?.trim()
            ?.split(Regex("\\s+"))
            ?.filter { it.isNotEmpty() }
            ?: List(plTokens.size) { "" }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (statusText.isNotBlank()) {
            Text(
                statusText,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = LbColors.Label,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Text(
            pinned.en,
            fontSize = 18.sp,
            fontWeight = if (enActive) FontWeight.Bold else FontWeight.SemiBold,
            color = if (enActive) LbColors.TextPrimary else LbColors.TextSecondary
        )
        // Big centered Polish row with per-token gloss columns. Row centers itself
        // in the available width (Arrangement.Center) and each token's column
        // centers its own contents. Gloss row reserved even when missing so columns
        // line up across token-count-1 (verb conjugation) and token-count-N
        // (sentence) renderings.
        val totalChars = plTokens.sumOf { it.length } + (plTokens.size - 1).coerceAtLeast(0) * 2
        val polishFontSize = when {
            totalChars > 55 -> 28.sp
            totalChars > 46 -> 32.sp
            totalChars > 38 -> 36.sp
            totalChars > 30 -> 42.sp
            else -> 52.sp
        }
        val glossFontSize = when {
            totalChars > 46 -> 11.sp
            totalChars > 38 -> 12.sp
            else -> 14.sp
        }
        val tokenPad = if (totalChars > 38) 4.dp else 8.dp
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            plTokens.forEachIndexed { i, plTok ->
                val gloss = glossTokens.getOrNull(i).orEmpty()
                val token = structuredWords?.getOrNull(i)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .padding(horizontal = tokenPad)
                        .clickable { onPlWordClick(plTok) }
                ) {
                    val displayed = if (plHidden) "•".repeat(plTok.length.coerceAtLeast(2))
                                    else plTok
                    val plColor = when {
                            plHidden -> LbColors.TextMuted.copy(alpha = 0.5f)
                            token?.gender != null -> GrammarVisuals.Gender.color(token.gender)
                            plActive || slowActive -> LbColors.TextPrimary
                            pausing -> LbColors.TextSecondary
                            else -> LbColors.TextPrimary
                        }
                    OutlinedPolishText(
                        text = displayed,
                        fillColor = plColor,
                        outlineColor = if (plHidden) null else token?.caseKey?.let { GrammarVisuals.Case.color(it) },
                        fontSize = polishFontSize,
                        fontWeight = FontWeight.Bold,
                        outlineWidth = GrammarVisuals.NounForm.NowVoicingOutlineWidth,
                        backingOutlineExtraWidth = GrammarVisuals.NounForm.NowVoicingBackingExtraWidth,
                        glyphGap = GrammarVisuals.NounForm.NowVoicingGlyphGap,
                        maxLines = 1,
                        softWrap = false
                    )
                    Text(
                        if (plHidden || gloss.isEmpty()) " " else gloss,
                        fontSize = glossFontSize,
                        maxLines = 1,
                        softWrap = false,
                        color = LbColors.Label,
                        fontStyle = FontStyle.Italic,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
