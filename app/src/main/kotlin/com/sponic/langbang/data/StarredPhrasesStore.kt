package com.sponic.langbang.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Sticky set of "starred" phrases — the learner's personal study deck. A phrase is
 * keyed by its Polish text ([SentenceExample.pl]) since phrase sentences have no stable
 * id. Backed by SharedPreferences so stars survive launches; exposed as a [MutableStateFlow]
 * so Compose surfaces (the Now Voicing star button, the phrase list, the quiz filter)
 * recompose the instant a star toggles.
 */
class StarredPhrasesStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("starred-phrases", Context.MODE_PRIVATE)

    /** Live view of the starred Polish-text set. Collect in Compose for reactive stars. */
    val starred = MutableStateFlow(load())

    private fun load(): Set<String> =
        prefs.getStringSet(KEY, emptySet())?.toSet() ?: emptySet()

    fun isStarred(pl: String): Boolean = pl in starred.value

    fun toggle(pl: String) {
        if (pl.isBlank()) return
        val now = if (pl in starred.value) starred.value - pl else starred.value + pl
        persist(now)
    }

    fun setStarred(pl: String, on: Boolean) {
        if (pl.isBlank()) return
        val now = if (on) starred.value + pl else starred.value - pl
        persist(now)
    }

    fun replaceAll(set: Set<String>) {
        persist(set.filter { it.isNotBlank() }.toSet())
    }

    private fun persist(set: Set<String>) {
        // Defensive copy — SharedPreferences must not be handed a set it may later mutate,
        // and getStringSet returns a live instance otherwise.
        prefs.edit().putStringSet(KEY, HashSet(set)).apply()
        starred.value = set
    }

    companion object {
        private const val KEY = "starred-phrase-pls"
    }
}
