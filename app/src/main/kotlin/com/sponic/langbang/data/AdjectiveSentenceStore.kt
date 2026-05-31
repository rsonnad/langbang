package com.sponic.langbang.data

import android.content.Context
import com.sponic.langbang.data.model.SentenceExample
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.File

/**
 * On-device cache of Gemini-generated example sentences for each adjective. Mirrors
 * [VerbSentenceStore] — keyed by lowercase lemma, stored at filesDir/adjective-sentences.json.
 */
class AdjectiveSentenceStore(context: Context) {

    private val file = File(context.filesDir, "adjective-sentences.json")
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    private val serializer = MapSerializer(
        String.serializer(),
        ListSerializer(SentenceExample.serializer())
    )

    // See VerbSentenceStore for the memo rationale — same ANR class.
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

    fun get(lemma: String): List<SentenceExample> =
        loadAll()[lemma.lowercase()].orEmpty()

    fun put(lemma: String, sentences: List<SentenceExample>) {
        synchronized(this) {
            val all = loadAll()
            all[lemma.lowercase()] = sentences
            file.writeText(json.encodeToString(serializer, all))
        }
    }

    fun clearAll() {
        synchronized(this) {
            memo = null
            if (file.exists()) file.delete()
        }
    }
}
