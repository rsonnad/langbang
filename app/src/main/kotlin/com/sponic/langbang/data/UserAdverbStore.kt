package com.sponic.langbang.data

import android.content.Context
import com.sponic.langbang.data.model.AdverbEntry
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

/**
 * On-device persistence for adverbs the user adds via the "+ Add adverb" flow.
 * Mirrors [UserAdjectiveStore] — file lives at filesDir/user-adverbs.json, merged into
 * the built-in Lesson 4 list on every load.
 */
class UserAdverbStore(context: Context) {

    private val file = File(context.filesDir, "user-adverbs.json")
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    private val serializer = ListSerializer(AdverbEntry.serializer())

    fun load(): List<AdverbEntry> {
        if (!file.exists()) return emptyList()
        return runCatching { json.decodeFromString(serializer, file.readText()) }.getOrDefault(emptyList())
    }

    fun add(entry: AdverbEntry) {
        val current = load().toMutableList()
        current.removeAll { it.lemma.equals(entry.lemma, ignoreCase = true) }
        current.add(entry)
        file.writeText(json.encodeToString(serializer, current))
    }
}
