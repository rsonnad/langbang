package com.sponic.langbang.domain

import android.media.MediaPlayer
import java.io.File

class AudioPlayer {
    private var current: MediaPlayer? = null

    fun play(file: File, onDone: () -> Unit = {}) {
        stop()
        if (!file.exists()) return
        val mp = MediaPlayer()
        mp.setDataSource(file.absolutePath)
        mp.setOnPreparedListener { it.start() }
        mp.setOnCompletionListener {
            it.release()
            if (current === it) current = null
            onDone()
        }
        mp.setOnErrorListener { _, _, _ ->
            mp.release()
            if (current === mp) current = null
            onDone()
            true
        }
        mp.prepareAsync()
        current = mp
    }

    fun stop() {
        try { current?.stop() } catch (_: Throwable) {}
        try { current?.release() } catch (_: Throwable) {}
        current = null
    }
}
