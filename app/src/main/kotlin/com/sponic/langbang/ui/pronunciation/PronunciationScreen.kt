package com.sponic.langbang.ui.pronunciation

import com.sponic.langbang.ui.theme.LbColors

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
import androidx.compose.material.icons.filled.Stop
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sponic.langbang.LangbangApplication
import com.sponic.langbang.data.model.ExampleWord
import com.sponic.langbang.data.model.PhonemeEntry
import com.sponic.langbang.data.model.TokenPair
import com.sponic.langbang.domain.NowVoicing
import com.sponic.langbang.domain.NowVoicingBus
import com.sponic.langbang.integrations.AzureTtsClient
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume
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
                modifier = Modifier
                    .width(300.dp)
                    .fillMaxHeight()
                    .background(LbColors.Canvas)
            )
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                selected?.let { ph ->
                    PhonemeDetail(
                        app = app,
                        phoneme = ph,
                        online = online,
                        onStartQuiz = { quizOpen = true }
                    )
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
    modifier: Modifier = Modifier
) {
    // Flashcard-quiz button moved out of here to the detail header (left of "Play
    // all"), so the list runs flush to the top and shows more rows.
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
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
                // Letter + IPA on the first line (top-left justified); the english
                // approximation wraps on its own line UNDERNEATH, spanning the full card
                // width. Stacking instead of a single row lets a long hint use the whole
                // width — fewer lines, no skinny right-hand column eating horizontal space.
                Column(
                    Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            ph.letter,
                            color = if (isSelected) Color.White else LbColors.Primary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            ph.ipa,
                            color = if (isSelected) Color.White else LbColors.TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Text(
                        ph.englishApproximation,
                        color = if (isSelected) Color.White.copy(alpha = 0.85f)
                        else LbColors.TextMuted,
                        fontSize = 11.sp,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun PhonemeDetail(
    app: LangbangApplication,
    phoneme: PhonemeEntry,
    online: Boolean,
    onStartQuiz: () -> Unit
) {
    val scope = rememberCoroutineScope()
    // Word currently being scored (only one at a time).
    var assessing by remember(phoneme) { mutableStateOf<String?>(null) }
    // Mic capture confirmed by SDK sessionStarted event. Drives "Listening…" badge.
    var micActive by remember(phoneme) { mutableStateOf(false) }
    // Live partial transcript from Azure's `recognizing` event stream.
    var partial by remember(phoneme) { mutableStateOf("") }
    // Latest score by Polish word, cleared when the user switches phonemes.
    val scores = remember(phoneme) { mutableStateOf(mapOf<String, PronunciationScore>()) }
    var error by remember(phoneme) { mutableStateOf<String?>(null) }
    // Play-all queue state. `playingAll` drives the button label/icon; `playingWord`
    // highlights whichever row is currently being voiced. Switching phonemes (the
    // `remember(phoneme)` keys) cancels the previous queue via job.cancel below.
    var playingAll by remember(phoneme) { mutableStateOf(false) }
    var playingWord by remember(phoneme) { mutableStateOf<String?>(null) }
    var playAllJob by remember(phoneme) {
        mutableStateOf<kotlinx.coroutines.Job?>(null)
    }
    // Stop any in-flight queue when the user navigates to a different phoneme.
    DisposableEffect(phoneme) {
        onDispose {
            playAllJob?.cancel()
            playAllJob = null
            app.audioPlayer.stop()
            NowVoicingBus.clear()
        }
    }

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
                color = LbColors.Primary
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(phoneme.name, fontSize = 12.sp, color = LbColors.TextMuted)
                Text(
                    phoneme.ipa,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = LbColors.TextPrimary
                )
            }
            // Flashcard quiz — relocated here from the top of the left list and made
            // compact, sitting just to the LEFT of "Play all".
            Button(
                onClick = onStartQuiz,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary
                ),
                shape = RoundedCornerShape(16.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Icon(
                    Icons.Default.School,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "?? Flashcard",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.width(8.dp))
            // Play all at the TOP next to the phoneme — was buried beside "Common
            // words" further down, easy to miss / below the fold (reported 2026-05-30).
            Button(
                onClick = {
                    if (playingAll) {
                        playAllJob?.cancel()
                        playAllJob = null
                        playingAll = false
                        playingWord = null
                        app.audioPlayer.stop()
                        NowVoicingBus.clear()
                    } else {
                        playingAll = true
                        playAllJob = scope.launch {
                            try {
                                phoneme.examples.forEach { ex ->
                                    playingWord = ex.pl
                                    // Surface the word in the sticky Now Voicing panel so the
                                    // user sees the PL/EN of whatever is sounding (same panel the
                                    // verb/noun reciters drive). Single-token gloss.
                                    NowVoicingBus.publish(
                                        NowVoicing(
                                            en = ex.en,
                                            pl = ex.pl,
                                            literal = null,
                                            lang = "pl",
                                            words = listOf(TokenPair(ex.pl, ex.en))
                                        )
                                    )
                                    val f = app.audioCache.fileFor(
                                        AzureTtsClient.LOCALE_PL,
                                        AzureTtsClient.PL_PL_F,
                                        ex.pl
                                    )
                                    playFileAndAwait(app, f)
                                    kotlinx.coroutines.delay(220)
                                }
                            } finally {
                                playingAll = false
                                playingWord = null
                                NowVoicingBus.clear()
                            }
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (playingAll) LbColors.Danger
                    else MaterialTheme.colorScheme.primary
                ),
                shape = RoundedCornerShape(16.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)
            ) {
                Icon(
                    if (playingAll) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = if (playingAll) "Stop" else "Play all",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    if (playingAll) "Stop" else "Play all",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = LbColors.SurfaceRaised),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)) {
                // "Sounds like" label dropped — the approximation already reads as a
                // sounds-like hint, and at full width approximation + description fit in
                // ~2 lines instead of the 3 the narrow column forced (reported 2026-05-30).
                Text(
                    phoneme.englishApproximation,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = LbColors.TextPrimary
                )
                Text(phoneme.description, fontSize = 12.sp, color = LbColors.TextSecondary)
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "Common words",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = LbColors.Primary,
                modifier = Modifier.weight(1f)
            )
            // Play-all moved to the phoneme header (top of panel). This duplicate
            // beside "Common words" is disabled via `if (false)` so it never renders;
            // kept the block minimal to avoid a risky multi-line delete during a
            // concurrent edit of this file (2026-05-30).
            if (false) Button(
                onClick = {
                    if (playingAll) {
                        playAllJob?.cancel()
                        playAllJob = null
                        playingAll = false
                        playingWord = null
                        app.audioPlayer.stop()
                    } else {
                        playingAll = true
                        playAllJob = scope.launch {
                            try {
                                phoneme.examples.forEach { ex ->
                                    playingWord = ex.pl
                                    val f = app.audioCache.fileFor(
                                        AzureTtsClient.LOCALE_PL,
                                        AzureTtsClient.PL_PL_F,
                                        ex.pl
                                    )
                                    playFileAndAwait(app, f)
                                    kotlinx.coroutines.delay(220)
                                }
                            } finally {
                                playingAll = false
                                playingWord = null
                            }
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (playingAll) LbColors.Danger
                    else MaterialTheme.colorScheme.primary
                ),
                shape = RoundedCornerShape(16.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp)
            ) {
                Icon(
                    if (playingAll) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = if (playingAll) "Stop" else "Play all",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    if (playingAll) "Stop" else "Play all",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            if (online) {
                Text(
                    "Tap mic to score yourself",
                    fontSize = 11.sp,
                    color = LbColors.TextMuted
                )
            } else {
                Text(
                    "Offline — mic scoring unavailable",
                    fontSize = 11.sp,
                    color = LbColors.Danger
                )
            }
        }

        // Inline "Listening…" / partial-transcript banner. Visible whenever a row is
        // being scored — bigger and louder than the per-row spinner so the user has
        // unambiguous feedback that the mic is open and what Azure thinks it's hearing.
        if (assessing != null) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (micActive) LbColors.SuccessSoft else LbColors.WarningSoft
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = if (micActive) LbColors.Success else LbColors.Label
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (micActive) "Listening — say \"${assessing}\"" else "Opening mic…",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (micActive) LbColors.Success else LbColors.Label
                        )
                        if (partial.isNotBlank()) {
                            Text(
                                "Heard: $partial",
                                fontSize = 12.sp,
                                color = LbColors.TextSecondary
                            )
                        }
                    }
                }
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
                    isCurrent = playingWord == ex.pl,
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
                        micActive = false
                        partial = ""
                        error = null
                        scope.launch {
                            app.pron.assessOnce(
                                locale = "pl-PL",
                                referenceText = ex.pl,
                                onListening = { micActive = true },
                                onPartial = { partial = it }
                            )
                                .onSuccess { s ->
                                    scores.value = scores.value + (ex.pl to s)
                                }
                                .onFailure { error = it.message }
                            assessing = null
                            micActive = false
                            partial = ""
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
    isCurrent: Boolean = false,
    score: PronunciationScore?,
    onPlay: () -> Unit,
    onMic: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrent) LbColors.PrimarySoft else LbColors.PrimarySoft
        ),
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
                    tint = LbColors.Primary
                )
            }
            Spacer(Modifier.width(4.dp))
            Text(
                ex.pl,
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
                color = LbColors.Primary,
                modifier = Modifier.width(150.dp)
            )
            Text(
                ex.en,
                fontSize = 13.sp,
                color = LbColors.TextSecondary,
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
        accuracy >= 80 -> LbColors.Success
        accuracy >= 60 -> LbColors.Warning
        else -> LbColors.Danger
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

/**
 * Suspends until [file] finishes playing. If the file is missing AudioPlayer.play
 * silently no-ops without firing onDone — checking up front avoids hanging the
 * Play-all queue. Cancellation stops playback so navigating away during a queue
 * doesn't keep audio bleeding into the next phoneme.
 */
private suspend fun playFileAndAwait(app: LangbangApplication, file: File) {
    if (!file.exists()) return
    suspendCancellableCoroutine<Unit> { cont ->
        app.audioPlayer.play(file) { if (cont.isActive) cont.resume(Unit) }
        cont.invokeOnCancellation { app.audioPlayer.stop() }
    }
}
