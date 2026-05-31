package com.sponic.langbang.domain

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Whatever source is currently driving audio (RandomPlayer, VerbsTab Quiz, Adjectives
 * Quiz, etc.) registers a [PlaybackTransport] here. The NowVoicing panel reads it to
 * render a single 2x2 transport bank — Restart / Rewind on top, Pause-Play / Stop on
 * bottom — so the controls live in the same position regardless of which screen
 * launched the queue.
 *
 * Callbacks the source doesn't support are left null; the NV panel disables those
 * buttons instead of hiding them so layout stays stable.
 */
data class PlaybackTransport(
    val stop: () -> Unit,
    val rewind: (() -> Unit)? = null,
    val restart: (() -> Unit)? = null,
    val pauseResume: (() -> Unit)? = null,
    /** Source supplies a callback the panel can poll to decide Pause vs Resume icon. */
    val isPaused: () -> Boolean = { false },
)

object PlaybackController {

    val playing = MutableStateFlow(false)
    val transport = MutableStateFlow<PlaybackTransport?>(null)

    fun register(t: PlaybackTransport) {
        transport.value = t
        playing.value = true
    }

    /** Back-compat for sources that only need a stop callback. */
    fun register(stop: () -> Unit) {
        register(PlaybackTransport(stop = stop))
    }

    fun unregister() {
        transport.value = null
        playing.value = false
    }

    fun stop() {
        val fn = transport.value?.stop
        transport.value = null
        playing.value = false
        fn?.invoke()
    }

    fun rewind() {
        transport.value?.rewind?.invoke()
    }

    fun restart() {
        transport.value?.restart?.invoke()
    }

    fun pauseResume() {
        transport.value?.pauseResume?.invoke()
    }
}
