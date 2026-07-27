package com.sponic.langbangtrans

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.sponic.langbangtrans.audio.MicProbe
import com.sponic.langbangtrans.g2.G2ConnectionProbeClient
import com.sponic.langbangtrans.g2.G2DiscoveryClient
import com.sponic.langbangtrans.g2.G2HudTextClient
import com.sponic.langbangtrans.gemini.G2TextTranslator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class BridgeService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var runnerJob: kotlinx.coroutines.Job? = null
    private var nowVoicingMirror: NowVoicingHudMirror? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopBridge()
                return START_NOT_STICKY
            }
            ACTION_MIRROR_NOW_VOICING -> mirrorNowVoicing(intent.getStringExtra(EXTRA_TEXT).orEmpty())
            ACTION_CLEAR_NOW_VOICING_MIRROR -> clearNowVoicingMirror()
            ACTION_DISCOVER_G2 -> discoverG2()
            ACTION_PROBE_G2_CONNECT -> probeG2Connect()
            ACTION_SEND_TEST_TEXT -> sendTestText()
            ACTION_MIC_TEST -> testMic()
            ACTION_TRANSLATE_TEXT -> translateText(intent.getStringExtra(EXTRA_TEXT).orEmpty())
            ACTION_START_REVERSE -> startBridge("pl", playAudio = false)
            ACTION_START_RU -> startBridge("ru", playAudio = false)
            else -> startBridge(BuildConfig.TARGET_LANGUAGE_CODE, playAudio = true)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopBridge()
        scope.cancel()
        super.onDestroy()
    }

    private fun startBridge(targetLanguageCode: String, playAudio: Boolean) {
        ensureForeground()
        // Take over from any running job (e.g. a held "send test text" page) instead of bailing out.
        runnerJob?.cancel()
        BridgeStatusBus.set("Starting", "Translating to ${targetLanguageCode.uppercase()}", running = true)
        runnerJob = scope.launch {
            try {
                val base = BridgeConfig.fromBuildConfig()
                val config = base.copy(
                    gemini = base.gemini.copy(targetLanguageCode = targetLanguageCode, playOutputAudio = playAudio),
                )
                BridgeRunner(applicationContext, config).run()
            } catch (cancellation: CancellationException) {
                BridgeStatusBus.set("Stopped", running = false)
            } catch (throwable: Throwable) {
                BridgeStatusBus.set("Error", throwable.message ?: throwable::class.java.simpleName, running = false)
            }
        }
    }

    private fun discoverG2() {
        ensureForeground("Discovering G2 BLE profile")
        runnerJob?.cancel()
        BridgeStatusBus.set("G2 discovery", "Scanning for Even G2 left/right devices", running = true)
        runnerJob = scope.launch {
            try {
                val report = G2DiscoveryClient(applicationContext, BridgeConfig.fromBuildConfig().g2).discoverAndSave()
                BridgeStatusBus.discovery(report)
                BridgeStatusBus.set("G2 discovery complete", "Report saved and shown below", running = false)
            } catch (cancellation: CancellationException) {
                BridgeStatusBus.set("G2 discovery stopped", running = false)
            } catch (throwable: Throwable) {
                BridgeStatusBus.set("G2 discovery error", throwable.message ?: throwable::class.java.simpleName, running = false)
            }
        }
    }

    private fun probeG2Connect() {
        ensureForeground("Testing G2 BLE command channel")
        runnerJob?.cancel()
        BridgeStatusBus.set("G2 connect probe", "Scanning for Even G2 left/right devices", running = true)
        runnerJob = scope.launch {
            try {
                val report = G2ConnectionProbeClient(applicationContext, BridgeConfig.fromBuildConfig().g2).probeAndSave()
                BridgeStatusBus.discovery(report)
                BridgeStatusBus.set("G2 connect probe complete", "Command channel report saved and shown below", running = false)
            } catch (cancellation: CancellationException) {
                BridgeStatusBus.set("G2 connect probe stopped", running = false)
            } catch (throwable: Throwable) {
                BridgeStatusBus.set("G2 connect probe error", throwable.message ?: throwable::class.java.simpleName, running = false)
            }
        }
    }

    private fun sendTestText() {
        ensureForeground("Sending test text to G2 HUD")
        runnerJob?.cancel()
        BridgeStatusBus.set("G2 send text", "Scanning for Even G2 left/right lenses", running = true)
        runnerJob = scope.launch {
            val text = "LBG2 v${BuildConfig.VERSION_NAME}\n" +
                "Polish: Nie odpowiedziałeś na moje pytanie.\n" +
                "Pronun: Nyeh ohd-poh-vyeh-dzyah-wesh nah moh-yeh pi-tah-nyeh.\n" +
                "English: You didn't answer my question.\n" +
                "Chcesz spróbować masażu na tym starym stole?\n" +
                "Hchesh sproo-boh-vach mah-sah-zhoo nah tim stah-rim stoh-leh?\n" +
                "Do you want to experience/try a massage on this old table?"
            try {
                val report = G2HudTextClient(applicationContext, BridgeConfig.fromBuildConfig().g2)
                    .sendTestTextAndSave(text)
                BridgeStatusBus.discovery(report)
                BridgeStatusBus.set("G2 send text complete", "Report saved and shown below", running = false)
            } catch (cancellation: CancellationException) {
                BridgeStatusBus.set("G2 send text stopped", running = false)
            } catch (throwable: Throwable) {
                BridgeStatusBus.set("G2 send text error", throwable.message ?: throwable::class.java.simpleName, running = false)
            }
        }
    }

    private fun testMic() {
        ensureForeground("Testing phone microphone")
        runnerJob?.cancel()
        BridgeStatusBus.set("Mic test", "Recording 5 seconds from phone mic", running = true)
        runnerJob = scope.launch {
            try {
                val report = MicProbe(applicationContext, BridgeConfig.fromBuildConfig().audio).recordAndSave()
                BridgeStatusBus.discovery(report)
                BridgeStatusBus.set("Mic test complete", "Peak/RMS report saved and shown below", running = false)
            } catch (cancellation: CancellationException) {
                BridgeStatusBus.set("Mic test stopped", running = false)
            } catch (throwable: Throwable) {
                BridgeStatusBus.set("Mic test error", throwable.message ?: throwable::class.java.simpleName, running = false)
            }
        }
    }

    private fun translateText(english: String) {
        ensureForeground("Translating typed text to G2 HUD")
        runnerJob?.cancel()
        val trimmed = english.trim()
        if (trimmed.isBlank()) {
            BridgeStatusBus.set("Text translate", "Enter English text first", running = false)
            return
        }
        BridgeStatusBus.set("Text translate", "Generating Polish and phonetics", running = true)
        runnerJob = scope.launch {
            try {
                val base = BridgeConfig.fromBuildConfig()
                val translation = G2TextTranslator(base.gemini).translate(trimmed)
                BridgeStatusBus.transcripts(translation.polish, translation.english)
                BridgeStatusBus.discovery(translation.displayText)
                BridgeStatusBus.set("Text translate", "Sending Polish text to G2 HUD", running = true)
                val report = G2HudTextClient(applicationContext, base.g2)
                    .sendLiveTextAndSave(translation.displayText)
                BridgeStatusBus.discovery(report)
                BridgeStatusBus.set("Text translate complete", "Report saved and shown below", running = false)
            } catch (cancellation: CancellationException) {
                BridgeStatusBus.set("Text translate stopped", running = false)
            } catch (throwable: Throwable) {
                BridgeStatusBus.set("Text translate error", throwable.message ?: throwable::class.java.simpleName, running = false)
            }
        }
    }

    private fun mirrorNowVoicing(text: String) {
        if (text.isBlank()) return
        ensureForeground("Mirroring LangBang Now Voicing to G2 HUD")
        nowVoicingMirror?.let {
            it.update(text)
            return
        }
        runnerJob?.cancel()
        val mirror = NowVoicingHudMirror(applicationContext, BridgeConfig.fromBuildConfig().g2, text)
        nowVoicingMirror = mirror
        runnerJob = scope.launch {
            try {
                mirror.run()
            } catch (cancellation: CancellationException) {
                BridgeStatusBus.set("LangBang learning mirror stopped", running = false)
            } catch (throwable: Throwable) {
                BridgeStatusBus.set("LangBang learning mirror error", throwable.message ?: throwable::class.java.simpleName, running = false)
            } finally {
                if (nowVoicingMirror === mirror) nowVoicingMirror = null
            }
        }
    }

    private fun clearNowVoicingMirror() {
        if (nowVoicingMirror == null) return
        runnerJob?.cancel()
        runnerJob = null
        nowVoicingMirror = null
        BridgeStatusBus.set("LangBang learning mirror stopped", running = false)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun stopBridge() {
        runnerJob?.cancel()
        runnerJob = null
        nowVoicingMirror = null
        BridgeStatusBus.set("Stopped", running = false)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun ensureForeground(text: String = "Listening, translating, and updating G2 HUD") {
        val channelId = "langbangtrans_bridge"
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                channelId,
                getString(R.string.notification_channel),
                NotificationManager.IMPORTANCE_LOW,
            )
        )

        val launchIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification: Notification = Notification.Builder(this, channelId)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        const val ACTION_START = "com.sponic.langbangtrans.START"
        const val ACTION_START_REVERSE = "com.sponic.langbangtrans.START_REVERSE"
        const val ACTION_START_RU = "com.sponic.langbangtrans.START_RU"
        const val ACTION_STOP = "com.sponic.langbangtrans.STOP"
        const val ACTION_DISCOVER_G2 = "com.sponic.langbangtrans.DISCOVER_G2"
        const val ACTION_PROBE_G2_CONNECT = "com.sponic.langbangtrans.PROBE_G2_CONNECT"
        const val ACTION_SEND_TEST_TEXT = "com.sponic.langbangtrans.SEND_TEST_TEXT"
        const val ACTION_MIC_TEST = "com.sponic.langbangtrans.MIC_TEST"
        const val ACTION_TRANSLATE_TEXT = "com.sponic.langbangtrans.TRANSLATE_TEXT"
        const val ACTION_MIRROR_NOW_VOICING = "com.sponic.langbangtrans.MIRROR_NOW_VOICING"
        const val ACTION_CLEAR_NOW_VOICING_MIRROR = "com.sponic.langbangtrans.CLEAR_NOW_VOICING_MIRROR"
        const val EXTRA_TEXT = "com.sponic.langbangtrans.extra.TEXT"
        private const val NOTIFICATION_ID = 4217
    }
}
