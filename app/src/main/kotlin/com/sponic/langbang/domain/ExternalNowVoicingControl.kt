package com.sponic.langbang.domain

import com.sponic.langbang.data.model.TokenPair
import kotlinx.coroutines.flow.MutableSharedFlow
import org.json.JSONArray
import org.json.JSONObject

/** A playback instruction emitted after a remote or local external-control command. */
data class ExternalNowVoicingPlaybackRequest(
    val mode: String,
    val section: ExternalVoiceSection
)

/**
 * One normalizer for every external Now Voicing transport. The protected Android
 * broadcast receiver and the Cloudflare WebSocket both feed this controller, so
 * the screen never develops transport-specific payload behavior.
 */
object ExternalNowVoicingCommandController {
    val playbackRequests = MutableSharedFlow<ExternalNowVoicingPlaybackRequest>(extraBufferCapacity = 8)

    fun applyJson(payload: String, focus: Boolean = true, allowPlayback: Boolean = true): Boolean =
        runCatching { applyJson(JSONObject(payload), focus, allowPlayback) }.getOrDefault(false)

    fun applyJson(json: JSONObject, focus: Boolean = true, allowPlayback: Boolean = true): Boolean {
        val op = json.optString("op", "show").lowercase()
        when (op) {
            "clear" -> {
                ExternalNowVoicingBus.clear()
                return true
            }
            "pause", "resume", "stop" -> {
                playbackRequests.tryEmit(ExternalNowVoicingPlaybackRequest(op, ExternalNowVoicingBus.state.value.activeSection))
                return true
            }
            "play" -> {
                val requested = json.optString("play", "sequence").lowercase().takeIf { it in PLAYBACK_MODES } ?: "sequence"
                if (allowPlayback) playbackRequests.tryEmit(ExternalNowVoicingPlaybackRequest(requested, ExternalNowVoicingBus.state.value.activeSection))
                return true
            }
            "show" -> Unit
            else -> return false
        }

        val stateJson = json.optJSONObject("state") ?: json
        val legacyDialog = stateJson.optBoolean("dialog", false) ||
            stateJson.optString("mode", "single").equals("dialog", ignoreCase = true)
        val legacyTop = stateJson.optJSONObject("top")
        val legacyBottom = stateJson.optJSONObject("bottom")
        val next = if (legacyTop != null || legacyBottom != null) {
            ExternalNowVoicingState(
                dialog = legacyDialog,
                activeSpeaker = speaker(stateJson.optString("activeSpeaker", stateJson.optString("active", "top"))),
                maroonSpeaker = speaker(stateJson.optString("maroonSpeaker", stateJson.optString("maroon", "bottom"))),
                top = legacyTop?.toSection("Speaker A") ?: ExternalNowVoicingBus.state.value.top,
                bottom = legacyBottom?.toSection("Speaker B") ?: ExternalNowVoicingBus.state.value.bottom
            )
        } else {
            val current = ExternalNowVoicingBus.state.value
            val targetSpeaker = speaker(stateJson.optString("speaker", "top"))
            val section = (stateJson.optJSONObject("section") ?: stateJson).toSection(
                if (targetSpeaker == "bottom") "Speaker B" else "Speaker A"
            )
            val dialog = stateJson.optBoolean("dialog", false) ||
                stateJson.optString("mode", "single").equals("dialog", ignoreCase = true)
            if (targetSpeaker == "bottom") {
                current.copy(
                    dialog = dialog,
                    activeSpeaker = speaker(stateJson.optString("activeSpeaker", stateJson.optString("active", "bottom"))),
                    maroonSpeaker = speaker(stateJson.optString("maroonSpeaker", stateJson.optString("maroon", current.maroonSpeaker))),
                    bottom = section
                )
            } else {
                current.copy(
                    dialog = dialog,
                    activeSpeaker = speaker(stateJson.optString("activeSpeaker", stateJson.optString("active", "top"))),
                    maroonSpeaker = speaker(stateJson.optString("maroonSpeaker", stateJson.optString("maroon", current.maroonSpeaker))),
                    top = section
                )
            }
        }
        ExternalNowVoicingBus.publish(next, focus)
        val playback = json.optString("play", "none").lowercase()
        if (allowPlayback && playback in PLAYBACK_MODES) {
            playbackRequests.tryEmit(ExternalNowVoicingPlaybackRequest(playback, next.activeSection))
        }
        return true
    }

    fun flatPayload(
        speaker: String,
        dialog: Boolean,
        maroon: String,
        label: String,
        source: String,
        target: String,
        literal: String?,
        lang: String?,
        position: String?,
        words: String?
    ): JSONObject = JSONObject().apply {
        put("op", "show")
        put("state", JSONObject().apply {
            put("speaker", speaker)
            put("dialog", dialog)
            put("maroon", maroon)
            put("section", JSONObject().apply {
                put("label", label)
                put("source", source)
                put("target", target)
                literal?.let { put("literal", it) }
                lang?.let { put("lang", it) }
                position?.let { put("position", it) }
                words?.let { put("words", runCatching { JSONArray(it) }.getOrNull() ?: JSONArray()) }
            })
        })
    }

    private fun JSONObject.toSection(defaultLabel: String): ExternalVoiceSection = ExternalVoiceSection(
        label = optString("label", defaultLabel),
        en = optString("source", optString("en", optString("english", ""))),
        pl = optString("target", optString("pl", optString("polish", ""))),
        literal = optString("literal").takeIf { it.isNotBlank() },
        lang = normalizeLang(optString("lang", "pl")),
        position = optString("position").takeIf { it.isNotBlank() },
        words = optJSONArray("words")?.toWords()
    )

    private fun JSONArray.toWords(): List<TokenPair> = buildList {
        for (index in 0 until length()) {
            val item = optJSONObject(index) ?: continue
            val target = item.optString("target", item.optString("pl")).takeIf { it.isNotBlank() } ?: continue
            val source = item.optString("source", item.optString("en", item.optString("gloss"))).takeIf { it.isNotBlank() } ?: continue
            add(
                TokenPair(
                    pl = target,
                    en = source,
                    gender = item.optString("gender").takeIf { it.isNotBlank() },
                    caseKey = item.optString("caseKey").takeIf { it.isNotBlank() },
                    caseLabel = item.optString("caseLabel").takeIf { it.isNotBlank() },
                    numberLabel = item.optString("numberLabel").takeIf { it.isNotBlank() },
                    variableStart = if (item.has("variableStart") && !item.isNull("variableStart")) item.optInt("variableStart") else null,
                    variableEnd = if (item.has("variableEnd") && !item.isNull("variableEnd")) item.optInt("variableEnd") else null,
                    variableKind = item.optString("variableKind").takeIf { it.isNotBlank() }
                )
            )
        }
    }

    private fun speaker(value: String): String = if (value.equals("bottom", true) || value.equals("b", true)) "bottom" else "top"

    private fun normalizeLang(value: String): String = when (value.lowercase()) {
        "en", "pl", "pl-slow", "pause" -> value.lowercase()
        else -> "pl"
    }

    private val PLAYBACK_MODES = setOf("source", "target", "sequence")
}
