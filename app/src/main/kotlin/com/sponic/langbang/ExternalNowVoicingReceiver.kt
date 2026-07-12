package com.sponic.langbang

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.sponic.langbang.domain.ExternalNowVoicingBus
import com.sponic.langbang.domain.ExternalNowVoicingCommandController

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
            ExternalNowVoicingCommandController.applyJson(payload, focus)
        } else {
            publishFlat(intent, focus)
        }
    }

    private fun publishFlat(intent: Intent, focus: Boolean) {
        val speaker = intent.getStringExtra("speaker") ?: "top"
        val dialog = intent.getBooleanExtra("dialog", false) ||
            intent.getStringExtra("mode").equals("dialog", ignoreCase = true)
        ExternalNowVoicingCommandController.applyJson(
            ExternalNowVoicingCommandController.flatPayload(
                speaker = speaker,
                dialog = dialog,
                maroon = intent.getStringExtra("maroon") ?: "bottom",
                label = intent.getStringExtra("label") ?: if (speaker.equals("bottom", true)) "Speaker B" else "Speaker A",
                source = intent.getStringExtra("en").orEmpty(),
                target = intent.getStringExtra("pl").orEmpty(),
                literal = intent.getStringExtra("literal"),
                lang = intent.getStringExtra("lang"),
                position = intent.getStringExtra("position"),
                words = intent.getStringExtra("words")
            ),
            focus
        )
    }

    companion object {
        const val ACTION_DRIVE_NOW_VOICING = "com.sponic.langbangml.DRIVE_NOW_VOICING"
        private const val EXTRA_CLEAR = "clear"
        private const val EXTRA_FOCUS = "focus"
        private const val EXTRA_PAYLOAD = "payload"
    }
}
