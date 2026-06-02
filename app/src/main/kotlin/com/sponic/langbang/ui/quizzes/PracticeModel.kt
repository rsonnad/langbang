package com.sponic.langbang.ui.quizzes

import com.sponic.langbang.data.VerbSentenceStore
import com.sponic.langbang.data.model.TokenPair

enum class PracticeWordType(val label: String) {
    HELPERS("Helpers"),
    VERBS("Verbs"),
    NOUNS("Nouns"),
    ADJECTIVES("Adj"),
    ADVERBS("Adv"),
    PHRASES("Phrases"),
}

enum class PracticeKind(val label: String) {
    HELPER_INFINITIVE("Helper + inf"),
    VERB_FORM("Verb form"),
    VERB_SENTENCE("Verb sentence"),
    NOUN_FORM("Noun form"),
    ADJECTIVE_FORM("Adjective form"),
    ADVERB_LEMMA("Adverb"),
    ADVERB_SENTENCE("Adverb sentence"),
    PHRASE("Phrase"),
}

enum class AutoPracticeStage(val label: String) {
    CORE("Auto: Core"),
    WIDER("Auto: Wider"),
    FULL("Auto: Full"),
    MIXED("Auto: Mixed"),
}

data class PracticeScope(
    val auto: Boolean = true,
    val wordTypes: Set<PracticeWordType> = setOf(
        PracticeWordType.VERBS,
        PracticeWordType.NOUNS,
        PracticeWordType.ADJECTIVES,
        PracticeWordType.ADVERBS
    ),
    val personKeys: Set<String> = setOf("1sg", "2sg", "3sg"),
    val tenses: Set<String> = setOf(VerbSentenceStore.TENSE_PRESENT),
) {
    val title: String
        get() = if (auto) "Practice · Auto" else "Practice · Manual"
}

data class PracticeItem(
    val id: String,
    val kind: PracticeKind,
    val prompt: String,
    val answerPl: String,
    val answerEn: String,
    val context: String,
    val literal: String? = null,
    val words: List<TokenPair>? = null,
    val targetLemma: String? = null,
    val targetForm: String? = null,
    val personKey: String? = null,
    val tense: String? = null,
    val grammarKey: String? = null,
)
