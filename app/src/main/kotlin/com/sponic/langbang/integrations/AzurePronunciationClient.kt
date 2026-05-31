package com.sponic.langbang.integrations

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
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
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.IOException

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

        var recognizer: SpeechRecognizer? = null
        try {
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
            var heardAnyAudio = false
            rec.sessionStarted.addEventListener { _, _ -> onListening?.invoke() }
            rec.recognizing.addEventListener { _, e ->
                heardAnyAudio = true
                val partial = e.result.text
                if (!partial.isNullOrEmpty()) onPartial?.invoke(partial)
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
                    val msg = if (heardAnyAudio) {
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
                    val msg = buildString {
                        append("Speech recognition canceled")
                        details?.reason?.let { append(" (").append(it.toString()).append(")") }
                        details?.errorDetails?.takeIf { it.isNotBlank() }
                            ?.let { append(": ").append(it) }
                    }
                    Result.failure(IOException(msg))
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
            Result.failure(t)
        } finally {
            try { recognizer?.close() } catch (_: Throwable) {}
        }
    }
}
