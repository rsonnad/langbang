package com.sponic.langbang.domain

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Whatever source is currently driving audio (RandomPlayer, VerbsTab Quiz, Adjectives
 * Quiz, etc.) registers a [PlaybackTransport] here. The NowVoicing panel reads it to
 * render one stable transport bank, so controls live in the same position regardless
 * of which screen
 * launched the queue.
 *
 * Callbacks the source doesn't support are left null; the NV panel disables those
 * buttons instead of hiding them so layout stays stable.
 */
data class PlaybackTransport(
    val stop: () -> Unit,
    val rewind: (() -> Unit)? = null,
    val next: (() -> Unit)? = null,
    val restart: (() -> Unit)? = null,
    val pauseResume: (() -> Unit)? = null,
    val parkCurrent: (() -> Unit)? = null,
    /** Source supplies a callback the panel can poll to decide Pause vs Resume icon. */
    val isPaused: () -> Boolean = { false },
)

object PlaybackController {

    val playing = MutableStateFlow(false)
    val transport = MutableStateFlow<PlaybackTransport?>(null)

    /**
     * Observable pause state. The NowVoicing panel collects this so the Pause/Resume icon
     * updates the moment the user pauses — the old panel polled [PlaybackTransport.isPaused]
     * during composition, so the icon only refreshed when some unrelated state ticked.
     * Sources keep it in sync from their own pause()/resume().
     */
    val paused = MutableStateFlow(false)

    fun setPaused(value: Boolean) {
        paused.value = value
    }

    fun register(t: PlaybackTransport) {
        transport.value = t
        playing.value = true
        paused.value = false
    }

    /** Back-compat for sources that only need a stop callback. */
    fun register(stop: () -> Unit) {
        register(PlaybackTransport(stop = stop))
    }

    fun unregister() {
        transport.value = null
        playing.value = false
        paused.value = false
    }

    fun stop() {
        val fn = transport.value?.stop
        transport.value = null
        playing.value = false
        paused.value = false
        fn?.invoke()
    }

    fun rewind() {
        transport.value?.rewind?.invoke()
    }

    fun next() {
        transport.value?.next?.invoke()
    }

    fun restart() {
        transport.value?.restart?.invoke()
    }

    fun pauseResume() {
        val t = transport.value ?: return
        t.pauseResume?.invoke()
        paused.value = t.isPaused()
    }

    fun parkCurrent() {
        val t = transport.value ?: return
        t.parkCurrent?.invoke()
        paused.value = t.isPaused()
    }
}
