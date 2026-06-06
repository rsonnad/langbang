package com.sponic.langbang.data

import android.content.Context
import com.sponic.langbang.cloud.CloudConfigStore
import com.sponic.langbang.cloud.CloudLanguagePair
import com.sponic.langbang.cloud.CloudUserWords
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
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.decodeFromJsonElement

class LessonRepository(
    context: Context,
    private val cloudConfig: CloudConfigStore? = null
) {

    private val context = context.applicationContext
    private val json = LbJson.lenient
    private val userVerbs = JsonListStore(this.context, "user-verbs.json", VerbEntry.serializer()) { it.lemma }
    private val verbSentences = VerbSentenceStore(this.context)
    private val userAdjectives =
        JsonListStore(this.context, "user-adjectives.json", AdjectiveEntry.serializer()) { it.lemma }
    private val adjectiveSentences = SentenceStore(this.context, "adjective-sentences.json")
    private val userAdverbs = JsonListStore(this.context, "user-adverbs.json", AdverbEntry.serializer()) { it.lemma }
    private val adverbSentences = SentenceStore(this.context, "adverb-sentences.json")
    private val userNouns = JsonListStore(this.context, "user-nouns.json", NounEntry.serializer()) { it.lemma }
    private val nounSentences = SentenceStore(this.context, "noun-sentences.json")

    private val pastSentenceSerializer = MapSerializer(
        String.serializer(),
        ListSerializer(SentenceExample.serializer())
    )

    private val userPhrases = JsonListStore(this.context, "user-phrases.json", PhraseGroup.serializer()) { it.id }

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
            (cloudLesson<Lesson>("lesson-02") ?: assetLesson<Lesson>("lesson-02.json"))
                .also { cachedLesson2Base = it }
        }
        val added = userVerbs.load()
        if (added.isEmpty()) return base
        val merged = (added + base.verbs).distinctBy { it.lemma.lowercase() }
        return base.copy(verbs = merged)
    }

    /** Lesson 3 — core adjectives in nom + acc, with user-added adjectives merged in. */
    fun lesson3(): AdjectiveLesson {
        val base = cachedLesson3Base ?: run {
            (cloudLesson<AdjectiveLesson>("lesson-03") ?: assetLesson<AdjectiveLesson>("lesson-03.json"))
                .also { cachedLesson3Base = it }
        }
        val added = userAdjectives.load()
        if (added.isEmpty()) return base
        val merged = (added + base.adjectives).distinctBy { it.lemma.lowercase() }
        return base.copy(adjectives = merged)
    }

    /** Lesson 1 — Polish pronunciation. */
    fun pronunciation(): PronunciationData {
        cachedPron?.let { return it }
        val parsed = cloudLesson<PronunciationData>("lesson-01") ?: assetLesson("lesson-01.json")
        cachedPron = parsed
        return parsed
    }

    fun addUserVerb(verb: VerbEntry) {
        userVerbs.add(verb)
    }

    fun userVerbEntries(): List<VerbEntry> =
        userVerbs.load()

    fun replaceUserVerbEntries(verbs: List<VerbEntry>) {
        userVerbs.replaceAll(verbs)
    }

    fun sentencesFor(
        lemma: String,
        tense: String = VerbSentenceStore.TENSE_PRESENT
    ): List<SentenceExample> {
        val cached = verbSentences.get(lemma, tense)
        if (cached.isNotEmpty()) return cached.map(::scrubSentenceExample)
        // Past-tense fallback: read from the bundled pregen asset if it exists. Lets a
        // fresh install have past-tense content (text + audio) without the user tapping
        // Generate — the asset is shipped with the APK and audio sits in R2.
        if (tense == VerbSentenceStore.TENSE_PAST) {
            return pastPregen()[lemma.lowercase()].orEmpty().map(::scrubSentenceExample)
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
        verbSentences.put(lemma, tense, sentences.map(::scrubSentenceExample))
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

    fun userAdjectiveEntries(): List<AdjectiveEntry> =
        userAdjectives.load()

    fun replaceUserAdjectiveEntries(adjectives: List<AdjectiveEntry>) {
        userAdjectives.replaceAll(adjectives)
    }

    fun adjectiveSentencesFor(lemma: String): List<SentenceExample> =
        adjectiveSentences.get(lemma).map(::scrubSentenceExample)

    fun saveAdjectiveSentences(lemma: String, sentences: List<SentenceExample>) {
        adjectiveSentences.put(lemma, sentences.map(::scrubSentenceExample))
    }

    /** Lesson 4 — common adverbs, with user-added adverbs merged in. */
    fun lesson4(): AdverbLesson {
        val base = cachedLesson4Base ?: run {
            (cloudLesson<AdverbLesson>("lesson-04") ?: assetLesson<AdverbLesson>("lesson-04.json"))
                .also { cachedLesson4Base = it }
        }
        val added = userAdverbs.load()
        if (added.isEmpty()) return base
        val merged = (added + base.adverbs).distinctBy { it.lemma.lowercase() }
        return base.copy(adverbs = merged)
    }

    fun addUserAdverb(adv: AdverbEntry) {
        userAdverbs.add(adv)
    }

    fun userAdverbEntries(): List<AdverbEntry> =
        userAdverbs.load()

    fun replaceUserAdverbEntries(adverbs: List<AdverbEntry>) {
        userAdverbs.replaceAll(adverbs)
    }

    fun adverbSentencesFor(lemma: String): List<SentenceExample> =
        adverbSentences.get(lemma).map(::scrubSentenceExample)

    fun saveAdverbSentences(lemma: String, sentences: List<SentenceExample>) {
        adverbSentences.put(lemma, sentences.map(::scrubSentenceExample))
    }

    /** Lesson 6 — core nouns in nom + acc + gen (sg/pl), with user-added nouns merged in. */
    fun lesson6(): NounLesson {
        val base = cachedLesson6Base ?: run {
            (cloudLesson<NounLesson>("lesson-06") ?: assetLesson<NounLesson>("lesson-06.json"))
                .also { cachedLesson6Base = it }
        }
        val added = userNouns.load()
        if (added.isEmpty()) return base
        val merged = (added + base.nouns).distinctBy { it.lemma.lowercase() }
        return base.copy(nouns = merged)
    }

    fun addUserNoun(noun: NounEntry) {
        userNouns.add(noun)
    }

    fun userNounEntries(): List<NounEntry> =
        userNouns.load()

    fun replaceUserNounEntries(nouns: List<NounEntry>) {
        userNouns.replaceAll(nouns)
    }

    fun userWords(): CloudUserWords =
        CloudUserWords(
            verbs = userVerbEntries(),
            nouns = userNounEntries(),
            adjectives = userAdjectiveEntries(),
            adverbs = userAdverbEntries()
        )

    fun replaceUserWords(words: CloudUserWords) {
        replaceUserVerbEntries(words.verbs)
        replaceUserNounEntries(words.nouns)
        replaceUserAdjectiveEntries(words.adjectives)
        replaceUserAdverbEntries(words.adverbs)
        clearCloudBackedBaseCache()
    }

    fun nounSentencesFor(lemma: String): List<SentenceExample> =
        nounSentences.get(lemma).map(::scrubSentenceExample)

    fun saveNounSentences(lemma: String, sentences: List<SentenceExample>) {
        nounSentences.put(lemma, sentences.map(::scrubSentenceExample))
    }

    /**
     * Lesson 5 — common multi-sentence phrases bundled with the app, plus any custom
     * groups the user has added (custom groups appear first in the list so they're
     * easy to find). Sentence text gets the same `(plural)` scrub as the other lessons
     * for parity.
     */
    fun lesson5(): PhrasesLesson {
        val base = cachedLesson5Base ?: run {
            (cloudLesson<PhrasesLesson>("lesson-05") ?: assetLesson<PhrasesLesson>("lesson-05.json"))
                .also { cachedLesson5Base = it }
        }
        val added = userPhrases.load()
        val scrubbedBase = base.copy(
            groups = base.groups.map { g ->
                g.copy(sentences = g.sentences.map(::scrubSentenceExample))
            }
        )
        if (added.isEmpty()) return scrubbedBase
        val merged = (added + scrubbedBase.groups).distinctBy { it.id.lowercase() }
        return scrubbedBase.copy(groups = merged)
    }

    fun addUserPhraseGroup(group: PhraseGroup) {
        userPhrases.add(group)
    }

    fun userPhraseGroups(): List<PhraseGroup> =
        userPhrases.load()

    fun replaceUserPhraseGroups(groups: List<PhraseGroup>) {
        userPhrases.replaceAll(groups)
    }

    fun addUserPhraseSentence(groupId: String, sentence: SentenceExample): PhraseGroup? {
        val group = lesson5().groups.firstOrNull { it.id.equals(groupId, ignoreCase = true) }
            ?: return null
        val updated = group.copy(sentences = group.sentences + sentence)
        userPhrases.add(updated)
        return updated
    }

    fun deleteUserPhraseGroup(id: String) {
        userPhrases.remove(id)
    }

    /**
     * Rewrites legacy Gemini outputs that annotated 2pl English subjects as
     * "You (plural)" and fixes generated rows where Gemini copied the English
     * pronoun "Y'all" into the Polish sentence. English keeps the app's 2pl
     * convention; Polish gets the actual pronoun "Wy".
     */
    private fun scrubSentenceExample(s: SentenceExample): SentenceExample = s.copy(
        pl = scrubPolishTargetText(s.pl),
        en = normalizeEnglishTranslation(scrubPluralText(s.en)),
        literal = s.literal?.let(::scrubPluralText),
        words = s.words?.map { token ->
            token.copy(
                pl = scrubPolishTargetText(token.pl),
                en = scrubPluralText(token.en)
            )
        }
    )

    fun clearCloudBackedBaseCache() {
        cachedLesson2Base = null
        cachedLesson3Base = null
        cachedLesson4Base = null
        cachedLesson5Base = null
        cachedLesson6Base = null
        cachedPron = null
    }

    fun cloudLanguagePair(): CloudLanguagePair? =
        cloudConfig?.state?.value?.bootstrap?.languagePair

    private inline fun <reified T> cloudLesson(id: String): T? {
        val payload = cloudConfig?.state?.value?.bootstrap?.content?.lessons
            ?.firstOrNull { it.id == id }
            ?.payload ?: return null
        return runCatching { json.decodeFromJsonElement<T>(payload) }.getOrNull()
    }

    private inline fun <reified T> assetLesson(fileName: String): T {
        val raw = context.assets.open(fileName).bufferedReader().use { it.readText() }
        return json.decodeFromString(raw)
    }

    companion object {
        private val PLURAL_YOU_REGEX = Regex("\\b(Y|y)ou\\s*\\(plural\\)")
        private val PAREN_PLURAL_REGEX = Regex("\\s*\\(plural\\)\\s*", RegexOption.IGNORE_CASE)
        private val ENGLISH_YALL_IN_POLISH_REGEX = Regex("\\by['’]all\\b", RegexOption.IGNORE_CASE)
        private val WHITESPACE_REGEX = Regex("\\s+")

        private fun scrubPluralText(text: String): String {
            if (!text.contains("(plural)", ignoreCase = true)) return text
            var t = PLURAL_YOU_REGEX.replace(text) { m ->
                if (m.groupValues[1] == "Y") "Y'all" else "y'all"
            }
            t = PAREN_PLURAL_REGEX.replace(t, " ")
            return WHITESPACE_REGEX.replace(t, " ").trim()
        }

        private fun scrubPolishTargetText(text: String): String {
            if (!text.contains("all", ignoreCase = true)) return text
            return ENGLISH_YALL_IN_POLISH_REGEX.replace(text) { match ->
                if (match.range.first == 0) "Wy" else "wy"
            }
        }
    }
}
