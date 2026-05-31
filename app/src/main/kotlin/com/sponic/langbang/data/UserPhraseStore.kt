package com.sponic.langbang.data

import android.content.Context
import com.sponic.langbang.data.model.PhraseGroup
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

/**
 * On-device persistence for custom phrase groups the user adds via the Phrases tab.
 * Stored as a JSON array of [PhraseGroup] at filesDir/user-phrases.json; merged ahead
 * of the bundled Lesson 5 groups so the user's own additions appear first.
 *
 * `id` is the dedupe key (case-insensitive). Replacing an entry with the same id
 * overwrites — useful for "edit then save" flows.
 */
class UserPhraseStore(context: Context) {

    private val file = File(context.filesDir, "user-phrases.json")
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    private val serializer = ListSerializer(PhraseGroup.serializer())

    fun load(): List<PhraseGroup> {
        if (!file.exists()) return emptyList()
        return runCatching { json.decodeFromString(serializer, file.readText()) }
            .getOrDefault(emptyList())
    }

    fun add(group: PhraseGroup) {
        val current = load().toMutableList()
        current.removeAll { it.id.equals(group.id, ignoreCase = true) }
        current.add(group)
        file.writeText(json.encodeToString(serializer, current))
    }

    fun remove(id: String) {
        val current = load().filterNot { it.id.equals(id, ignoreCase = true) }
        file.writeText(json.encodeToString(serializer, current))
    }
}
