package com.sponic.langbang.domain

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.YearMonth

data class UsageSnapshot(
    val ttsCharsThisMonth: Long,
    val ttsCharsAllTime: Long,
    val pronSecondsThisMonth: Long,
    val pronSecondsAllTime: Long,
    val geminiInputTokensThisMonth: Long,
    val geminiInputTokensAllTime: Long,
    val geminiOutputTokensThisMonth: Long,
    val geminiOutputTokensAllTime: Long,
    val rateTtsPerMillionCharsUsd: Double,
    val ratePronPerAudioHourUsd: Double,
    val rateGeminiInputPerMillionTokensUsd: Double,
    val rateGeminiOutputPerMillionTokensUsd: Double,
    val monthLabel: String,
) {
    val ttsCostThisMonthUsd: Double get() =
        ttsCharsThisMonth.toDouble() / 1_000_000.0 * rateTtsPerMillionCharsUsd
    val ttsCostAllTimeUsd: Double get() =
        ttsCharsAllTime.toDouble() / 1_000_000.0 * rateTtsPerMillionCharsUsd
    val pronCostThisMonthUsd: Double get() =
        pronSecondsThisMonth.toDouble() / 3600.0 * ratePronPerAudioHourUsd
    val pronCostAllTimeUsd: Double get() =
        pronSecondsAllTime.toDouble() / 3600.0 * ratePronPerAudioHourUsd
    val geminiCostThisMonthUsd: Double get() =
        geminiInputTokensThisMonth.toDouble() / 1_000_000.0 * rateGeminiInputPerMillionTokensUsd +
            geminiOutputTokensThisMonth.toDouble() / 1_000_000.0 * rateGeminiOutputPerMillionTokensUsd
    val geminiCostAllTimeUsd: Double get() =
        geminiInputTokensAllTime.toDouble() / 1_000_000.0 * rateGeminiInputPerMillionTokensUsd +
            geminiOutputTokensAllTime.toDouble() / 1_000_000.0 * rateGeminiOutputPerMillionTokensUsd
    val azureThisMonthUsd: Double get() = ttsCostThisMonthUsd + pronCostThisMonthUsd
    val azureAllTimeUsd: Double get() = ttsCostAllTimeUsd + pronCostAllTimeUsd
    val totalThisMonthUsd: Double get() = azureThisMonthUsd + geminiCostThisMonthUsd
    val totalAllTimeUsd: Double get() = azureAllTimeUsd + geminiCostAllTimeUsd
}

/**
 * Local estimate of Azure Speech spend. Counts characters sent to TTS and seconds of
 * audio submitted to Pronunciation Assessment, multiplied by configurable per-unit rates.
 * Not the real invoice — close enough to catch a runaway loop and to budget month-to-month.
 */
class UsageTracker(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _snapshot = MutableStateFlow(read())
    val snapshot: StateFlow<UsageSnapshot> = _snapshot.asStateFlow()

    fun recordTtsChars(chars: Int) {
        if (chars <= 0) return
        val ym = currentMonthKey()
        prefs.edit().apply {
            putLong(KEY_TTS_TOTAL, prefs.getLong(KEY_TTS_TOTAL, 0) + chars)
            putLong("$KEY_TTS_MONTH_PREFIX$ym", prefs.getLong("$KEY_TTS_MONTH_PREFIX$ym", 0) + chars)
            apply()
        }
        _snapshot.value = read()
    }

    fun recordPronunciationSeconds(seconds: Double) {
        if (seconds <= 0.0) return
        val whole = seconds.toLong().coerceAtLeast(1)
        val ym = currentMonthKey()
        prefs.edit().apply {
            putLong(KEY_PRON_TOTAL, prefs.getLong(KEY_PRON_TOTAL, 0) + whole)
            putLong("$KEY_PRON_MONTH_PREFIX$ym", prefs.getLong("$KEY_PRON_MONTH_PREFIX$ym", 0) + whole)
            apply()
        }
        _snapshot.value = read()
    }

    fun recordGeminiTokens(inputTokens: Int, outputTokens: Int) {
        if (inputTokens <= 0 && outputTokens <= 0) return
        val ym = currentMonthKey()
        val inC = inputTokens.coerceAtLeast(0)
        val outC = outputTokens.coerceAtLeast(0)
        prefs.edit().apply {
            putLong(KEY_GEMINI_IN_TOTAL, prefs.getLong(KEY_GEMINI_IN_TOTAL, 0) + inC)
            putLong("$KEY_GEMINI_IN_MONTH_PREFIX$ym",
                prefs.getLong("$KEY_GEMINI_IN_MONTH_PREFIX$ym", 0) + inC)
            putLong(KEY_GEMINI_OUT_TOTAL, prefs.getLong(KEY_GEMINI_OUT_TOTAL, 0) + outC)
            putLong("$KEY_GEMINI_OUT_MONTH_PREFIX$ym",
                prefs.getLong("$KEY_GEMINI_OUT_MONTH_PREFIX$ym", 0) + outC)
            apply()
        }
        _snapshot.value = read()
    }

    fun setRates(ttsPerMillionChars: Double, pronPerAudioHour: Double) {
        prefs.edit()
            .putFloat(KEY_RATE_TTS, ttsPerMillionChars.toFloat())
            .putFloat(KEY_RATE_PRON, pronPerAudioHour.toFloat())
            .apply()
        _snapshot.value = read()
    }

    fun resetAll() {
        prefs.edit().clear().apply()
        _snapshot.value = read()
    }

    private fun read(): UsageSnapshot {
        val ym = currentMonthKey()
        return UsageSnapshot(
            ttsCharsThisMonth = prefs.getLong("$KEY_TTS_MONTH_PREFIX$ym", 0),
            ttsCharsAllTime = prefs.getLong(KEY_TTS_TOTAL, 0),
            pronSecondsThisMonth = prefs.getLong("$KEY_PRON_MONTH_PREFIX$ym", 0),
            pronSecondsAllTime = prefs.getLong(KEY_PRON_TOTAL, 0),
            geminiInputTokensThisMonth = prefs.getLong("$KEY_GEMINI_IN_MONTH_PREFIX$ym", 0),
            geminiInputTokensAllTime = prefs.getLong(KEY_GEMINI_IN_TOTAL, 0),
            geminiOutputTokensThisMonth = prefs.getLong("$KEY_GEMINI_OUT_MONTH_PREFIX$ym", 0),
            geminiOutputTokensAllTime = prefs.getLong(KEY_GEMINI_OUT_TOTAL, 0),
            rateTtsPerMillionCharsUsd = prefs.getFloat(KEY_RATE_TTS, DEFAULT_TTS_RATE).toDouble(),
            ratePronPerAudioHourUsd = prefs.getFloat(KEY_RATE_PRON, DEFAULT_PRON_RATE).toDouble(),
            rateGeminiInputPerMillionTokensUsd =
                prefs.getFloat(KEY_RATE_GEMINI_IN, DEFAULT_GEMINI_IN_RATE).toDouble(),
            rateGeminiOutputPerMillionTokensUsd =
                prefs.getFloat(KEY_RATE_GEMINI_OUT, DEFAULT_GEMINI_OUT_RATE).toDouble(),
            monthLabel = ym,
        )
    }

    private fun currentMonthKey(): String = YearMonth.now().toString()

    companion object {
        const val PREFS = "langbang_usage"
        private const val KEY_TTS_TOTAL = "tts_chars_total"
        private const val KEY_TTS_MONTH_PREFIX = "tts_chars_"
        private const val KEY_PRON_TOTAL = "pron_seconds_total"
        private const val KEY_PRON_MONTH_PREFIX = "pron_seconds_"
        private const val KEY_GEMINI_IN_TOTAL = "gemini_in_tokens_total"
        private const val KEY_GEMINI_IN_MONTH_PREFIX = "gemini_in_tokens_"
        private const val KEY_GEMINI_OUT_TOTAL = "gemini_out_tokens_total"
        private const val KEY_GEMINI_OUT_MONTH_PREFIX = "gemini_out_tokens_"
        private const val KEY_RATE_TTS = "rate_tts_per_million_chars_usd"
        private const val KEY_RATE_PRON = "rate_pron_per_audio_hour_usd"
        private const val KEY_RATE_GEMINI_IN = "rate_gemini_input_per_million_tokens_usd"
        private const val KEY_RATE_GEMINI_OUT = "rate_gemini_output_per_million_tokens_usd"
        // Azure Neural TTS Standard, eastus, S0 tier (per service-access.md).
        private const val DEFAULT_TTS_RATE = 16.0f
        // Pronunciation Assessment realtime — published rate is ~$1/audio-hour.
        private const val DEFAULT_PRON_RATE = 1.0f
        // Gemini 2.5 Flash published rates (paid tier): $0.30/M input, $2.50/M output.
        private const val DEFAULT_GEMINI_IN_RATE = 0.30f
        private const val DEFAULT_GEMINI_OUT_RATE = 2.50f
    }
}
