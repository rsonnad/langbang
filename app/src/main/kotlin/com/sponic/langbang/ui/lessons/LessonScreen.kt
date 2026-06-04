package com.sponic.langbang.ui.lessons

import androidx.compose.foundation.layout.height
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sponic.langbang.LangbangApplication
import com.sponic.langbang.domain.PrefetchProgress

internal enum class L2Tab(val label: String) {
    Verbs("Verbs"),
    Phrases("Phrases")
}

@Composable
fun LessonScreen(
    app: LangbangApplication,
    prefetch: PrefetchProgress,
    nowVoicing: @Composable () -> Unit = {}
) {
    // Both toggle states are rendered by VerbsTab. Routing Phrases elsewhere restores
    // the legacy phrase detail pane and hides the verb-phrase playback controls.
    var tab by remember { mutableStateOf(L2Tab.Verbs) }
    VerbsTab(app, prefetch, tab, { tab = it }, nowVoicing)
}

/** The Verbs/Phrases segmented toggle, emitted into the caller's chip row. */
@Composable
internal fun L2TabChips(tab: L2Tab, onSelect: (L2Tab) -> Unit) {
    L2Tab.values().forEach { t ->
        FilterChip(
            selected = tab == t,
            onClick = { onSelect(t) },
            label = { Text(t.label, fontSize = 11.sp) },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primary,
                selectedLabelColor = Color.White
            ),
            modifier = Modifier.height(30.dp)
        )
    }
}
