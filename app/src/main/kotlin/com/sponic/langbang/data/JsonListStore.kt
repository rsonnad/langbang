package com.sponic.langbang.data

import android.content.Context
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import java.io.File

/**
 * Generic on-device JSON persistence for a list of user-added entries (verbs, adjectives,
 * adverbs, nouns, phrase groups). Replaces the five near-identical User*Store classes,
 * which differed only by element type, file name, and dedupe key.
 *
 * [keyOf] supplies the case-insensitive identity key: [add] replaces any existing entry
 * with the same key (so "edit then save" overwrites) and appends new ones, preserving the
 * original stores' ordering; [remove] drops by key.
 */
class JsonListStore<T>(
    context: Context,
    fileName: String,
    serializer: KSerializer<T>,
    private val keyOf: (T) -> String,
) {
    private val file = File(context.filesDir, fileName)
    private val listSerializer = ListSerializer(serializer)

    fun load(): List<T> {
        if (!file.exists()) return emptyList()
        return runCatching { LbJson.pretty.decodeFromString(listSerializer, file.readText()) }
            .getOrDefault(emptyList())
    }

    fun add(entry: T) {
        val current = load().toMutableList()
        current.removeAll { keyOf(it).equals(keyOf(entry), ignoreCase = true) }
        current.add(entry)
        file.writeText(LbJson.pretty.encodeToString(listSerializer, current))
    }

    fun remove(key: String) {
        val current = load().filterNot { keyOf(it).equals(key, ignoreCase = true) }
        file.writeText(LbJson.pretty.encodeToString(listSerializer, current))
    }
}
