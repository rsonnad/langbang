package com.sponic.langbang.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.sponic.langbang.LangbangApplication
import com.sponic.langbang.domain.NowVoicing
import com.sponic.langbang.domain.NowVoicingBus
import com.sponic.langbang.domain.PlaybackController
import com.sponic.langbang.domain.awaitAudioPlayback
import com.sponic.langbang.domain.ensureCachedAudio
import com.sponic.langbang.domain.targetAudioVoice
import com.sponic.langbang.domain.targetSlowVoice
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext

data class NowVoicingWordDrill(
    val composing: Boolean,
    val play: (String) -> Unit
)

@Composable
fun rememberNowVoicingWordDrill(
    app: LangbangApplication,
    item: NowVoicing?
): NowVoicingWordDrill {
    val transport by PlaybackController.transport.collectAsState()
    val scope = rememberCoroutineScope()
    var wordDrillJob by remember { mutableStateOf<Job?>(null) }
    val visibleWords = remember(item?.pl, item?.words) {
        item.nowVoicingPolishWords()
    }
    var composing by remember { mutableStateOf(false) }
    LaunchedEffect(visibleWords) {
        val slowVoice = app.targetSlowVoice()
        val target = app.targetAudioVoice()
        visibleWords.forEach { word ->
            app.ensureCachedAudio(word, target.locale, slowVoice)
            app.ensureCachedAudio(word, target.locale, target.voice)
        }
    }
    val play: (String) -> Unit = { raw ->
        val word = raw.polishWordForAudio()
        val currentItem = item
        if (word.isNotEmpty() && currentItem != null) {
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
                    composing = true
                    val slowVoice = app.targetSlowVoice()
                    val target = app.targetAudioVoice()
                    NowVoicingBus.publish(currentItem.copy(lang = "pl-slow", plHidden = false))
                    app.playCachedFirst(word, target.locale, slowVoice)
                    NowVoicingBus.publish(currentItem.copy(lang = "pl", plHidden = false))
                    app.playCachedFirst(word, target.locale, target.voice)
                } finally {
                    if (wordDrillJob == thisJob) {
                        composing = false
                        wordDrillJob = null
                    }
                }
            }
        }
    }
    return NowVoicingWordDrill(composing = composing, play = play)
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
