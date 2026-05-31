package com.sponic.langbang.integrations

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.microsoft.cognitiveservices.speech.CancellationDetails
import com.microsoft.cognitiveservices.speech.PropertyId
import com.microsoft.cognitiveservices.speech.ResultReason
import com.microsoft.cognitiveservices.speech.SpeechConfig
import com.microsoft.cognitiveservices.speech.SpeechRecognizer
import com.microsoft.cognitiveservices.speech.PronunciationAssessmentConfig
import com.microsoft.cognitiveservices.speech.PronunciationAssessmentGradingSystem
import com.microsoft.cognitiveservices.speech.PronunciationAssessmentGranularity
import com.microsoft.cognitiveservices.speech.PronunciationAssessmentResult
import com.microsoft.cognitiveservices.speech.audio.AudioConfig
import com.sponic.langbang.BuildConfig
import com.sponic.langbang.domain.NetworkMonitor
import com.sponic.langbang.domain.UsageTracker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

data class WordScore(
    val word: String,
    val accuracy: Double,
    val errorType: String
)

data class PronunciationScore(
    val transcribed: String,
    val accuracy: Double,
    val fluency: Double,
    val completeness: Double,
    val pronunciation: Double,
    val words: List<WordScore>
)

class AzurePronunciationClient(
    private val context: Context,
    private val usage: UsageTracker? = null,
    private val network: NetworkMonitor? = null
) {
    private val assessmentMutex = Mutex()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var lastAssessmentEndedAtMs = 0L

    /**
     * Records from the device microphone and scores pronunciation against [referenceText]
     * in [locale]. Uses Azure Speech SDK's Pronunciation Assessment.
     *
     * Runs on Dispatchers.IO — the underlying `recognizeOnceAsync().get()` is a blocking
     * JNI call and must NOT run on the Main thread (would freeze the UI and look like a hang).
     *
     * @param locale e.g. "pl-PL"
     * @param referenceText The exact phrase the user is expected to say
     */
    /**
     * Optional callback that fires whenever the Speech SDK emits a `recognizing` event —
     * i.e. live partial transcripts while the user is still speaking. Useful for showing
     * a "Listening: …" hint in the UI so the user knows the mic is actually capturing
     * audio (vs. a silent timeout that returns 0).
     */
    suspend fun assessOnce(
        locale: String,
        referenceText: String,
        onListening: (() -> Unit)? = null,
        onPartial: ((String) -> Unit)? = null
    ): Result<PronunciationScore> = withContext(Dispatchers.IO) {
        // Permission check first — without RECORD_AUDIO the native mic capture would
        // either silently produce no audio or block forever waiting for samples.
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return@withContext Result.failure(
                SecurityException(
                    "Microphone permission not granted. Tap again to grant, or enable it in " +
                        "Android Settings → Apps → langbang → Permissions."
                )
            )
        }
        if (network?.isOnline() == false) {
            return@withContext Result.failure(
                IOException("Offline — pronunciation scoring needs the network.")
            )
        }

        assessmentMutex.withLock {
            val sinceLastAssessment = System.currentTimeMillis() - lastAssessmentEndedAtMs
            if (sinceLastAssessment in 0 until RECOGNIZER_COOLDOWN_MS) {
                delay(RECOGNIZER_COOLDOWN_MS - sinceLastAssessment)
            }

            val first = runAssessment(locale, referenceText, onListening, onPartial)
            val retriable = first.exceptionOrNull() is AzureSpeechConnectionException
            if (!retriable) {
                lastAssessmentEndedAtMs = System.currentTimeMillis()
                return@withLock first
            }

            delay(RECOGNIZER_RETRY_DELAY_MS)
            val second = runAssessment(locale, referenceText, onListening, onPartial)
            lastAssessmentEndedAtMs = System.currentTimeMillis()
            second
        }
    }

    private suspend fun runAssessment(
        locale: String,
        referenceText: String,
        onListening: (() -> Unit)?,
        onPartial: ((String) -> Unit)?
    ): Result<PronunciationScore> {
        var recognizer: SpeechRecognizer? = null
        return try {
            val speechConfig = SpeechConfig.fromSubscription(
                BuildConfig.AZURE_SPEECH_KEY,
                BuildConfig.AZURE_SPEECH_REGION
            )
            speechConfig.speechRecognitionLanguage = locale
            // Slightly chatty logging surface for diagnostics.
            speechConfig.setProperty(PropertyId.Speech_LogFilename, "")

            val pronConfig = PronunciationAssessmentConfig(
                referenceText,
                PronunciationAssessmentGradingSystem.HundredMark,
                PronunciationAssessmentGranularity.Phoneme,
                /* enableMiscue = */ true
            )

            val audioConfig = AudioConfig.fromDefaultMicrophoneInput()
            val rec = SpeechRecognizer(speechConfig, audioConfig)
            recognizer = rec
            pronConfig.applyTo(rec)

            // Live partial transcripts: the SDK fires `recognizing` events as it processes
            // incoming audio chunks. If we never see one, the mic isn't capturing — that's
            // a much more useful diagnostic than the silent 0 we used to return.
            val heardAnyAudio = AtomicBoolean(false)
            rec.sessionStarted.addEventListener { _, _ -> postToMain(onListening) }
            rec.recognizing.addEventListener { _, e ->
                heardAnyAudio.set(true)
                val partial = e.result.text
                if (!partial.isNullOrEmpty()) postToMain { onPartial?.invoke(partial) }
            }

            val startMs = System.currentTimeMillis()
            // 30s safety net. Azure's silence-timeout normally resolves recognizeOnceAsync
            // in ~5s even if the user doesn't speak; if the network or SDK init wedges,
            // we surface a clear error instead of leaving "Listening…" up forever.
            val result = withTimeout(30_000) {
                runInterruptible {
                    rec.recognizeOnceAsync().get()
                }
            }

            // Wall-clock around recognizeOnceAsync — slightly over-estimates vs the
            // recognized-audio span Azure actually bills, which is fine for a budget gauge.
            val seconds = (System.currentTimeMillis() - startMs).coerceAtLeast(0L) / 1000.0
            usage?.recordPronunciationSeconds(seconds)

            when (result.reason) {
                ResultReason.RecognizedSpeech -> {
                    val pronResult = PronunciationAssessmentResult.fromResult(result)
                    val words = pronResult.words?.map {
                        WordScore(it.word, it.accuracyScore, it.errorType?.toString() ?: "None")
                    } ?: emptyList()
                    val score = PronunciationScore(
                        transcribed = result.text ?: "",
                        accuracy = pronResult.accuracyScore,
                        fluency = pronResult.fluencyScore,
                        completeness = pronResult.completenessScore,
                        pronunciation = pronResult.pronunciationScore,
                        words = words
                    )
                    result.close()
                    Result.success(score)
                }
                ResultReason.NoMatch -> {
                    result.close()
                    val msg = if (heardAnyAudio.get()) {
                        "Didn't recognize what you said. Try again, closer to the mic."
                    } else {
                        "No audio captured. Check the mic isn't muted and try again."
                    }
                    Result.failure(IOException(msg))
                }
                ResultReason.Canceled -> {
                    val details = runCatching { CancellationDetails.fromResult(result) }
                        .getOrNull()
                    result.close()
                    Result.failure(cancellationException(details))
                }
                else -> {
                    val reason = result.reason
                    result.close()
                    Result.failure(IOException("Unexpected recognition result: $reason"))
                }
            }
        } catch (t: TimeoutCancellationException) {
            Result.failure(
                IOException("Speech recognition timed out after 30s. Check network and try again.")
            )
        } catch (t: Throwable) {
            Result.failure(t.toUserFacingSpeechError())
        } finally {
            try { recognizer?.close() } catch (_: Throwable) {}
        }
    }

    private fun postToMain(callback: (() -> Unit)?) {
        if (callback == null) return
        if (Looper.myLooper() == Looper.getMainLooper()) callback() else mainHandler.post(callback)
    }

    private fun cancellationException(details: CancellationDetails?): IOException {
        val reason = details?.reason?.toString().orEmpty()
        val errorDetails = details?.errorDetails.orEmpty()
        if (errorDetails.isNotBlank()) {
            Log.w(TAG, "Azure pronunciation canceled: reason=$reason details=$errorDetails")
        }

        if (isTransientTransportError(errorDetails)) {
            return AzureSpeechConnectionException(CONNECTION_FAILURE_MESSAGE)
        }

        val suffix = when {
            reason.isNotBlank() -> " ($reason)"
            else -> ""
        }
        return IOException("Speech recognition canceled$suffix. Try again.")
    }

    private fun Throwable.toUserFacingSpeechError(): Throwable {
        val text = listOfNotNull(message, cause?.message).joinToString(" ")
        if (isTransientTransportError(text)) {
            Log.w(TAG, "Azure pronunciation connection failure", this)
            return AzureSpeechConnectionException(CONNECTION_FAILURE_MESSAGE, this)
        }
        return this
    }

    private fun isTransientTransportError(text: String): Boolean {
        return text.contains("WS_OPEN_ERROR_UNDERLYING_IO_OPEN_FAILED", ignoreCase = true) ||
            text.contains("no connection to the remote host", ignoreCase = true) ||
            (
                text.contains("connection failed", ignoreCase = true) &&
                    text.contains("speech.microsoft.com", ignoreCase = true)
                )
    }

    private class AzureSpeechConnectionException(
        message: String,
        cause: Throwable? = null
    ) : IOException(message, cause)

    private companion object {
        const val TAG = "AzurePronClient"
        const val CONNECTION_FAILURE_MESSAGE =
            "Speech service connection failed. Wait a moment and tap the mic again."
        const val RECOGNIZER_COOLDOWN_MS = 750L
        const val RECOGNIZER_RETRY_DELAY_MS = 900L
    }
}
