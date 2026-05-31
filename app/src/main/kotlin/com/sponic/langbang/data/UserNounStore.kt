package com.sponic.langbang.data

import android.content.Context
import com.sponic.langbang.data.model.NounEntry
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

/**
 * On-device persistence for nouns the user adds via the "+ Add noun" flow.
 * Mirrors [UserAdjectiveStore] — file lives at filesDir/user-nouns.json, merged into the
 * built-in Lesson 6 list on every load.
 */
class UserNounStore(context: Context) {

    private val file = File(context.filesDir, "user-nouns.json")
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    private val serializer = ListSerializer(NounEntry.serializer())

    fun load(): List<NounEntry> {
        if (!file.exists()) return emptyList()
        return runCatching { json.decodeFromString(serializer, file.readText()) }.getOrDefault(emptyList())
    }

    fun add(entry: NounEntry) {
        val current = load().toMutableList()
        current.removeAll { it.lemma.equals(entry.lemma, ignoreCase = true) }
        current.add(entry)
        file.writeText(json.encodeToString(serializer, current))
    }
}
