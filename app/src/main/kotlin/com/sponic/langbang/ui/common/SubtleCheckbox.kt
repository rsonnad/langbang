package com.sponic.langbang.ui.common

import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import com.sponic.langbang.ui.theme.LbColors

private const val SUBTLE_CHECKBOX_SCALE = 0.75f

@Composable
fun SubtleCheckbox(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Checkbox(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        colors = CheckboxDefaults.colors(
            checkedColor = LbColors.SurfaceTint,
            checkmarkColor = LbColors.TextPrimary,
            uncheckedColor = LbColors.ChipBorder,
            disabledCheckedColor = LbColors.SurfaceTint.copy(alpha = 0.55f),
            disabledUncheckedColor = LbColors.ChipBorder.copy(alpha = 0.45f)
        ),
        modifier = modifier.scale(SUBTLE_CHECKBOX_SCALE)
    )
}
