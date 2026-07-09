package com.sponic.langbangtrans

import android.content.Context
import android.util.Log
import com.sponic.langbangtrans.audio.LastTranslation
import com.sponic.langbangtrans.audio.PcmAudioPlayer
import com.sponic.langbangtrans.audio.PcmAudioRecorder
import com.sponic.langbangtrans.g2.G2HudLink
import com.sponic.langbangtrans.gemini.GeminiEvent
import com.sponic.langbangtrans.gemini.GeminiLiveClient
import com.sponic.langbangtrans.hud.HudTextState
import com.sponic.langbangtrans.hud.TextLayoutManager
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BridgeRunner(
    private val context: Context,
    private val config: BridgeConfig,
) {
    suspend fun run(): Unit = coroutineScope {
        val problems = config.validationProblems()
        if (problems.isNotEmpty()) {
            throw IllegalStateException(problems.joinToString("\n"))
        }

        BridgeStatusBus.set("Running", "Connecting glasses, microphone, and Gemini", running = true)

        // Connect the HUD over the real EvenHub protocol before opening mic + Gemini.
        val targetLabel = config.gemini.targetLanguageCode.uppercase() // "EN" or "PL"

        val hud = G2HudLink(context, config.g2)
        val connected = hud.connect(TextLayoutManager().render(HudTextState(), targetLabel, hudStatusText()))
        Log.i("LBG2bridge", "glasses connected=${connected.ok}; starting mic+gemini (target=${config.gemini.targetLanguageCode})")
        if (!connected.ok) {
            throw IllegalStateException("G2 HUD connect failed — keep the glasses awake and close the Even app")
        }

        val audioIn = Channel<ByteArray>(capacity = 64)
        val geminiEvents = Channel<GeminiEvent>(capacity = 64)
        val outputAudio = Channel<ByteArray>(capacity = 64)

        try {
            launch { hud.runKeepAlive() }
            launch { PcmAudioRecorder(config.audio).run(audioIn) }
            launch { GeminiLiveClient(config.gemini, config.audio).run(audioIn, geminiEvents) }
            if (config.gemini.playOutputAudio) {
                launch { PcmAudioPlayer(config.audio).run(outputAudio) }
            }
            launch { renderLoop(hud, geminiEvents, outputAudio) }

            awaitCancellation()
        } finally {
            hud.close()
        }
    }

    private suspend fun renderLoop(
        hud: G2HudLink,
        geminiEvents: Channel<GeminiEvent>,
        outputAudio: Channel<ByteArray>,
    ): Unit = coroutineScope {
        val layout = TextLayoutManager()
        val state = HudTextState()
        // One-language display: show only the target translation (the language the user reads).
        val targetLabel = config.gemini.targetLanguageCode.uppercase() // "EN" or "PL"

        // Coalesced sender: push the latest frame to the glasses on a steady cadence rather than on
        // every transcript delta, so rapid speech doesn't flood the BLE link (which delays heartbeats
        // and flashes "connection lost" on the lenses).
        val sender = launch {
            var lastFrame = ""
            while (isActive) {
                delay(HUD_UPDATE_MS)
                val frame = layout.render(state, targetLabel, hudStatusText())
                if (frame != lastFrame) {
                    lastFrame = frame
                    hud.updateHud(frame)
                }
            }
        }

        var lastReplayMs = 0L
        try {
            for (event in geminiEvents) {
                if (event.inputText != null || event.outputText != null) {
                    Log.i("LBG2bridge", "transcript in='${event.inputText}' out='${event.outputText}'")
                }
                if (event.turnComplete) LastTranslation.endUtterance()
                if (event.outputAudio != null) {
                    LastTranslation.addChunk(event.outputAudio) // buffer for replay in any mode
                    if (config.gemini.playOutputAudio) outputAudio.send(event.outputAudio)
                }
                // "play" code word → replay the last translation in the headphones (debounced).
                if (event.inputText != null && PLAY_WORD.containsMatchIn(event.inputText)) {
                    val now = System.currentTimeMillis()
                    if (now - lastReplayMs > 2_000) {
                        lastReplayMs = now
                        Log.i("LBG2bridge", "replay trigger: voice 'play'")
                        launch { LastTranslation.playLast(config.audio.outputSampleRate) }
                    }
                }
                state.apply(event.inputText, event.outputText)
                // Keep the phone UI's PL/EN fields language-correct regardless of direction.
                val polish = if (targetLabel == "PL") state.targetText else state.sourceText
                val english = if (targetLabel == "PL") state.sourceText else state.targetText
                BridgeStatusBus.transcripts(polish, english)
            }
        } finally {
            sender.cancel()
            if (!kotlin.coroutines.coroutineContext.isActive) outputAudio.close()
        }
    }

    companion object {
        // Max HUD refresh rate to the glasses (coalesces rapid transcript deltas).
        private const val HUD_UPDATE_MS = 400L
        // Code word (in the spoken-language transcript) that replays the last translation's audio.
        private val PLAY_WORD = Regex("\\bplay\\b", RegexOption.IGNORE_CASE)
        private val CLOCK_FORMAT = SimpleDateFormat("HH:mm:ss", Locale.US)

        fun clockText(): String = CLOCK_FORMAT.format(Date())
        fun hudStatusText(): String = "v${BuildConfig.VERSION_NAME} ${clockText()}"
    }
}
