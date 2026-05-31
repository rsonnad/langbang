package com.sponic.langbang.domain

/**
 * Conjugates an English verb lemma (like "to be" / "to go") into the form that
 * agrees with a given Polish person key. Used by every playback path that
 * needs to render or speak "I am" / "she goes" / "they went" instead of the
 * dictionary entry "to be" / "to go".
 *
 * Two render paths used to duplicate this logic — Random Play
 * ([com.sponic.langbang.ui.RandomPlayer]) and the Verbs tab's "Play All
 * Conjugations" button ([com.sponic.langbang.ui.lessons.VerbsTab]). The first
 * fix for "he — to be" → "he is" only landed in RandomPlayer, so the Verbs tab
 * regressed silently. Pulling it here so both paths converge and the bug class
 * can't recur the next time a third playback surface is added.
 *
 * Person keys: "1sg" "2sg" "3sg" "1pl" "2pl" "3pl".
 *
 * [verbEn] is the lemma's `en` field from `lesson-02.json` — typically prefixed
 * with "to ", possibly with parentheticals ("to live (reside)") or an "/"
 * alternative ("can / to be able"). [past] picks the past-tense table.
 */
fun englishConjugate(verbEn: String, personKey: String, past: Boolean): String {
    val base = verbEn.lowercase().trim()
        .substringBefore("/")
        .substringBefore("(")
        .trim()
        .removePrefix("to ")
        .trim()
    val table = if (past) IRREGULAR_PAST else IRREGULAR_PRESENT
    table[base]?.let { return it[personKey] ?: defaultForm(base, personKey, past) }
    return defaultForm(base, personKey, past)
}

/**
 * Renders the English subject pronoun for a person key. Matches the gloss
 * convention used in the verb-sentence Gemini prompts ("y'all" for 2pl, "he"
 * as the canonical 3sg even when "she" / "it" would also be valid — the panel
 * surfaces gender separately).
 */
fun englishSubjectFor(personKey: String): String = when (personKey) {
    "1sg" -> "I"
    "2sg" -> "you"
    "3sg" -> "he"
    "1pl" -> "we"
    "2pl" -> "y'all"
    "3pl" -> "they"
    else -> personKey
}

private fun defaultForm(base: String, personKey: String, past: Boolean): String {
    if (past) return regularPast(base)
    return when (personKey) {
        "3sg" -> regularPresent3sg(base)
        else -> base
    }
}

private fun regularPresent3sg(base: String): String = when {
    base.endsWith("s") || base.endsWith("x") || base.endsWith("ch") ||
        base.endsWith("sh") || base.endsWith("o") -> base + "es"
    base.endsWith("y") && base.length > 1 && base[base.length - 2] !in "aeiou" ->
        base.dropLast(1) + "ies"
    else -> base + "s"
}

private fun regularPast(base: String): String = when {
    base.endsWith("e") -> base + "d"
    base.endsWith("y") && base.length > 1 && base[base.length - 2] !in "aeiou" ->
        base.dropLast(1) + "ied"
    else -> base + "ed"
}

// 6 slots, indexed by person key: "I am / you are / he is / we are / y'all are / they are".
private val IRREGULAR_PRESENT: Map<String, Map<String, String>> = mapOf(
    "be" to mapOf("1sg" to "am", "2sg" to "are", "3sg" to "is",
        "1pl" to "are", "2pl" to "are", "3pl" to "are"),
    "have" to mapOf("1sg" to "have", "2sg" to "have", "3sg" to "has",
        "1pl" to "have", "2pl" to "have", "3pl" to "have"),
    "do" to mapOf("1sg" to "do", "2sg" to "do", "3sg" to "does",
        "1pl" to "do", "2pl" to "do", "3pl" to "do"),
    "go" to mapOf("1sg" to "go", "2sg" to "go", "3sg" to "goes",
        "1pl" to "go", "2pl" to "go", "3pl" to "go"),
    "say" to mapOf("1sg" to "say", "2sg" to "say", "3sg" to "says",
        "1pl" to "say", "2pl" to "say", "3pl" to "say"),
    // Modals are invariant — same form for every person.
    "can" to mapOf("1sg" to "can", "2sg" to "can", "3sg" to "can",
        "1pl" to "can", "2pl" to "can", "3pl" to "can"),
    "must" to mapOf("1sg" to "must", "2sg" to "must", "3sg" to "must",
        "1pl" to "must", "2pl" to "must", "3pl" to "must")
)

private fun invariantPast(form: String): Map<String, String> =
    mapOf("1sg" to form, "2sg" to form, "3sg" to form,
        "1pl" to form, "2pl" to form, "3pl" to form)

private val IRREGULAR_PAST: Map<String, Map<String, String>> = mapOf(
    "be" to mapOf("1sg" to "was", "2sg" to "were", "3sg" to "was",
        "1pl" to "were", "2pl" to "were", "3pl" to "were"),
    "have" to invariantPast("had"),
    "do" to invariantPast("did"),
    "go" to invariantPast("went"),
    "see" to invariantPast("saw"),
    "eat" to invariantPast("ate"),
    "drink" to invariantPast("drank"),
    "read" to invariantPast("read"),
    "write" to invariantPast("wrote"),
    "buy" to invariantPast("bought"),
    "feel" to invariantPast("felt"),
    "sleep" to invariantPast("slept"),
    "meet" to invariantPast("met"),
    "pay" to invariantPast("paid"),
    "speak" to invariantPast("spoke"),
    "understand" to invariantPast("understood"),
    "know" to invariantPast("knew"),
    "say" to invariantPast("said"),
    "can" to invariantPast("could"),
    "must" to invariantPast("had to")
)
