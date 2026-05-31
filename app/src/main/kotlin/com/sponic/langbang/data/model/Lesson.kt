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
    /** Present-tense conjugation: 1sg, 2sg, 3sg, 1pl, 2pl, 3pl. Always populated. */
    val forms: Map<String, String>,
    /**
     * Collapsed past-tense conjugation (masculine-singular + virile-plural defaults)
     * keyed the same way: 1sg, 2sg, 3sg, 1pl, 2pl, 3pl. Nullable so existing verbs
     * deserialize unchanged; new content has it populated, UI surfaces a "Past" block
     * only when present. Gendered variants (feminine sg, non-virile pl) are deferred
     * to a follow-up — keep the grammar lite.
     */
    val past_forms: Map<String, String>? = null
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
    val literal: String? = null,
    /**
     * Per-token alignment between Polish and English. Each pair is (pl_word, en_gloss)
     * in left-to-right Polish order. Lets the sticky NowVoicing panel render each
     * English gloss directly below its Polish word. Nullable so cached sentences from
     * before the Gemini prompt change still deserialize; the panel falls back to
     * whitespace-splitting `literal` when this field is absent.
     */
    val words: List<TokenPair>? = null
)

@Serializable
data class TokenPair(
    val pl: String,
    val en: String,
    val gender: String? = null,
    val caseKey: String? = null,
    val caseLabel: String? = null,
    val numberLabel: String? = null
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

@Serializable
data class AdverbLesson(
    val id: String,
    val title: String,
    val summary: String,
    val adverbs: List<AdverbEntry>
)

/**
 * Polish adverbs are uninflected — one form covers every context. So each entry just
 * stores the lemma + English gloss. Example sentences are stored separately in
 * AdverbSentenceStore, mirroring the adjective pattern.
 */
@Serializable
data class AdverbEntry(
    val lemma: String,
    val en: String
)

@Serializable
data class NounLesson(
    val id: String,
    val title: String,
    val summary: String,
    val nouns: List<NounEntry>
)

/**
 * A Polish noun shown across its three workhorse cases — nominative (subject),
 * accusative (direct object), genitive ("of" / negation / after numbers) — each in
 * singular and plural. [gender] is "m" / "f" / "n"; the masculine accusative already
 * encodes animacy in the stored forms (animate acc.sg = gen.sg, inanimate acc.sg =
 * nom.sg) so the UI doesn't have to derive it. Each case map is keyed "sg" / "pl".
 */
@Serializable
data class NounEntry(
    val lemma: String,
    val en: String,
    val gender: String,
    val nom: Map<String, String>,
    val acc: Map<String, String>,
    val gen: Map<String, String>
)

/**
 * Lesson 5 — multi-sentence real-world phrase groups (introductions, small-talk, etc.).
 * Each group is a runnable monologue: 6-10 sentences played in order, EN cue → PL target.
 */
@Serializable
data class PhrasesLesson(
    val id: String,
    val title: String,
    val summary: String,
    val groups: List<PhraseGroup>
)

@Serializable
data class PhraseGroup(
    val id: String,
    val title: String,
    val subtitle: String = "",
    val sentences: List<SentenceExample>
)
