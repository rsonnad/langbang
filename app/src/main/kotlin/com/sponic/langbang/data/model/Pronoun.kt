package com.sponic.langbang.data.model

/**
 * Single Polish pronoun used when speaking a conjugated form aloud — e.g. "ja" before
 * "jestem" so the audio matches a natural sentence subject. The 3sg case picks "on" and
 * the 3pl case picks "oni" arbitrarily (the conjugated verb form is identical across the
 * gendered variants).
 */
fun audioPronoun(personKey: String): String = when (personKey) {
    "1sg" -> "ja"
    "2sg" -> "ty"
    "3sg" -> "on"
    "1pl" -> "my"
    "2pl" -> "wy"
    "3pl" -> "oni"
    else -> ""
}

/** Canonical order for conjugation paradigm rows. */
val PERSON_KEYS: List<String> = listOf("1sg", "2sg", "3sg", "1pl", "2pl", "3pl")
