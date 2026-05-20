package com.sponic.langbang.data

import android.content.Context
import com.sponic.langbang.data.model.SentenceExample
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.File

/**
 * On-device cache for the 5 example sentences we generate per verb. Keyed by lowercase
 * lemma so it works for both built-in lesson verbs and user-added ones. Stored as JSON in
 * filesDir/verb-sentences.json so generation only happens once per verb.
 */
class VerbSentenceStore(context: Context) {

    private val file = File(context.filesDir, "verb-sentences.json")
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    private val serializer = MapSerializer(
        String.serializer(),
        ListSerializer(SentenceExample.serializer())
    )

    private fun loadAll(): MutableMap<String, List<SentenceExample>> {
        if (!file.exists()) return mutableMapOf()
        return runCatching {
            json.decodeFromString(serializer, file.readText()).toMutableMap()
        }.getOrDefault(mutableMapOf())
    }

    fun get(lemma: String): List<SentenceExample> =
        loadAll()[lemma.lowercase()].orEmpty()

    fun put(lemma: String, sentences: List<SentenceExample>) {
        val all = loadAll()
        all[lemma.lowercase()] = sentences
        file.writeText(json.encodeToString(serializer, all))
    }
}
