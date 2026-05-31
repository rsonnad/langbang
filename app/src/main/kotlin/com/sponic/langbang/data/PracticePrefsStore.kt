package com.sponic.langbang.data

import android.content.Context

class PracticePrefsStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("practice-prefs", Context.MODE_PRIVATE)

    fun slowFirst(): Boolean = prefs.getBoolean(KEY_SLOW_FIRST, true)

    fun setSlowFirst(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SLOW_FIRST, enabled).apply()
    }

    /**
     * Sticky set of verb lemmas the learner has ticked in the Verbs list. These are the
     * verbs included (in random order) when Play all / the quizzes run across multiple
     * verbs. Empty set = nothing ticked → fall back to the single selected verb.
     */
    fun checkedVerbs(): Set<String> =
        prefs.getStringSet(KEY_CHECKED_VERBS, emptySet())?.toSet() ?: emptySet()

    fun setCheckedVerbs(lemmas: Set<String>) {
        // Store a defensive copy — SharedPreferences must not be handed a set it might
        // later mutate, and getStringSet returns a live instance otherwise.
        prefs.edit().putStringSet(KEY_CHECKED_VERBS, HashSet(lemmas)).apply()
    }

    companion object {
        private const val KEY_SLOW_FIRST = "slow-first"
        private const val KEY_CHECKED_VERBS = "checked-verbs"
    }
}
