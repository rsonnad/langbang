package com.sponic.langbang.ui.common

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.TextUnit

private data class VariableRange(val start: Int, val end: Int)

private data class ShadeRange(val start: Int, val end: Int, val shadeIndex: Int)

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
    val shadeRanges = remember(text, syllableShades) {
        if (syllableShades == null) emptyList() else syllableShadeRanges(text)
    }
    var textLayout by remember(text, shadeRanges) { mutableStateOf<TextLayoutResult?>(null) }
    val textModifier = if (syllableShades != null && shadeRanges.isNotEmpty()) {
        modifier.drawBehind {
            textLayout?.let { layout ->
                drawSyllableShadeBands(layout, shadeRanges, syllableShades)
            }
        }
    } else {
        modifier
    }
    Text(
        text = buildAnnotatedString {
            append(text)
            if (range != null) {
                addStyle(SpanStyle(color = variableColor), range.start, range.end)
            }
        },
        fontSize = fontSize,
        fontWeight = fontWeight,
        maxLines = maxLines,
        softWrap = softWrap,
        color = fixedColor,
        onTextLayout = { textLayout = it },
        modifier = textModifier
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

private fun syllableShadeRanges(text: String): List<ShadeRange> {
    var offset = 0
    var shadeIndex = 0
    val ranges = mutableListOf<ShadeRange>()
    for (piece in polishSyllables(text)) {
        val start = offset
        offset += piece.length
        // Only shade chunks that actually contain a letter, so leading/trailing
        // punctuation never starts an orphan band or flips the alternation.
        if (piece.any { it.isLetter() }) {
            ranges += ShadeRange(start, offset, shadeIndex)
            shadeIndex++
        }
    }
    return ranges
}

private fun DrawScope.drawSyllableShadeBands(
    layout: TextLayoutResult,
    ranges: List<ShadeRange>,
    shades: Pair<Color, Color>
) {
    val horizontalPad = 3.dp.toPx()
    val verticalInset = 4.dp.toPx()
    val gap = 1.dp.toPx()
    val corner = 4.dp.toPx()
    ranges.forEach { range ->
        if (range.start >= range.end) return@forEach
        val firstLine = layout.getLineForOffset(range.start)
        val lastLine = layout.getLineForOffset((range.end - 1).coerceAtLeast(range.start))
        for (line in firstLine..lastLine) {
            val segmentStart = if (line == firstLine) range.start else layout.getLineStart(line)
            val segmentEnd = if (line == lastLine) {
                range.end
            } else {
                layout.getLineEnd(line, visibleEnd = true)
            }
            if (segmentStart >= segmentEnd) continue

            val firstBox = layout.getBoundingBox(segmentStart)
            val lastBox = layout.getBoundingBox(segmentEnd - 1)
            val left = minOf(firstBox.left, lastBox.left) - horizontalPad + gap
            val right = maxOf(firstBox.right, lastBox.right) + horizontalPad - gap
            val lineTop = layout.getLineTop(line)
            val lineBottom = layout.getLineBottom(line)
            val top = (lineTop + verticalInset).coerceAtLeast(0f)
            val bottom = (lineBottom - verticalInset).coerceAtMost(size.height)
            if (right > left && bottom > top) {
                drawRoundRect(
                    color = if (range.shadeIndex % 2 == 0) shades.first else shades.second,
                    topLeft = androidx.compose.ui.geometry.Offset(left, top),
                    size = androidx.compose.ui.geometry.Size(right - left, bottom - top),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(corner, corner)
                )
            }
        }
    }
}
