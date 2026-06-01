package com.sponic.langbang.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.sponic.langbang.LangbangApplication
import com.sponic.langbang.data.IncludeMode
import com.sponic.langbang.data.PlayMode
import com.sponic.langbang.data.RandomConfig
import com.sponic.langbang.data.VerbSentenceStore
import com.sponic.langbang.data.model.SentenceExample
import com.sponic.langbang.domain.NowVoicing
import com.sponic.langbang.domain.NowVoicingBus
import com.sponic.langbang.domain.PlaybackController
import com.sponic.langbang.domain.PlaybackTransport
import com.sponic.langbang.domain.ensureCachedAudio
import com.sponic.langbang.domain.englishConjugate
import com.sponic.langbang.domain.playAudioAndAwait
import com.sponic.langbang.integrations.AzureTtsClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Drives the top-level "Play random" button. Reads a [RandomConfig] to decide which
 * cached sentences feed the queue, then plays each EN → PL-slow → EN → PL.
 *
 * Supports transport controls: pause/resume, rewind (jump to previous sentence),
 * and restart (jump back to sentence 1). All controls preserve the underlying
 * queue — only [stop] tears it down.
 *
 * Filter semantics:
 *   - Verb sentences: include if any token equals one of the verb's conjugations for
 *     the selected person keys (1sg/2sg/3sg/…) in any of the selected tenses
 *     (present and/or past). Present and past sentences are stored in tense-keyed
 *     buckets in VerbSentenceStore.
 *   - Preposition filter (when any selected): sentence must contain at least one of
 *     the selected prepositions as a standalone word.
 *   - Must-contain word: filtered by case-insensitive substring match so morphological
 *     variants all hit.
 *   - Adjectives:
 *       OFF — skip all adjective sentences
 *       YES — include adjective sentences that pass other filters
 *       ALL — round-robin one sentence per adjective so each appears once before any
 *             repeats
 *   - Adverbs: identical semantics to adjectives.
 */
internal class RandomPlayerState(
    private val app: LangbangApplication,
    private val scope: CoroutineScope
) {
    private var job: Job? = null
    private var phrases: List<SentenceExample> = emptyList()
    // currentIndex points at the sentence that's about to play (or paused on).
    // pause/rewind/restart cancel the job, mutate this, then either relaunch or
    // remain in `paused` state — so the next resume picks up exactly here.
    private var currentIndex: Int = 0

    var playing: Boolean by mutableStateOf(false)
        private set
    var paused: Boolean by mutableStateOf(false)
        private set
    var queueSize: Int by mutableStateOf(0)
        private set
    var position: Int by mutableStateOf(0)   // 1-based for UI ("12 / 439")
        private set

    val hasQueue: Boolean get() = phrases.isNotEmpty()

    fun stop() {
        job?.cancel()
        job = null
        playing = false
        paused = false
        phrases = emptyList()
        queueSize = 0
        position = 0
        currentIndex = 0
        app.audioPlayer.stop()
        NowVoicingBus.clear()
        PlaybackController.unregister()
    }

    fun start(config: RandomConfig) {
        if (job?.isActive == true) {
            job?.cancel()
            job = null
        }
        phrases = collectPhrases(config)
        queueSize = phrases.size
        currentIndex = 0
        position = if (phrases.isEmpty()) 0 else 1
        if (phrases.isEmpty()) {
            playing = false
            paused = false
            return
        }
        launchFromCurrent()
    }

    fun reconfigure(config: RandomConfig) {
        if (!playing && !paused) return
        val wasPaused = paused
        job?.cancel()
        job = null
        app.audioPlayer.stop()
        phrases = collectPhrases(config)
        queueSize = phrases.size
        currentIndex = 0
        position = if (phrases.isEmpty()) 0 else 1
        if (phrases.isEmpty()) {
            playing = false
            paused = false
            NowVoicingBus.clear()
            PlaybackController.unregister()
            return
        }
        if (wasPaused) {
            playing = false
            paused = true
            republishCurrent()
        } else {
            launchFromCurrent()
        }
    }

    fun pause() {
        if (!playing) return
        playing = false
        paused = true
        job?.cancel()
        job = null
        app.audioPlayer.stop()
    }

    fun resume() {
        if (!paused || phrases.isEmpty()) return
        launchFromCurrent()
    }

    /** Jump to the previous sentence and keep the existing play/pause state. */
    fun rewind() {
        if (phrases.isEmpty()) return
        val wasPlaying = playing
        job?.cancel()
        job = null
        app.audioPlayer.stop()
        currentIndex = (currentIndex - 1).coerceAtLeast(0)
        position = currentIndex + 1
        if (wasPlaying) {
            launchFromCurrent()
        } else {
            // Stay paused but at the new position. Republish the new phrase so the
            // sticky now-voicing panel reflects where we are.
            paused = true
            republishCurrent()
        }
    }

    /** Restart the queue from sentence 1 with the current settings. */
    fun restart() {
        if (phrases.isEmpty()) return
        val wasPlaying = playing || paused
        job?.cancel()
        job = null
        app.audioPlayer.stop()
        currentIndex = 0
        position = 1
        if (wasPlaying && playing) {
            launchFromCurrent()
        } else if (paused) {
            // Was paused — relaunch so the user gets immediate audio after Start Over.
            launchFromCurrent()
        } else {
            launchFromCurrent()
        }
    }

    private fun republishCurrent() {
        val s = phrases.getOrNull(currentIndex) ?: return
        NowVoicingBus.publish(
            NowVoicing(s.en, s.pl, s.literal, "en",
                "${currentIndex + 1}/${phrases.size}", s.words)
        )
    }

    private fun launchFromCurrent() {
        playing = true
        paused = false
        PlaybackController.register(
            PlaybackTransport(
                stop = { stop() },
                rewind = { rewind() },
                restart = { restart() },
                pauseResume = { if (playing) pause() else resume() },
                isPaused = { paused }
            )
        )
        job = scope.launch {
            while (currentIndex < phrases.size) {
                val i = currentIndex
                val s = phrases[i]
                position = i + 1
                val pos = "${i + 1}/${phrases.size}"
                val slowFirst = app.practicePrefs.slowFirst()
                val slowPlVoice = app.audioPrefs.slowPlVoice()
                if (slowFirst) {
                    app.ensureCachedAudio(s.pl, AzureTtsClient.LOCALE_PL, slowPlVoice)
                }
                app.ensureCachedAudio(s.pl, AzureTtsClient.LOCALE_PL, AzureTtsClient.PL_PL_F)
                pub(s, "en", pos)
                playAndAwait(s.en, AzureTtsClient.LOCALE_EN, AzureTtsClient.EN_US_F)
                if (slowFirst) {
                    pub(s, "pl-slow", pos)
                    playAndAwait(s.pl, AzureTtsClient.LOCALE_PL, slowPlVoice)
                    pub(s, "en", pos)
                    playAndAwait(s.en, AzureTtsClient.LOCALE_EN, AzureTtsClient.EN_US_F)
                }
                pub(s, "pl", pos)
                playAndAwait(s.pl, AzureTtsClient.LOCALE_PL, AzureTtsClient.PL_PL_F)
                // Only advance if pause/rewind/restart didn't move us elsewhere.
                if (currentIndex == i) {
                    currentIndex = i + 1
                    // Beat between items — user asked for a touch more breathing room.
                    if (currentIndex < phrases.size) delay(1000)
                }
            }
            // Natural completion only reaches here (cancellation exits via exception).
            playing = false
            paused = false
            NowVoicingBus.clear()
            PlaybackController.unregister()
        }
    }

    private fun pub(s: SentenceExample, lang: String, pos: String) {
        NowVoicingBus.publish(NowVoicing(s.en, s.pl, s.literal, lang, pos, s.words))
    }

    private suspend fun playAndAwait(text: String, locale: String, voice: String) {
        app.playAudioAndAwait(text, locale, voice)
    }

    private fun collectPhrases(config: RandomConfig): List<SentenceExample> {
        // Intersect the sheet's persons with the global Settings → Practice pronouns
        // filter. Settings always wins as a restriction so a pronoun toggled off there
        // never leaks into any queue, regardless of what the sheet chips say.
        val effectiveConfig = config.copy(
            personKeys = config.personKeys.intersect(
                app.pronounFilter.allIncluded(
                    RandomConfig.PERSONS,
                    com.sponic.langbang.data.PronounFilterStore.TENSE_PRESENT
                )
            )
        )
        // VERBS mode short-circuits the whole phrase pipeline — we emit synthetic
        // "pronoun + form" SentenceExamples instead of pulling from the Gemini caches.
        if (effectiveConfig.playMode == PlayMode.VERBS) {
            return collectConjugationPhrases(effectiveConfig).shuffled()
        }

        // Multi-token must-contain: split on whitespace, every token must appear as
        // a substring in the sentence. "kawa duża" → both must hit.
        val mustContainTokens = effectiveConfig.mustContainWord
            .lowercase()
            .split(Regex("\\s+"))
            .filter { it.isNotEmpty() }
            .takeIf { it.isNotEmpty() }
        val prepFilter = effectiveConfig.prepositions.toUiPrepositions()
        // Union of every verb conjugation matching the selected persons × tenses.
        // Adjective + adverb sentences are required to contain at least one of these
        // verb forms so an "oni/wy/my" sentence can't sneak in when the user has only
        // singular persons selected.
        val allowedVerbForms = collectAllowedVerbForms(effectiveConfig.personKeys, effectiveConfig.tenses)
        val verbSentences = buildList {
            if (VerbSentenceStore.TENSE_PRESENT in effectiveConfig.tenses) {
                addAll(collectVerbSentences(
                    effectiveConfig.personKeys, mustContainTokens, prepFilter,
                    VerbSentenceStore.TENSE_PRESENT
                ))
            }
            if (VerbSentenceStore.TENSE_PAST in effectiveConfig.tenses) {
                addAll(collectVerbSentences(
                    effectiveConfig.personKeys, mustContainTokens, prepFilter,
                    VerbSentenceStore.TENSE_PAST
                ))
            }
        }

        val adjSentences = when (effectiveConfig.adjectiveMode) {
            IncludeMode.OFF -> emptyList()
            IncludeMode.YES -> collectAdjectiveSentencesFiltered(
                mustContainTokens, prepFilter, allowedVerbForms
            )
            IncludeMode.ALL -> collectAdjectivesRoundRobin(
                mustContainTokens, prepFilter, allowedVerbForms
            )
        }

        val advSentences: List<SentenceExample> = when (effectiveConfig.adverbMode) {
            IncludeMode.OFF -> emptyList()
            IncludeMode.YES -> collectAdverbSentencesFiltered(
                mustContainTokens, prepFilter, allowedVerbForms
            )
            IncludeMode.ALL -> collectAdverbsRoundRobin(
                mustContainTokens, prepFilter, allowedVerbForms
            )
        }

        val phraseBankSentences = collectPhraseBankSentences(mustContainTokens, prepFilter)
        val combined = (verbSentences + adjSentences + advSentences + phraseBankSentences).distinctBy { it.pl }
        // Always shuffle, regardless of adjective mode. Earlier behavior kept
        // round-robin order when adjMode == ALL — but a user reported on
        // 2026-05-28 that "Play Phrases" felt deterministic, and the hidden
        // round-robin lock was the cause. IncludeMode.ALL still gives breadth
        // (one sentence per adjective per pass via collectAdjectivesRoundRobin)
        // — we just shuffle the resulting list so the order doesn't feel
        // canned. The Verbs-tab Quiz mode is the one place we want
        // shortest-first buildup; it sorts independently and isn't affected.
        return combined.shuffled()
    }

    /**
     * Builds synthetic "pronoun + verb form" SentenceExamples for VERBS mode. One entry
     * per verb × included person × included tense. Polish gets the spoken form
     * ("ja jestem"); English gets a short subject-verb gloss ("I am") so the NV panel
     * has something readable in the upper line.
     */
    private fun collectConjugationPhrases(config: RandomConfig): List<SentenceExample> {
        val verbLesson = app.lessonRepo.lesson2()
        val out = mutableListOf<SentenceExample>()
        verbLesson.verbs.forEach { verb ->
            if (VerbSentenceStore.TENSE_PRESENT in config.tenses) {
                addConjEntries(out, verb, verb.forms, config.personKeys, tenseGlossSuffix = "")
            }
            if (VerbSentenceStore.TENSE_PAST in config.tenses) {
                verb.past_forms?.let { past ->
                    addConjEntries(out, verb, past, config.personKeys, tenseGlossSuffix = " (past)")
                }
            }
        }
        return out
    }

    private fun addConjEntries(
        out: MutableList<SentenceExample>,
        verb: com.sponic.langbang.data.model.VerbEntry,
        forms: Map<String, String>,
        personKeys: Set<String>,
        tenseGlossSuffix: String
    ) {
        val isPast = tenseGlossSuffix.contains("past", ignoreCase = true)
        forms.forEach { (k, form) ->
            if (k !in personKeys) return@forEach
            if (form.isBlank()) return@forEach
            val pl = "${conjPronoun(k)} $form".trim()
            val enSubject = when (k) {
                "1sg" -> "I"
                "2sg" -> "you"
                "3sg" -> "he"
                "1pl" -> "we"
                "2pl" -> "y'all"
                "3pl" -> "they"
                else -> k
            }
            val enVerb = englishConjugate(verb.en, k, isPast)
            val en = "$enSubject $enVerb"
            val words = listOf(
                com.sponic.langbang.data.model.TokenPair(conjPronoun(k), enSubject),
                com.sponic.langbang.data.model.TokenPair(form, enVerb)
            )
            out += SentenceExample(pl = pl, en = en, literal = null, words = words)
        }
    }

    // englishConjugate moved to domain/EnglishConjugator.kt so the Verbs tab's
    // "Play All Conjugations" path can share it instead of re-rolling its own.
    // See 2026-05-28 session note.

    private fun conjPronoun(k: String): String = when (k) {
        "1sg" -> "ja"
        "2sg" -> "ty"
        "3sg" -> "on"
        "1pl" -> "my"
        "2pl" -> "wy"
        "3pl" -> "oni"
        else -> ""
    }

    private fun tokenCount(s: String): Int =
        s.trim().split(Regex("\\s+")).count { it.isNotEmpty() }

    private fun collectVerbSentences(
        personKeys: Set<String>,
        mustContainTokens: List<String>?,
        prepFilter: Set<String>,
        tense: String
    ): List<SentenceExample> {
        val verbLesson = app.lessonRepo.lesson2()
        return verbLesson.verbs.flatMap { verb ->
            val formsForTense = if (tense == VerbSentenceStore.TENSE_PAST) verb.past_forms.orEmpty()
            else verb.forms
            val allowedForms = formsForTense
                .filterKeys { it in personKeys }
                .values
                .filter { it.isNotBlank() }
                .map { it.lowercase() }
                .toSet()
            if (allowedForms.isEmpty()) emptyList()
            else app.lessonRepo.sentencesFor(verb.lemma, tense)
                .filter { tokensMatch(it.pl, allowedForms) }
                .filter { passesPrepFilter(it.pl, prepFilter) }
                .filter { passesMustContain(it.pl, mustContainTokens) }
        }
    }

    private fun collectAdjectiveSentencesFiltered(
        mustContainTokens: List<String>?,
        prepFilter: Set<String>,
        allowedVerbForms: Set<String>
    ): List<SentenceExample> {
        val adjLesson = app.lessonRepo.lesson3()
        return adjLesson.adjectives.flatMap { adj ->
            app.lessonRepo.adjectiveSentencesFor(adj.lemma)
                .filter { tokensMatch(it.pl, allowedVerbForms) }
                .filter { passesPrepFilter(it.pl, prepFilter) }
                .filter { passesMustContain(it.pl, mustContainTokens) }
        }
    }

    /**
     * Round-robin: one sentence per adjective per pass. Cycles until every adjective's
     * eligible-sentence pool is exhausted. Result: every adjective with any sentences
     * gets equal airtime before anything repeats.
     */
    private fun collectAdjectivesRoundRobin(
        mustContainTokens: List<String>?,
        prepFilter: Set<String>,
        allowedVerbForms: Set<String>
    ): List<SentenceExample> {
        val adjLesson = app.lessonRepo.lesson3()
        val perAdj: List<List<SentenceExample>> = adjLesson.adjectives.map { adj ->
            app.lessonRepo.adjectiveSentencesFor(adj.lemma)
                .filter { tokensMatch(it.pl, allowedVerbForms) }
                .filter { passesPrepFilter(it.pl, prepFilter) }
                .filter { passesMustContain(it.pl, mustContainTokens) }
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

    private fun collectAdverbSentencesFiltered(
        mustContainTokens: List<String>?,
        prepFilter: Set<String>,
        allowedVerbForms: Set<String>
    ): List<SentenceExample> {
        val advLesson = app.lessonRepo.lesson4()
        return advLesson.adverbs.flatMap { adv ->
            app.lessonRepo.adverbSentencesFor(adv.lemma)
                .filter { tokensMatch(it.pl, allowedVerbForms) }
                .filter { passesPrepFilter(it.pl, prepFilter) }
                .filter { passesMustContain(it.pl, mustContainTokens) }
        }
    }

    private fun collectPhraseBankSentences(
        mustContainTokens: List<String>?,
        prepFilter: Set<String>
    ): List<SentenceExample> {
        return app.lessonRepo.lesson5().groups.flatMap { group ->
            group.sentences
                .filter { passesPrepFilter(it.pl, prepFilter) }
                .filter { passesMustContain(it.pl, mustContainTokens) }
        }
    }

    private fun collectAdverbsRoundRobin(
        mustContainTokens: List<String>?,
        prepFilter: Set<String>,
        allowedVerbForms: Set<String>
    ): List<SentenceExample> {
        val advLesson = app.lessonRepo.lesson4()
        val perAdv: List<List<SentenceExample>> = advLesson.adverbs.map { adv ->
            app.lessonRepo.adverbSentencesFor(adv.lemma)
                .filter { tokensMatch(it.pl, allowedVerbForms) }
                .filter { passesPrepFilter(it.pl, prepFilter) }
                .filter { passesMustContain(it.pl, mustContainTokens) }
                .shuffled()
        }.filter { it.isNotEmpty() }
        val maxLen = perAdv.maxOfOrNull { it.size } ?: 0
        return buildList {
            for (i in 0 until maxLen) {
                perAdv.forEach { pool ->
                    pool.getOrNull(i)?.let { add(it) }
                }
            }
        }
    }

    /**
     * Union of every verb conjugation across lesson 2 verbs whose person-key is in
     * [personKeys], drawn from the present and/or past paradigm per [tenses]. Used as
     * the gating set for adjective + adverb sentences so they can't bypass the
     * Persons/Tense filters via verb forms outside the user's selection.
     */
    private fun collectAllowedVerbForms(
        personKeys: Set<String>,
        tenses: Set<String>
    ): Set<String> {
        val verbLesson = app.lessonRepo.lesson2()
        val out = mutableSetOf<String>()
        verbLesson.verbs.forEach { verb ->
            if (VerbSentenceStore.TENSE_PRESENT in tenses) {
                verb.forms.filterKeys { it in personKeys }.values.forEach {
                    if (it.isNotBlank()) out += it.lowercase()
                }
            }
            if (VerbSentenceStore.TENSE_PAST in tenses) {
                verb.past_forms.orEmpty().filterKeys { it in personKeys }.values.forEach {
                    if (it.isNotBlank()) out += it.lowercase()
                }
            }
        }
        return out
    }

    private fun tokensMatch(pl: String, allowed: Set<String>): Boolean {
        return pl.lowercase()
            .split(Regex("[^\\p{L}]+"))
            .any { it.isNotEmpty() && it in allowed }
    }

    private fun passesPrepFilter(pl: String, prepFilter: Set<String>): Boolean {
        val tokens = pl.lowercase().split(Regex("[^\\p{L}]+"))
        val tracked = tokens.filter { it in RandomConfig.PREPOSITIONS }
        val allowsNone = RandomConfig.PREPOSITION_NONE in prepFilter
        if (tracked.isEmpty()) return allowsNone
        return tracked.any { it in prepFilter }
    }

    private fun passesMustContain(pl: String, mustContainTokens: List<String>?): Boolean {
        if (mustContainTokens == null) return true
        // Substring rather than exact-token so morphological variants ("kawa"/"kawę"/
        // "kawy") all match the stem. With multiple tokens, every token must hit.
        val hay = pl.lowercase()
        return mustContainTokens.all { hay.contains(it) }
    }

    private fun Set<String>.toUiPrepositions(): Set<String> {
        val allowed = RandomConfig.PREPOSITIONS.toSet() + RandomConfig.PREPOSITION_NONE
        val cleaned = intersect(allowed)
        return cleaned.ifEmpty { setOf(RandomConfig.PREPOSITION_NONE) }
    }
}
