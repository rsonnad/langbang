package com.sponic.langbang.ui.common

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit

private data class VariableRange(val start: Int, val end: Int)

@Composable
fun VariablePolishText(
    text: String,
    fixedColor: Color,
    variableColor: Color,
    fontSize: TextUnit,
    fontWeight: FontWeight,
    modifier: Modifier = Modifier,
    baseText: String? = null,
    variableStart: Int? = null,
    variableEnd: Int? = null,
    fallbackWholeWord: Boolean = false,
    maxLines: Int = Int.MAX_VALUE,
    softWrap: Boolean = true,
    /**
     * When non-null, paint alternating syllable backgrounds (first/second of the pair)
     * behind the word so the learner can see the syllable boundaries. Null (default)
     * leaves every existing caller untouched.
     */
    syllableShades: Pair<Color, Color>? = null
) {
    val range = inferVariableRange(
        text = text,
        baseText = baseText,
        variableStart = variableStart,
        variableEnd = variableEnd,
        fallbackWholeWord = fallbackWholeWord
    )
    if (range == null && syllableShades == null) {
        Text(
            text = text,
            fontSize = fontSize,
            fontWeight = fontWeight,
            maxLines = maxLines,
            softWrap = softWrap,
            color = fixedColor,
            modifier = modifier
        )
        return
    }
    Text(
        text = buildAnnotatedString {
            append(text)
            if (syllableShades != null) {
                var offset = 0
                var shadeIndex = 0
                for (piece in polishSyllables(text)) {
                    val start = offset
                    offset += piece.length
                    // Only shade chunks that actually contain a letter, so leading/
                    // trailing punctuation never starts an orphan band or flips the
                    // alternation.
                    if (piece.any { it.isLetter() }) {
                        val shade = if (shadeIndex % 2 == 0) syllableShades.first else syllableShades.second
                        addStyle(SpanStyle(background = shade), start, offset)
                        shadeIndex++
                    }
                }
            }
            if (range != null) {
                addStyle(SpanStyle(color = variableColor), range.start, range.end)
            }
        },
        fontSize = fontSize,
        fontWeight = fontWeight,
        maxLines = maxLines,
        softWrap = softWrap,
        color = fixedColor,
        modifier = modifier
    )
}

fun variableStartForPolishForm(baseText: String?, text: String): Int? =
    inferVariableRange(
        text = text,
        baseText = baseText,
        fallbackWholeWord = true
    )?.start

fun variableEndForPolishForm(baseText: String?, text: String): Int? =
    inferVariableRange(
        text = text,
        baseText = baseText,
        fallbackWholeWord = true
    )?.end

private fun inferVariableRange(
    text: String,
    baseText: String? = null,
    variableStart: Int? = null,
    variableEnd: Int? = null,
    fallbackWholeWord: Boolean = false
): VariableRange? {
    val bounds = wordBounds(text) ?: return null
    if (variableStart != null) {
        val start = variableStart.coerceIn(bounds.start, bounds.end)
        val end = (variableEnd ?: bounds.end).coerceIn(start, bounds.end)
        return if (start < end) VariableRange(start, end) else null
    }

    val base = baseText?.trim()?.takeIf { it.isNotEmpty() }
    if (base != null) {
        val word = text.substring(bounds.start, bounds.end)
        val prefix = commonPrefixLength(base, word)
        if (prefix in 1 until word.length && prefix >= 2) {
            return VariableRange(bounds.start + prefix, bounds.end)
        }
        if (prefix >= word.length) {
            val endingStart = defaultEndingStart(word)
            return VariableRange(bounds.start + endingStart, bounds.end)
        }
        if (fallbackWholeWord) return bounds
    }

    return if (fallbackWholeWord) bounds else null
}

private fun wordBounds(text: String): VariableRange? {
    val start = text.indexOfFirst { it.isLetter() }
    if (start < 0) return null
    val end = text.indexOfLast { it.isLetter() } + 1
    return if (start < end) VariableRange(start, end) else null
}

private fun commonPrefixLength(base: String, word: String): Int {
    val max = minOf(base.length, word.length)
    var i = 0
    while (i < max && base[i].lowercaseChar() == word[i].lowercaseChar()) i += 1
    return i
}

private fun defaultEndingStart(word: String): Int {
    if (word.length <= 1) return 0
    val last = word.last().lowercaseChar()
    return if (last in setOf('a', 'ą', 'e', 'ę', 'i', 'o', 'u', 'y')) {
        word.length - 1
    } else {
        0
    }
}
