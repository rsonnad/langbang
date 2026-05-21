package com.sponic.langbang.domain

import com.sponic.langbang.data.model.TokenPair
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * What is currently being voiced. Surfaces in a persistent panel so the user can always
 * see the English / Polish / word-for-word literal of whatever audio is playing.
 *
 * `lang` is one of: "en", "pl", "pl-slow", "pause", or null when silent.
 * `words` is the per-token (pl, en) mapping for word-aligned gloss rendering; when
 * absent the panel falls back to whitespace-zipping `literal`.
 */
data class NowVoicing(
    val en: String,
    val pl: String,
    val literal: String?,
    val lang: String,
    val position: String? = null,
    val words: List<TokenPair>? = null
)

object NowVoicingBus {
    val state = MutableStateFlow<NowVoicing?>(null)
    fun publish(v: NowVoicing?) { state.value = v }
    fun clear() { state.value = null }
}
