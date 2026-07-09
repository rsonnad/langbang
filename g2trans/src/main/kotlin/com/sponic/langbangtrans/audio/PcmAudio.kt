package com.sponic.langbangtrans.audio

import android.Manifest
import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import com.sponic.langbangtrans.AudioConfig
import com.sponic.langbangtrans.BridgeStatusBus
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive

/**
 * Half-duplex gate: the mic is muted (to Gemini) while translated audio is playing, plus a short
 * tail, so the model never re-ingests its own English output — which otherwise loops endlessly
 * ("the same thing over and over"), regardless of speaker vs. headphones.
 */
object AudioGate {
    @Volatile
    var muteMicUntilMs: Long = 0L

    fun micMuted(): Boolean = System.currentTimeMillis() < muteMicUntilMs
}

class PcmAudioRecorder(private val config: AudioConfig) {
    @SuppressLint("MissingPermission")
    suspend fun run(output: SendChannel<ByteArray>) {
        val frameBytes = config.captureSampleRate * config.captureFrameMs / 1000 * BYTES_PER_SAMPLE
        val minBuffer = AudioRecord.getMinBufferSize(
            config.captureSampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        val bufferSize = maxOf(minBuffer, frameBytes * 4)
        val recorder = AudioRecord.Builder()
            .setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(config.captureSampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .build()

        BridgeStatusBus.set("Microphone", "${config.captureSampleRate} Hz / ${config.captureFrameMs} ms PCM")
        recorder.startRecording()
        try {
            val frame = ByteArray(frameBytes)
            var lastSpeechMs = 0L
            while (currentCoroutineContext().isActive) {
                val read = recorder.read(frame, 0, frame.size, AudioRecord.READ_BLOCKING)
                if (read <= 0 || AudioGate.micMuted()) continue
                // Noise gate: only forward audio when there's real speech (plus a short hangover),
                // so ambient room noise isn't sent to Gemini and mis-transcribed into filler words.
                val now = System.currentTimeMillis()
                if (peakAmplitude(frame, read) >= NOISE_GATE_THRESHOLD) lastSpeechMs = now
                if (now - lastSpeechMs <= SPEECH_HANGOVER_MS) output.send(frame.copyOf(read))
            }
        } finally {
            recorder.stop()
            recorder.release()
        }
    }

    companion object {
        private const val BYTES_PER_SAMPLE = 2
        private const val NOISE_GATE_THRESHOLD = 1000 // 16-bit peak below this ≈ ambient noise
        private const val SPEECH_HANGOVER_MS = 700L // keep sending this long after the last loud frame

        private fun peakAmplitude(frame: ByteArray, len: Int): Int {
            var max = 0
            var i = 0
            while (i + 1 < len) {
                val sample = (frame[i + 1].toInt() shl 8) or (frame[i].toInt() and 0xFF)
                val abs = if (sample < 0) -sample else sample
                if (abs > max) max = abs
                i += 2
            }
            return max
        }
    }
}

class PcmAudioPlayer(private val config: AudioConfig) {
    suspend fun run(input: ReceiveChannel<ByteArray>) {
        val minBuffer = AudioTrack.getMinBufferSize(
            config.outputSampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        val player = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(config.outputSampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build()
            )
            .setBufferSizeInBytes(maxOf(minBuffer, config.outputSampleRate))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        BridgeStatusBus.set("Headphones", "Playing translated audio at ${config.outputSampleRate} Hz")
        player.play()
        try {
            for (chunk in input) {
                // Gemini streams silent filler audio (all-zero PCM) between utterances. Skip it so it
                // neither plays nor mutes the mic — otherwise the mic stays muted forever and no
                // speech ever reaches Gemini.
                if (chunk.all { it == 0.toByte() }) continue
                // Mute the mic for this chunk's duration + a tail, so real playback can't loop back.
                val playMs = chunk.size.toLong() * 1000 / (config.outputSampleRate * 2)
                AudioGate.muteMicUntilMs = System.currentTimeMillis() + playMs + 500L
                player.write(chunk, 0, chunk.size)
            }
        } finally {
            player.pause()
            player.flush()
            player.release()
        }
    }
}
