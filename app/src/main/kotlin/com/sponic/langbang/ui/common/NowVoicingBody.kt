package com.sponic.langbang.ui.common

import com.sponic.langbang.ui.theme.LbColors

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sponic.langbang.domain.NowVoicing

/**
 * Single source of truth for the Now Voicing card body: status, delayed English,
 * grammar reference, centered Polish, and word-for-word glosses. The outer panel
 * chrome also lives in [NowVoicingPanel], so every screen renders the same component.
 *
 * @param pinned the sentence to display (sticky in the header; the live value in the
 *   sheet). Pass null to render the idle placeholder.
 * @param live the currently-active NowVoicing (drives active-language bolding +
 *   plHidden masking during quiz reveal). When null, nothing is bolded.
 * @param statusText pre-formatted status line shown above the EN.
     * @param onPlWordClick invoked when the user taps a Polish token.
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
    val structuredWords = pinned.words
    // Token + gloss splitting (and the Regex) depend only on the sentence content, which
    // changes once per item — not per en/pl/pause segment that re-runs this composable.
    // Memoise on the content so each playback tick doesn't re-split + re-allocate.
    val tokenPair = remember(pinned.pl, pinned.words, pinned.literal) {
        val pl: List<String>
        val gloss: List<String>
        if (structuredWords != null && structuredWords.isNotEmpty()) {
            pl = structuredWords.map { it.pl }
            gloss = structuredWords.map { it.en }
        } else {
            pl = pinned.pl.trim().split(NV_WHITESPACE).filter { it.isNotEmpty() }
            gloss = pinned.literal?.trim()?.split(NV_WHITESPACE)?.filter { it.isNotEmpty() }
                ?: List(pl.size) { "" }
        }
        pl to gloss
    }
    val plTokens = tokenPair.first
    val glossTokens = tokenPair.second
    val showEnglish = rememberDelayedTranslationVisible(
        key = "${pinned.pl}\n${pinned.en}\n${pinned.literal.orEmpty()}"
    )
    val englishText = pinned.en
    val grammarReference = remember(pinned.words) { nowVoicingGrammarReference(pinned) }
    val primaryText = LbColors.TextPrimary
    val secondaryText = LbColors.TextSecondary
    val mutedText = LbColors.TextMuted
    val accentText = LbColors.Label
    val statusLines = statusText.lines().filter { it.isNotBlank() }
    val statusLabel = statusLines.firstOrNull().orEmpty()
    val statusDetail = statusLines.drop(1).joinToString(" · ")

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            Column(
                modifier = Modifier.width(136.dp),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                if (statusLabel.isNotBlank()) {
                    Text(
                        statusLabel,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = LbColors.Label,
                        maxLines = 1
                    )
                }
                if (statusDetail.isNotBlank()) {
                    Text(
                        statusDetail,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = LbColors.Label,
                        maxLines = 1
                    )
                }
            }
            DelayedEnglishTranslation(
                text = englishText,
                fontSize = 16.sp,
                fontWeight = if (enActive) FontWeight.Bold else FontWeight.SemiBold,
                color = if (enActive) primaryText else secondaryText,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp, top = 14.dp, end = 8.dp),
                maxLines = 1
            )
            Box(
                modifier = Modifier.width(170.dp),
                contentAlignment = Alignment.TopEnd
            ) {
                grammarReference?.let { reference ->
                    Text(
                        reference,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Normal,
                        color = mutedText,
                        textAlign = TextAlign.End,
                        maxLines = 1
                    )
                }
            }
        }
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
            plTokens.size >= 5 -> 38.sp
            plTokens.size >= 3 -> 44.sp
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
                val wordModifier = Modifier.clickable { onPlWordClick(plTok) }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = tokenPad)
                ) {
                    val displayed = if (plHidden) "•".repeat(plTok.length.coerceAtLeast(2))
                                    else plTok
                    val fixedColor = when {
                            plHidden -> mutedText.copy(alpha = 0.5f)
                            plActive || slowActive -> primaryText
                            pausing -> secondaryText
                            else -> primaryText
                        }
                    val variableToken = if (plHidden) null else token
                    val variableColor = variableToken?.let { GrammarVisuals.Variable.color(it) }
                    if (variableToken != null && variableColor != null) {
                        VariablePolishText(
                            text = displayed,
                            fixedColor = fixedColor,
                            variableColor = variableColor,
                            fontSize = polishFontSize,
                            fontWeight = FontWeight.Bold,
                            variableStart = variableToken.variableStart,
                            variableEnd = variableToken.variableEnd,
                            fallbackWholeWord = variableToken.variableStart == null,
                            maxLines = 1,
                            softWrap = false,
                            modifier = wordModifier
                        )
                    } else {
                        Text(
                            displayed,
                            fontSize = polishFontSize,
                            fontWeight = FontWeight.Bold,
                            color = fixedColor,
                            maxLines = 1,
                            softWrap = false,
                            modifier = wordModifier
                        )
                    }
                    if (showEnglish && !plHidden && gloss.isNotEmpty()) {
                        Text(
                            gloss,
                            fontSize = glossFontSize,
                            maxLines = 1,
                            softWrap = false,
                            color = accentText,
                            fontWeight = FontWeight.Normal
                        )
                    } else {
                        Spacer(Modifier.height((glossFontSize.value + 2f).dp))
                    }
                }
            }
        }
    }
}

private val NV_WHITESPACE = Regex("\\s+")

private fun nowVoicingGrammarReference(pinned: NowVoicing): String? {
    val token = pinned.words?.firstOrNull {
        it.gender != null || it.caseLabel != null || it.caseKey != null || it.numberLabel != null
    } ?: return null
    val parts = listOfNotNull(
        token.gender?.let(::nowVoicingGenderLabel),
        token.caseLabel?.let(::nowVoicingGrammarLabel)
            ?: token.caseKey?.let(::nowVoicingCaseLabel),
        token.numberLabel?.let(::nowVoicingGrammarLabel)
    )
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" - ", prefix = "(", postfix = ")")
}

private fun nowVoicingGenderLabel(gender: String): String = when (gender.trim().lowercase()) {
    "m", "masculine", "masc." -> "masculine"
    "f", "feminine", "fem." -> "feminine"
    "n", "neuter", "neut." -> "neuter"
    "mp", "virile" -> "men or mixed plural"
    "other", "non-virile" -> "other plural"
    else -> nowVoicingGrammarLabel(gender)
}

private fun nowVoicingCaseLabel(caseKey: String): String = when (caseKey.trim().lowercase()) {
    "nom" -> "nominative"
    "acc" -> "accusative"
    "gen" -> "genitive"
    "dat" -> "dative"
    "inst" -> "instrumental"
    "loc" -> "locative"
    "voc" -> "vocative"
    else -> nowVoicingGrammarLabel(caseKey)
}

private fun nowVoicingGrammarLabel(label: String): String =
    label.trim().lowercase().takeIf { it.isNotEmpty() } ?: label
