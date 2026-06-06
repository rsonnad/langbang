package com.sponic.langbang.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sponic.langbang.ui.theme.LbColors
import com.sponic.langbang.ui.theme.LbShapes

@Composable
fun WordListPlaybackHeader(
    allChecked: Boolean,
    onAllCheckedChange: (Boolean) -> Unit,
    random: Boolean,
    onRandomChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    allLabel: String = "all"
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 2.dp)
    ) {
        SubtleCheckbox(
            checked = allChecked,
            onCheckedChange = onAllCheckedChange,
            enabled = enabled,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(2.dp))
        Text(
            allLabel,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = LbColors.TextSecondary
        )
        Spacer(Modifier.width(10.dp))
        SubtleCheckbox(
            checked = random,
            onCheckedChange = onRandomChange,
            enabled = enabled,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(3.dp))
        Text("random", fontSize = 11.sp, color = LbColors.TextMuted)
    }
}

@Composable
fun WordPlayLimitControl(
    limitText: String,
    onLimitTextChange: (String) -> Unit,
    enabled: Boolean = true,
    leadingLabel: String,
    trailingLabel: String?,
    minValue: Int = 1,
    maxValue: Int = 99,
    modifier: Modifier = Modifier
) {
    val current = limitText.toIntOrNull()?.coerceIn(minValue, maxValue) ?: minValue
    Row(
        modifier = modifier.height(30.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            leadingLabel,
            fontSize = 11.sp,
            color = if (enabled) LbColors.TextSecondary else LbColors.TextMuted
        )
        Spacer(Modifier.width(4.dp))
        CompactStepper(
            value = current,
            onValueChange = { onLimitTextChange(it.toString()) },
            enabled = enabled,
            minValue = minValue,
            maxValue = maxValue
        )
        trailingLabel?.let {
            Spacer(Modifier.width(2.dp))
            Text(
                it,
                fontSize = 11.sp,
                color = if (enabled) LbColors.TextSecondary else LbColors.TextMuted
            )
        }
    }
}

@Composable
private fun CompactStepper(
    value: Int,
    onValueChange: (Int) -> Unit,
    enabled: Boolean,
    minValue: Int,
    maxValue: Int
) {
    Surface(
        color = LbColors.Sheet,
        shape = LbShapes.Button,
        border = BorderStroke(1.dp, LbColors.Line),
        modifier = Modifier
            .width(68.dp)
            .height(30.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            StepperArrow(
                decrement = true,
                enabled = enabled && value > minValue,
                onClick = { onValueChange((value - 1).coerceIn(minValue, maxValue)) }
            )
            Text(
                value.toString(),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (enabled) LbColors.TextPrimary else LbColors.TextMuted,
                modifier = Modifier.width(24.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            StepperArrow(
                decrement = false,
                enabled = enabled && value < maxValue,
                onClick = { onValueChange((value + 1).coerceIn(minValue, maxValue)) }
            )
        }
    }
}

@Composable
private fun StepperArrow(
    decrement: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Surface(
        color = androidx.compose.ui.graphics.Color.Transparent,
        shape = LbShapes.Button,
        modifier = Modifier
            .width(22.dp)
            .height(30.dp)
            .clip(LbShapes.Button)
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center
        ) {
            Icon(
                imageVector = if (decrement) {
                    Icons.AutoMirrored.Filled.KeyboardArrowLeft
                } else {
                    Icons.AutoMirrored.Filled.KeyboardArrowRight
                },
                contentDescription = if (decrement) "Decrease variations" else "Increase variations",
                tint = if (enabled) LbColors.TextSecondary else LbColors.TextMuted,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
