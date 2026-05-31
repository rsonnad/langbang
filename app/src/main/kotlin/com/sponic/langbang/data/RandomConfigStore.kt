package com.sponic.langbang.data

import android.content.Context

/**
 * Persisted choices for the "Play random" config sheet. SharedPreferences so the user's
 * matrix selections survive app restart. Stored as comma-separated sets for sets, raw
 * strings for scalars.
 *
 * Defaults match the pre-config behaviour: 1sg/2sg/3sg only, present tense, adjectives
 * yes, adverbs off, no preposition filter, no must-contain word.
 */
class RandomConfigStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("random-config", Context.MODE_PRIVATE)

    fun load(): RandomConfig = RandomConfig(
        mustContainWord = prefs.getString(KEY_MUST_CONTAIN, "").orEmpty(),
        personKeys = readSet(KEY_PERSONS, DEFAULT_PERSONS),
        tenses = readSet(KEY_TENSES, DEFAULT_TENSES),
        prepositions = readSet(KEY_PREPS, emptySet()),
        adjectiveMode = readMode(KEY_ADJ_MODE, IncludeMode.YES),
        adverbMode = readMode(KEY_ADV_MODE, IncludeMode.OFF),
        playMode = readPlayMode(KEY_PLAY_MODE, PlayMode.PHRASES),
        quizDelaySeconds = prefs.getFloat(KEY_QUIZ_DELAY, 1.5f)
    )

    fun save(config: RandomConfig) {
        prefs.edit()
            .putString(KEY_MUST_CONTAIN, config.mustContainWord)
            .putString(KEY_PERSONS, config.personKeys.joinToString(","))
            .putString(KEY_TENSES, config.tenses.joinToString(","))
            .putString(KEY_PREPS, config.prepositions.joinToString(","))
            .putString(KEY_ADJ_MODE, config.adjectiveMode.name)
            .putString(KEY_ADV_MODE, config.adverbMode.name)
            .putString(KEY_PLAY_MODE, config.playMode.name)
            .putFloat(KEY_QUIZ_DELAY, config.quizDelaySeconds)
            .apply()
    }

    private fun readSet(key: String, default: Set<String>): Set<String> {
        val raw = prefs.getString(key, null) ?: return default
        return raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    }

    private fun readMode(key: String, default: IncludeMode): IncludeMode {
        val raw = prefs.getString(key, null) ?: return default
        return runCatching { IncludeMode.valueOf(raw) }.getOrDefault(default)
    }

    private fun readPlayMode(key: String, default: PlayMode): PlayMode {
        val raw = prefs.getString(key, null) ?: return default
        return runCatching { PlayMode.valueOf(raw) }.getOrDefault(default)
    }

    companion object {
        private const val KEY_MUST_CONTAIN = "must-contain"
        private const val KEY_PERSONS = "persons"
        private const val KEY_TENSES = "tenses"
        private const val KEY_PREPS = "preps"
        private const val KEY_ADJ_MODE = "adj-mode"
        private const val KEY_ADV_MODE = "adv-mode"
        private const val KEY_PLAY_MODE = "play-mode"
        private const val KEY_QUIZ_DELAY = "quiz-delay-seconds"
        val DEFAULT_PERSONS = setOf("1sg", "2sg", "3sg")
        val DEFAULT_TENSES = setOf("present")
    }
}

/** Tri-state include mode for category-wide toggles (adjectives, adverbs). */
enum class IncludeMode {
    /** Don't include sentences from this category. */
    OFF,

    /** Include normally — sentences are eligible the same way verb sentences are. */
    YES,

    /**
     * Cycle through every word in the category so each sentence uses a different word
     * (e.g. every adjective gets one example before any repeats). When some words have
     * no cached sentences, the player offers to generate them first.
     */
    ALL,
}

/**
 * What "Play Phrases" actually plays. PHRASES → Gemini-generated example sentences (the
 * historical behavior). VERBS → just the pronoun + verb conjugation, no surrounding
 * sentence ("ja jestem", "ty jesteś"). Adj/Adv toggles in the now-voicing panel only
 * apply in PHRASES mode.
 */
enum class PlayMode {
    VERBS,
    PHRASES,
}

/**
 * Snapshot of every user choice that gates the random play queue. All filters compose
 * as ANDs across categories. Within a multi-select category (persons, tenses, prepositions)
 * a sentence passes if it matches ANY of the selected values.
 */
data class RandomConfig(
    val mustContainWord: String = "",
    val personKeys: Set<String> = RandomConfigStore.DEFAULT_PERSONS,
    val tenses: Set<String> = RandomConfigStore.DEFAULT_TENSES,
    val prepositions: Set<String> = emptySet(),
    val adjectiveMode: IncludeMode = IncludeMode.YES,
    val adverbMode: IncludeMode = IncludeMode.OFF,
    val playMode: PlayMode = PlayMode.PHRASES,
    /** Seconds the quiz pauses between English audio → reveal Polish, and reveal → speak. */
    val quizDelaySeconds: Float = 1.5f,
) {
    companion object {
        /** Top-5 most common Polish prepositions for the chip row. */
        val PREPOSITIONS = listOf("w", "na", "do", "z", "o")
        val TENSES = listOf("present", "past")
        val PERSONS = listOf("1sg", "2sg", "3sg", "1pl", "2pl", "3pl")
    }
}
