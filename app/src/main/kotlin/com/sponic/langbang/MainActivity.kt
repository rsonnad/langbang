package com.sponic.langbang

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.sponic.langbang.domain.VoicingMediaSession
import com.sponic.langbang.ui.LangbangApp
import com.sponic.langbang.ui.theme.LangbangTheme

class MainActivity : ComponentActivity() {
    private lateinit var voicingMediaSession: VoicingMediaSession

    private val requestMic = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* result available on next composition via permission state read */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestMic.launch(Manifest.permission.RECORD_AUDIO)
        }

        val app = application as LangbangApplication
        voicingMediaSession = VoicingMediaSession(this)
        voicingMediaSession.start(lifecycleScope)
        // Prefetch is enqueued in LangbangApplication.onCreate via WorkManager.

        setContent {
            LangbangTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    LangbangApp(app)
                }
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (::voicingMediaSession.isInitialized && voicingMediaSession.handleKeyEvent(event)) {
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onResume() {
        super.onResume()
        AdbWifiKeeper.enableIfGranted(this, "activity resume")
        (application as LangbangApplication).analytics.track(
            name = "app_resumed",
            feature = "app",
            action = "resume"
        )
    }

    override fun onPause() {
        (application as LangbangApplication).analytics.track(
            name = "app_paused",
            feature = "app",
            action = "pause"
        )
        (application as LangbangApplication).analytics.flushSoon(delayMs = 0L)
        super.onPause()
    }

    override fun onDestroy() {
        if (::voicingMediaSession.isInitialized) {
            voicingMediaSession.release()
        }
        super.onDestroy()
    }
}
