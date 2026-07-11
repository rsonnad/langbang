package com.sponic.langbang.shared.practice

/**
 * The audio playback seam — highest drift-risk area per architecture review.
 * Declared in commonMain as an interface. No android.* or Context here.
 *
 * Platform code provides the actual implementation (MediaPlayer / AVAudioPlayer).
 * Real audio resolution (key -> resource) lives in the platform impls.
 */
interface AudioPlayer {
    /**
     * Play the audio for the given key (e.g. PL form "mam" or a resource identifier).
     * Calls onDone when playback completes (or immediately for stub).
     */
    fun play(audioKey: String, onDone: () -> Unit = {})

    /** Stop any playing audio. */
    fun stop()

    /** Best-effort "is currently playing". */
    fun isPlaying(): Boolean
}

/**
 * Factory for platform implementations. Each platform provides an actual.
 */
expect fun createAudioPlayer(): AudioPlayer