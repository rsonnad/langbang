package com.sponic.langbang.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Lesson(
    val id: String,
    val title: String,
    val summary: String,
    val verbs: List<VerbEntry>,
    val pronouns: List<PronounEntry>,
    val phrases: List<PhraseEntry>
)

@Serializable
data class VerbEntry(
    val lemma: String,
    val en: String,
    val forms: Map<String, String>
)

@Serializable
data class SentenceExample(
    val pl: String,
    val en: String,
    /**
     * Word-for-word literal English rendering of the Polish sentence — preserves the
     * Polish word order and prepositions so the learner can see what each word means
     * (e.g. "I am in home" for "Jestem w domu"). Nullable so older cached sentences
     * that don't have this field still deserialize.
     */
    val literal: String? = null
)

@Serializable
data class PronounEntry(
    val lemma: String,
    val en: String,
    val case_forms: Map<String, String>
)

@Serializable
data class PhraseEntry(
    val en: String,
    val pl: String,
    val focus: List<String> = emptyList()
)

@Serializable
data class AdjectiveLesson(
    val id: String,
    val title: String,
    val summary: String,
    val adjectives: List<AdjectiveEntry>
)

@Serializable
data class AdjectiveEntry(
    val lemma: String,
    val en: String,
    val nom: Map<String, String>,
    val acc: Map<String, String>
)
