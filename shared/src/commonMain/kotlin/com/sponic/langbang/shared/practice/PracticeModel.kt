package com.sponic.langbang.shared.practice

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * The shared FeatureModel (NOT a ViewModel).
 * All decision logic for the practice flashcard flow lives here.
 * Both Compose and SwiftUI are thin: observe state, render, forward events.
 *
 * This proves the no-drift architecture: one impl, two dumb UIs.
 */
class PracticeModel(
    private val audioPlayer: AudioPlayer,
    initialItems: List<PracticeItem> = SimplePracticeGenerator.buildVerbFormItems()
) {
    data class UiState(
        val current: PracticeItem? = null,
        val isRevealed: Boolean = false,
        val index: Int = 0,
        val total: Int = 0,
        val isFinished: Boolean = false,
        val statusMessage: String = ""
    )

    sealed class Event {
        object Start : Event()
        object Reveal : Event()
        object PlayAudio : Event()
        object Next : Event()
        object Previous : Event()
        object Restart : Event()
    }

    private val _state = MutableStateFlow(
        if (initialItems.isEmpty()) {
            UiState(total = 0, isFinished = true, statusMessage = "No practice items")
        } else {
            UiState(current = initialItems[0], total = initialItems.size)
        }
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var items: List<PracticeItem> = initialItems
    private var currentIndex: Int = 0

    fun onEvent(event: Event) {
        when (event) {
            is Event.Start -> startIfNeeded()
            is Event.Reveal -> reveal()
            is Event.PlayAudio -> playCurrentAudio()
            is Event.Next -> advance(+1)
            is Event.Previous -> advance(-1)
            is Event.Restart -> restart()
        }
    }

    private fun startIfNeeded() {
        if (items.isEmpty()) {
            _state.update { UiState(total = 0, isFinished = true, statusMessage = "No practice items") }
            return
        }
        if (_state.value.current == null && items.isNotEmpty()) {
            currentIndex = 0
            _state.update { it.copy(current = items[0], isRevealed = false, isFinished = false, index = 0) }
        }
    }

    private fun reveal() {
        val s = _state.value
        if (s.current != null && !s.isRevealed && !s.isFinished) {
            _state.update { it.copy(isRevealed = true) }
            // Decision to auto-play after reveal lives here (shared logic).
            playCurrentAudio()
        }
    }

    private fun playCurrentAudio() {
        val item = _state.value.current ?: return
        if (_state.value.isFinished) return
        _state.update { it.copy(statusMessage = "Playing audio...") }
        audioPlayer.play(item.audioKey) {
            _state.update { it.copy(statusMessage = "Audio done") }
        }
    }

    private fun advance(delta: Int) {
        if (items.isEmpty()) {
            _state.update { it.copy(isFinished = true) }
            return
        }
        val next = (currentIndex + delta).coerceIn(0, items.lastIndex)
        if (next == currentIndex && delta != 0) {
            // wrap handling at edges for demo: wrap to start on next-from-last
            if (delta > 0) {
                currentIndex = 0
            } else {
                currentIndex = items.lastIndex
            }
        } else {
            currentIndex = next
        }
        val finished = false
        _state.update {
            it.copy(
                current = items[currentIndex],
                isRevealed = false,
                index = currentIndex,
                isFinished = finished,
                statusMessage = ""
            )
        }
    }

    private fun restart() {
        if (items.isEmpty()) {
            _state.update { UiState(total = 0, isFinished = true) }
            return
        }
        currentIndex = 0
        _state.update {
            UiState(
                current = items[0],
                isRevealed = false,
                index = 0,
                total = items.size,
                isFinished = false,
                statusMessage = ""
            )
        }
    }

    // For tests: allow injecting a controlled item list after construction (test seam only)
    internal fun replaceItemsForTest(newItems: List<PracticeItem>) {
        items = newItems
        currentIndex = 0
        _state.update {
            if (newItems.isEmpty()) {
                UiState(total = 0, isFinished = true)
            } else {
                UiState(current = newItems[0], index = 0, total = newItems.size)
            }
        }
    }
}
