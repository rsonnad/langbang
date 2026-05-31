package com.sponic.langbang.data

import android.content.Context
import com.sponic.langbang.data.model.AdjectiveEntry
import com.sponic.langbang.data.model.AdjectiveLesson
import com.sponic.langbang.data.model.AdverbEntry
import com.sponic.langbang.data.model.AdverbLesson
import com.sponic.langbang.data.model.Lesson
import com.sponic.langbang.data.model.NounEntry
import com.sponic.langbang.data.model.NounLesson
import com.sponic.langbang.data.model.PhraseGroup
import com.sponic.langbang.data.model.PhrasesLesson
import com.sponic.langbang.data.model.PronunciationData
import com.sponic.langbang.data.model.SentenceExample
import com.sponic.langbang.data.model.VerbEntry
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

class LessonRepository(context: Context) {

    private val context = context.applicationContext
    private val json = Json { ignoreUnknownKeys = true }
    private val userVerbs = UserVerbStore(this.context)
    private val verbSentences = VerbSentenceStore(this.context)
    private val userAdjectives = UserAdjectiveStore(this.context)
    private val adjectiveSentences = AdjectiveSentenceStore(this.context)
    private val userAdverbs = UserAdverbStore(this.context)
    private val adverbSentences = AdverbSentenceStore(this.context)
    private val userNouns = UserNounStore(this.context)
    private val nounSentences = NounSentenceStore(this.context)

    private val pastSentenceSerializer = MapSerializer(
        String.serializer(),
        ListSerializer(SentenceExample.serializer())
    )

    private val userPhrases = UserPhraseStore(this.context)

    private var cachedLesson2Base: Lesson? = null
    private var cachedLesson3Base: AdjectiveLesson? = null
    private var cachedLesson4Base: AdverbLesson? = null
    private var cachedLesson5Base: PhrasesLesson? = null
    private var cachedLesson6Base: NounLesson? = null
    private var cachedPron: PronunciationData? = null
    /** Lazily-loaded bundled asset of pregen past-tense sentences (lemma → list). */
    private var cachedPastPregen: Map<String, List<SentenceExample>>? = null

    /** Lesson 2 — core verbs in present tense, with user-added verbs merged in. */
    fun lesson2(): Lesson {
        val base = cachedLesson2Base ?: run {
            val raw = context.assets.open("lesson-02.json").bufferedReader().use { it.readText() }
            json.decodeFromString<Lesson>(raw).also { cachedLesson2Base = it }
        }
        val added = userVerbs.load()
        if (added.isEmpty()) return base
        val merged = (added + base.verbs).distinctBy { it.lemma.lowercase() }
        return base.copy(verbs = merged)
    }

    /** Lesson 3 — core adjectives in nom + acc, with user-added adjectives merged in. */
    fun lesson3(): AdjectiveLesson {
        val base = cachedLesson3Base ?: run {
            val raw = context.assets.open("lesson-03.json").bufferedReader().use { it.readText() }
            json.decodeFromString<AdjectiveLesson>(raw).also { cachedLesson3Base = it }
        }
        val added = userAdjectives.load()
        if (added.isEmpty()) return base
        val merged = (added + base.adjectives).distinctBy { it.lemma.lowercase() }
        return base.copy(adjectives = merged)
    }

    /** Lesson 1 — Polish pronunciation. */
    fun pronunciation(): PronunciationData {
        cachedPron?.let { return it }
        val raw = context.assets.open("lesson-01.json").bufferedReader().use { it.readText() }
        val parsed = json.decodeFromString<PronunciationData>(raw)
        cachedPron = parsed
        return parsed
    }

    fun addUserVerb(verb: VerbEntry) {
        userVerbs.add(verb)
    }

    fun sentencesFor(
        lemma: String,
        tense: String = VerbSentenceStore.TENSE_PRESENT
    ): List<SentenceExample> {
        val cached = verbSentences.get(lemma, tense)
        if (cached.isNotEmpty()) return cached.map(::scrubPluralMarkers)
        // Past-tense fallback: read from the bundled pregen asset if it exists. Lets a
        // fresh install have past-tense content (text + audio) without the user tapping
        // Generate — the asset is shipped with the APK and audio sits in R2.
        if (tense == VerbSentenceStore.TENSE_PAST) {
            return pastPregen()[lemma.lowercase()].orEmpty().map(::scrubPluralMarkers)
        }
        return emptyList()
    }

    /**
     * Cache-only variant — bypasses the bundled past-pregen asset fallback in
     * [sentencesFor]. Used by [com.sponic.langbang.domain.SentenceRegenService.hasFreshLocal]
     * so the R2 downloader doesn't decide a past bundle is "already cached" just
     * because the static asset has content for it. Without this distinction the
     * service skipped every past bundle download.
     */
    fun cachedVerbSentencesFor(lemma: String, tense: String): List<SentenceExample> =
        verbSentences.get(lemma, tense)

    private fun pastPregen(): Map<String, List<SentenceExample>> {
        cachedPastPregen?.let { return it }
        val loaded = runCatching {
            val raw = context.assets.open("verb-past-sentences-pregen.json")
                .bufferedReader().use { it.readText() }
            json.decodeFromString(pastSentenceSerializer, raw)
                .mapKeys { it.key.lowercase() }
        }.getOrDefault(emptyMap())
        cachedPastPregen = loaded
        return loaded
    }

    fun saveSentences(
        lemma: String,
        sentences: List<SentenceExample>,
        tense: String = VerbSentenceStore.TENSE_PRESENT
    ) {
        verbSentences.put(lemma, tense, sentences)
    }

    /** Drop every cached Gemini sentence — verbs, adjectives, adverbs, nouns. */
    fun clearAllSentences() {
        verbSentences.clearAll()
        adjectiveSentences.clearAll()
        adverbSentences.clearAll()
        nounSentences.clearAll()
    }

    /** Drop only the verb sentence cache (both present + past). */
    fun clearVerbSentences() {
        verbSentences.clearAll()
    }

    /** Drop only the adjective sentence cache. */
    fun clearAdjectiveSentencesCache() {
        adjectiveSentences.clearAll()
    }

    /** Drop only the adverb sentence cache. */
    fun clearAdverbSentencesCache() {
        adverbSentences.clearAll()
    }

    /** Drop only the noun sentence cache. */
    fun clearNounSentencesCache() {
        nounSentences.clearAll()
    }

    fun addUserAdjective(adj: AdjectiveEntry) {
        userAdjectives.add(adj)
    }

    fun adjectiveSentencesFor(lemma: String): List<SentenceExample> =
        adjectiveSentences.get(lemma).map(::scrubPluralMarkers)

    fun saveAdjectiveSentences(lemma: String, sentences: List<SentenceExample>) {
        adjectiveSentences.put(lemma, sentences)
    }

    /** Lesson 4 — common adverbs, with user-added adverbs merged in. */
    fun lesson4(): AdverbLesson {
        val base = cachedLesson4Base ?: run {
            val raw = context.assets.open("lesson-04.json").bufferedReader().use { it.readText() }
            json.decodeFromString<AdverbLesson>(raw).also { cachedLesson4Base = it }
        }
        val added = userAdverbs.load()
        if (added.isEmpty()) return base
        val merged = (added + base.adverbs).distinctBy { it.lemma.lowercase() }
        return base.copy(adverbs = merged)
    }

    fun addUserAdverb(adv: AdverbEntry) {
        userAdverbs.add(adv)
    }

    fun adverbSentencesFor(lemma: String): List<SentenceExample> =
        adverbSentences.get(lemma).map(::scrubPluralMarkers)

    fun saveAdverbSentences(lemma: String, sentences: List<SentenceExample>) {
        adverbSentences.put(lemma, sentences)
    }

    /** Lesson 6 — core nouns in nom + acc + gen (sg/pl), with user-added nouns merged in. */
    fun lesson6(): NounLesson {
        val base = cachedLesson6Base ?: run {
            val raw = context.assets.open("lesson-06.json").bufferedReader().use { it.readText() }
            json.decodeFromString<NounLesson>(raw).also { cachedLesson6Base = it }
        }
        val added = userNouns.load()
        if (added.isEmpty()) return base
        val merged = (added + base.nouns).distinctBy { it.lemma.lowercase() }
        return base.copy(nouns = merged)
    }

    fun addUserNoun(noun: NounEntry) {
        userNouns.add(noun)
    }

    fun nounSentencesFor(lemma: String): List<SentenceExample> =
        nounSentences.get(lemma).map(::scrubPluralMarkers)

    fun saveNounSentences(lemma: String, sentences: List<SentenceExample>) {
        nounSentences.put(lemma, sentences)
    }

    /**
     * Lesson 5 — common multi-sentence phrases bundled with the app, plus any custom
     * groups the user has added (custom groups appear first in the list so they're
     * easy to find). Sentence text gets the same `(plural)` scrub as the other lessons
     * for parity.
     */
    fun lesson5(): PhrasesLesson {
        val base = cachedLesson5Base ?: run {
            val raw = context.assets.open("lesson-05.json").bufferedReader().use { it.readText() }
            json.decodeFromString<PhrasesLesson>(raw).also { cachedLesson5Base = it }
        }
        val added = userPhrases.load()
        val scrubbedBase = base.copy(
            groups = base.groups.map { g ->
                g.copy(sentences = g.sentences.map(::scrubPluralMarkers))
            }
        )
        if (added.isEmpty()) return scrubbedBase
        val merged = (added + scrubbedBase.groups).distinctBy { it.id.lowercase() }
        return scrubbedBase.copy(groups = merged)
    }

    fun addUserPhraseGroup(group: PhraseGroup) {
        userPhrases.add(group)
    }

    fun deleteUserPhraseGroup(id: String) {
        userPhrases.remove(id)
    }

    /**
     * Rewrites legacy Gemini outputs that annotated 2pl subjects as "You (plural)" — the
     * prompt now requires "Y'all" but old cached sentences (and any straggler that
     * slips past) need cleaning at read time. Stray bare "(plural)" markers are stripped
     * too. Cheap enough to run on every read; no need to persist back.
     */
    private fun scrubPluralMarkers(s: SentenceExample): SentenceExample = s.copy(
        en = scrubPluralText(s.en),
        literal = s.literal?.let(::scrubPluralText),
        words = s.words?.map { it.copy(en = scrubPluralText(it.en)) }
    )

    companion object {
        private val PLURAL_YOU_REGEX = Regex("\\b(Y|y)ou\\s*\\(plural\\)")
        private val PAREN_PLURAL_REGEX = Regex("\\s*\\(plural\\)\\s*", RegexOption.IGNORE_CASE)
        private val WHITESPACE_REGEX = Regex("\\s+")

        private fun scrubPluralText(text: String): String {
            if (!text.contains("(plural)", ignoreCase = true)) return text
            var t = PLURAL_YOU_REGEX.replace(text) { m ->
                if (m.groupValues[1] == "Y") "Y'all" else "y'all"
            }
            t = PAREN_PLURAL_REGEX.replace(t, " ")
            return WHITESPACE_REGEX.replace(t, " ").trim()
        }
    }
}
