package com.sponic.langbangtrans

import android.content.Context
import com.sponic.langbangtrans.g2.G2HudLink
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Owns one persistent Even G2 HUD session for LangBang's learning playback.
 *
 * The learning app supplies only already-spoken text. Audio remains entirely in
 * LangBang, so this companion never opens the microphone or changes the phone's
 * output route.
 */
class NowVoicingHudMirror(
    private val context: Context,
    private val config: G2Config,
    initialText: String,
) {
    private val latestText = MutableStateFlow(initialText)

    fun update(text: String) {
        latestText.value = text
    }

    suspend fun run(): Unit {
        while (currentCoroutineContext().isActive) {
            val hud = G2HudLink(context, config)
            try {
                val connected = hud.connect(latestText.value)
                if (!connected.ok) {
                    throw IllegalStateException(
                        "G2 HUD connect failed — keep the glasses awake and close the Even app"
                    )
                }
                BridgeStatusBus.set(
                    "LangBang learning mirror",
                    "Mirroring Now Voicing to G2",
                    running = true,
                )
                coroutineScope {
                    launch { hud.runKeepAlive() }
                    var lastSent = latestText.value
                    latestText.collect { text ->
                        if (text != lastSent) {
                            lastSent = text
                            hud.updateHud(text)
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (throwable: Throwable) {
                BridgeStatusBus.set(
                    "G2 reconnecting",
                    throwable.message ?: "Connection lost",
                    running = true,
                )
                delay(RECONNECT_DELAY_MS)
            } finally {
                hud.close()
            }
        }
    }

    private companion object {
        const val RECONNECT_DELAY_MS = 2_000L
    }
}
