package com.sponic.langbang.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sponic.langbang.ui.theme.LbColors

@Composable
fun <T> SelectionNavButtons(
    items: List<T>,
    selected: T?,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    buttonSize: Dp = 42.dp,
    iconSize: Dp = 27.dp,
    gap: Dp = 14.dp,
    previousContentDescription: String = "Previous",
    nextContentDescription: String = "Next"
) {
    if (items.isEmpty()) return
    val selectedIndex = items.indexOf(selected)
    val enabled = items.size > 1 && selectedIndex >= 0

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(gap),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = { onSelect(items[(selectedIndex - 1).floorMod(items.size)]) },
            enabled = enabled,
            modifier = Modifier.size(buttonSize)
        ) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = previousContentDescription,
                tint = if (enabled) LbColors.Label else LbColors.TextMuted.copy(alpha = 0.45f),
                modifier = Modifier.size(iconSize)
            )
        }
        IconButton(
            onClick = { onSelect(items[(selectedIndex + 1).floorMod(items.size)]) },
            enabled = enabled,
            modifier = Modifier.size(buttonSize)
        ) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = nextContentDescription,
                tint = if (enabled) LbColors.Label else LbColors.TextMuted.copy(alpha = 0.45f),
                modifier = Modifier.size(iconSize)
            )
        }
    }
}

private fun Int.floorMod(other: Int): Int = ((this % other) + other) % other
