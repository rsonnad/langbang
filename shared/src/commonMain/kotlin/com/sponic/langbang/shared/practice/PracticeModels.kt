package com.sponic.langbang.shared.practice

/**
 * Minimal faithful port of the core PracticeItem / PracticeKind from LangBangML
 * ui/quizzes/PracticeModel.kt and generators for the anti-drift vertical slice.
 * Only the subset needed for a simple EN-cue -> reveal PL + play audio flashcard flow.
 */

enum class PracticeKind(val label: String) {
    VERB_FORM("Verb form"),
    PHRASE("Phrase"),
    // Extend as more generators are ported in later slices.
}

data class TokenPair(
    val pl: String,
    val en: String
)

data class PracticeItem(
    val id: String,
    val kind: PracticeKind,
    val prompt: String,      // EN cue, e.g. "I have"
    val answerPl: String,    // PL answer to reveal + speak, e.g. "mam"
    val answerEn: String,    // English gloss for reveal (often the prompt)
    val context: String,     // e.g. "mieć · present · 1sg"
    val literal: String? = null,
    val words: List<TokenPair>? = null,
    val targetLemma: String? = null,
    val targetForm: String? = null,
) {
    // Convenience for audio key (in real would resolve to R2 or asset path)
    val audioKey: String get() = targetForm ?: answerPl
}