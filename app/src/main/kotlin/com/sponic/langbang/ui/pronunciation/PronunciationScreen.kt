package com.sponic.langbang.ui.pronunciation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sponic.langbang.LangbangApplication
import com.sponic.langbang.data.model.ExampleWord
import com.sponic.langbang.data.model.PhonemeEntry
import com.sponic.langbang.integrations.AzureTtsClient
import com.sponic.langbang.integrations.PronunciationScore
import kotlinx.coroutines.launch

@Composable
fun PronunciationScreen(app: LangbangApplication) {
    val data = remember { app.lessonRepo.pronunciation() }
    var selected by remember { mutableStateOf(data.phonemes.firstOrNull()) }
    var quizOpen by remember { mutableStateOf(false) }
    val online by app.network.online.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxSize()) {
            PhonemeList(
                phonemes = data.phonemes,
                selected = selected,
                onSelect = { selected = it },
                onStartQuiz = { quizOpen = true },
                modifier = Modifier
                    .width(360.dp)
                    .fillMaxHeight()
                    .background(Color(0xFFF3EFE6))
            )
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                selected?.let { ph ->
                    PhonemeDetail(app = app, phoneme = ph, online = online)
                }
            }
        }
    }

    if (quizOpen) {
        PhonemeQuiz(
            app = app,
            phonemes = data.phonemes,
            onDismiss = { quizOpen = false }
        )
    }
}

@Composable
private fun PhonemeList(
    phonemes: List<PhonemeEntry>,
    selected: PhonemeEntry?,
    onSelect: (PhonemeEntry) -> Unit,
    onStartQuiz: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Button(
            onClick = onStartQuiz,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondary
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Icon(Icons.Default.School, contentDescription = null, tint = Color.White)
            Spacer(Modifier.width(8.dp))
            Text("Flashcard quiz", color = Color.White, fontWeight = FontWeight.SemiBold)
        }
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(phonemes) { ph ->
                val isSelected = ph == selected
                Card(
                    onClick = { onSelect(ph) },
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) MaterialTheme.colorScheme.primary
                        else Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            ph.letter,
                            color = if (isSelected) Color.White else Color(0xFF0F4C81),
                            fontWeight = FontWeight.Bold,
                            fontSize = 28.sp,
                            modifier = Modifier.width(56.dp)
                        )
                        Column {
                            Text(
                                ph.ipa,
                                color = if (isSelected) Color.White
                                else Color(0xFF555555),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                ph.englishApproximation,
                                color = if (isSelected) Color.White.copy(alpha = 0.85f)
                                else Color(0xFF888888),
                                fontSize = 12.sp,
                                maxLines = 2
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PhonemeDetail(
    app: LangbangApplication,
    phoneme: PhonemeEntry,
    online: Boolean
) {
    val scope = rememberCoroutineScope()
    // Word currently being scored (only one at a time).
    var assessing by remember(phoneme) { mutableStateOf<String?>(null) }
    // Latest score by Polish word, cleared when the user switches phonemes.
    val scores = remember(phoneme) { mutableStateOf(mapOf<String, PronunciationScore>()) }
    var error by remember(phoneme) { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                phoneme.letter,
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0F4C81)
            )
            Spacer(Modifier.width(16.dp))
            Column {
                Text(phoneme.name, fontSize = 12.sp, color = Color(0xFF888888))
                Text(
                    phoneme.ipa,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF333333)
                )
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8EE)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "Sounds like",
                    fontSize = 11.sp,
                    color = Color(0xFF7A5A1F),
                    fontWeight = FontWeight.SemiBold
                )
                Text(phoneme.englishApproximation, fontSize = 14.sp)
                Text(phoneme.description, fontSize = 12.sp, color = Color(0xFF555555))
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Common words",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF0F4C81),
                modifier = Modifier.weight(1f)
            )
            if (online) {
                Text(
                    "Tap mic to score yourself",
                    fontSize = 11.sp,
                    color = Color(0xFF888888)
                )
            } else {
                Text(
                    "Offline — mic scoring unavailable",
                    fontSize = 11.sp,
                    color = Color(0xFFC62828)
                )
            }
        }

        error?.let {
            Text("Error: $it", color = Color.Red, fontSize = 12.sp)
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            phoneme.examples.forEach { ex ->
                ExampleRow(
                    ex = ex,
                    online = online,
                    isAssessing = assessing == ex.pl,
                    score = scores.value[ex.pl],
                    onPlay = {
                        val f = app.audioCache.fileFor(
                            AzureTtsClient.LOCALE_PL, AzureTtsClient.PL_PL_F, ex.pl
                        )
                        app.audioPlayer.play(f)
                    },
                    onMic = {
                        if (assessing != null) return@ExampleRow
                        assessing = ex.pl
                        error = null
                        scope.launch {
                            app.pron.assessOnce("pl-PL", ex.pl)
                                .onSuccess { s ->
                                    scores.value = scores.value + (ex.pl to s)
                                }
                                .onFailure { error = it.message }
                            assessing = null
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun ExampleRow(
    ex: ExampleWord,
    online: Boolean,
    isAssessing: Boolean,
    score: PronunciationScore?,
    onPlay: () -> Unit,
    onMic: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFFEAF1F7)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier
                .padding(horizontal = 12.dp, vertical = 4.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onPlay, modifier = Modifier.size(28.dp)) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    tint = Color(0xFF0F4C81)
                )
            }
            Spacer(Modifier.width(4.dp))
            Text(
                ex.pl,
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF0F4C81),
                modifier = Modifier.width(150.dp)
            )
            Text(
                ex.en,
                fontSize = 13.sp,
                color = Color(0xFF555555),
                modifier = Modifier.weight(1f)
            )
            score?.let {
                ScoreChip(it.accuracy.toInt())
                Spacer(Modifier.width(8.dp))
            }
            if (online) {
                if (isAssessing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    IconButton(onClick = onMic, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Default.Mic,
                            contentDescription = "Score",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ScoreChip(accuracy: Int) {
    val color = when {
        accuracy >= 80 -> Color(0xFF2E7D32)
        accuracy >= 60 -> Color(0xFFE65100)
        else -> Color(0xFFC62828)
    }
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = color.copy(alpha = 0.12f)
    ) {
        Text(
            "$accuracy",
            color = color,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}
