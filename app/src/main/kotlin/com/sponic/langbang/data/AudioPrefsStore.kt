package com.sponic.langbang.data

import android.content.Context
import com.sponic.langbang.integrations.AzureTtsClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Per-device audio rendering choices. Currently just `slowStyle` — whether the slow
 * Polish playback in every "EN → PL-slow → EN → PL" loop is the time-stretched
 * `<prosody rate="-60%">` mp3 ("stretch") or the re-articulated `-10%` + `<break/>`
 * mp3 ("articulate"). Exposed as a StateFlow so call sites can `collectAsState()` and
 * recompose when the user flips the toggle in Settings.
 */
class AudioPrefsStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("audio-prefs", Context.MODE_PRIVATE)

    private val _slowStyle = MutableStateFlow(loadSlowStyle())
    val slowStyle: StateFlow<SlowStyle> = _slowStyle

    fun setSlowStyle(style: SlowStyle) {
        if (style == _slowStyle.value) return
        prefs.edit().putString(KEY_SLOW_STYLE, style.name).apply()
        _slowStyle.value = style
    }

    /** Azure voice id for slow PL audio based on the current preference. */
    fun slowPlVoice(): String = when (_slowStyle.value) {
        SlowStyle.STRETCH -> AzureTtsClient.PL_PL_F_SLOW_V2
        SlowStyle.ARTICULATE -> AzureTtsClient.PL_PL_F_SLOW_ART
    }

    fun slowTargetVoice(configuredVoices: List<String>, fallbackVoice: String): String {
        val suffix = when (_slowStyle.value) {
            SlowStyle.STRETCH -> AzureTtsClient.SLOW_SUFFIX_V2
            SlowStyle.ARTICULATE -> AzureTtsClient.SLOW_SUFFIX_V3
        }
        return configuredVoices.firstOrNull { it.endsWith(suffix) }
            ?: configuredVoices.firstOrNull()
            ?: fallbackVoice
    }

    private fun loadSlowStyle(): SlowStyle {
        val raw = prefs.getString(KEY_SLOW_STYLE, null) ?: return SlowStyle.STRETCH
        return runCatching { SlowStyle.valueOf(raw) }.getOrDefault(SlowStyle.STRETCH)
    }

    companion object {
        private const val KEY_SLOW_STYLE = "slow-style"
    }
}

enum class SlowStyle {
    /** Single Azure utterance at `<prosody rate="-60%">` — time-stretched normal speech. */
    STRETCH,

    /**
     * `<prosody rate="-10%">` with `<break time="250ms"/>` between every word — Azure
     * re-articulates each word in isolation, no coarticulation muddiness.
     */
    ARTICULATE,
}
