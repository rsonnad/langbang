package com.sponic.langbang.data.model

import kotlinx.serialization.Serializable

@Serializable
data class PronunciationData(
    val id: String,
    val title: String,
    val summary: String,
    val phonemes: List<PhonemeEntry>
)

@Serializable
data class PhonemeEntry(
    val letter: String,
    val name: String,
    val ipa: String,
    val englishApproximation: String,
    val description: String,
    val examples: List<ExampleWord>
)

@Serializable
data class ExampleWord(
    val pl: String,
    val en: String
)
