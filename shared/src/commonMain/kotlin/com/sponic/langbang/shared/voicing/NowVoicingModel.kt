package com.sponic.langbang.shared.voicing

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class NowVoicingWord(
    val polish: String,
    val english: String,
    val phonetic: String = "",
)

data class NowVoicingPhrase(
    val id: String,
    val english: String,
    val words: List<NowVoicingWord>,
    val context: String = "",
) {
    val polish: String get() = words.joinToString(" ") { it.polish }
}

enum class NowVoicingSegment {
    IDLE,
    ENGLISH,
    POLISH_SLOW,
    POLISH,
    WORD,
}

/** A platform-owned player performs this request and reports its id on completion. */
data class NowVoicingAudioRequest(
    val id: Long,
    val text: String,
    val languageTag: String,
    val rate: Float,
    val advancesSequence: Boolean,
)

/**
 * Shared Now Voicing state machine. Compose and SwiftUI render [state], platform audio
 * performs [UiState.audioRequest], and completion is returned through [audioCompleted].
 */
class NowVoicingModel {
    data class UiState(
        val phrase: NowVoicingPhrase? = null,
        val activeSegment: NowVoicingSegment = NowVoicingSegment.IDLE,
        val audioRequest: NowVoicingAudioRequest? = null,
        val queueIndex: Int = 0,
        val queueSize: Int = 0,
        val isPaused: Boolean = false,
        val isStarred: Boolean = false,
        val speakEnglish: Boolean = true,
        val slowPolish: Boolean = true,
        val loop: Boolean = false,
    ) {
        val isVisible: Boolean get() = phrase != null
        val canGoPrevious: Boolean get() = queueIndex > 0
        val canGoNext: Boolean get() = queueIndex + 1 < queueSize
        val canPause: Boolean get() = audioRequest != null
        val position: String get() = if (queueSize > 0) "${queueIndex + 1}/$queueSize" else ""
        val statusDetail: String
            get() = when {
                isPaused -> "PAUSED"
                activeSegment == NowVoicingSegment.ENGLISH -> "EN"
                activeSegment == NowVoicingSegment.POLISH_SLOW -> "PL (slow)"
                activeSegment == NowVoicingSegment.POLISH -> "PL"
                activeSegment == NowVoicingSegment.WORD -> "PL word"
                else -> "idle"
            }
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var queue: List<NowVoicingPhrase> = emptyList()
    private var playbackSequence: List<NowVoicingSegment> = emptyList()
    private val starredPhraseIds = mutableSetOf<String>()
    private var segmentIndex = 0
    private var nextRequestId = 1L

    fun start(phrase: NowVoicingPhrase) = startQueue(listOf(phrase), 0)

    fun startQueue(phrases: List<NowVoicingPhrase>, startIndex: Int = 0) {
        if (phrases.isEmpty()) {
            stop()
            return
        }
        queue = phrases
        val index = startIndex.coerceIn(0, phrases.lastIndex)
        segmentIndex = 0
        playbackSequence = configuredSequence()
        _state.update {
            it.copy(
                phrase = phrases[index],
                queueIndex = index,
                queueSize = phrases.size,
                isPaused = false,
                isStarred = phrases[index].id in starredPhraseIds,
            )
        }
        emitCurrentSegment()
    }

    fun replay() {
        if (_state.value.phrase == null) return
        segmentIndex = 0
        playbackSequence = configuredSequence()
        _state.update { it.copy(isPaused = false) }
        emitCurrentSegment()
    }

    fun next() = moveQueue(1)

    fun previous() = moveQueue(-1)

    fun togglePause() {
        if (_state.value.audioRequest == null) return
        _state.update { it.copy(isPaused = !it.isPaused) }
    }

    fun stop() {
        queue = emptyList()
        playbackSequence = emptyList()
        segmentIndex = 0
        _state.value = UiState(
            speakEnglish = _state.value.speakEnglish,
            slowPolish = _state.value.slowPolish,
            loop = _state.value.loop,
        )
    }

    fun toggleStar() {
        val phrase = _state.value.phrase ?: return
        if (phrase.id in starredPhraseIds) starredPhraseIds.remove(phrase.id)
        else starredPhraseIds.add(phrase.id)
        _state.update { it.copy(isStarred = phrase.id in starredPhraseIds) }
    }

    fun toggleEnglish() {
        _state.update { it.copy(speakEnglish = !it.speakEnglish) }
    }

    fun toggleSlow() {
        _state.update { it.copy(slowPolish = !it.slowPolish) }
    }

    fun toggleLoop() {
        _state.update { it.copy(loop = !it.loop) }
    }

    fun speakWord(index: Int) {
        val word = _state.value.phrase?.words?.getOrNull(index) ?: return
        // Word drill is an intentional one-off interruption, matching Android's
        // token tap behavior. It stops the automatic phrase sequence; Replay starts
        // the configured EN -> slow PL -> PL sequence again.
        playbackSequence = emptyList()
        segmentIndex = 0
        _state.update {
            it.copy(
                activeSegment = NowVoicingSegment.WORD,
                isPaused = false,
                audioRequest = request(
                    text = word.polish,
                    languageTag = "pl-PL",
                    rate = NORMAL_RATE,
                    advancesSequence = false,
                ),
            )
        }
    }

    fun audioCompleted(requestId: Long) {
        val current = _state.value.audioRequest ?: return
        if (current.id != requestId) return
        // A route/system cancellation can race the platform pause callback. Keep
        // the current request parked; the platform retries it if resume cannot
        // continue the suspended utterance.
        if (_state.value.isPaused) return
        if (!current.advancesSequence) {
            _state.update {
                it.copy(
                    activeSegment = NowVoicingSegment.IDLE,
                    audioRequest = null,
                    isPaused = false,
                )
            }
            return
        }

        segmentIndex += 1
        if (segmentIndex < playbackSequence.size) {
            emitCurrentSegment()
        } else if (_state.value.loop) {
            segmentIndex = 0
            playbackSequence = configuredSequence()
            emitCurrentSegment()
        } else {
            _state.update {
                it.copy(
                    activeSegment = NowVoicingSegment.IDLE,
                    audioRequest = null,
                    isPaused = false,
                )
            }
        }
    }

    private fun moveQueue(delta: Int) {
        if (queue.isEmpty()) return
        val nextIndex = (_state.value.queueIndex + delta).coerceIn(0, queue.lastIndex)
        if (nextIndex == _state.value.queueIndex) return
        segmentIndex = 0
        playbackSequence = configuredSequence()
        _state.update {
            it.copy(
                phrase = queue[nextIndex],
                queueIndex = nextIndex,
                isPaused = false,
                isStarred = queue[nextIndex].id in starredPhraseIds,
            )
        }
        emitCurrentSegment()
    }

    private fun emitCurrentSegment() {
        val phrase = _state.value.phrase ?: return
        val segment = playbackSequence.getOrNull(segmentIndex) ?: return
        val request = when (segment) {
            NowVoicingSegment.ENGLISH -> request(
                phrase.english,
                "en-US",
                NORMAL_RATE,
                advancesSequence = true,
            )
            NowVoicingSegment.POLISH_SLOW -> request(
                phrase.polish,
                "pl-PL",
                SLOW_RATE,
                advancesSequence = true,
            )
            NowVoicingSegment.POLISH -> request(
                phrase.polish,
                "pl-PL",
                NORMAL_RATE,
                advancesSequence = true,
            )
            else -> return
        }
        _state.update {
            it.copy(
                activeSegment = segment,
                audioRequest = request,
                isPaused = false,
            )
        }
    }

    private fun configuredSequence(): List<NowVoicingSegment> = buildList {
        if (_state.value.speakEnglish) add(NowVoicingSegment.ENGLISH)
        if (_state.value.slowPolish) add(NowVoicingSegment.POLISH_SLOW)
        add(NowVoicingSegment.POLISH)
    }

    private fun request(
        text: String,
        languageTag: String,
        rate: Float,
        advancesSequence: Boolean,
    ) = NowVoicingAudioRequest(
        id = nextRequestId++,
        text = text,
        languageTag = languageTag,
        rate = rate,
        advancesSequence = advancesSequence,
    )

    private companion object {
        const val NORMAL_RATE = 0.48f
        const val SLOW_RATE = 0.32f
    }
}

object NowVoicingSamples {
    fun greeting() = NowVoicingPhrase(
        id = "greeting",
        english = "Good morning",
        words = listOf(
            NowVoicingWord("Dzień", "day", "jeeehny"),
            NowVoicingWord("dobry", "good", "doh-brih"),
        ),
        context = "masculine · nominative",
    )

    fun appreciate() = NowVoicingPhrase(
        id = "appreciate",
        english = "I appreciate your help",
        words = listOf(
            NowVoicingWord("Doceniam", "I appreciate", "doh-tseh-nyam"),
            NowVoicingWord("twoją", "your", "tvoh-yoh"),
            NowVoicingWord("pomoc", "help", "poh-mohts"),
        ),
        context = "present · first person",
    )
}
