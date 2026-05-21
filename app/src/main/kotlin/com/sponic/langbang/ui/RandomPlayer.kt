package com.sponic.langbang.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.sponic.langbang.LangbangApplication
import com.sponic.langbang.data.IncludeMode
import com.sponic.langbang.data.RandomConfig
import com.sponic.langbang.data.model.SentenceExample
import com.sponic.langbang.domain.NowVoicing
import com.sponic.langbang.domain.NowVoicingBus
import com.sponic.langbang.integrations.AzureTtsClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Drives the top-level "Play random" button. Reads a [RandomConfig] to decide which
 * cached sentences feed the queue, then plays each EN → PL-slow → EN → PL.
 *
 * Filter semantics:
 *   - Verb sentences: include if any token equals one of the verb's conjugations for
 *     the selected person keys (1sg/2sg/3sg/…). Past tense is gated on but currently
 *     no data exists (tasks #7).
 *   - Preposition filter (when any selected): sentence must contain at least one of
 *     the selected prepositions as a standalone word.
 *   - Must-contain word: filtered by case-insensitive token match. The "regen if too
 *     few hits" prompt lands in task #10.
 *   - Adjectives:
 *       OFF — skip all adjective sentences
 *       YES — include adjective sentences that pass other filters
 *       ALL — round-robin one sentence per adjective so each appears once before any
 *             repeats
 *   - Adverbs: identical semantics, but no content exists yet (task #8).
 */
internal class RandomPlayerState(
    private val app: LangbangApplication,
    private val scope: CoroutineScope
) {
    private var job: Job? = null

    var playing: Boolean by mutableStateOf(false)
        private set
    var queueSize: Int by mutableStateOf(0)
        private set

    fun stop() {
        job?.cancel()
        job = null
        playing = false
        app.audioPlayer.stop()
        NowVoicingBus.clear()
    }

    fun start(config: RandomConfig) {
        if (job?.isActive == true) stop()
        val phrases = collectPhrases(config)
        queueSize = phrases.size
        if (phrases.isEmpty()) return
        playing = true
        job = scope.launch {
            try {
                phrases.forEachIndexed { i, s ->
                    val pos = "${i + 1}/${phrases.size}"
                    pub(s, "en", pos)
                    playAndAwait(s.en, AzureTtsClient.LOCALE_EN, AzureTtsClient.EN_US_F)
                    pub(s, "pl-slow", pos)
                    playAndAwait(s.pl, AzureTtsClient.LOCALE_PL,
                        AzureTtsClient.PL_PL_F_SLOW_V2)
                    pub(s, "en", pos)
                    playAndAwait(s.en, AzureTtsClient.LOCALE_EN, AzureTtsClient.EN_US_F)
                    pub(s, "pl", pos)
                    playAndAwait(s.pl, AzureTtsClient.LOCALE_PL, AzureTtsClient.PL_PL_F)
                }
            } finally {
                playing = false
                job = null
                NowVoicingBus.clear()
            }
        }
    }

    private fun pub(s: SentenceExample, lang: String, pos: String) {
        NowVoicingBus.publish(NowVoicing(s.en, s.pl, s.literal, lang, pos, s.words))
    }

    private suspend fun playAndAwait(text: String, locale: String, voice: String) {
        if (text.isEmpty()) return
        val file = app.audioCache.fileFor(locale, voice, text)
        if (!app.audioCache.has(file)) {
            app.tts.synthesize(text, voice, locale, file)
        }
        if (!app.audioCache.has(file)) return
        suspendCancellableCoroutine<Unit> { cont ->
            app.audioPlayer.play(file) { if (cont.isActive) cont.resume(Unit) }
            cont.invokeOnCancellation { app.audioPlayer.stop() }
        }
    }

    private fun collectPhrases(config: RandomConfig): List<SentenceExample> {
        val mustContain = config.mustContainWord.lowercase().takeIf { it.isNotEmpty() }
        val prepFilter = config.prepositions.takeIf { it.isNotEmpty() }
        val verbSentences = if ("present" in config.tenses) {
            collectVerbSentences(config.personKeys, mustContain, prepFilter)
        } else emptyList()

        val adjSentences = when (config.adjectiveMode) {
            IncludeMode.OFF -> emptyList()
            IncludeMode.YES -> collectAdjectiveSentencesFiltered(mustContain, prepFilter)
            IncludeMode.ALL -> collectAdjectivesRoundRobin(mustContain, prepFilter)
        }

        val advSentences: List<SentenceExample> = when (config.adverbMode) {
            IncludeMode.OFF -> emptyList()
            else -> emptyList() // task #8 — no content yet
        }

        val combined = (verbSentences + adjSentences + advSentences).distinctBy { it.pl }
        return if (config.adjectiveMode == IncludeMode.ALL) {
            // Don't shuffle when round-robin is in play — the cycle order is the point.
            combined
        } else {
            combined.shuffled()
        }
    }

    private fun collectVerbSentences(
        personKeys: Set<String>,
        mustContain: String?,
        prepFilter: Set<String>?
    ): List<SentenceExample> {
        val verbLesson = app.lessonRepo.lesson2()
        return verbLesson.verbs.flatMap { verb ->
            val allowedForms = verb.forms
                .filterKeys { it in personKeys }
                .values
                .filter { it.isNotBlank() }
                .map { it.lowercase() }
                .toSet()
            if (allowedForms.isEmpty()) emptyList()
            else app.lessonRepo.sentencesFor(verb.lemma)
                .filter { tokensMatch(it.pl, allowedForms) }
                .filter { passesPrepFilter(it.pl, prepFilter) }
                .filter { passesMustContain(it.pl, mustContain) }
        }
    }

    private fun collectAdjectiveSentencesFiltered(
        mustContain: String?,
        prepFilter: Set<String>?
    ): List<SentenceExample> {
        val adjLesson = app.lessonRepo.lesson3()
        return adjLesson.adjectives.flatMap { adj ->
            app.lessonRepo.adjectiveSentencesFor(adj.lemma)
                .filter { passesPrepFilter(it.pl, prepFilter) }
                .filter { passesMustContain(it.pl, mustContain) }
        }
    }

    /**
     * Round-robin: one sentence per adjective per pass. Cycles until every adjective's
     * eligible-sentence pool is exhausted. Result: every adjective with any sentences
     * gets equal airtime before anything repeats.
     */
    private fun collectAdjectivesRoundRobin(
        mustContain: String?,
        prepFilter: Set<String>?
    ): List<SentenceExample> {
        val adjLesson = app.lessonRepo.lesson3()
        val perAdj: List<List<SentenceExample>> = adjLesson.adjectives.map { adj ->
            app.lessonRepo.adjectiveSentencesFor(adj.lemma)
                .filter { passesPrepFilter(it.pl, prepFilter) }
                .filter { passesMustContain(it.pl, mustContain) }
                .shuffled()
        }.filter { it.isNotEmpty() }
        val maxLen = perAdj.maxOfOrNull { it.size } ?: 0
        return buildList {
            for (i in 0 until maxLen) {
                perAdj.forEach { pool ->
                    pool.getOrNull(i)?.let { add(it) }
                }
            }
        }
    }

    private fun tokensMatch(pl: String, allowed: Set<String>): Boolean {
        return pl.lowercase()
            .split(Regex("[^\\p{L}]+"))
            .any { it.isNotEmpty() && it in allowed }
    }

    private fun passesPrepFilter(pl: String, prepFilter: Set<String>?): Boolean {
        if (prepFilter == null) return true
        val tokens = pl.lowercase().split(Regex("[^\\p{L}]+"))
        return tokens.any { it in prepFilter }
    }

    private fun passesMustContain(pl: String, mustContain: String?): Boolean {
        if (mustContain == null) return true
        // Substring rather than exact-token so morphological variants ("kawa"/"kawę"/
        // "kawy") all match the stem. Crude but useful as a starting heuristic.
        return pl.lowercase().contains(mustContain)
    }
}
