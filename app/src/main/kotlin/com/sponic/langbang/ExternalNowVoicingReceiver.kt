package com.sponic.langbang

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.sponic.langbang.data.model.TokenPair
import com.sponic.langbang.domain.ExternalNowVoicingBus
import com.sponic.langbang.domain.ExternalNowVoicingState
import com.sponic.langbang.domain.ExternalVoiceSection
import org.json.JSONArray
import org.json.JSONObject

class ExternalNowVoicingReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_DRIVE_NOW_VOICING) return
        if (intent.getBooleanExtra(EXTRA_CLEAR, false)) {
            ExternalNowVoicingBus.clear()
            return
        }

        val focus = intent.getBooleanExtra(EXTRA_FOCUS, true)
        val payload = intent.getStringExtra(EXTRA_PAYLOAD)?.takeIf { it.isNotBlank() }
        if (payload != null) {
            publishJson(payload, focus)
        } else {
            publishFlat(intent, focus)
        }
    }

    private fun publishJson(payload: String, focus: Boolean) {
        val json = runCatching { JSONObject(payload) }.getOrNull() ?: return
        val dialog = json.optString("mode", "single").equals("dialog", ignoreCase = true) ||
            json.optBoolean("dialog", false)
        val active = normalizeSpeaker(json.optString("active", json.optString("activeSpeaker", "top")))
        val maroon = normalizeSpeaker(json.optString("maroon", json.optString("maroonSpeaker", "bottom")))
        val top = json.optJSONObject("top")?.toSection(defaultLabel = "Speaker A")
            ?: ExternalNowVoicingBus.state.value.top
        val bottom = json.optJSONObject("bottom")?.toSection(defaultLabel = "Speaker B")
            ?: ExternalNowVoicingBus.state.value.bottom
        ExternalNowVoicingBus.publish(
            ExternalNowVoicingState(
                dialog = dialog,
                activeSpeaker = active,
                maroonSpeaker = maroon,
                top = top,
                bottom = bottom
            ),
            focus = focus
        )
    }

    private fun publishFlat(intent: Intent, focus: Boolean) {
        val speaker = normalizeSpeaker(intent.getStringExtra("speaker") ?: "top")
        val dialog = intent.getBooleanExtra("dialog", false) ||
            intent.getStringExtra("mode").equals("dialog", ignoreCase = true)
        val section = ExternalVoiceSection(
            label = intent.getStringExtra("label") ?: if (speaker == "bottom") "Speaker B" else "Speaker A",
            en = intent.getStringExtra("en").orEmpty(),
            pl = intent.getStringExtra("pl").orEmpty(),
            literal = intent.getStringExtra("literal"),
            lang = normalizeLang(intent.getStringExtra("lang")),
            position = intent.getStringExtra("position"),
            words = intent.getStringExtra("words")?.let(::parseWords)
        )
        ExternalNowVoicingBus.publishSection(
            speaker = speaker,
            section = section,
            dialog = dialog,
            maroonSpeaker = normalizeSpeaker(intent.getStringExtra("maroon") ?: "bottom"),
            focus = focus
        )
    }

    private fun JSONObject.toSection(defaultLabel: String): ExternalVoiceSection = ExternalVoiceSection(
        label = optString("label", defaultLabel),
        en = optString("en", optString("english", "")),
        pl = optString("pl", optString("polish", "")),
        literal = optString("literal").takeIf { it.isNotBlank() },
        lang = normalizeLang(optString("lang", "pl")),
        position = optString("position").takeIf { it.isNotBlank() },
        words = optJSONArray("words")?.toWords()
    )

    private fun parseWords(payload: String): List<TokenPair>? =
        runCatching { JSONArray(payload).toWords() }.getOrNull()

    private fun JSONArray.toWords(): List<TokenPair> {
        val out = mutableListOf<TokenPair>()
        for (i in 0 until length()) {
            val item = optJSONObject(i) ?: continue
            val pl = item.optString("pl").takeIf { it.isNotBlank() } ?: continue
            val en = item.optString("en", item.optString("gloss")).takeIf { it.isNotBlank() } ?: continue
            out += TokenPair(
                pl = pl,
                en = en,
                gender = item.optString("gender").takeIf { it.isNotBlank() },
                caseKey = item.optString("caseKey").takeIf { it.isNotBlank() },
                caseLabel = item.optString("caseLabel").takeIf { it.isNotBlank() },
                numberLabel = item.optString("numberLabel").takeIf { it.isNotBlank() },
                variableStart = item.optIntOrNull("variableStart"),
                variableEnd = item.optIntOrNull("variableEnd"),
                variableKind = item.optString("variableKind").takeIf { it.isNotBlank() }
            )
        }
        return out
    }

    private fun JSONObject.optIntOrNull(name: String): Int? =
        if (has(name) && !isNull(name)) optInt(name) else null

    private fun normalizeSpeaker(value: String): String =
        if (value.equals("bottom", ignoreCase = true) || value.equals("b", ignoreCase = true)) "bottom" else "top"

    private fun normalizeLang(value: String?): String = when (value?.lowercase()) {
        "en", "pl", "pl-slow", "pause" -> value.lowercase()
        else -> "pl"
    }

    companion object {
        const val ACTION_DRIVE_NOW_VOICING = "com.sponic.langbangml.DRIVE_NOW_VOICING"
        private const val EXTRA_CLEAR = "clear"
        private const val EXTRA_FOCUS = "focus"
        private const val EXTRA_PAYLOAD = "payload"
    }
}
