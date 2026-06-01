package com.sponic.langbang.ui.lessons

import com.sponic.langbang.ui.theme.LbColors

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sponic.langbang.LangbangApplication
import com.sponic.langbang.data.model.PhraseEntry
import com.sponic.langbang.domain.PrefetchProgress
import com.sponic.langbang.ui.common.CompactLessonListCard
import com.sponic.langbang.ui.common.CompactLessonListDefaults
import com.sponic.langbang.ui.common.DelayedEnglishTranslation
import com.sponic.langbang.ui.common.SelectionNavButtons
import com.sponic.langbang.ui.phrase.PhraseDetail
import com.sponic.langbang.ui.word.WordSheet

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
    // The Verbs/Phrases toggle used to sit in a full-width band ABOVE the list, which
    // pushed the word list down. It now lives in each pane's right-hand control column
    // so the list runs flush to the top, directly under the app header.
    var tab by remember { mutableStateOf(L2Tab.Verbs) }
    Box(modifier = Modifier.fillMaxSize()) {
        when (tab) {
            L2Tab.Verbs -> VerbsTab(app, prefetch, tab, { tab = it }, nowVoicing)
            L2Tab.Phrases -> PhrasesPane(app, prefetch, tab, { tab = it }, nowVoicing)
        }
    }
}

/** The Verbs/Phrases segmented toggle — emitted into the caller's chip row. */
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

@Composable
private fun PhrasesPane(
    app: LangbangApplication,
    prefetch: PrefetchProgress,
    tab: L2Tab,
    onTabChange: (L2Tab) -> Unit,
    nowVoicing: @Composable () -> Unit
) {
    val lesson = remember { app.lessonRepo.lesson2() }
    var selected by remember { mutableStateOf(lesson.phrases.firstOrNull()) }
    var wordSheet by remember { mutableStateOf<String?>(null) }

    Row(modifier = Modifier.fillMaxSize()) {
        PhraseList(
            phrases = lesson.phrases,
            selected = selected,
            onSelect = { selected = it; wordSheet = null },
            modifier = Modifier
                .width(288.dp)
                .fillMaxHeight()
                .background(LbColors.Canvas)
        )
        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
            nowVoicing()
            Surface(color = LbColors.SurfaceRaised, modifier = Modifier.fillMaxWidth()) {
                Row(
                    Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    L2TabChips(tab, onTabChange)
                    Spacer(Modifier.weight(1f))
                    SelectionNavButtons(
                        items = lesson.phrases,
                        selected = selected,
                        onSelect = {
                            selected = it
                            wordSheet = null
                        },
                        previousContentDescription = "Previous phrase",
                        nextContentDescription = "Next phrase"
                    )
                }
            }
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                selected?.let { phrase ->
                    PhraseDetail(
                        app = app,
                        phrase = phrase,
                        prefetchReady = prefetch.finished,
                        onWordTap = { wordSheet = it }
                    )
                }
                wordSheet?.let { word ->
                    WordSheet(
                        app = app,
                        polishToken = word,
                        onDismiss = { wordSheet = null },
                        modifier = Modifier.align(Alignment.CenterEnd)
                    )
                }
            }
        }
    }
}

@Composable
private fun PhraseList(
    phrases: List<PhraseEntry>,
    selected: PhraseEntry?,
    onSelect: (PhraseEntry) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = CompactLessonListDefaults.ContentPadding,
        verticalArrangement = Arrangement.spacedBy(CompactLessonListDefaults.ItemGap)
    ) {
        itemsIndexed(phrases) { index, p ->
            val isSelected = p == selected
            CompactLessonListCard(
                selected = isSelected,
                onClick = { onSelect(p) },
                alternate = index % 2 == 1,
                contentPadding = CompactLessonListDefaults.MultiLineItemPadding
            ) {
                Column {
                    Text(
                        p.pl,
                        color = if (isSelected) Color.White else Color.Black,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    DelayedEnglishTranslation(
                        text = p.en,
                        color = if (isSelected) Color.White.copy(alpha = 0.85f)
                        else LbColors.TextSecondary,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}
