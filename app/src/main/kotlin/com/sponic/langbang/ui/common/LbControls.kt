package com.sponic.langbang.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sponic.langbang.ui.theme.LbColors
import com.sponic.langbang.ui.theme.LbShapes

object LbButton {
    @Composable
    fun Audio(
        label: String,
        onClick: () -> Unit,
        modifier: Modifier = Modifier,
        icon: ImageVector? = Icons.Filled.PlayArrow,
        count: Int? = null,
        enabled: Boolean = true
    ) = ButtonSurface(
        label = label,
        onClick = onClick,
        modifier = modifier,
        icon = icon,
        count = count,
        enabled = enabled,
        containerColor = LbColors.Audio,
        contentColor = Color.White,
        borderColor = LbColors.Audio
    )

    @Composable
    fun Primary(
        label: String,
        onClick: () -> Unit,
        modifier: Modifier = Modifier,
        icon: ImageVector? = null,
        count: Int? = null,
        enabled: Boolean = true
    ) = ButtonSurface(
        label = label,
        onClick = onClick,
        modifier = modifier,
        icon = icon,
        count = count,
        enabled = enabled,
        containerColor = LbColors.Primary,
        contentColor = Color.White,
        borderColor = LbColors.Primary
    )

    @Composable
    fun Ghost(
        label: String,
        onClick: () -> Unit,
        modifier: Modifier = Modifier,
        icon: ImageVector? = null,
        count: Int? = null,
        enabled: Boolean = true
    ) = ButtonSurface(
        label = label,
        onClick = onClick,
        modifier = modifier,
        icon = icon,
        count = count,
        enabled = enabled,
        containerColor = Color.White,
        contentColor = LbColors.TextPrimary,
        borderColor = LbColors.Line
    )

    @Composable
    fun Stop(
        label: String,
        onClick: () -> Unit,
        modifier: Modifier = Modifier,
        icon: ImageVector? = null,
        enabled: Boolean = true
    ) = ButtonSurface(
        label = label,
        onClick = onClick,
        modifier = modifier,
        icon = icon,
        enabled = enabled,
        containerColor = LbColors.Stop,
        contentColor = Color.White,
        borderColor = LbColors.Stop
    )
}

@Composable
private fun ButtonSurface(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector?,
    count: Int? = null,
    enabled: Boolean,
    containerColor: Color,
    contentColor: Color,
    borderColor: Color
) {
    val disabledAlpha = if (enabled) 1f else 0.45f
    Surface(
        color = containerColor.copy(alpha = disabledAlpha),
        shape = LbShapes.Button,
        border = BorderStroke(1.dp, borderColor.copy(alpha = disabledAlpha)),
        modifier = modifier
            .defaultMinSize(minHeight = 44.dp)
            .clip(LbShapes.Button)
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            icon?.let {
                Icon(it, contentDescription = null, tint = contentColor, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(6.dp))
            }
            Text(
                label,
                color = contentColor,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            count?.let {
                Spacer(Modifier.width(7.dp))
                Text(
                    it.toString(),
                    color = contentColor.copy(alpha = 0.78f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }
}

@Composable
fun LbChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selectedColor: Color = LbColors.Primary,
    showCheck: Boolean = true,
    dark: Boolean = false
) {
    val bg = if (selected) selectedColor else Color.Transparent
    val border = if (selected) selectedColor else if (dark) LbColors.OnDark2.copy(alpha = 0.35f) else LbColors.Line
    val fg = when {
        selected -> Color.White
        dark -> LbColors.OnDark2
        else -> LbColors.TextSecondary
    }
    Surface(
        color = bg,
        shape = LbShapes.Pill,
        border = BorderStroke(1.5.dp, border),
        modifier = modifier
            .height(30.dp)
            .clip(LbShapes.Pill)
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selected && showCheck) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(4.dp))
            }
            Text(
                label,
                color = fg.copy(alpha = if (enabled) 1f else 0.45f),
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SelectionBar(
    modifier: Modifier = Modifier,
    note: String? = null,
    content: @Composable () -> Unit
) {
    Surface(
        color = LbColors.SurfaceTint,
        shape = LbShapes.Card,
        border = BorderStroke(1.dp, LbColors.Line),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                content()
            }
            note?.takeIf { it.isNotBlank() }?.let {
                Text(it, fontSize = 12.sp, color = LbColors.TextMuted, fontStyle = FontStyle.Italic)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FilterGroup(
    label: String,
    modifier: Modifier = Modifier,
    dark: Boolean = false,
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            label.uppercase(),
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold,
            color = if (dark) LbColors.OnDark2 else LbColors.TextMuted,
            letterSpacing = 0.8.sp
        )
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp), content = content)
    }
}

@Composable
fun PlayCircle(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    sizeDp: Int = 46
) {
    val bg = if (active) LbColors.Audio else Color.White
    val fg = if (active) Color.White else LbColors.Audio
    Surface(
        color = bg,
        shape = CircleShape,
        border = BorderStroke(1.5.dp, LbColors.Audio),
        modifier = modifier
            .size(sizeDp.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Icon(
                if (active) Icons.Filled.GraphicEq else Icons.Filled.PlayArrow,
                contentDescription = if (active) "Now playing" else "Play",
                tint = fg,
                modifier = Modifier.size((sizeDp * 0.46f).dp)
            )
        }
    }
}

@Composable
fun StarToggle(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    sizeDp: Int = 44
) {
    Surface(
        color = Color.Transparent,
        shape = CircleShape,
        modifier = modifier
            .size(sizeDp.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Icon(
                if (selected) Icons.Filled.Star else Icons.Filled.StarBorder,
                contentDescription = if (selected) "Unstar" else "Star",
                tint = if (selected) LbColors.Star else LbColors.TextMuted,
                modifier = Modifier.size((sizeDp * 0.5f).dp)
            )
        }
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, dark: Boolean = false) {
    Text(
        text.uppercase(),
        modifier = modifier,
        fontSize = 11.sp,
        fontWeight = FontWeight.ExtraBold,
        color = if (dark) LbColors.OnDark2 else LbColors.TextMuted,
        letterSpacing = 0.8.sp
    )
}
