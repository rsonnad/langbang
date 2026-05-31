package com.sponic.langbang.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp

@Composable
fun OutlinedPolishText(
    text: String,
    fillColor: Color,
    outlineColor: Color?,
    fontSize: TextUnit,
    fontWeight: FontWeight,
    modifier: Modifier = Modifier,
    outlineWidth: Float = 3f,
    backingOutlineColor: Color? = GrammarVisuals.NounForm.OutlineBacking,
    backingOutlineExtraWidth: Float = 2f,
    glyphGap: Dp = 2.dp,
    maxLines: Int = Int.MAX_VALUE,
    softWrap: Boolean = true
) {
    if (outlineColor != null && outlineWidth > 0f) {
        Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(glyphGap),
            verticalAlignment = Alignment.CenterVertically
        ) {
            text.forEach { glyph ->
                OutlinedGlyph(
                    text = glyph.toString(),
                    fillColor = fillColor,
                    outlineColor = outlineColor,
                    fontSize = fontSize,
                    fontWeight = fontWeight,
                    outlineWidth = outlineWidth,
                    backingOutlineColor = backingOutlineColor,
                    backingOutlineExtraWidth = backingOutlineExtraWidth,
                    maxLines = maxLines,
                    softWrap = softWrap
                )
            }
        }
        return
    }
    Text(
        text = text,
        fontSize = fontSize,
        fontWeight = fontWeight,
        maxLines = maxLines,
        softWrap = softWrap,
        color = fillColor,
        modifier = modifier
    )
}

@Composable
private fun OutlinedGlyph(
    text: String,
    fillColor: Color,
    outlineColor: Color,
    fontSize: TextUnit,
    fontWeight: FontWeight,
    outlineWidth: Float,
    backingOutlineColor: Color?,
    backingOutlineExtraWidth: Float,
    maxLines: Int,
    softWrap: Boolean
) {
    Box {
        if (backingOutlineColor != null && backingOutlineExtraWidth > 0f) {
            Text(
                text = text,
                fontSize = fontSize,
                fontWeight = fontWeight,
                maxLines = maxLines,
                softWrap = softWrap,
                color = backingOutlineColor,
                style = TextStyle(
                    drawStyle = Stroke(width = outlineWidth + backingOutlineExtraWidth)
                )
            )
        }
        Text(
            text = text,
            fontSize = fontSize,
            fontWeight = fontWeight,
            maxLines = maxLines,
            softWrap = softWrap,
            color = outlineColor,
            style = TextStyle(drawStyle = Stroke(width = outlineWidth))
        )
        Text(
            text = text,
            fontSize = fontSize,
            fontWeight = fontWeight,
            maxLines = maxLines,
            softWrap = softWrap,
            color = fillColor
        )
    }
}
