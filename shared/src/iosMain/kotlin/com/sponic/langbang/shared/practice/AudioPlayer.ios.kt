package com.sponic.langbang.shared.practice

/**
 * iOS actual.
 * AVFoundation integration was blocking full headless build in this skeleton env (no full Xcode/KotlinNative interop at configure for all symbols).
 * Per task allowance: clearly-marked stub actual is acceptable if AV blocks the build.
 * The contract, model, tests, and both thin renderers remain real and exercised.
 * In a real device/CI build with proper framework linking, replace with full AVAudioPlayer impl.
 */
actual fun createAudioPlayer(): AudioPlayer = IosStubAudioPlayer()

private class IosStubAudioPlayer : AudioPlayer {
    private var playing = false

    override fun play(audioKey: String, onDone: () -> Unit) {
        playing = true
        println("AudioPlayer iOS: STUB (AVFoundation integration deferred) play($audioKey)")
        // Simulate completion synchronously for tests / SwiftUI host loop.
        // Real impl would use AVAudioPlayer + dispatch_after for onDone.
        playing = false
        onDone()
    }

    override fun stop() {
        playing = false
    }

    override fun isPlaying(): Boolean = playing
}