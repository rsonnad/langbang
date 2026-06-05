package com.sponic.langbang.data

/**
 * Cleans the user-facing English translation line for generated examples.
 * Keep word-for-word `literal` / token glosses separate; this is only for the
 * normal English sentence shown at the top of Now Voicing.
 */
internal fun normalizeEnglishTranslation(text: String): String {
    if (text.isBlank()) return text
    return SUBJECT_MALFORMED_PAST_REGEX.replace(text.trim()) { match ->
        val prefix = match.groupValues[1]
        val subject = match.groupValues[2]
        val malformedVerb = match.groupValues[3]
        val fixedVerb = fixedSimplePast(subject, malformedVerb)
        "$prefix$subject $fixedVerb"
    }
}

private fun fixedSimplePast(subject: String, malformedVerb: String): String {
    val key = malformedVerb.lowercase().replace('’', '\'')
    if (key == "been" || key == "beed") return wasWereFor(subject)
    return SIMPLE_PAST_FIXES[key] ?: malformedVerb
}

private fun wasWereFor(subject: String): String =
    when (subject.lowercase().replace('’', '\'')) {
        "you", "we", "they", "y'all" -> "were"
        else -> "was"
    }

private val SIMPLE_PAST_FIXES = mapOf(
    "gived" to "gave",
    "given" to "gave",
    "taked" to "took",
    "taken" to "took",
    "thinked" to "thought",
    "eated" to "ate",
    "eaten" to "ate",
    "drinked" to "drank",
    "drunk" to "drank",
    "writed" to "wrote",
    "written" to "wrote",
    "seed" to "saw",
    "seen" to "saw",
    "speaked" to "spoke",
    "spoken" to "spoke",
    "knowed" to "knew",
    "known" to "knew",
    "doed" to "did",
    "done" to "did",
    "goed" to "went",
    "gone" to "went"
)

private val SUBJECT_MALFORMED_PAST_REGEX = Regex(
    "(^|[.!?;:]\\s+)(I|[Yy]ou|[Hh]e|[Ss]he|[Ii]t|[Ww]e|[Tt]hey|[Yy]['’]all)\\s+" +
        "(gived|given|taked|taken|thinked|eated|eaten|drinked|drunk|writed|" +
        "written|seed|seen|speaked|spoken|knowed|known|doed|done|beed|been|" +
        "goed|gone)\\b",
    RegexOption.IGNORE_CASE
)
