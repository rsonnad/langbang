package com.sponic.langbang.data

import android.content.Context
import com.sponic.langbang.data.model.VerbEntry
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

/**
 * On-device persistence for verbs the user adds via the "+ Add verb" flow.
 *
 * Stored as JSON in filesDir/user-verbs.json so it survives across launches and is
 * merged into the lesson by [LessonRepository] on every load.
 */
class UserVerbStore(context: Context) {

    private val file = File(context.filesDir, "user-verbs.json")
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    private val serializer = ListSerializer(VerbEntry.serializer())

    fun load(): List<VerbEntry> {
        if (!file.exists()) return emptyList()
        return runCatching { json.decodeFromString(serializer, file.readText()) }.getOrDefault(emptyList())
    }

    fun add(entry: VerbEntry) {
        val current = load().toMutableList()
        current.removeAll { it.lemma.equals(entry.lemma, ignoreCase = true) }
        current.add(entry)
        file.writeText(json.encodeToString(serializer, current))
    }
}
