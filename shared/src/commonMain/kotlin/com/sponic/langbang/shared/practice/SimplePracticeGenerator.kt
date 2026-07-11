package com.sponic.langbang.shared.practice

/**
 * Minimal port of verbFormItems logic + englishSubjectFor from LangBangML's
 * PracticeGenerators + EnglishConjugator.
 * Pure Kotlin — no platform, no repo.
 */
object SimplePracticeGenerator {

    fun buildVerbFormItems(limit: Int = 6): List<PracticeItem> {
        val items = mutableListOf<PracticeItem>()
        val persons = SimplePracticeData.personKeys
        for (verb in SimplePracticeData.verbs) {
            for (person in persons) {
                val form = verb.forms[person] ?: continue
                val subject = englishSubjectFor(person)
                val enPrompt = "$subject ${englishConjugateSimple(verb.en, person)}"
                val pl = "${audioPronoun(person)} $form".trim()
                items += PracticeItem(
                    id = "verb-form:${verb.lemma}:present:$person",
                    kind = PracticeKind.VERB_FORM,
                    prompt = enPrompt,
                    answerPl = pl,
                    answerEn = enPrompt,
                    context = "${verb.lemma} · present · ${personLabel(person)}",
                    targetLemma = verb.lemma,
                    targetForm = form,
                )
                if (items.size >= limit) return items
            }
        }
        return items
    }

    private fun englishSubjectFor(personKey: String): String = when (personKey) {
        "1sg" -> "I"
        "2sg" -> "you"
        "3sg" -> "he"
        "1pl" -> "we"
        "2pl" -> "y'all"
        "3pl" -> "they"
        else -> personKey
    }

    private fun audioPronoun(personKey: String): String = when (personKey) {
        "1sg" -> "ja"
        "2sg" -> "ty"
        "3sg" -> "on"
        "1pl" -> "my"
        "2pl" -> "wy"
        "3pl" -> "oni"
        else -> ""
    }

    private fun personLabel(personKey: String): String = when (personKey) {
        "1sg" -> "1sg"
        "3sg" -> "3sg"
        else -> personKey
    }

    /**
     * Ultra-minimal englishConjugate for present tense demo (subset of full EnglishConjugator).
     */
    private fun englishConjugateSimple(verbEn: String, personKey: String): String {
        val base = verbEn.lowercase().trim()
            .substringBefore("/")
            .substringBefore("(")
            .trim()
            .removePrefix("to ")
            .trim()
        if (personKey != "3sg") return base
        return when {
            base.endsWith("s") || base.endsWith("x") || base.endsWith("ch") ||
                base.endsWith("sh") || base.endsWith("o") -> base + "es"
            base.endsWith("y") && base.length > 1 && base[base.length - 2] !in "aeiou" ->
                base.dropLast(1) + "ies"
            else -> base + "s"
        }
    }
}