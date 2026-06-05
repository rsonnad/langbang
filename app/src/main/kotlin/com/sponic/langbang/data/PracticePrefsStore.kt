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
     * verbs included (in random order) when Play / the quizzes run across multiple
     * verbs. Empty set = nothing ticked → fall back to the single selected verb.
     */
    fun checkedVerbs(): Set<String> =
        prefs.getStringSet(KEY_CHECKED_VERBS, emptySet())?.toSet() ?: emptySet()

    fun setCheckedVerbs(lemmas: Set<String>) {
        // Store a defensive copy — SharedPreferences must not be handed a set it might
        // later mutate, and getStringSet returns a live instance otherwise.
        prefs.edit().putStringSet(KEY_CHECKED_VERBS, HashSet(lemmas)).apply()
    }

    fun hasCheckedWordLemmas(category: String): Boolean =
        prefs.contains(checkedWordKey(category))

    fun checkedWordLemmas(category: String): Set<String> =
        prefs.getStringSet(checkedWordKey(category), emptySet())?.toSet() ?: emptySet()

    fun setCheckedWordLemmas(category: String, lemmas: Set<String>) {
        prefs.edit().putStringSet(checkedWordKey(category), HashSet(lemmas)).apply()
    }

    fun wordPlayLimit(category: String): Int =
        prefs.getInt(wordPlayLimitKey(category), DEFAULT_WORD_PLAY_LIMIT)
            .coerceIn(MIN_WORD_PLAY_LIMIT, MAX_WORD_PLAY_LIMIT)

    fun setWordPlayLimit(category: String, count: Int) {
        prefs.edit()
            .putInt(wordPlayLimitKey(category), count.coerceIn(MIN_WORD_PLAY_LIMIT, MAX_WORD_PLAY_LIMIT))
            .apply()
    }

    fun wordPlayRandom(category: String): Boolean =
        prefs.getBoolean(wordPlayRandomKey(category), false)

    fun setWordPlayRandom(category: String, enabled: Boolean) {
        prefs.edit().putBoolean(wordPlayRandomKey(category), enabled).apply()
    }

    fun verbPhraseIncludePronouns(): Boolean =
        prefs.getBoolean(KEY_VERB_PHRASE_INCLUDE_PRONOUNS, true)

    fun setVerbPhraseIncludePronouns(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_VERB_PHRASE_INCLUDE_PRONOUNS, enabled).apply()
    }

    fun verbPhraseIncludeHelperVerb(): Boolean =
        prefs.getBoolean(KEY_VERB_PHRASE_INCLUDE_HELPER_VERB, false)

    fun setVerbPhraseIncludeHelperVerb(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_VERB_PHRASE_INCLUDE_HELPER_VERB, enabled).apply()
    }

    fun verbPhraseIncludeAdjectives(): Boolean =
        prefs.getBoolean(KEY_VERB_PHRASE_INCLUDE_ADJECTIVES, true)

    fun setVerbPhraseIncludeAdjectives(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_VERB_PHRASE_INCLUDE_ADJECTIVES, enabled).apply()
    }

    fun verbPhraseIncludeAdverbs(): Boolean =
        prefs.getBoolean(KEY_VERB_PHRASE_INCLUDE_ADVERBS, true)

    fun setVerbPhraseIncludeAdverbs(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_VERB_PHRASE_INCLUDE_ADVERBS, enabled).apply()
    }

    fun verbPhraseIncludeNouns(): Boolean =
        prefs.getBoolean(KEY_VERB_PHRASE_INCLUDE_NOUNS, true)

    fun setVerbPhraseIncludeNouns(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_VERB_PHRASE_INCLUDE_NOUNS, enabled).apply()
    }

    fun rejectedVerbPhraseKeys(): Set<String> =
        prefs.getStringSet(KEY_REJECTED_VERB_PHRASES, emptySet())?.toSet() ?: emptySet()

    fun addRejectedVerbPhraseKey(key: String) {
        val updated = rejectedVerbPhraseKeys() + key
        prefs.edit().putStringSet(KEY_REJECTED_VERB_PHRASES, HashSet(updated)).apply()
    }

    companion object {
        private const val KEY_SLOW_FIRST = "slow-first"
        private const val KEY_CHECKED_VERBS = "checked-verbs"
        private const val KEY_VERB_PHRASE_INCLUDE_PRONOUNS = "verb-phrase-include-pronouns"
        private const val KEY_VERB_PHRASE_INCLUDE_HELPER_VERB = "verb-phrase-include-helper-verb"
        private const val KEY_VERB_PHRASE_INCLUDE_ADJECTIVES = "verb-phrase-include-adjectives"
        private const val KEY_VERB_PHRASE_INCLUDE_ADVERBS = "verb-phrase-include-adverbs"
        private const val KEY_VERB_PHRASE_INCLUDE_NOUNS = "verb-phrase-include-nouns"
        private const val KEY_REJECTED_VERB_PHRASES = "rejected-verb-phrases"
        const val CATEGORY_VERBS = "verbs"
        const val CATEGORY_ADJECTIVES = "adjectives"
        const val CATEGORY_ADVERBS = "adverbs"
        const val CATEGORY_NOUNS = "nouns"
        const val DEFAULT_WORD_PLAY_LIMIT = 3
        const val MIN_WORD_PLAY_LIMIT = 1
        const val MAX_WORD_PLAY_LIMIT = 99

        private fun checkedWordKey(category: String) = "checked-words-$category"
        private fun wordPlayLimitKey(category: String) = "word-play-limit-$category"
        private fun wordPlayRandomKey(category: String) = "word-play-random-$category"
    }
}
