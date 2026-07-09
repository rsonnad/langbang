package com.sponic.langbangtrans.audio

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import com.sponic.langbangtrans.AudioConfig
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.log10
import kotlin.math.sqrt

private const val TAG = "LBG2mic"

class MicProbe(
    private val context: Context,
    private val config: AudioConfig,
) {
    @SuppressLint("MissingPermission")
    fun recordAndSave(durationMs: Long = 5_000L): String {
        val frameBytes = config.captureSampleRate * FRAME_MS / 1000 * BYTES_PER_SAMPLE
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

        val frame = ByteArray(frameBytes)
        var frames = 0
        var speechFrames = 0
        var clippedSamples = 0
        var peak = 0
        var sampleCount = 0L
        var squareSum = 0.0
        val startedAt = System.currentTimeMillis()

        recorder.startRecording()
        try {
            while (System.currentTimeMillis() - startedAt < durationMs) {
                val read = recorder.read(frame, 0, frame.size, AudioRecord.READ_BLOCKING)
                if (read <= 0) continue
                frames += 1
                val framePeak = analyze(frame, read) { abs ->
                    if (abs > peak) peak = abs
                    if (abs >= CLIP_THRESHOLD) clippedSamples += 1
                    squareSum += abs.toDouble() * abs.toDouble()
                    sampleCount += 1
                }
                if (framePeak >= SPEECH_THRESHOLD) speechFrames += 1
            }
        } finally {
            recorder.stop()
            recorder.release()
        }

        val rms = if (sampleCount == 0L) 0.0 else sqrt(squareSum / sampleCount)
        val dbfs = if (rms <= 0.0) -120.0 else 20.0 * log10(rms / PCM_FULL_SCALE)
        val activePercent = if (frames == 0) 0 else speechFrames * 100 / frames
        val report = buildString {
            appendLine("G2Trans mic probe")
            appendLine("time=${ISO_FORMAT.format(Date(startedAt))}")
            appendLine("source=VOICE_RECOGNITION")
            appendLine("sampleRate=${config.captureSampleRate}")
            appendLine("durationMs=$durationMs")
            appendLine("frames=$frames")
            appendLine("speechFrames=$speechFrames ($activePercent%)")
            appendLine("speechThreshold=$SPEECH_THRESHOLD")
            appendLine("peak=$peak")
            appendLine("rms=${"%.1f".format(Locale.US, rms)}")
            appendLine("rmsDbfs=${"%.1f".format(Locale.US, dbfs)}")
            appendLine("clippedSamples=$clippedSamples")
            appendLine("interpretation=${interpret(peak, speechFrames)}")
        }
        Log.i(TAG, report.replace('\n', ' '))
        return save(report)
    }

    private fun save(report: String): String {
        val dir = File(context.getExternalFilesDir(null), "mic-probe").apply { mkdirs() }
        val file = File(dir, "g2-mic-probe-${FILE_FORMAT.format(Date())}.txt")
        file.writeText(report)
        return "$report\nfile=${file.absolutePath}"
    }

    private fun analyze(frame: ByteArray, len: Int, sample: (Int) -> Unit): Int {
        var framePeak = 0
        var i = 0
        while (i + 1 < len) {
            val value = (frame[i + 1].toInt() shl 8) or (frame[i].toInt() and 0xFF)
            val abs = if (value < 0) -value else value
            if (abs > framePeak) framePeak = abs
            sample(abs)
            i += BYTES_PER_SAMPLE
        }
        return framePeak
    }

    private fun interpret(peak: Int, speechFrames: Int): String =
        when {
            peak == 0 -> "silent/no samples"
            speechFrames == 0 -> "mic has signal, but below bridge speech gate"
            peak < SPEECH_THRESHOLD * 2 -> "quiet speech/low input level"
            else -> "mic input is active"
        }

    companion object {
        private const val BYTES_PER_SAMPLE = 2
        private const val FRAME_MS = 20
        private const val SPEECH_THRESHOLD = 1_000
        private const val CLIP_THRESHOLD = 32_000
        private const val PCM_FULL_SCALE = 32768.0
        private val ISO_FORMAT = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US)
        private val FILE_FORMAT = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)
    }
}
