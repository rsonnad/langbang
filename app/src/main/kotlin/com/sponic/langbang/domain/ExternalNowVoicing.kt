package com.sponic.langbang.domain

import com.sponic.langbang.data.model.TokenPair
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

data class ExternalVoiceSection(
    val label: String = "Speaker",
    val en: String = "",
    val pl: String = "",
    val literal: String? = null,
    val lang: String = "pl",
    val position: String? = null,
    val words: List<TokenPair>? = null
) {
    fun toNowVoicing(): NowVoicing = NowVoicing(
        en = en,
        pl = pl,
        literal = literal,
        lang = lang,
        position = position,
        words = words
    )
}

data class ExternalNowVoicingState(
    val dialog: Boolean = false,
    val activeSpeaker: String = "top",
    val maroonSpeaker: String = "bottom",
    val top: ExternalVoiceSection = ExternalVoiceSection(label = "Speaker A"),
    val bottom: ExternalVoiceSection? = null,
    val updatedAtMillis: Long = System.currentTimeMillis()
) {
    val activeSection: ExternalVoiceSection
        get() = if (activeSpeaker == "bottom") bottom ?: top else top
}

object ExternalNowVoicingBus {
    val state = MutableStateFlow(ExternalNowVoicingState())
    val focusRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    fun focus() {
        focusRequests.tryEmit(Unit)
    }

    fun clear() {
        state.value = ExternalNowVoicingState(updatedAtMillis = System.currentTimeMillis())
        NowVoicingBus.clear()
        focus()
    }

    fun publish(next: ExternalNowVoicingState, focus: Boolean = true) {
        state.value = next.copy(updatedAtMillis = System.currentTimeMillis())
        NowVoicingBus.publish(state.value.activeSection.toNowVoicing())
        if (focus) focus()
    }

    fun publishSingle(section: ExternalVoiceSection, focus: Boolean = true) {
        publish(
            ExternalNowVoicingState(
                dialog = false,
                activeSpeaker = "top",
                maroonSpeaker = "bottom",
                top = section
            ),
            focus = focus
        )
    }

    fun publishSection(
        speaker: String,
        section: ExternalVoiceSection,
        dialog: Boolean = true,
        maroonSpeaker: String = state.value.maroonSpeaker,
        focus: Boolean = true
    ) {
        val current = state.value
        val normalizedSpeaker = speaker.lowercase().takeIf { it == "bottom" } ?: "top"
        val next = if (normalizedSpeaker == "bottom") {
            current.copy(
                dialog = dialog,
                activeSpeaker = "bottom",
                maroonSpeaker = maroonSpeaker,
                bottom = section
            )
        } else {
            current.copy(
                dialog = dialog,
                activeSpeaker = "top",
                maroonSpeaker = maroonSpeaker,
                top = section
            )
        }
        publish(next, focus = focus)
    }
}
