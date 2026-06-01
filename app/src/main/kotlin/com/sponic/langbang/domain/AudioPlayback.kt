package com.sponic.langbang.domain

import com.sponic.langbang.LangbangApplication
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume

/**
 * Shared audio-cache + playback helpers for lesson queues.
 *
 * Keep cache misses out of the middle of an EN -> PL pair: callers can prepare the
 * Polish file before the English cue starts, so normal study playback has no silent
 * fetch/synth gap after English.
 */
suspend fun LangbangApplication.ensureCachedAudio(
    text: String,
    locale: String,
    voice: String
): File? {
    if (text.isEmpty()) return null
    val file = audioCache.fileFor(locale, voice, text)
    if (!audioCache.has(file)) {
        tts.synthesize(text, voice, locale, file)
    }
    return file.takeIf { audioCache.has(it) }
}

suspend fun LangbangApplication.playAudioAndAwait(
    text: String,
    locale: String,
    voice: String
) {
    val file = ensureCachedAudio(text, locale, voice) ?: return
    awaitAudioPlayback(file)
}

suspend fun LangbangApplication.awaitAudioPlayback(file: File) {
    suspendCancellableCoroutine<Unit> { cont ->
        audioPlayer.play(file) {
            if (cont.isActive) cont.resume(Unit)
        }
        cont.invokeOnCancellation { audioPlayer.stop() }
    }
}
