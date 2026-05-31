package com.sponic.langbang.data

import android.content.Context
import com.sponic.langbang.data.model.SentenceExample
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.File

/**
 * On-device cache for the example sentences we generate per verb, per tense. The map key
 * is "$lemma#$tense" (e.g. "być#present", "być#past"). Older builds wrote bare "lemma"
 * keys (always present-tense) — those are read as present on load so existing caches
 * survive upgrade, and we migrate them lazily the next time the user generates.
 */
class VerbSentenceStore(context: Context) {

    private val file = File(context.filesDir, "verb-sentences.json")
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    private val serializer = MapSerializer(
        String.serializer(),
        ListSerializer(SentenceExample.serializer())
    )

    /**
     * In-memory snapshot of the on-disk map. Lazily filled on first read; mutated
     * in place on [put] so we never re-parse on subsequent reads in the same
     * session. Pre-memo, every call to [get] re-read + re-parsed the 1.3 MB file
     * — `PrefetchService.prefetchLesson1` alone called this ~50× during the
     * units build (~65 MB of redundant parsing) and `SentenceRegenService`
     * hammered it 124× more (~150 MB), all concurrently with main-thread
     * Compose work at app start. That was the 2026-05-28 ANR root cause.
     * @Volatile + synchronized double-check is fine: parse is idempotent.
     */
    @Volatile private var memo: MutableMap<String, List<SentenceExample>>? = null

    private fun loadAll(): MutableMap<String, List<SentenceExample>> {
        memo?.let { return it }
        return synchronized(this) {
            memo ?: run {
                val loaded = if (!file.exists()) mutableMapOf()
                else runCatching {
                    json.decodeFromString(serializer, file.readText()).toMutableMap()
                }.getOrDefault(mutableMapOf())
                memo = loaded
                loaded
            }
        }
    }

    fun get(lemma: String, tense: String = TENSE_PRESENT): List<SentenceExample> {
        val all = loadAll()
        val key = compositeKey(lemma, tense)
        all[key]?.let { return it }
        // Back-compat: pre-tense builds stored present-tense sentences under bare lemma.
        if (tense == TENSE_PRESENT) {
            all[lemma.lowercase()]?.let { return it }
        }
        return emptyList()
    }

    fun put(lemma: String, tense: String, sentences: List<SentenceExample>) {
        synchronized(this) {
            val all = loadAll()
            all[compositeKey(lemma, tense)] = sentences
            // If the legacy bare key still exists and we're saving present, drop it so
            // the tense-aware key is canonical going forward.
            if (tense == TENSE_PRESENT) all.remove(lemma.lowercase())
            file.writeText(json.encodeToString(serializer, all))
        }
    }

    /** Wipes the entire cache — used by the Settings "Regenerate all sentences" action. */
    fun clearAll() {
        synchronized(this) {
            memo = null
            if (file.exists()) file.delete()
        }
    }

    private fun compositeKey(lemma: String, tense: String): String =
        "${lemma.lowercase()}#$tense"

    companion object {
        const val TENSE_PRESENT = "present"
        const val TENSE_PAST = "past"
    }
}
