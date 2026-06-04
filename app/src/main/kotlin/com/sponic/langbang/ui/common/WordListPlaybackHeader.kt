package com.sponic.langbang.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
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
        Spacer(Modifier.width(4.dp))
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
    leadingLabel: String = "groups of",
    trailingLabel: String? = null,
    modifier: Modifier = Modifier
) {
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
        CompactNumberField(
            value = limitText,
            onValueChange = onLimitTextChange,
            enabled = enabled
        )
        trailingLabel?.let {
            Spacer(Modifier.width(4.dp))
            Text(
                it,
                fontSize = 11.sp,
                color = if (enabled) LbColors.TextSecondary else LbColors.TextMuted
            )
        }
    }
}

@Composable
private fun CompactNumberField(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean
) {
    Surface(
        color = LbColors.Sheet,
        shape = LbShapes.Button,
        border = BorderStroke(1.dp, LbColors.Line),
        modifier = Modifier
            .width(42.dp)
            .height(30.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (enabled) LbColors.TextPrimary else LbColors.TextMuted,
                    textAlign = TextAlign.Center
                ),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(28.dp)
            )
        }
    }
}
