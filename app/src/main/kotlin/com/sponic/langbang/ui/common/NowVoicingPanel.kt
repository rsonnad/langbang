package com.sponic.langbang.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sponic.langbang.LangbangApplication
import com.sponic.langbang.domain.NowVoicing
import com.sponic.langbang.domain.NowVoicingBus
import com.sponic.langbang.domain.PlaybackController
import com.sponic.langbang.domain.awaitAudioPlayback
import com.sponic.langbang.domain.ensureCachedAudio
import com.sponic.langbang.domain.targetAudioVoice
import com.sponic.langbang.domain.targetSlowVoice
import com.sponic.langbang.ui.theme.LbColors
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext

/**
 * Single Now Voicing surface for every app location.
 *
 * Keep the panel chrome, transport controls, status format, and body renderer here.
 * Callers only supply the current phrase and optional star behavior; that keeps
 * quiz, app-shell, and Play Phrases sheet visuals from drifting.
 */
@Composable
fun NowVoicingPanel(
    app: LangbangApplication,
    pinned: NowVoicing?,
    live: NowVoicing?,
    modifier: Modifier = Modifier,
    isStarred: Boolean = false,
    onToggleStar: () -> Unit = {},
    syllableShading: Boolean = true,
    idlePlaceholder: String = "Playback is starting..."
) {
    val transport by PlaybackController.transport.collectAsState()
    // Observe the controller's pause flag directly so a pause-in-place (StudyQueuePlayer,
    // which pauses the clip without republishing NowVoicing) flips the icon immediately;
    // OR-ing the transport's own flag keeps screens not yet on the controller correct too.
    val pausedSignal by PlaybackController.paused.collectAsState()
    val paused = pausedSignal || transport?.isPaused?.invoke() == true
    val effectiveLive = remember(live, paused) { if (paused) live?.copy(lang = "pause") else live }
    val scope = rememberCoroutineScope()
    var wordDrillJob by remember { mutableStateOf<Job?>(null) }
    val visibleWords = remember(pinned?.pl, pinned?.words) {
        pinned.nowVoicingPolishWords()
    }
    LaunchedEffect(visibleWords) {
        val slowVoice = app.targetSlowVoice()
        val target = app.targetAudioVoice()
        visibleWords.forEach { word ->
            app.ensureCachedAudio(word, target.locale, slowVoice)
            app.ensureCachedAudio(word, target.locale, target.voice)
        }
    }
    val playTappedWord: (String) -> Unit = { raw ->
        val word = raw.polishWordForAudio()
        val item = pinned
        if (word.isNotEmpty() && item != null) {
            val currentTransport = transport
            if (currentTransport?.parkCurrent != null) {
                PlaybackController.parkCurrent()
            } else if (currentTransport?.pauseResume != null && !currentTransport.isPaused()) {
                PlaybackController.pauseResume()
            } else {
                app.audioPlayer.stop()
            }
            wordDrillJob?.cancel()
            wordDrillJob = scope.launch {
                val thisJob = coroutineContext[Job]
                try {
                    val slowVoice = app.targetSlowVoice()
                    NowVoicingBus.publish(item.copy(lang = "pl-slow", plHidden = false))
                    app.playCachedFirst(word, app.targetAudioVoice().locale, slowVoice)
                    NowVoicingBus.publish(item.copy(lang = "pl", plHidden = false))
                    app.playCachedFirst(word, app.targetAudioVoice().locale, app.targetAudioVoice().voice)
                } finally {
                    if (wordDrillJob == thisJob) {
                        wordDrillJob = null
                    }
                }
            }
        }
    }

    Surface(
        color = GrammarVisuals.NowVoicingPanel.Background,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(
            GrammarVisuals.NowVoicingPanel.BorderWidth,
            GrammarVisuals.NowVoicingPanel.Border
        ),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.Top
            ) {
                NowVoicingBody(
                    pinned = pinned,
                    live = effectiveLive,
                    statusText = nowVoicingStatus(pinned, effectiveLive),
                    onPlWordClick = playTappedWord,
                    idlePlaceholder = idlePlaceholder,
                    syllableShading = syllableShading,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp, end = 16.dp, bottom = 24.dp)
                )
                Column(
                    modifier = Modifier.width(40.dp),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    NowVoicingTransport()
                }
            }
            NowVoicingPlaybackOptions(
                app = app,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 56.dp, bottom = 8.dp)
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 18.dp, bottom = 8.dp)
                    .size(48.dp)
                    .clickable(enabled = pinned != null, onClick = onToggleStar),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isStarred) Icons.Default.Star else Icons.Default.StarBorder,
                    contentDescription = if (isStarred) "Unstar phrase" else "Star phrase",
                    tint = when {
                        pinned == null -> LbColors.TextMuted.copy(alpha = 0.35f)
                        isStarred -> LbColors.Stop
                        else -> LbColors.Stop.copy(alpha = 0.78f)
                    },
                    modifier = Modifier.size(44.dp)
                )
            }
        }
    }
}

@Composable
private fun NowVoicingPlaybackOptions(
    app: LangbangApplication,
    modifier: Modifier = Modifier
) {
    var speakEnglish by remember { mutableStateOf(app.practicePrefs.speakEnglishFirst()) }
    var slowFirst by remember { mutableStateOf(app.practicePrefs.slowFirst()) }
    var loopPractice by remember { mutableStateOf(app.practicePrefs.loopPractice()) }
    Row(
        modifier = modifier.height(24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        NowVoicingOptionCheckbox(
            label = "English",
            checked = speakEnglish,
            onCheckedChange = {
                speakEnglish = it
                app.practicePrefs.setSpeakEnglishFirst(it)
            }
        )
        NowVoicingOptionCheckbox(
            label = "Slow",
            checked = slowFirst,
            onCheckedChange = {
                slowFirst = it
                app.practicePrefs.setSlowFirst(it)
            }
        )
        NowVoicingLoopToggle(
            checked = loopPractice,
            onCheckedChange = {
                loopPractice = it
                app.practicePrefs.setLoopPractice(it)
            }
        )
    }
}

@Composable
private fun NowVoicingLoopToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        color = if (checked) LbColors.Audio.copy(alpha = 0.16f) else Color.Transparent,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .size(24.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable { onCheckedChange(!checked) }
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                Icons.Default.Repeat,
                contentDescription = if (checked) "Loop practice on" else "Loop practice off",
                tint = if (checked) LbColors.Audio else LbColors.TextMuted,
                modifier = Modifier.size(17.dp)
            )
        }
    }
}

@Composable
private fun NowVoicingOptionCheckbox(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clickable { onCheckedChange(!checked) }
    ) {
        SubtleCheckbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(1.dp))
        Text(
            label,
            color = LbColors.TextSecondary,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

private const val WORD_EDGE_PUNCTUATION = ".,;:!?()[]{}\"'"

private fun String.polishWordForAudio(): String =
    trim().trim { WORD_EDGE_PUNCTUATION.contains(it) }

private fun NowVoicing?.nowVoicingPolishWords(): List<String> {
    if (this == null) return emptyList()
    val raw = words?.map { it.pl }?.takeIf { it.isNotEmpty() }
        ?: pl.split(Regex("\\s+"))
    return raw.map { it.polishWordForAudio() }
        .filter { it.isNotEmpty() }
        .distinct()
        .take(12)
}

private suspend fun LangbangApplication.playCachedFirst(
    text: String,
    locale: String,
    voice: String
) {
    val file = audioCache.fileFor(locale, voice, text)
    val readyFile = if (audioCache.has(file)) file
        else ensureCachedAudio(text, locale, voice)
    readyFile?.let { awaitAudioPlayback(it) }
}

private fun nowVoicingStatus(pinned: NowVoicing?, live: NowVoicing?): String {
    val activeLang = live?.lang
    val tag = when (activeLang) {
        "pl-slow" -> "PL (slow)"
        "pl" -> "PL"
        "en" -> "EN"
        "pause" -> ""
        null -> "idle"
        else -> activeLang
    }
    val detail = listOfNotNull(
        pinned?.position,
        tag.takeIf { it.isNotEmpty() }
    ).joinToString(" · ")
    return if (detail.isBlank()) "NOW VOICING" else "NOW VOICING\n$detail"
}

@Composable
private fun NowVoicingTransport() {
    val transport by PlaybackController.transport.collectAsState()
    val anyPlaying by PlaybackController.playing.collectAsState()
    val pausedSignal by PlaybackController.paused.collectAsState()
    val isPaused = pausedSignal || transport?.isPaused?.invoke() == true
    val playPauseIcon = if (isPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause
    val playPauseLabel = if (isPaused) "Resume" else "Pause"

    Surface(
        color = Color.White,
        shape = RoundedCornerShape(11.dp),
        border = BorderStroke(1.dp, LbColors.Line)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(4.dp)
        ) {
            TransportIcon(
                icon = Icons.Filled.SkipPrevious,
                label = "Rewind",
                enabled = transport?.rewind != null,
                onClick = { PlaybackController.rewind() }
            )
            TransportIcon(
                icon = Icons.Filled.SkipNext,
                label = "Next",
                enabled = transport?.next != null,
                onClick = { PlaybackController.next() }
            )
            TransportIcon(
                icon = playPauseIcon,
                label = playPauseLabel,
                enabled = transport?.pauseResume != null,
                onClick = { PlaybackController.pauseResume() }
            )
            TransportIcon(
                icon = Icons.Filled.Stop,
                label = "Stop",
                enabled = anyPlaying,
                onClick = { PlaybackController.stop() }
            )
        }
    }
}

@Composable
private fun TransportIcon(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val active = label == "Stop" || label == "Pause" || label == "Resume"
    val bg = when {
        label == "Stop" -> LbColors.Stop
        label == "Pause" || label == "Resume" -> LbColors.Audio
        else -> Color.White
    }
    val border = if (active) bg else LbColors.Line
    val fg = if (active) Color.White else LbColors.TextPrimary
    ControlIconButton(
        icon = icon,
        label = label,
        enabled = enabled,
        container = bg,
        border = border,
        content = fg,
        onClick = onClick
    )
}

@Composable
private fun ControlIconButton(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    container: Color,
    border: Color,
    content: Color,
    onClick: () -> Unit,
    size: Int = 32,
    iconSize: Int = 17
) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = Modifier
            .size(size.dp)
            .background(container.copy(alpha = if (enabled) 1f else 0.35f), shape)
            .border(1.dp, border.copy(alpha = if (enabled) 1f else 0.4f), shape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (enabled) content else LbColors.TextMuted.copy(alpha = 0.7f),
            modifier = Modifier.size(iconSize.dp)
        )
    }
}
