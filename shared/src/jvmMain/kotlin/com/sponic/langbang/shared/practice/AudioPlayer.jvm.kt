package com.sponic.langbang.shared.practice

/**
 * JVM (test) actual. Pure simulation so commonTest can instantiate and drive the model.
 */
actual fun createAudioPlayer(): AudioPlayer = JvmTestAudioPlayer()

private class JvmTestAudioPlayer : AudioPlayer {
    private var playing = false
    override fun play(audioKey: String, onDone: () -> Unit) {
        playing = true
        println("[JvmTestAudioPlayer] play($audioKey)")
        onDone()
        playing = false
    }
    override fun stop() { playing = false }
    override fun isPlaying(): Boolean = playing
}