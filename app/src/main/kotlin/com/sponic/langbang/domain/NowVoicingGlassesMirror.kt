package com.sponic.langbang.domain

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import com.sponic.langbang.data.model.TokenPair
import com.sponic.langbang.ui.common.polishEnglishPronunciationGuide

/** Sends the active learning phrase to the separately-installed Even G2 companion. */
class NowVoicingGlassesMirror(private val context: Context) {
    private var mirrorStartedByThisProcess = false

    fun publish(now: NowVoicing?, enabled: Boolean) {
        // StateFlow emits its initial null as soon as LangBang starts. Do not start
        // the companion service merely to clear a mirror this process never opened;
        // Android would then wait for an unnecessary foreground promotion and ANR it.
        if (!enabled && !mirrorStartedByThisProcess) return
        // A queue naturally clears NowVoicing between phrases. Keep the proven G2
        // heartbeat/page-refresh session alive through those gaps; only disabling
        // the Settings checkbox tears the connection down.
        if (enabled && now == null) return
        val intent = Intent().apply {
            component = ComponentName(G2_PACKAGE, G2_SERVICE)
            action = if (!enabled) ACTION_CLEAR else ACTION_MIRROR
            if (now != null) putExtra(EXTRA_TEXT, now.toG2HudText())
        }
        // The G2 app is optional. A missing or older companion must never disrupt
        // normal learning audio/UI.
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && enabled) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }.onSuccess {
            mirrorStartedByThisProcess = enabled
        }
    }

    private companion object {
        const val G2_PACKAGE = "com.sponic.langbangtrans"
        const val G2_SERVICE = "com.sponic.langbangtrans.BridgeService"
        const val ACTION_MIRROR = "com.sponic.langbangtrans.MIRROR_NOW_VOICING"
        const val ACTION_CLEAR = "com.sponic.langbangtrans.CLEAR_NOW_VOICING_MIRROR"
        const val EXTRA_TEXT = "com.sponic.langbangtrans.extra.TEXT"
    }
}

/** Six-line G2 layout mirroring the phone's Now Voicing information hierarchy. */
internal fun NowVoicing.toG2HudText(): String {
    val alignedWords = alignedWords()
    val polishLine = alignedWords.joinToString(" ") { it.polish.padEnd(it.width) }.trimEnd()
    val phoneticLine = alignedWords.joinToString(" ") { it.phonetic.padEnd(it.width) }.trimEnd()
    val glossLine = alignedWords.joinToString(" ") { it.gloss.padEnd(it.width) }.trimEnd()
    return listOf(
        en,
        "",
        polishLine.ifBlank { pl },
        phoneticLine.ifBlank { polishEnglishPronunciationGuide(pl) },
        "",
        glossLine.ifBlank { literal.orEmpty() },
    ).joinToString("\n")
}

private data class G2AlignedWord(
    val polish: String,
    val phonetic: String,
    val gloss: String,
) {
    val width: Int = maxOf(polish.length, phonetic.length, gloss.length)
}

private fun NowVoicing.alignedWords(): List<G2AlignedWord> {
    val pairs = words?.takeIf { it.isNotEmpty() } ?: fallbackTokenPairs()
    return pairs.map { pair ->
        G2AlignedWord(
            polish = pair.pl,
            phonetic = polishEnglishPronunciationGuide(pair.pl),
            gloss = pair.en,
        )
    }
}

private fun NowVoicing.fallbackTokenPairs(): List<TokenPair> {
    val polishTokens = pl.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    val glossTokens = literal.orEmpty().trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    return polishTokens.mapIndexed { index, polish ->
        TokenPair(pl = polish, en = glossTokens.getOrNull(index).orEmpty())
    }
}
