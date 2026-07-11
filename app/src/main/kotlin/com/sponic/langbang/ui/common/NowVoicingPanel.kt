package com.sponic.langbang.ui.common

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.sponic.langbang.LangbangApplication
import com.sponic.langbang.domain.NowVoicing
import com.sponic.langbang.domain.NowVoicingBus
import com.sponic.langbang.domain.PlaybackController
import com.sponic.langbang.domain.SpeechRating
import com.sponic.langbang.ui.theme.LbColors

/**
 * Single Now Voicing surface for every app location.
 *
 * Keep the panel chrome, transport controls, status format, and body renderer here.
 * Callers only supply the current phrase and optional star behavior; that keeps
 * quiz, app-shell, Play Phrases sheet, and the LLM screen visuals from drifting.
 *
 * @param largeFormat render the body in the larger external/LLM layout.
 * @param showStar     show the star toggle (off for surfaces with no star backing).
 * @param chrome       true = bordered card (the default in-app panel); false = full-bleed
 *                     surface with [background] color (the LLM screen fills the whole tab).
 * @param background   fill color when [chrome] is false (e.g. maroon for an error speaker).
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
    idlePlaceholder: String = "Playback is starting...",
    largeFormat: Boolean = false,
    showStar: Boolean = true,
    chrome: Boolean = true,
    background: Color = GrammarVisuals.NowVoicingPanel.Background,
) {
    val transport by PlaybackController.transport.collectAsState()
    // Observe the controller's pause flag directly so a pause-in-place (StudyQueuePlayer,
    // which pauses the clip without republishing NowVoicing) flips the icon immediately;
    // OR-ing the transport's own flag keeps screens not yet on the controller correct too.
    val pausedSignal by PlaybackController.paused.collectAsState()
    val targetLinesSwapped by app.practicePrefs.nowVoicingTargetLinesSwappedState.collectAsState()
    val paused = pausedSignal || transport?.isPaused?.invoke() == true
    val effectiveLive = remember(live, paused) { if (paused) live?.copy(lang = "pause") else live }
    val wordDrill = rememberNowVoicingWordDrill(app, pinned)
    val cloudState by app.cloudConfig.state.collectAsState()
    val targetPresentation = remember(
        pinned,
        cloudState.bootstrap?.languagePair?.targetLocale,
        targetLinesSwapped
    ) {
        nowVoicingTargetPresentation(
            nowVoicing = pinned,
            targetLocale = cloudState.bootstrap?.languagePair?.targetLocale,
            japaneseReading = pinned?.let { app.lessonRepo.targetReadingFor(it.pl) },
            swapped = targetLinesSwapped
        )
    }

    val inner: @Composable (Modifier) -> Unit = { fillMod ->
        Box(modifier = fillMod) {
            Row(
                modifier = fillMod.padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.Top
            ) {
                NowVoicingBody(
                    pinned = pinned,
                    live = effectiveLive,
                    statusText = nowVoicingStatus(pinned, effectiveLive),
                    onPlWordClick = wordDrill.play,
                    idlePlaceholder = idlePlaceholder,
                    syllableShading = syllableShading,
                    largeFormat = largeFormat,
                    composing = wordDrill.composing,
                    targetPresentation = targetPresentation,
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
            SpeechRatingReadout(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 8.dp)
            )
            if (showStar) {
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

    if (chrome) {
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
            inner(Modifier.fillMaxWidth())
        }
    } else {
        Surface(
            color = background,
            shape = RoundedCornerShape(0.dp),
            modifier = modifier
        ) {
            inner(Modifier.fillMaxSize())
        }
    }
}

@Composable
fun NowVoicingPlaybackOptions(
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
internal fun NowVoicingTransport() {
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
            // Fifth control: the sticky speech-rating mic, under the four transport buttons.
            SpeechMicToggle()
        }
    }
}

/**
 * Sticky microphone latch. While on, the active player listens for the user to repeat each
 * phrase after it's voiced and scores them 0–100 (see [SpeechRating]). First tap requests
 * RECORD_AUDIO if needed — the same permission the phrase-detail "Tap to speak" flow uses.
 */
@Composable
private fun SpeechMicToggle() {
    val context = LocalContext.current
    val armed by SpeechRating.armed.collectAsState()
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        if (granted) SpeechRating.setArmed(true)
    }
    ControlIconButton(
        icon = Icons.Filled.Mic,
        label = if (armed) "Turn off speech rating" else "Rate my speech",
        enabled = true,
        container = if (armed) LbColors.Audio else Color.White,
        border = if (armed) LbColors.Audio else LbColors.Line,
        content = if (armed) Color.White else LbColors.TextPrimary,
        onClick = {
            when {
                armed -> SpeechRating.setArmed(false)
                hasPermission -> SpeechRating.setArmed(true)
                else -> permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    )
}

/**
 * Compact readout for the speech-rating cycle: "Listening…" with a live partial transcript,
 * then the 0–100 score (coloured by band) and what was heard. Renders nothing when the mic
 * is off and there's no prior result, so the panel is unchanged in the default state.
 */
@Composable
private fun SpeechRatingReadout(modifier: Modifier = Modifier) {
    val armed by SpeechRating.armed.collectAsState()
    val phase by SpeechRating.phase.collectAsState()
    val partial by SpeechRating.partial.collectAsState()
    val result by SpeechRating.result.collectAsState()
    if (!armed && result == null) return

    Surface(
        color = Color.White,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, LbColors.Line),
        modifier = modifier.widthIn(max = 260.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Mic,
                contentDescription = null,
                tint = if (armed) LbColors.Audio else LbColors.TextMuted,
                modifier = Modifier.size(16.dp)
            )
            val current = result
            when {
                phase == SpeechRating.Phase.Listening -> Text(
                    if (partial.isBlank()) "Listening…" else "Listening… $partial",
                    color = LbColors.TextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1
                )
                phase == SpeechRating.Phase.Scored && current != null && current.error != null -> Text(
                    current.error,
                    color = LbColors.Danger,
                    fontSize = 12.sp,
                    maxLines = 2
                )
                phase == SpeechRating.Phase.Scored && current != null -> {
                    val band = when {
                        current.score >= 80 -> LbColors.Success
                        current.score >= 60 -> LbColors.Warning
                        else -> LbColors.Danger
                    }
                    Text(
                        current.score.toString(),
                        color = band,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text("/ 100", color = LbColors.TextMuted, fontSize = 11.sp)
                    if (current.transcribed.isNotBlank()) {
                        Text(
                            "“${current.transcribed}”",
                            color = LbColors.TextSecondary,
                            fontSize = 12.sp,
                            maxLines = 1
                        )
                    }
                }
                else -> Text(
                    "Repeat each phrase to be scored",
                    color = LbColors.TextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1
                )
            }
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
