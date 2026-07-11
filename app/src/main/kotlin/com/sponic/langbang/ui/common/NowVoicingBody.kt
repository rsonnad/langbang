package com.sponic.langbang.ui.common

import com.sponic.langbang.ui.theme.LbColors

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sponic.langbang.BuildConfig
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
    syllableShading: Boolean = true,
    largeFormat: Boolean = false,
    composing: Boolean = false,
    targetPresentation: NowVoicingTargetPresentation? = null,
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
    // Token + gloss splitting (and the Regex) depend only on the canonical sentence content, which
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
    val delayedEnglishVisible = rememberDelayedTranslationVisible(
        key = "${pinned.pl}\n${pinned.en}\n${pinned.literal.orEmpty()}"
    )
    val showEnglish = enActive || delayedEnglishVisible
    val englishText = pinned.en
    val grammarReference = remember(pinned.words) { nowVoicingGrammarReference(pinned) }
    val primaryText = LbColors.TextPrimary
    val secondaryText = LbColors.TextSecondary
    val mutedText = LbColors.TextMuted
    val accentText = LbColors.Label
    val showSyllableShading = syllableShadingAvailable && syllableShading
    val statusLines = statusText.lines().filter { it.isNotBlank() }
    val statusLabel = statusLines.firstOrNull().orEmpty()
    val statusDetail = statusLines.drop(1).joinToString(" · ")

    Box(modifier = modifier.fillMaxWidth()) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(if (largeFormat) 8.dp else 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (largeFormat && statusLabel.isBlank() && statusDetail.isBlank() && grammarReference == null) {
            Text(
                text = englishText,
                fontSize = if (largeFormat && englishText.length > 90) 20.sp
                    else if (largeFormat) 23.sp
                    else 16.sp,
                fontWeight = if (enActive) FontWeight.Bold else FontWeight.SemiBold,
                color = when {
                    !showEnglish -> Color.Transparent
                    enActive -> primaryText
                    else -> secondaryText
                },
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
                maxLines = if (largeFormat) 3 else 1
            )
        } else {
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
                Text(
                    text = englishText,
                    fontSize = if (largeFormat && englishText.length > 90) 20.sp
                        else if (largeFormat) 23.sp
                        else 16.sp,
                    fontWeight = if (enActive) FontWeight.Bold else FontWeight.SemiBold,
                    color = when {
                        !showEnglish -> Color.Transparent
                        enActive -> primaryText
                        else -> secondaryText
                    },
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp, top = 14.dp, end = 8.dp),
                    maxLines = if (largeFormat) 3 else 1
                )
                Box(
                    // Reserve the grammar-reference gutter only when there's a reference to
                    // show; otherwise give the width back to the English line so it doesn't
                    // wrap a trailing word against empty space (phrases have no reference).
                    modifier = Modifier.width(if (grammarReference != null) 170.dp else 0.dp),
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
        }
        targetPresentation?.let { presentation ->
            val compact = if (plHidden) "•".repeat(presentation.compactText.length.coerceAtLeast(3))
            else presentation.compactText
            Text(
                text = compact,
                fontSize = if (largeFormat) 18.sp else 13.sp,
                fontWeight = FontWeight.Medium,
                color = if (plHidden) mutedText.copy(alpha = 0.5f) else secondaryText,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
                maxLines = if (largeFormat) 2 else 1
            )
        }
        if (targetPresentation != null) {
            // A language that supplies independent writing and pronunciation needs a
            // deliberately line-based layout. Romaji and Japanese script do not share
            // whitespace/token boundaries, so forcing the legacy word columns here
            // would misalign the literal gloss when the learner swaps the two lines.
            val emphasized = if (plHidden) {
                "•".repeat(targetPresentation.emphasizedText.length.coerceIn(3, 24))
            } else {
                targetPresentation.emphasizedText
            }
            val emphasizedFontSize = when {
                largeFormat && emphasized.length > 80 -> 28.sp
                largeFormat -> 44.sp
                emphasized.length > 52 -> 28.sp
                emphasized.length > 36 -> 36.sp
                else -> 52.sp
            }
            Text(
                text = emphasized,
                fontSize = emphasizedFontSize,
                fontWeight = FontWeight.Bold,
                color = if (plHidden) mutedText.copy(alpha = 0.5f) else primaryText,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
                maxLines = if (largeFormat) 4 else 2,
                softWrap = true
            )
            pinned.literal?.takeIf { it.isNotBlank() }?.let { literal ->
                Text(
                    text = literal,
                    fontSize = if (largeFormat) 13.sp else 14.sp,
                    color = accentText,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = if (largeFormat) 3 else 2,
                    softWrap = true
                )
            }
        } else {
        // Big centered Polish row with per-token gloss columns. Row centers itself
        // in the available width (Arrangement.Center) and each token's column
        // centers its own contents. Gloss row reserved even when missing so columns
        // line up across token-count-1 (verb conjugation) and token-count-N
        // (sentence) renderings.
        val totalChars = plTokens.sumOf { it.length } + (plTokens.size - 1).coerceAtLeast(0) * 2
        val polishFontSize = when {
            largeFormat && totalChars > 140 -> 24.sp
            largeFormat && totalChars > 95 -> 28.sp
            largeFormat && totalChars > 65 -> 32.sp
            largeFormat && totalChars > 45 -> 36.sp
            largeFormat -> 44.sp
            totalChars > 55 -> 28.sp
            totalChars > 46 -> 32.sp
            totalChars > 38 -> 36.sp
            plTokens.size >= 5 -> 38.sp
            plTokens.size >= 3 -> 44.sp
            totalChars > 30 -> 42.sp
            else -> 52.sp
        }
        val glossFontSize = when {
            largeFormat && totalChars > 95 -> 11.sp
            largeFormat -> 13.sp
            totalChars > 46 -> 11.sp
            totalChars > 38 -> 12.sp
            else -> 14.sp
        }
        val tokenPad = if (totalChars > 38) 8.dp else 16.dp
        val selection = rememberPolishTokenSelectionState()
        val selectedIndexes = selection.selectedIndexes
        val tokenContainer: @Composable (@Composable () -> Unit) -> Unit = { content ->
            // Always wrap. A single non-wrapping Row clipped the tail words of long phrases
            // (e.g. a 13-word affirmation) off the right edge. FlowRow flows the overflow
            // tokens onto new lines, and the shell renders this card at wrap-content height,
            // so the card grows to fit every word instead of hiding the end of the phrase.
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(selection.dragModifier(plTokens)),
                horizontalArrangement = Arrangement.Center,
                verticalArrangement = Arrangement.spacedBy(if (largeFormat) 4.dp else 2.dp)
            ) {
                content()
            }
        }
        tokenContainer {
            plTokens.forEachIndexed { i, plTok ->
                val gloss = glossTokens.getOrNull(i).orEmpty()
                val token = structuredWords?.getOrNull(i)
                val wordModifier = Modifier.clickable {
                    onPlWordClick(selection.selectionOrTokenText(i, plTokens))
                }
                val selected = i in selectedIndexes
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .padding(horizontal = tokenPad)
                        .onGloballyPositioned { selection.updateBounds(i, it) }
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (selected) LbColors.Audio.copy(alpha = 0.14f)
                            else Color.Transparent
                        )
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
                    // Subtle two-tone syllable shading helps the learner chunk the
                    // target Polish word. Only on the EN→PL build (where Polish is the
                    // language being learned) and never over the masked dots.
                    val shades = if (showSyllableShading && !plHidden) SYLLABLE_SHADES else null
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
                            maxLines = if (largeFormat) 4 else 1,
                            softWrap = largeFormat,
                            syllableShades = shades,
                            modifier = wordModifier
                        )
                    } else if (shades != null) {
                        VariablePolishText(
                            text = displayed,
                            fixedColor = fixedColor,
                            variableColor = fixedColor,
                            fontSize = polishFontSize,
                            fontWeight = FontWeight.Bold,
                            maxLines = if (largeFormat) 4 else 1,
                            softWrap = largeFormat,
                            syllableShades = shades,
                            modifier = wordModifier
                        )
                    } else {
                        Text(
                            displayed,
                            fontSize = polishFontSize,
                            fontWeight = FontWeight.Bold,
                            color = fixedColor,
                            maxLines = if (largeFormat) 4 else 1,
                            softWrap = largeFormat,
                            modifier = wordModifier
                        )
                    }
                    if (!plHidden && gloss.isNotEmpty()) {
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
        if (composing) {
            Surface(
                color = LbColors.WarningSoft,
                shape = RoundedCornerShape(7.dp),
                modifier = Modifier.align(Alignment.TopEnd)
            ) {
                Text(
                    "Composing...",
                    color = LbColors.Warning,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
    }
}

private val NV_WHITESPACE = Regex("\\s+")

/**
 * Syllable shading is a learning aid for EN→PL learners, where Polish is the target
 * language being decoded. Keep every other instance plain.
 */
private val syllableShadingAvailable: Boolean =
    BuildConfig.LANGBANGML_INSTANCE_ID == "langbangml-en-pl"

private val SYLLABLE_SHADES: Pair<Color, Color> =
    GrammarVisuals.SyllableShading.ShadeA to GrammarVisuals.SyllableShading.ShadeB

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
