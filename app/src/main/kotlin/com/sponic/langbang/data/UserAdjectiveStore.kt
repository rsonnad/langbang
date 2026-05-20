package com.sponic.langbang.data

import android.content.Context
import com.sponic.langbang.data.model.AdjectiveEntry
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

/**
 * On-device persistence for adjectives the user adds via the "+ Add adjective" flow.
 * Mirrors [UserVerbStore] — file lives at filesDir/user-adjectives.json, merged into the
 * built-in Lesson 3 list on every load.
 */
class UserAdjectiveStore(context: Context) {

    private val file = File(context.filesDir, "user-adjectives.json")
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    private val serializer = ListSerializer(AdjectiveEntry.serializer())

    fun load(): List<AdjectiveEntry> {
        if (!file.exists()) return emptyList()
        return runCatching { json.decodeFromString(serializer, file.readText()) }.getOrDefault(emptyList())
    }

    fun add(entry: AdjectiveEntry) {
        val current = load().toMutableList()
        current.removeAll { it.lemma.equals(entry.lemma, ignoreCase = true) }
        current.add(entry)
        file.writeText(json.encodeToString(serializer, current))
    }
}
