package com.sponic.langbang.ui.common

import com.sponic.langbang.data.model.JapaneseReading
import com.sponic.langbang.domain.NowVoicing

/**
 * Language data is adapted into these two visual slots before it reaches
 * [NowVoicingBody]. The renderer itself stays language-neutral: line two is compact,
 * line three is emphasized, and the setting can swap those slots for every pack.
 */
data class NowVoicingTargetPresentation(
    val compactText: String,
    val emphasizedText: String
) {
    fun swapped(): NowVoicingTargetPresentation =
        NowVoicingTargetPresentation(compactText = emphasizedText, emphasizedText = compactText)
}

fun nowVoicingTargetPresentation(
    nowVoicing: NowVoicing?,
    targetLocale: String?,
    japaneseReading: JapaneseReading?,
    swapped: Boolean
): NowVoicingTargetPresentation? {
    val pinned = nowVoicing ?: return null
    val presentation = when (targetLocale) {
        // Beginners see the native Japanese as the compact confirmation and romaji as
        // the large, readable study line. The global swap setting is for learners who
        // prefer Japanese writing at the larger size.
        "ja-JP" -> japaneseReading?.let {
            NowVoicingTargetPresentation(
                compactText = it.japanese,
                emphasizedText = it.romaji
            )
        }
        // Polish has a large spelling line and a smaller Roman-pronunciation guide,
        // just as the established Polish presentation. The same global swap setting
        // lets a learner make the guide large instead.
        "pl-PL" -> NowVoicingTargetPresentation(
            compactText = polishRomanPronunciation(pinned.pl),
            emphasizedText = pinned.pl
        )
        else -> null
    }
    return if (swapped) presentation?.swapped() else presentation
}

/**
 * Lightweight, deterministic reading aid for Polish in the shared Now Voicing engine.
 * It deliberately favors an English learner's approximation over IPA; the detailed
 * pronunciation lesson remains the authoritative place for sound-by-sound teaching.
 */
private fun polishRomanPronunciation(text: String): String =
    text.lowercase()
        .replace("dż", "j")
        .replace("dź", "j")
        .replace("dzi", "jee")
        .replace("ci", "chee")
        .replace("si", "shee")
        .replace("zi", "zhee")
        .replace("ni", "nyee")
        .replace("cz", "ch")
        .replace("sz", "sh")
        .replace("rz", "zh")
        .replace("ch", "kh")
        .replace("dz", "dz")
        .replace("ć", "ch")
        .replace("ś", "sh")
        .replace("ź", "zh")
        .replace("ż", "zh")
        .replace("ń", "ny")
        .replace("ł", "w")
        .replace("ą", "ohn")
        .replace("ę", "ehn")
        .replace("ó", "oo")
        .replace("u", "oo")
        .replace("w", "v")
        .replace("j", "y")
        .replace("c", "ts")
        .replace("y", "ih")
        .replace("a", "ah")
        .replace("e", "eh")
        .replace("i", "ee")
        .replace("o", "oh")
