package com.sponic.langbang.ui.external

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.sponic.langbang.LangbangApplication
import com.sponic.langbang.data.model.TokenPair
import com.sponic.langbang.domain.ExternalNowVoicingBus
import com.sponic.langbang.domain.ExternalNowVoicingState
import com.sponic.langbang.domain.ExternalVoiceSection
import com.sponic.langbang.domain.NowVoicing
import com.sponic.langbang.domain.NowVoicingBus
import com.sponic.langbang.domain.ensureCachedAudio
import com.sponic.langbang.domain.sourceAudioVoice
import com.sponic.langbang.domain.targetAudioVoice
import com.sponic.langbang.domain.targetSlowVoice
import com.sponic.langbang.ui.common.NowVoicingPanel
import com.sponic.langbang.ui.common.StudyQueuePlayer
import com.sponic.langbang.ui.settings.AgentApiCard
import com.sponic.langbang.ui.theme.LbColors

/**
 * The phrase shown when the LLM screen is idle (info card dismissed, no LLM content yet).
 * It's a real, playable Now Voicing item — the same EN→PL→word-for-word loop as any phrase —
 * so the user can hear how the screen behaves before wiring up an agent.
 */
private val LLM_IDLE_NOW_VOICING = NowVoicing(
    en = "You can use your favorite LLM to drive drills on this screen.",
    pl = "Możesz użyć LLM do sterowania ćwiczeniami na tym ekranie.",
    literal = null,
    lang = "pause",
    words = listOf(
        TokenPair(pl = "Możesz", en = "You can"),
        TokenPair(pl = "użyć", en = "use"),
        TokenPair(pl = "LLM", en = "LLM"),
        TokenPair(pl = "do", en = "to"),
        TokenPair(pl = "sterowania", en = "drive"),
        TokenPair(pl = "ćwiczeniami", en = "drills"),
        TokenPair(pl = "na", en = "on"),
        TokenPair(pl = "tym", en = "this"),
        TokenPair(pl = "ekranie", en = "screen"),
    ),
)

@Composable
fun ExternalNowVoicingScreen(app: LangbangApplication) {
    val state by ExternalNowVoicingBus.state.collectAsState()
    val bottom = state.bottom
    val hasContent = state.top.hasContent() || bottom?.hasContent() == true
    var dismissed by rememberSaveable { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LbColors.Canvas)
    ) {
        when {
            !hasContent && !dismissed -> {
                Column(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .widthIn(max = 920.dp)
                        .fillMaxWidth()
                        .padding(24.dp)
                ) {
                    AgentApiCard(
                        app = app,
                        scope = rememberCoroutineScope(),
                        context = LocalContext.current,
                        onDismiss = { dismissed = true }
                    )
                }
            }
            !hasContent -> IdleDemoPanel(app = app, modifier = Modifier.fillMaxSize())
            state.dialog && bottom != null -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    SpeakerPanel(
                        app = app,
                        section = state.top,
                        speaker = "top",
                        state = state,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    )
                    SpeakerPanel(
                        app = app,
                        section = bottom,
                        speaker = "bottom",
                        state = state,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    )
                }
            }
            else -> SpeakerPanel(
                app = app,
                section = state.top,
                speaker = "top",
                state = state,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/** An LLM-driven speaker rendered through the shared [NowVoicingPanel]. Display-only:
 *  the external agent drives the content; tapping a Polish word still speaks it. */
@Composable
private fun SpeakerPanel(
    app: LangbangApplication,
    section: ExternalVoiceSection,
    speaker: String,
    state: ExternalNowVoicingState,
    modifier: Modifier = Modifier
) {
    val active = state.activeSpeaker == speaker
    val maroon = state.maroonSpeaker == speaker
    val nowVoicing = section.toNowVoicing()
    val starred by app.starredPhrases.starred.collectAsState()
    val pinned = if (section.hasContent()) nowVoicing else null
    NowVoicingPanel(
        app = app,
        pinned = pinned,
        live = if (active) nowVoicing else null,
        isStarred = pinned?.pl?.let { it in starred } == true,
        onToggleStar = { pinned?.pl?.let { app.starredPhrases.toggle(it) } },
        syllableShading = true,
        idlePlaceholder = "",
        largeFormat = true,
        chrome = false,
        background = if (maroon) Color(0xFFFFF1F3) else Color.White,
        modifier = modifier
    )
}

/** The idle demo: a one-item study queue started in the parked/paused state so the
 *  transport's ▶ Resume button kicks off the standard EN→(slow)→PL playback loop.
 *  Uses the same [StudyQueuePlayer] + [NowVoicingPanel] codepath as the Phrases tab. */
@Composable
private fun IdleDemoPanel(app: LangbangApplication, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val player = remember { StudyQueuePlayer(app, scope) }
    val nowVoicing by NowVoicingBus.state.collectAsState()
    val starred by app.starredPhrases.starred.collectAsState()

    // Arm a parked queue on mount, and re-arm whenever a playthrough finishes (the queue
    // tears down to playingIndex == -1) so the ▶ control is always available.
    LaunchedEffect(player.playingIndex) {
        if (player.playingIndex != -1) return@LaunchedEffect
        val src = app.sourceAudioVoice()
        val tgt = app.targetAudioVoice()
        val slow = app.practicePrefs.slowFirst()
        val slowVoice = app.targetSlowVoice()
        val speakEnglish = app.practicePrefs.speakEnglishFirst()
        player.start(
            total = 1,
            startParked = true,
            rewindable = false,
            nextable = false,
            restartable = false,
            publishParked = { NowVoicingBus.publish(LLM_IDLE_NOW_VOICING.copy(lang = "pause")) },
            prefetchItem = { app.ensureCachedAudio(LLM_IDLE_NOW_VOICING.pl, tgt.locale, tgt.voice) },
        ) {
            if (speakEnglish) {
                NowVoicingBus.publish(LLM_IDLE_NOW_VOICING.copy(lang = "en"))
                say(LLM_IDLE_NOW_VOICING.en, src.locale, src.voice)
            }
            if (slow) {
                NowVoicingBus.publish(LLM_IDLE_NOW_VOICING.copy(lang = "pl-slow"))
                say(LLM_IDLE_NOW_VOICING.pl, tgt.locale, slowVoice)
            }
            NowVoicingBus.publish(LLM_IDLE_NOW_VOICING.copy(lang = "pl"))
            say(LLM_IDLE_NOW_VOICING.pl, tgt.locale, tgt.voice)
        }
    }
    DisposableEffect(Unit) { onDispose { player.stop() } }

    val pinned = nowVoicing ?: LLM_IDLE_NOW_VOICING
    NowVoicingPanel(
        app = app,
        pinned = pinned,
        live = nowVoicing,
        isStarred = pinned.pl in starred,
        onToggleStar = { app.starredPhrases.toggle(pinned.pl) },
        syllableShading = true,
        idlePlaceholder = "",
        largeFormat = true,
        chrome = false,
        modifier = modifier
    )
}

private fun ExternalVoiceSection.hasContent(): Boolean =
    en.isNotBlank() || pl.isNotBlank() || literal?.isNotBlank() == true || words?.isNotEmpty() == true
