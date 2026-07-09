package com.sponic.langbang.domain

import com.sponic.langbang.LangbangApplication
import com.sponic.langbang.integrations.PronunciationScore
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Global state + driver glue for the **speech rating** mode — the sticky microphone
 * toggle that sits under the four transport buttons in the Now Voicing panel.
 *
 * When [armed] is true, whichever player is driving playback (RandomPlayerState or
 * StudyQueuePlayer) pauses after each phrase finishes voicing, listens for the user to
 * repeat that phrase, scores the attempt 0–100 with the **same** Azure pronunciation
 * assessment the phrase-detail "Tap to speak" feature uses
 * ([com.sponic.langbang.integrations.AzurePronunciationClient.assessOnce]), surfaces the
 * score in the panel, then auto-advances to the next phrase. The latch stays on until the
 * user taps the mic again — so it keeps scoring phrase after phrase, hands-free.
 */
object SpeechRating {
    enum class Phase { Idle, Listening, Scored }

    /** Sticky on/off latch toggled from the panel mic button. */
    val armed = MutableStateFlow(false)

    /** Where we are in the current listen→score cycle (drives the panel readout). */
    val phase = MutableStateFlow(Phase.Idle)

    /** Live partial transcript while listening, so the user sees the mic is capturing. */
    val partial = MutableStateFlow("")

    /** Most recent scored attempt (or an error). Null until the first attempt, cleared on disarm. */
    val result = MutableStateFlow<Attempt?>(null)

    /** How long to hold the score on-screen before the loop advances to the next phrase. */
    const val RESULT_HOLD_MS = 1800L

    data class Attempt(
        /** 0–100, "how close they were". */
        val score: Int,
        val transcribed: String,
        val reference: String,
        val error: String? = null
    )

    fun setArmed(value: Boolean) {
        armed.value = value
        if (!value) reset()
    }

    fun toggle() = setArmed(!armed.value)

    fun beginListening() {
        phase.value = Phase.Listening
        partial.value = ""
    }

    fun onPartial(text: String) {
        if (text.isNotBlank()) partial.value = text
    }

    fun onScored(score: PronunciationScore, reference: String) {
        result.value = Attempt(
            // The overall pronunciation score already blends accuracy/fluency/completeness
            // into one 0–100 figure — the single "how close were you" number we want.
            score = score.pronunciation.toInt().coerceIn(0, 100),
            transcribed = score.transcribed,
            reference = reference
        )
        phase.value = Phase.Scored
        partial.value = ""
    }

    fun onError(message: String?, reference: String) {
        result.value = Attempt(
            score = 0,
            transcribed = "",
            reference = reference,
            error = message ?: "Couldn't score that one — try again."
        )
        phase.value = Phase.Scored
        partial.value = ""
    }

    fun reset() {
        phase.value = Phase.Idle
        partial.value = ""
        result.value = null
    }
}

/**
 * If speech rating is [SpeechRating.armed], run a single listen→score→hold cycle against
 * the phrase currently shown in the Now Voicing panel, then return so the caller can
 * auto-advance. No-op when disarmed or when there's no scorable phrase.
 *
 * @param pauseGate suspends while the caller is paused — StudyQueuePlayer passes its own
 *                  gate so a held Pause also holds the score readout. RandomPlayer cancels
 *                  its job on pause/stop (which interrupts the mic capture), so it leaves
 *                  this as the default no-op.
 */
suspend fun LangbangApplication.runSpeechRatingCycle(
    pauseGate: suspend () -> Unit = {}
) {
    if (!SpeechRating.armed.value) return
    val reference = NowVoicingBus.state.value?.pl?.trim().orEmpty()
    if (reference.isBlank()) return

    pauseGate()
    if (!SpeechRating.armed.value) return

    SpeechRating.beginListening()
    val outcome = pron.assessOnce(
        locale = targetAudioVoice().locale,
        referenceText = reference,
        onPartial = { SpeechRating.onPartial(it) }
    )
    // Drop the result if the user disarmed while we were listening — otherwise a stale
    // score would flash up after the mic was turned off.
    if (!SpeechRating.armed.value) return
    outcome.onSuccess { SpeechRating.onScored(it, reference) }
        .onFailure { SpeechRating.onError(it.message, reference) }

    // Hold the score on-screen briefly before the loop moves on. Bail early if the user
    // disarms mid-hold so playback returns to normal without an extra pause.
    var remaining = SpeechRating.RESULT_HOLD_MS
    while (remaining > 0 && SpeechRating.armed.value) {
        pauseGate()
        val step = minOf(120L, remaining)
        delay(step)
        remaining -= step
    }
}
