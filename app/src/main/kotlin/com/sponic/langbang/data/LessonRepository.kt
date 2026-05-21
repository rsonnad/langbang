package com.sponic.langbang.data

import android.content.Context
import com.sponic.langbang.data.model.AdjectiveEntry
import com.sponic.langbang.data.model.AdjectiveLesson
import com.sponic.langbang.data.model.AdverbEntry
import com.sponic.langbang.data.model.AdverbLesson
import com.sponic.langbang.data.model.Lesson
import com.sponic.langbang.data.model.PronunciationData
import com.sponic.langbang.data.model.SentenceExample
import com.sponic.langbang.data.model.VerbEntry
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

    private var cachedLesson2Base: Lesson? = null
    private var cachedLesson3Base: AdjectiveLesson? = null
    private var cachedLesson4Base: AdverbLesson? = null
    private var cachedPron: PronunciationData? = null

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

    fun sentencesFor(lemma: String): List<SentenceExample> = verbSentences.get(lemma)

    fun saveSentences(lemma: String, sentences: List<SentenceExample>) {
        verbSentences.put(lemma, sentences)
    }

    fun addUserAdjective(adj: AdjectiveEntry) {
        userAdjectives.add(adj)
    }

    fun adjectiveSentencesFor(lemma: String): List<SentenceExample> =
        adjectiveSentences.get(lemma)

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
        adverbSentences.get(lemma)

    fun saveAdverbSentences(lemma: String, sentences: List<SentenceExample>) {
        adverbSentences.put(lemma, sentences)
    }
}
