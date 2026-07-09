package com.sponic.langbangtrans

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import android.text.InputType
import android.view.WindowInsets
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class MainActivity : Activity() {
    private val scope = MainScope()
    private lateinit var statusView: TextView
    private lateinit var transcriptView: TextView
    private lateinit var textInput: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val basePaddingLeft = 32
        val basePaddingTop = 18
        val basePaddingRight = 32
        val basePaddingBottom = 32

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(basePaddingLeft, basePaddingTop, basePaddingRight, basePaddingBottom)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        }

        val title = TextView(this).apply {
            text = "G2Trans v. ${BuildConfig.VERSION_NAME}"
            textSize = 17f
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        statusView = TextView(this).apply {
            textSize = 16f
            setPadding(0, 24, 0, 24)
        }
        transcriptView = TextView(this).apply {
            textSize = 15f
            setPadding(0, 16, 0, 16)
        }

        val startButton = Button(this).apply {
            text = "Speak PL → hear EN"
            setOnClickListener { startBridge() }
        }
        val startReverseButton = Button(this).apply {
            text = "Speak EN → see PL"
            setOnClickListener { startBridgeReverse() }
        }
        val startRuButton = Button(this).apply {
            text = "Speak EN → see RU"
            setOnClickListener { startBridgeRu() }
        }
        val stopButton = Button(this).apply {
            text = "Stop bridge"
            setOnClickListener { stopBridge() }
        }
        val discoverButton = Button(this).apply {
            text = "Discover G2 BLE profile"
            setOnClickListener { discoverG2() }
        }
        val probeButton = Button(this).apply {
            text = "Test G2 BLE connect"
            setOnClickListener { probeG2Connect() }
        }
        val sendTextButton = Button(this).apply {
            text = "Send test text to glasses"
            setOnClickListener { sendTestText() }
        }
        val micTestButton = Button(this).apply {
            text = "Test phone mic (5s)"
            setOnClickListener { testMic() }
        }
        textInput = EditText(this).apply {
            hint = "Type English for G2"
            minLines = 2
            maxLines = 4
            inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        }
        val translateTextButton = Button(this).apply {
            text = "Translate text → G2 PL"
            setOnClickListener { translateTypedText() }
        }

        root.addView(title)
        root.addView(startButton)
        root.addView(startReverseButton)
        root.addView(startRuButton)
        root.addView(stopButton)
        root.addView(discoverButton)
        root.addView(probeButton)
        root.addView(sendTextButton)
        root.addView(micTestButton)
        root.addView(textInput)
        root.addView(translateTextButton)
        root.addView(statusView)
        root.addView(transcriptView)
        val scrollView = ScrollView(this).apply {
            clipToPadding = false
            addView(root)
            setOnApplyWindowInsetsListener { _, insets ->
                val bars = insets.getInsets(
                    WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()
                )
                root.setPadding(
                    basePaddingLeft + bars.left,
                    basePaddingTop + bars.top,
                    basePaddingRight + bars.right,
                    basePaddingBottom + bars.bottom,
                )
                insets
            }
        }
        setContentView(scrollView)
        scrollView.requestApplyInsets()

        scope.launch {
            BridgeStatusBus.status.collect { status ->
                statusView.text = buildString {
                    append("Status: ").append(status.stage)
                    if (status.details.isNotBlank()) append("\n").append(status.details)
                    append("\nRunning: ").append(status.running)
                    append("\nModel: ").append(BuildConfig.GEMINI_MODEL)
                    append("\nG2 HUD: real BLE")
                }
                transcriptView.text = buildString {
                    append("PL\n").append(status.lastPolish.ifBlank { "..." })
                    append("\n\nEN\n").append(status.lastEnglish.ifBlank { "..." })
                    append("\n\nG2 Discovery\n").append(status.lastDiscovery.ifBlank { "..." })
                }
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun startBridge() {
        val missing = missingPermissions()
        if (missing.isNotEmpty()) {
            requestPermissions(missing.toTypedArray(), 5001)
            return
        }
        startForegroundService(Intent(this, BridgeService::class.java).setAction(BridgeService.ACTION_START))
    }

    private fun startBridgeReverse() {
        val missing = missingPermissions()
        if (missing.isNotEmpty()) {
            requestPermissions(missing.toTypedArray(), 5001)
            return
        }
        startForegroundService(Intent(this, BridgeService::class.java).setAction(BridgeService.ACTION_START_REVERSE))
    }

    private fun startBridgeRu() {
        val missing = missingPermissions()
        if (missing.isNotEmpty()) {
            requestPermissions(missing.toTypedArray(), 5001)
            return
        }
        startForegroundService(Intent(this, BridgeService::class.java).setAction(BridgeService.ACTION_START_RU))
    }

    private fun stopBridge() {
        startService(Intent(this, BridgeService::class.java).setAction(BridgeService.ACTION_STOP))
    }

    private fun discoverG2() {
        val missing = missingPermissions()
        if (missing.isNotEmpty()) {
            requestPermissions(missing.toTypedArray(), 5001)
            return
        }
        startForegroundService(Intent(this, BridgeService::class.java).setAction(BridgeService.ACTION_DISCOVER_G2))
    }

    private fun probeG2Connect() {
        val missing = missingPermissions()
        if (missing.isNotEmpty()) {
            requestPermissions(missing.toTypedArray(), 5001)
            return
        }
        startForegroundService(Intent(this, BridgeService::class.java).setAction(BridgeService.ACTION_PROBE_G2_CONNECT))
    }

    private fun sendTestText() {
        val missing = missingPermissions()
        if (missing.isNotEmpty()) {
            requestPermissions(missing.toTypedArray(), 5001)
            return
        }
        startForegroundService(Intent(this, BridgeService::class.java).setAction(BridgeService.ACTION_SEND_TEST_TEXT))
    }

    private fun testMic() {
        val missing = missingPermissions()
        if (missing.isNotEmpty()) {
            requestPermissions(missing.toTypedArray(), 5001)
            return
        }
        startForegroundService(Intent(this, BridgeService::class.java).setAction(BridgeService.ACTION_MIC_TEST))
    }

    private fun translateTypedText() {
        val missing = missingPermissions()
        if (missing.isNotEmpty()) {
            requestPermissions(missing.toTypedArray(), 5001)
            return
        }
        val text = textInput.text?.toString().orEmpty().trim()
        if (text.isBlank()) {
            BridgeStatusBus.set("Text translate", "Enter English text first", running = false)
            return
        }
        startForegroundService(
            Intent(this, BridgeService::class.java)
                .setAction(BridgeService.ACTION_TRANSLATE_TEXT)
                .putExtra(BridgeService.EXTRA_TEXT, text)
        )
    }

    private fun missingPermissions(): List<String> = requiredPermissions().filter {
        checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
    }

    private fun requiredPermissions(): List<String> = buildList {
        add(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_SCAN)
            add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
