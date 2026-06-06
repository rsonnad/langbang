package com.sponic.langbang.ui.common

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.sponic.langbang.LangbangApplication
import com.sponic.langbang.domain.NowVoicingBus
import com.sponic.langbang.domain.PlaybackController
import com.sponic.langbang.domain.PlaybackTransport
import com.sponic.langbang.domain.awaitAudioPlayback
import com.sponic.langbang.domain.ensureCachedAudio
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Shared driver for a "study queue" — the EN→(reveal)→PL play loop that VerbsTab, Phrases,
 * and the declension screens each used to re-implement (~60 lines apiece, copied five
 * times, each with its own subtly-different stop/next/rewind/teardown). This owns the
 * index, the coroutine [Job], transport registration, the pause gate, prefetch-ahead, and
 * teardown. The caller supplies only per-item behaviour.
 *
 * Key wins over the old per-screen loops:
 *  - **Pause is true pause-in-place.** [pause] pauses the live clip (via the pooled
 *    [com.sponic.langbang.domain.AudioPlayer]) or holds at the next [say]/[reveal] gate;
 *    [resume] continues from exactly there. The old code cancelled the job and replayed
 *    the whole item from the English cue.
 *  - **No synth stall mid-queue.** [start] takes a [prefetchItem] that warms item N+1's
 *    audio while N plays, so a cache miss never freezes the loop between EN and PL.
 *  - **One implementation.** Stop/next/rewind/restart and the NowVoicing + PlaybackController
 *    teardown live here once.
 *
 * Usage: call [start] with the item count and a `playItem` lambda that voices one item
 * using the [say]/[reveal] primitives (which honour pause/stop) and publishes its own
 * screen-specific NowVoicing payload.
 */
class StudyQueuePlayer(
    private val app: LangbangApplication,
    private val scope: CoroutineScope,
) {
    /** Index of the item currently playing / parked on, or -1 when idle. Snapshot-backed. */
    var playingIndex by mutableIntStateOf(-1)
        private set

    /** True while paused. Snapshot-backed so callers can render a Pause/Resume control. */
    var isPaused by mutableStateOf(false)
        private set

    private var total = 0
    private var index = 0
    private var job: Job? = null
    private var retainOnCancel = false
    private val pausedFlow = MutableStateFlow(false)

    private var playItem: (suspend StudyQueuePlayer.(Int) -> Unit)? = null
    private var prefetchItem: (suspend (Int) -> Unit)? = null
    private var publishParked: ((Int) -> Unit)? = null

    /** A coroutine is currently driving the queue (running or suspended at a pause/clip). */
    val isActive: Boolean get() = job?.isActive == true

    /** True if a queue is loaded (playing, paused, or parked) — i.e. controls should show. */
    val hasQueue: Boolean get() = playingIndex >= 0

    /**
     * Begin (or restart) the queue.
     *
     * @param total       number of items.
     * @param startIndex  item to begin at.
     * @param publishParked optional: republish item [i]'s NowVoicing without audio — used
     *                      when the user rewinds/advances while paused so the panel shows
     *                      the new item without auto-playing it.
     * @param prefetchItem optional: ensure item [i]'s audio is cached (called for N+1 in the
     *                      background while N plays).
     * @param playItem     voices one item; runs with this player as receiver so [say]/[reveal]
     *                      are in scope and pause/stop are honoured at every step.
     */
    fun start(
        total: Int,
        startIndex: Int = 0,
        rewindable: Boolean = true,
        nextable: Boolean = true,
        restartable: Boolean = true,
        publishParked: ((Int) -> Unit)? = null,
        prefetchItem: (suspend (Int) -> Unit)? = null,
        playItem: suspend StudyQueuePlayer.(Int) -> Unit,
    ) {
        if (total <= 0) return
        this.total = total
        this.index = startIndex.coerceIn(0, total - 1)
        this.playItem = playItem
        this.prefetchItem = prefetchItem
        this.publishParked = publishParked
        PlaybackController.register(
            PlaybackTransport(
                stop = ::stop,
                rewind = if (rewindable) ::rewind else null,
                next = if (nextable) ::next else null,
                restart = if (restartable) ::restart else null,
                pauseResume = ::pauseResume,
                parkCurrent = ::parkCurrentForInterruption,
                isPaused = { isPaused },
            )
        )
        launchLoop()
    }

    private fun launchLoop() {
        job?.cancel()
        updatePaused(false)
        retainOnCancel = false
        val body = playItem ?: return
        job = scope.launch {
            while (index < total) {
                playingIndex = index
                val nextIdx = index + 1
                if (nextIdx < total) {
                    prefetchItem?.let { pf -> launch { runCatching { pf(nextIdx) } } }
                }
                body(index)
                index++
            }
            teardown()
        }
    }

    // --- primitives the playItem lambda uses (pause/stop-aware) ---

    /** Voice one clip: hold at the pause gate, ensure it's cached, then play to completion.
     *  A pause mid-clip holds it in place (the pooled player pauses); resume continues it. */
    suspend fun say(text: String, locale: String, voice: String) {
        gate()
        if (text.isBlank()) return
        val file = app.ensureCachedAudio(text, locale, voice) ?: return
        app.awaitAudioPlayback(file)
    }

    /** A reveal / inter-item pause that honours pause (holds while paused instead of
     *  counting down through it). */
    suspend fun reveal(ms: Long) {
        var remaining = ms
        while (remaining > 0) {
            gate()
            val step = minOf(120L, remaining)
            delay(step)
            remaining -= step
        }
    }

    private suspend fun gate() {
        if (pausedFlow.value) pausedFlow.first { !it }
    }

    // --- transport ---

    fun pauseResume() {
        if (isPaused) resume() else pause()
    }

    fun pause() {
        if (!hasQueue) return
        updatePaused(true)
        app.audioPlayer.pause()
    }

    fun resume() {
        if (!isPaused) return
        updatePaused(false)
        if (isActive) {
            // Paused mid-clip — continue the held clip in place.
            app.audioPlayer.resume()
        } else {
            // Parked on an item (paused after a rewind/next) — start playing it.
            launchLoop()
        }
    }

    fun next() {
        if (total <= 0) return
        val wasPaused = isPaused
        cancelForRetain()
        val n = index + 1
        if (n >= total) { stop(); return }
        index = n
        if (wasPaused) parkAt(index) else launchLoop()
    }

    fun rewind() {
        if (total <= 0) return
        val wasPaused = isPaused
        cancelForRetain()
        index = (index - 1).coerceAtLeast(0)
        if (wasPaused) parkAt(index) else launchLoop()
    }

    fun restart() {
        if (total <= 0) return
        cancelForRetain()
        index = 0
        launchLoop()
    }

    fun parkCurrentForInterruption() {
        if (!hasQueue) return
        cancelForRetain()
        playingIndex = index.coerceIn(0, (total - 1).coerceAtLeast(0))
        updatePaused(true)
        publishParked?.invoke(playingIndex)
    }

    fun stop() {
        retainOnCancel = false
        job?.cancel()
        job = null
        app.audioPlayer.stop()
        teardown()
    }

    private fun parkAt(i: Int) {
        playingIndex = i
        updatePaused(true)
        publishParked?.invoke(i)
    }

    private fun cancelForRetain() {
        retainOnCancel = true
        job?.cancel()
        job = null
        app.audioPlayer.stop()
    }

    private fun teardown() {
        if (retainOnCancel) return
        playingIndex = -1
        updatePaused(false)
        NowVoicingBus.clear()
        PlaybackController.unregister()
        job = null
    }

    private fun updatePaused(v: Boolean) {
        isPaused = v
        pausedFlow.value = v
        PlaybackController.setPaused(v)
    }
}
