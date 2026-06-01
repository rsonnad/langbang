package com.sponic.langbang.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.sponic.langbang.ui.theme.LbColors

object CompactLessonListDefaults {
    val ContentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
    val ItemPadding = PaddingValues(horizontal = 12.dp, vertical = 3.dp)
    val MultiLineItemPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
    val ItemGap = 2.dp
    val Shape = RoundedCornerShape(8.dp)
    val ItemColor = Color.White
    val AlternateItemColor = LbColors.SurfaceTint.copy(alpha = 0.62f)
}

@Composable
fun CompactLessonListCard(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    alternate: Boolean = false,
    contentPadding: PaddingValues = CompactLessonListDefaults.ItemPadding,
    content: @Composable BoxScope.() -> Unit
) {
    val containerColor = when {
        selected -> MaterialTheme.colorScheme.primary
        alternate -> CompactLessonListDefaults.AlternateItemColor
        else -> CompactLessonListDefaults.ItemColor
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(CompactLessonListDefaults.Shape)
            .background(containerColor)
            .clickable(onClick = onClick)
            .padding(contentPadding),
        content = content
    )
}
