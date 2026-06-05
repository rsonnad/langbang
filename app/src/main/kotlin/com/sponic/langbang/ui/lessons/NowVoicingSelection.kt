package com.sponic.langbang.ui.lessons

import com.sponic.langbang.data.model.AdjectiveEntry
import com.sponic.langbang.data.model.AdverbEntry
import com.sponic.langbang.data.model.NounEntry
import com.sponic.langbang.data.model.VerbEntry
import com.sponic.langbang.domain.NowVoicing

internal fun nowVoicingVerb(nowVoicing: NowVoicing?, verbs: List<VerbEntry>): VerbEntry? =
    findNowVoicingEntry(nowVoicing, verbs) { it.polishVerbTokens() }

internal fun nowVoicingAdjective(
    nowVoicing: NowVoicing?,
    adjectives: List<AdjectiveEntry>
): AdjectiveEntry? =
    findNowVoicingEntry(nowVoicing, adjectives) { it.polishAdjectiveTokens() }

internal fun nowVoicingAdverb(nowVoicing: NowVoicing?, adverbs: List<AdverbEntry>): AdverbEntry? =
    findNowVoicingEntry(nowVoicing, adverbs) { it.polishAdverbTokens() }

internal fun nowVoicingNoun(nowVoicing: NowVoicing?, nouns: List<NounEntry>): NounEntry? =
    findNowVoicingEntry(nowVoicing, nouns) { it.polishNounTokens() }

private fun <T> findNowVoicingEntry(
    nowVoicing: NowVoicing?,
    entries: List<T>,
    tokensFor: (T) -> Set<String>
): T? {
    if (nowVoicing == null) return null
    val entriesByToken = buildMap {
        entries.forEach { entry ->
            tokensFor(entry).forEach { token -> putIfAbsent(token, entry) }
        }
    }
    return nowVoicing.polishTokensInOrder()
        .firstNotNullOfOrNull { token -> entriesByToken[token] }
}

internal fun VerbEntry.polishVerbTokens(): Set<String> = buildSet {
    addAll(lemma.lessonPolishTokens())
    forms.values.forEach { addAll(it.lessonPolishTokens()) }
    past_forms?.values?.forEach { addAll(it.lessonPolishTokens()) }
}

internal fun AdjectiveEntry.polishAdjectiveTokens(): Set<String> = buildSet {
    addAll(lemma.lessonPolishTokens())
    nom.values.forEach { addAll(it.lessonPolishTokens()) }
    acc.values.forEach { addAll(it.lessonPolishTokens()) }
}

internal fun AdverbEntry.polishAdverbTokens(): Set<String> =
    lemma.lessonPolishTokens().toSet()

internal fun NounEntry.polishNounTokens(): Set<String> = buildSet {
    addAll(lemma.lessonPolishTokens())
    nom.values.forEach { addAll(it.lessonPolishTokens()) }
    acc.values.forEach { addAll(it.lessonPolishTokens()) }
    gen.values.forEach { addAll(it.lessonPolishTokens()) }
}

internal fun NowVoicing.polishTokensInOrder(): List<String> {
    val fromWords = words?.flatMap { it.pl.lessonPolishTokens() }.orEmpty()
    return if (fromWords.isNotEmpty()) fromWords else pl.lessonPolishTokens()
}

internal fun String.lessonPolishTokens(): List<String> =
    lowercase()
        .split(Regex("[^\\p{L}]+"))
        .filter { it.isNotBlank() }
