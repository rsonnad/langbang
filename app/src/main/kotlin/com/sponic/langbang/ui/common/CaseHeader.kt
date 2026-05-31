package com.sponic.langbang.ui.common

import com.sponic.langbang.ui.theme.LbColors

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/**
 * One-line case/paradigm header (e.g. "Nominative", "Accusative", "Genitive") whose
 * longer description is tucked behind a tap/hover tooltip instead of wrapping across
 * two or three rows under the label. Keeps the paradigm vertically compact — the
 * descriptions used to push the actual inflected forms below the fold.
 *
 * The trailing ⓘ is the affordance: tap it (or the label) to surface the hint, which
 * also shows on long-press / pointer hover via the Material3 tooltip default.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaseHeader(label: String, hint: String) {
    val scope = rememberCoroutineScope()
    val tooltipState = rememberTooltipState(isPersistent = false)
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = {
            PlainTooltip {
                Text(hint, fontSize = 12.sp)
            }
        },
        state = tooltipState
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .clickable { scope.launch { tooltipState.show() } }
                .padding(top = 6.dp, bottom = 2.dp)
        ) {
            Text(
                label,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = LbColors.Label
            )
            Icon(
                Icons.Outlined.Info,
                contentDescription = "Show $label description",
                tint = LbColors.ChipRing,
                modifier = Modifier.size(13.dp)
            )
        }
    }
}
