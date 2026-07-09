package com.sponic.langbangtrans.g2

import android.content.Context
import com.sponic.langbangtrans.BridgeStatusBus
import com.sponic.langbangtrans.G2Config
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Drives the "Send test text to glasses" button: connects a [G2HudLink], shows a numbered test
 * page, then keeps it live with a ticking clock (so the content is provably fresh, not stale) and
 * heartbeats until the coroutine is cancelled (Stop bridge). The real BLE/protocol work lives in
 * [G2HudLink], which the live translation bridge reuses.
 */
class G2HudTextClient(
    context: Context,
    config: G2Config,
) {
    private val link = G2HudLink(context, config)

    suspend fun sendTestTextAndSave(baseText: String): String = sendLiveTextAndSave(baseText)

    suspend fun sendLiveTextAndSave(baseText: String): String {
        val connect = link.connect(baseText)
        if (!connect.ok) return connect.report

        BridgeStatusBus.set("G2 live", "HUD live — tap Stop bridge to end", running = true)
        try {
            coroutineScope {
                launch { link.runKeepAlive() }
                var tick = 0
                while (isActive) {
                    delay(LIVE_TICK_MS)
                    tick++
                    val elapsedS = tick * (LIVE_TICK_MS / 1000)
                    // Smooth 1s text updates; the link's keep-alive re-arms the page/display.
                    link.updateHud("$baseText\nlive ${CLOCK_FORMAT.format(Date())} (+${elapsedS}s)")
                    BridgeStatusBus.set("G2 live", "HUD live ${elapsedS}s — tap Stop bridge to end", running = true)
                }
            }
        } finally {
            link.close()
        }
        return connect.report
    }

    companion object {
        // Fast refresh: push new text every second so the clock is visibly live. The link's
        // keep-alive loop re-arms the page so the head-up display stays lit.
        private const val LIVE_TICK_MS = 1_000L
        private val CLOCK_FORMAT = SimpleDateFormat("HH:mm:ss", Locale.US)
    }
}
