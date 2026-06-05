package com.sponic.langbang.domain

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Bridges LangBang's shared Now Voicing transport to Android headset/Bluetooth controls.
 *
 * The app plays short TTS clips with a pooled MediaPlayer, so Android has no native player
 * session to target. This MediaSession advertises the current PlaybackController state and
 * routes media buttons back through the same callbacks used by the on-screen controls.
 */
class VoicingMediaSession(context: Context) {
    private val mediaSession = MediaSession(context.applicationContext, "LangBangVoicing")
    private val audioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private var stateJob: Job? = null

    init {
        mediaSession.setPlaybackToLocal(audioAttributes)
        mediaSession.setCallback(
            object : MediaSession.Callback() {
                override fun onMediaButtonEvent(mediaButtonIntent: Intent): Boolean {
                    val event = mediaButtonIntent.getParcelableExtra(
                        Intent.EXTRA_KEY_EVENT,
                        KeyEvent::class.java
                    ) ?: return false
                    return handleKeyEvent(event)
                }

                override fun onPlay() {
                    resumeIfPaused()
                }

                override fun onPause() {
                    pauseIfPlaying()
                }

                override fun onStop() {
                    if (PlaybackController.transport.value != null) {
                        PlaybackController.stop()
                    }
                }

                override fun onSkipToNext() {
                    if (PlaybackController.transport.value?.next != null) {
                        PlaybackController.next()
                    }
                }

                override fun onSkipToPrevious() {
                    if (PlaybackController.transport.value?.rewind != null) {
                        PlaybackController.rewind()
                    }
                }

                override fun onFastForward() {
                    onSkipToNext()
                }

                override fun onRewind() {
                    onSkipToPrevious()
                }
            },
            Handler(Looper.getMainLooper())
        )
    }

    fun start(scope: CoroutineScope) {
        if (stateJob != null) return
        stateJob = scope.launch(Dispatchers.Main.immediate) {
            combine(
                PlaybackController.transport,
                PlaybackController.paused,
                PlaybackController.playing
            ) { transport, paused, playing ->
                SessionSnapshot(transport, paused || transport?.isPaused?.invoke() == true, playing)
            }.collect { snapshot ->
                updatePlaybackState(snapshot.transport, snapshot.paused, snapshot.playing)
            }
        }
    }

    fun handleKeyEvent(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN || event.repeatCount != 0) return false
        return when (event.keyCode) {
            KeyEvent.KEYCODE_HEADSETHOOK,
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> playPause()
            KeyEvent.KEYCODE_MEDIA_PLAY -> resumeIfPaused()
            KeyEvent.KEYCODE_MEDIA_PAUSE -> pauseIfPlaying()
            KeyEvent.KEYCODE_MEDIA_STOP -> stop()
            KeyEvent.KEYCODE_MEDIA_NEXT,
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> next()
            KeyEvent.KEYCODE_MEDIA_PREVIOUS,
            KeyEvent.KEYCODE_MEDIA_REWIND -> rewind()
            else -> false
        }
    }

    fun release() {
        stateJob?.cancel()
        stateJob = null
        mediaSession.release()
    }

    private fun playPause(): Boolean {
        val transport = PlaybackController.transport.value ?: return false
        if (transport.pauseResume == null) return false
        PlaybackController.pauseResume()
        return true
    }

    private fun pauseIfPlaying(): Boolean {
        val transport = PlaybackController.transport.value ?: return false
        if (transport.pauseResume == null) return false
        if (!isPaused(transport)) {
            PlaybackController.pauseResume()
        }
        return true
    }

    private fun resumeIfPaused(): Boolean {
        val transport = PlaybackController.transport.value ?: return false
        if (transport.pauseResume == null) return false
        if (isPaused(transport)) {
            PlaybackController.pauseResume()
        }
        return true
    }

    private fun stop(): Boolean {
        if (PlaybackController.transport.value == null) return false
        PlaybackController.stop()
        return true
    }

    private fun next(): Boolean {
        if (PlaybackController.transport.value?.next == null) return false
        PlaybackController.next()
        return true
    }

    private fun rewind(): Boolean {
        if (PlaybackController.transport.value?.rewind == null) return false
        PlaybackController.rewind()
        return true
    }

    private fun updatePlaybackState(
        transport: PlaybackTransport?,
        paused: Boolean,
        playing: Boolean
    ) {
        val state = when {
            transport == null -> PlaybackState.STATE_STOPPED
            paused -> PlaybackState.STATE_PAUSED
            playing -> PlaybackState.STATE_PLAYING
            else -> PlaybackState.STATE_STOPPED
        }
        val speed = if (state == PlaybackState.STATE_PLAYING) 1f else 0f
        val actions = transport?.mediaActions() ?: 0L
        mediaSession.setPlaybackState(
            PlaybackState.Builder()
                .setState(state, PlaybackState.PLAYBACK_POSITION_UNKNOWN, speed)
                .setActions(actions)
                .build()
        )
        mediaSession.setActive(transport != null)
    }

    private fun PlaybackTransport.mediaActions(): Long {
        var actions = PlaybackState.ACTION_STOP
        if (pauseResume != null) {
            actions = actions or PlaybackState.ACTION_PLAY
            actions = actions or PlaybackState.ACTION_PAUSE
            actions = actions or PlaybackState.ACTION_PLAY_PAUSE
        }
        if (next != null) {
            actions = actions or PlaybackState.ACTION_SKIP_TO_NEXT
            actions = actions or PlaybackState.ACTION_FAST_FORWARD
        }
        if (rewind != null) {
            actions = actions or PlaybackState.ACTION_SKIP_TO_PREVIOUS
            actions = actions or PlaybackState.ACTION_REWIND
        }
        return actions
    }

    private fun isPaused(transport: PlaybackTransport): Boolean {
        return PlaybackController.paused.value || transport.isPaused()
    }
}

private data class SessionSnapshot(
    val transport: PlaybackTransport?,
    val paused: Boolean,
    val playing: Boolean,
)
