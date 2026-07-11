package com.sponic.langbang.shared.practice

import android.media.MediaPlayer
import android.util.Log

/**
 * Android actual for AudioPlayer using MediaPlayer.
 * This file lives in androidMain so android.* is allowed.
 */
actual fun createAudioPlayer(): AudioPlayer = AndroidAudioPlayer()

private class AndroidAudioPlayer : AudioPlayer {
    private var player: MediaPlayer? = null
    private var playing = false

    override fun play(audioKey: String, onDone: () -> Unit) {
        stop()
        // Skeleton: simulated short playback (real port resolves key to file/url and uses real MediaPlayer).
        playing = true
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            playing = false
            player?.release()
            player = null
            onDone()
        }, 420)
        Log.d("AudioPlayer", "play($audioKey) [simulated on Android]")
    }

    override fun stop() {
        player?.let {
            try { it.stop(); it.release() } catch (_: Exception) {}
        }
        player = null
        playing = false
    }

    override fun isPlaying(): Boolean = playing
}