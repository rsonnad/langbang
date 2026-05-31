package com.sponic.langbang.ui.phrases

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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sponic.langbang.LangbangApplication
import com.sponic.langbang.data.model.PhraseGroup
import com.sponic.langbang.data.model.SentenceExample
import com.sponic.langbang.domain.NowVoicing
import com.sponic.langbang.domain.NowVoicingBus
import com.sponic.langbang.domain.PlaybackController
import com.sponic.langbang.integrations.AzureTtsClient
import com.sponic.langbang.ui.common.WordAlignedPolish
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume

/**
 * Phrases tab — multi-sentence real-world utterances (introductions, small-talk, etc.).
 * Two-pane: left list of groups, right detail with each sentence playable individually
 * plus a "Play all" that plays the whole group EN → PL-slow → PL per sentence.
 *
 * Custom user-added groups merge in from `UserPhraseStore` ahead of the bundled ones
 * (so personal phrases are immediately visible at the top). Editing flow is deferred
 * to a later turn — for now the bundled "Introduction — Rahul" is enough to dogfood.
 */
@Composable
fun PhrasesScreen(
    app: LangbangApplication,
    nowVoicing: @Composable () -> Unit = {}
) {
    val data = remember { app.lessonRepo.lesson5() }
    var selected by remember { mutableStateOf(data.groups.firstOrNull()) }

    Row(modifier = Modifier.fillMaxSize()) {
        PhraseGroupList(
            groups = data.groups,
            selected = selected,
            onSelect = { selected = it },
            modifier = Modifier
                .width(280.dp)
                .fillMaxHeight()
                .background(LbColors.Canvas)
        )
        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
            nowVoicing()
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                selected?.let { group ->
                    PhraseDetail(app = app, group = group)
                } ?: Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "No phrases yet.",
                        fontSize = 13.sp, color = LbColors.TextMuted
                    )
                }
            }
        }
    }
}

@Composable
private fun PhraseGroupList(
    groups: List<PhraseGroup>,
    selected: PhraseGroup?,
    onSelect: (PhraseGroup) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(groups, key = { "g-${it.id}" }) { g ->
            val isSel = g == selected
            Card(
                onClick = { onSelect(g) },
                colors = CardDefaults.cardColors(
                    containerColor = if (isSel) MaterialTheme.colorScheme.primary
                    else Color.White
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        g.title,
                        color = if (isSel) Color.White else LbColors.Primary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                    if (g.subtitle.isNotBlank()) {
                        Text(
                            g.subtitle,
                            color = if (isSel) Color.White.copy(alpha = 0.85f)
                            else LbColors.TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                    Text(
                        "${g.sentences.size} sentences",
                        color = if (isSel) Color.White.copy(alpha = 0.75f)
                        else LbColors.TextMuted,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun PhraseDetail(app: LangbangApplication, group: PhraseGroup) {
    val scope = rememberCoroutineScope()
    var playingIndex by remember(group) { mutableStateOf(-1) }
    var playJob by remember(group) { mutableStateOf<Job?>(null) }
    val playing = playJob?.isActive == true
    val starred by app.starredPhrases.starred.collectAsState()
    // "Starred only" scopes the quiz to the learner's personal deck (across ALL groups),
    // not just the current group. Sticky within this composition.
    var starredOnly by remember { mutableStateOf(false) }
    // Cancel any in-flight playback when this group's detail leaves composition.
    DisposableEffect(group) {
        onDispose {
            playJob?.cancel()
            playJob = null
            app.audioPlayer.stop()
            NowVoicingBus.clear()
        }
    }

    val stopPlayback = {
        playJob?.cancel()
        playJob = null
        playingIndex = -1
        app.audioPlayer.stop()
        NowVoicingBus.clear()
        PlaybackController.unregister()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Header: title + Play all + Quiz
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    group.title,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = LbColors.Primary
                )
                if (group.subtitle.isNotBlank()) {
                    Text(
                        group.subtitle,
                        fontSize = 12.sp,
                        color = LbColors.TextSecondary
                    )
                }
            }
            Button(
                onClick = {
                    if (playing) {
                        stopPlayback()
                    } else {
                        PlaybackController.register { stopPlayback() }
                        playJob = scope.launch {
                            try {
                                group.sentences.forEachIndexed { i, s ->
                                    playingIndex = i
                                    val position = "${i + 1}/${group.sentences.size}"
                                    publishNV(s, "en", position)
                                    playAndAwait(app, s.en, AzureTtsClient.LOCALE_EN,
                                        AzureTtsClient.EN_US_F)
                                    if (app.practicePrefs.slowFirst()) {
                                        publishNV(s, "pl-slow", position)
                                        playAndAwait(app, s.pl, AzureTtsClient.LOCALE_PL,
                                            app.audioPrefs.slowPlVoice())
                                    }
                                    publishNV(s, "pl", position)
                                    playAndAwait(app, s.pl, AzureTtsClient.LOCALE_PL,
                                        AzureTtsClient.PL_PL_F)
                                }
                            } finally {
                                playingIndex = -1
                                playJob = null
                                NowVoicingBus.clear()
                                PlaybackController.unregister()
                            }
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (playing) LbColors.Danger
                    else MaterialTheme.colorScheme.primary
                ),
                shape = RoundedCornerShape(20.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)
            ) {
                Icon(
                    if (playing) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    if (playing) "Stop" else "Play all",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.width(8.dp))
            // Quiz: show EN, hold 1.5s, reveal PL, then voice PL. Optionally scoped to
            // the starred deck (across all groups). Disabled while Play-all is running.
            if (!playing) {
                Button(
                    onClick = {
                        val pool: List<SentenceExample> =
                            if (starredOnly) {
                                app.lessonRepo.lesson5().groups
                                    .flatMap { it.sentences }
                                    .filter { it.pl in starred }
                            } else group.sentences
                        if (pool.isEmpty()) return@Button
                        PlaybackController.register { stopPlayback() }
                        playJob = scope.launch {
                            try {
                                pool.forEachIndexed { i, s ->
                                    playingIndex = i
                                    val position = "${i + 1}/${pool.size}"
                                    // English cue, Polish hidden so the learner recalls it.
                                    publishNV(s, "en", position, plHidden = true, quiz = true)
                                    playAndAwait(app, s.en, AzureTtsClient.LOCALE_EN,
                                        AzureTtsClient.EN_US_F)
                                    // 1.5s recall window — Polish still hidden.
                                    publishNV(s, "pause", position, plHidden = true, quiz = true)
                                    delay(1500L)
                                    // Reveal Polish text, then speak it.
                                    publishNV(s, "pl", position, plHidden = false, quiz = true)
                                    playAndAwait(app, s.pl, AzureTtsClient.LOCALE_PL,
                                        AzureTtsClient.PL_PL_F)
                                }
                            } finally {
                                playingIndex = -1
                                playJob = null
                                NowVoicingBus.clear()
                                PlaybackController.unregister()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = LbColors.Accent),
                    shape = RoundedCornerShape(20.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)
                ) {
                    Text("Quiz", color = Color.White, fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // "Starred only" quiz scope toggle.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = starredOnly,
                onCheckedChange = { starredOnly = it },
                colors = CheckboxDefaults.colors(
                    checkedColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "Quiz starred only (${starred.size} ★ across all groups)",
                fontSize = 11.sp,
                color = LbColors.TextSecondary
            )
        }

        // Sentence list
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            group.sentences.forEachIndexed { i, s ->
                SentenceRow(
                    sentence = s,
                    isCurrent = i == playingIndex,
                    isStarred = s.pl in starred,
                    onToggleStar = { app.starredPhrases.toggle(s.pl) },
                    onPlay = {
                        // If the global queue is playing, leave it alone; just play this
                        // one sentence as a one-off.
                        scope.launch {
                            publishNV(s, "pl", "single")
                            playAndAwait(app, s.pl, AzureTtsClient.LOCALE_PL,
                                AzureTtsClient.PL_PL_F)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun SentenceRow(
    sentence: SentenceExample,
    isCurrent: Boolean,
    isStarred: Boolean,
    onToggleStar: () -> Unit,
    onPlay: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrent) LbColors.PrimarySoft else LbColors.SurfaceRaised
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    sentence.en,
                    fontSize = 12.sp,
                    color = LbColors.TextMuted
                )
                WordAlignedPolish(
                    sentence = sentence,
                    plFontSize = 18.sp,
                    plFontWeight = FontWeight.SemiBold,
                    glossFontSize = 11.sp
                )
            }
            // Star toggles membership in the personal quiz deck (gold when starred).
            IconButton(onClick = onToggleStar) {
                Icon(
                    if (isStarred) Icons.Default.Star else Icons.Default.StarBorder,
                    contentDescription = if (isStarred) "Unstar" else "Star",
                    tint = if (isStarred) LbColors.Accent else LbColors.TextMuted,
                    modifier = Modifier.size(24.dp)
                )
            }
            IconButton(onClick = onPlay) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    tint = LbColors.Primary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

private fun publishNV(
    s: SentenceExample,
    lang: String,
    position: String,
    plHidden: Boolean = false,
    quiz: Boolean = false
) {
    NowVoicingBus.publish(
        NowVoicing(
            en = s.en, pl = s.pl, literal = s.literal,
            lang = lang, position = position, words = s.words,
            plHidden = plHidden, quizMode = quiz
        )
    )
}

private suspend fun playAndAwait(
    app: LangbangApplication,
    text: String,
    locale: String,
    voice: String
) {
    if (text.isEmpty()) return
    val file = app.audioCache.fileFor(locale, voice, text)
    if (!app.audioCache.has(file)) {
        app.tts.synthesize(text, voice, locale, file)
    }
    if (!app.audioCache.has(file)) return
    awaitPlayback(app, file)
}

private suspend fun awaitPlayback(app: LangbangApplication, file: File) {
    suspendCancellableCoroutine<Unit> { cont ->
        app.audioPlayer.play(file) { if (cont.isActive) cont.resume(Unit) }
        cont.invokeOnCancellation { app.audioPlayer.stop() }
    }
}
