package com.sponic.langbang.domain

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File

object AudioActivityBus {
    val active = MutableStateFlow(false)
    private val handler = Handler(Looper.getMainLooper())
    private var generation = 0

    fun markActive() {
        generation += 1
        active.value = true
    }

    fun markInactiveSoon() {
        val observed = generation
        handler.postDelayed({
            if (generation == observed) active.value = false
        }, 2200L)
    }

    fun clear() {
        generation += 1
        active.value = false
    }
}

/**
 * Plays short cached mp3 clips through a SINGLE reused [MediaPlayer].
 *
 * The previous implementation allocated a fresh MediaPlayer (setDataSource + prepareAsync
 * + release) on every clip — paying codec setup/teardown on each of the 2–4 clips per
 * study item, which made every tap and queue-advance feel sluggish. Reusing one instance
 * (`reset()` between clips) removes that churn.
 *
 * Adds true [pause]/[resume]: a paused clip stays prepared at its current position and
 * resumes in place, so the study queues no longer have to cancel + replay the whole item
 * (English cue and all) when the user pauses mid-Polish.
 *
 * "Active" is flagged the instant [play] is called (before prepare completes) so UI tied
 * to [AudioActivityBus] reacts to the tap immediately instead of after the prepare gap.
 *
 * All methods must be called from the main thread (as today's callers do).
 */
class AudioPlayer {
    private val mp = MediaPlayer()
    private val audioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private var onDoneCurrent: (() -> Unit)? = null
    private var prepared = false
    private var paused = false

    init {
        applyAudioAttributes()
        mp.setOnPreparedListener {
            prepared = true
            if (!paused) it.start()
        }
        mp.setOnCompletionListener {
            prepared = false
            AudioActivityBus.markInactiveSoon()
            consumeOnDone()
        }
        mp.setOnErrorListener { _, _, _ ->
            prepared = false
            runCatching { mp.reset() }
            AudioActivityBus.markInactiveSoon()
            consumeOnDone()
            true
        }
    }

    /**
     * Play [file] now, superseding whatever was playing. [onDone] fires on natural
     * completion, on error, and on a missing file (so an awaiting coroutine never hangs).
     * It is intentionally NOT fired when a later [play]/[stop] supersedes this clip — those
     * paths rely on the caller's coroutine being cancelled, matching the prior behaviour.
     */
    fun play(file: File, onDone: () -> Unit = {}) {
        // Drop the superseded clip's pending callback without firing it.
        onDoneCurrent = null
        paused = false
        prepared = false
        runCatching { mp.reset() }
        if (!file.exists()) { onDone(); return }
        applyAudioAttributes()
        // Reflect "active" the instant the tap lands, before prepare completes.
        AudioActivityBus.markActive()
        onDoneCurrent = onDone
        try {
            mp.setDataSource(file.absolutePath)
            mp.prepareAsync()
        } catch (_: Throwable) {
            onDoneCurrent = null
            runCatching { mp.reset() }
            AudioActivityBus.markInactiveSoon()
            onDone()
        }
    }

    /** Pause the current clip in place. No-op if nothing is playing. */
    fun pause() {
        paused = true
        runCatching { if (mp.isPlaying) mp.pause() }
        AudioActivityBus.clear()
    }

    /** Resume a clip paused with [pause]. */
    fun resume() {
        if (!paused) return
        paused = false
        if (prepared) {
            AudioActivityBus.markActive()
            runCatching { mp.start() }
        }
    }

    fun isPaused(): Boolean = paused

    fun stop() {
        onDoneCurrent = null
        paused = false
        prepared = false
        runCatching { mp.reset() }
        AudioActivityBus.clear()
    }

    private fun consumeOnDone() {
        val cb = onDoneCurrent
        onDoneCurrent = null
        cb?.invoke()
    }

    private fun applyAudioAttributes() {
        runCatching { mp.setAudioAttributes(audioAttributes) }
    }
}
