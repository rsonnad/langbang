package com.sponic.langbang.ui.lessons

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import com.sponic.langbang.ui.phrase.PhraseDetail
import com.sponic.langbang.ui.word.WordSheet

private enum class L2Tab(val label: String) {
    Verbs("Verbs"),
    Phrases("Phrases")
}

@Composable
fun LessonScreen(app: LangbangApplication, prefetch: PrefetchProgress) {
    val lesson = remember { app.lessonRepo.lesson2() }
    var tab by remember { mutableStateOf(L2Tab.Verbs) }

    Column(modifier = Modifier.fillMaxSize()) {
        Header(tab = tab, onSelect = { tab = it })
        Box(modifier = Modifier.fillMaxSize()) {
            when (tab) {
                L2Tab.Verbs -> VerbsTab(app, prefetch)
                L2Tab.Phrases -> PhrasesPane(app, prefetch)
            }
        }
    }
}

@Composable
private fun Header(
    tab: L2Tab,
    onSelect: (L2Tab) -> Unit
) {
    Surface(color = Color(0xFFEDE5D2), modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            L2Tab.values().forEach { t ->
                FilterChip(
                    selected = tab == t,
                    onClick = { onSelect(t) },
                    label = { Text(t.label, fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = Color.White
                    )
                )
            }
        }
    }
}

@Composable
private fun PhrasesPane(app: LangbangApplication, prefetch: PrefetchProgress) {
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
                .background(Color(0xFFF3EFE6))
        )
        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
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

@Composable
private fun PhraseList(
    phrases: List<PhraseEntry>,
    selected: PhraseEntry?,
    onSelect: (PhraseEntry) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(phrases) { p ->
            val isSelected = p == selected
            Card(
                onClick = { onSelect(p) },
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) MaterialTheme.colorScheme.primary
                    else Color.White
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text(
                        p.en,
                        color = if (isSelected) Color.White else Color.Black,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp
                    )
                    Text(
                        p.pl,
                        color = if (isSelected) Color.White.copy(alpha = 0.85f)
                        else Color(0xFF555555),
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}
