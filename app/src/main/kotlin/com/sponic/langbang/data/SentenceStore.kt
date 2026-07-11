package com.sponic.langbang.data

import android.content.Context
import com.sponic.langbang.data.model.SentenceExample
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import java.io.File

/**
 * On-device cache of Gemini-generated example sentences keyed by lowercase lemma.
 * Replaces the three byte-identical AdjectiveSentenceStore / AdverbSentenceStore /
 * NounSentenceStore classes. [VerbSentenceStore] stays separate — it adds a tense
 * dimension (composite key) plus legacy bare-lemma back-compat.
 *
 * The @Volatile memo is load-bearing: pre-memo, every [get] re-read + re-parsed the whole
 * file, and the prefetch / regen paths hammered it hundreds of times at app start — the
 * 2026-05-28 ANR. Parse is idempotent so the double-checked lock is safe.
 */
class SentenceStore(
    context: Context,
    private val fileName: () -> String
) {

    private val directory = context.filesDir
    private val serializer = MapSerializer(
        String.serializer(),
        ListSerializer(SentenceExample.serializer())
    )

    @Volatile private var memo: MutableMap<String, List<SentenceExample>>? = null
    @Volatile private var memoFileName: String? = null

    private fun file(): File = File(directory, fileName())

    private fun loadAll(): MutableMap<String, List<SentenceExample>> {
        val file = file()
        memo?.takeIf { memoFileName == file.name }?.let { return it }
        return synchronized(this) {
            memo?.takeIf { memoFileName == file.name } ?: run {
                val loaded = if (!file.exists()) mutableMapOf()
                else runCatching {
                    LbJson.pretty.decodeFromString(serializer, file.readText()).toMutableMap()
                }.getOrDefault(mutableMapOf())
                memo = loaded
                memoFileName = file.name
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
            file().writeText(LbJson.pretty.encodeToString(serializer, all))
        }
    }

    fun clearAll() {
        synchronized(this) {
            memo = null
            memoFileName = null
            val file = file()
            if (file.exists()) file.delete()
        }
    }
}
