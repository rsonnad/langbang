package com.sponic.langbang.ui.pronunciation

import com.sponic.langbang.ui.theme.LbColors

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Stop
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sponic.langbang.LangbangApplication
import com.sponic.langbang.data.model.ExampleWord
import com.sponic.langbang.data.model.PhonemeEntry
import com.sponic.langbang.data.model.TokenPair
import com.sponic.langbang.domain.NowVoicing
import com.sponic.langbang.domain.NowVoicingBus
import com.sponic.langbang.domain.PlaybackController
import com.sponic.langbang.domain.ensureCachedAudio
import com.sponic.langbang.domain.targetAudioVoice
import com.sponic.langbang.ui.common.CompactLessonListCard
import com.sponic.langbang.ui.common.CompactLessonListDefaults
import com.sponic.langbang.ui.common.SelectionNavButtons
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
                    .width(360.dp)
                    .fillMaxHeight()
                    .background(LbColors.Canvas)
            )
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                selected?.let { ph ->
                    PhonemeDetail(
                        app = app,
                        phoneme = ph,
                        phonemes = data.phonemes,
                        online = online,
                        onSelectPhoneme = { selected = it },
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
        contentPadding = CompactLessonListDefaults.ContentPadding,
        verticalArrangement = Arrangement.spacedBy(CompactLessonListDefaults.ItemGap)
    ) {
        itemsIndexed(phonemes) { index, ph ->
            val isSelected = ph == selected
            CompactLessonListCard(
                selected = isSelected,
                onClick = { onSelect(ph) },
                alternate = index % 2 == 1,
                contentPadding = CompactLessonListDefaults.MultiLineItemPadding
            ) {
                val glyphColor = if (isSelected) Color.White else LbColors.Primary
                val ipaColor = if (isSelected) Color.White.copy(alpha = 0.9f)
                else LbColors.TextSecondary
                val hintColor = if (isSelected) Color.White.copy(alpha = 0.85f)
                else LbColors.TextMuted
                Text(
                    text = buildAnnotatedString {
                        withStyle(
                            SpanStyle(
                                color = glyphColor,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        ) {
                            append(ph.letter)
                        }
                        append("  ")
                        withStyle(
                            SpanStyle(
                                color = ipaColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        ) {
                            append(ph.ipa)
                        }
                        append("  ")
                        withStyle(SpanStyle(color = hintColor)) {
                            append(ph.englishApproximation)
                        }
                    },
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    color = hintColor,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun PhonemeDetail(
    app: LangbangApplication,
    phoneme: PhonemeEntry,
    phonemes: List<PhonemeEntry>,
    online: Boolean,
    onSelectPhoneme: (PhonemeEntry) -> Unit,
    onStartQuiz: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val targetVoice = app.targetAudioVoice()
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

    fun publishExample(ex: ExampleWord) {
        NowVoicingBus.publish(
            NowVoicing(
                en = ex.en,
                pl = ex.pl,
                literal = null,
                lang = "pl",
                words = listOf(TokenPair(ex.pl, ex.en))
            )
        )
    }

    fun stopPronunciationPlayback() {
        playAllJob?.cancel()
        playAllJob = null
        playingAll = false
        playingWord = null
        app.audioPlayer.stop()
        NowVoicingBus.clear()
        PlaybackController.unregister()
    }

    fun startPronunciationPlayback() {
        playingAll = true
        PlaybackController.register { stopPronunciationPlayback() }
        playAllJob = scope.launch {
            try {
                phoneme.examples.forEach { ex ->
                    playingWord = ex.pl
                    publishExample(ex)
                    val f = app.ensureCachedAudio(ex.pl, targetVoice.locale, targetVoice.voice)
                    if (f != null) playFileAndAwait(app, f)
                    kotlinx.coroutines.delay(220)
                }
            } finally {
                playingAll = false
                playingWord = null
                NowVoicingBus.clear()
                PlaybackController.unregister()
            }
        }
    }

    fun playSingleExample(ex: ExampleWord) {
        stopPronunciationPlayback()
        playingWord = ex.pl
        scope.launch {
            val f = app.ensureCachedAudio(ex.pl, targetVoice.locale, targetVoice.voice) ?: return@launch
            app.audioPlayer.play(f) {
                if (playingWord == ex.pl) playingWord = null
            }
        }
    }

    // Stop any in-flight queue when the user navigates to a different phoneme.
    DisposableEffect(phoneme) {
        onDispose {
            stopPronunciationPlayback()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                phoneme.letter,
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
                color = LbColors.Primary
            )
            Spacer(Modifier.width(16.dp))
            Column {
                Text(phoneme.name, fontSize = 12.sp, color = LbColors.TextMuted)
                Text(
                    phoneme.ipa,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = LbColors.TextPrimary
                )
            }
            Spacer(Modifier.weight(1f))
            // Flashcard quiz — relocated here from the top of the left list and made
            // compact, sitting just to the LEFT of the play queue control.
            CompactPronHeaderButton(
                label = "Flashcard quiz",
                onClick = onStartQuiz,
                icon = Icons.Default.School,
                containerColor = MaterialTheme.colorScheme.secondary
            )
            Spacer(Modifier.width(6.dp))
            // Play queue at the TOP next to the phoneme — was buried beside "Common
            // words" further down, easy to miss / below the fold (reported 2026-05-30).
            CompactPronHeaderButton(
                label = if (playingAll) "Stop" else "Play ${phoneme.examples.size}",
                onClick = {
                    if (playingAll) {
                        stopPronunciationPlayback()
                    } else {
                        startPronunciationPlayback()
                    }
                },
                icon = if (playingAll) Icons.Default.Stop else Icons.Default.PlayArrow,
                contentDescription = if (playingAll) "Stop playback" else "Play ${phoneme.examples.size}",
                containerColor = if (playingAll) LbColors.Danger else MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(8.dp))
            SelectionNavButtons(
                items = phonemes,
                selected = phoneme,
                onSelect = onSelectPhoneme,
                previousContentDescription = "Previous phoneme",
                nextContentDescription = "Next phoneme"
            )
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
                        playSingleExample(ex)
                    },
                    onMic = {
                        if (assessing != null) return@ExampleRow
                        assessing = ex.pl
                        micActive = false
                        partial = ""
                        error = null
                        scope.launch {
                            app.pron.assessOnce(
                                locale = targetVoice.locale,
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
private fun CompactPronHeaderButton(
    label: String,
    onClick: () -> Unit,
    icon: ImageVector,
    containerColor: Color,
    contentDescription: String? = label
) {
    Surface(
        color = containerColor,
        contentColor = Color.White,
        shape = RoundedCornerShape(7.dp),
        border = BorderStroke(1.dp, containerColor),
        modifier = Modifier
            .height(30.dp)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = Color.White,
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(5.dp))
            Text(
                label,
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
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
            Icon(
                Icons.Default.PlayArrow,
                contentDescription = "Play",
                tint = LbColors.Primary,
                modifier = Modifier
                    .size(18.dp)
                    .clickable(onClick = onPlay)
            )
            Spacer(Modifier.width(8.dp))
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
