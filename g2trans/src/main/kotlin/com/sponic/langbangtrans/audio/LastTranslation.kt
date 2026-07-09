package com.sponic.langbangtrans.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * Buffers the most recent completed translation's audio (Gemini's TTS, even when live playback is
 * off) so it can be replayed on demand — via the "play" code word or a glasses tap.
 */
object LastTranslation {
    private const val MAX_UTTERANCE_BYTES = 24_000 * 2 * 12 // ~12s at 24 kHz mono 16-bit

    private val lock = Any()
    private val current = ByteArrayOutputStream()

    @Volatile
    private var last: ByteArray = ByteArray(0)

    /** Append a translated-audio chunk to the in-progress utterance (silent filler is dropped). */
    fun addChunk(chunk: ByteArray) {
        if (chunk.all { it == 0.toByte() }) return
        synchronized(lock) {
            if (current.size() < MAX_UTTERANCE_BYTES) current.write(chunk)
        }
    }

    /** Mark the in-progress utterance complete; it becomes the one [playLast] will replay. */
    fun endUtterance() {
        synchronized(lock) {
            if (current.size() > 0) {
                last = current.toByteArray()
                current.reset()
            }
        }
    }

    /** Replay the last completed utterance (or the in-progress one if none has completed yet). */
    suspend fun playLast(sampleRate: Int) {
        val pcm = last.takeIf { it.isNotEmpty() } ?: synchronized(lock) { current.toByteArray() }
        if (pcm.isEmpty()) return

        // Mute the mic for the replay's duration + a tail so it isn't re-ingested by Gemini.
        val durationMs = pcm.size.toLong() * 1000 / (sampleRate * 2)
        AudioGate.muteMicUntilMs = System.currentTimeMillis() + durationMs + 500L

        withContext(Dispatchers.IO) {
            val minBuffer = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            )
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .build()
                )
                .setBufferSizeInBytes(maxOf(minBuffer, pcm.size))
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
            runCatching {
                track.write(pcm, 0, pcm.size)
                track.play()
                delay(durationMs + 250)
            }
            runCatching { track.stop() }
            runCatching { track.release() }
        }
    }
}
