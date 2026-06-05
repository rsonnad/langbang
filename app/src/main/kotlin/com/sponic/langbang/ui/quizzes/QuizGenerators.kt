package com.sponic.langbang.ui.quizzes

import com.sponic.langbang.data.LessonRepository
import com.sponic.langbang.data.model.PERSON_KEYS
import com.sponic.langbang.data.model.VerbEntry
import com.sponic.langbang.data.VerbSentenceStore

/**
 * Builds [QuizQuestion] lists for every quiz type. Each generator pulls from the
 * canonical lesson data in [LessonRepository] (which already merges user-added
 * entries with the bundled assets), so quizzes pick up new content automatically
 * without any extra wiring.
 *
 * Distractors are drawn from the same pool the correct answer came from so the
 * quiz tests recall, not category. For verbs the pool is "other forms of this
 * verb" (per-verb) or "same person across other verbs" (cross-verb). For vocab
 * it's "other entries in the same lesson". Every generator returns an empty
 * list when its pool can't produce 4 distinct options for a single question —
 * the runner shows an empty-state screen rather than crashing.
 */
object QuizGenerators {

    private val PERSON_LABEL_EN: Map<String, String> = mapOf(
        "1sg" to "I",
        "2sg" to "you",
        "3sg" to "he / she / it",
        "1pl" to "we",
        "2pl" to "y'all",
        "3pl" to "they"
    )

    /**
     * Per-verb conjugation drill — pick a single verb, ask all 6 person forms in
     * the chosen tense. The verb is already shown in the header + context line,
     * so the prompt is JUST the person ("ty (you)", "he / she / it"). Repeating
     * the verb in every prompt would make the drill trivially auto-readable —
     * the recall the quiz is testing is "what form of THIS verb matches THIS
     * person?", and the person alone is enough of a cue.
     *
     * Distractors: the other 5 forms of the SAME verb, sampled randomly per
     * question so the user can't memorize positions across reruns.
     */
    fun perVerbConjugation(
        verb: VerbEntry,
        tense: String
    ): List<QuizQuestion> {
        val forms = if (tense == VerbSentenceStore.TENSE_PAST) verb.past_forms.orEmpty() else verb.forms
        val allForms = forms.values.filter { it.isNotBlank() }.distinct()
        if (allForms.size < 4) return emptyList()
        val tenseLabel = if (tense == VerbSentenceStore.TENSE_PAST) "past" else "present"
        return PERSON_KEYS.mapNotNull { k ->
            val correct = forms[k]?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val pool = allForms.filter { it != correct }
            if (pool.size < 3) return@mapNotNull null
            QuizQuestion(
                prompt = personPrompt(k),
                correct = correct,
                distractors = pool.shuffled().take(3),
                context = "${verb.lemma} (${verb.en}) · $tenseLabel"
            )
        }
    }

    /**
     * Polish pronoun + English subject — used as the prompt for per-verb quizzes
     * so the learner sees both the Polish cue ("ty") they'll hear in speech AND
     * the English subject ("you") that disambiguates 3sg gender. Cross-verb
     * quizzes already lock the person via the header, so they use [PERSON_LABEL_EN]
     * directly for variety.
     */
    private fun personPrompt(k: String): String = when (k) {
        "1sg" -> "ja (I)"
        "2sg" -> "ty (you)"
        "3sg" -> "on / ona (he / she)"
        "1pl" -> "my (we)"
        "2pl" -> "wy (y'all)"
        "3pl" -> "oni / one (they)"
        else -> k
    }

    /**
     * Cross-verb conjugation drill — lock one person slot, ask the form for every
     * verb in the lesson. Prompt: "to drink — he / she / it". Distractors: the
     * SAME person-slot conjugations of three other random verbs, so a learner has
     * to distinguish e.g. `pije` from `je` / `ma` / `robi`.
     */
    fun crossVerbConjugation(
        repo: LessonRepository,
        personKey: String,
        tense: String
    ): List<QuizQuestion> {
        val verbs = repo.lesson2().verbs
        val tenseLabel = if (tense == VerbSentenceStore.TENSE_PAST) "past" else "present"
        val pool: List<Pair<VerbEntry, String>> = verbs.mapNotNull { v ->
            val forms = if (tense == VerbSentenceStore.TENSE_PAST) v.past_forms.orEmpty() else v.forms
            forms[personKey]?.takeIf { it.isNotBlank() }?.let { v to it }
        }
        if (pool.size < 4) return emptyList()
        val allForms = pool.map { it.second }.distinct()
        return pool.mapNotNull { (v, correct) ->
            val distractorPool = allForms.filter { it != correct }
            if (distractorPool.size < 3) return@mapNotNull null
            QuizQuestion(
                prompt = "${v.en} — ${PERSON_LABEL_EN[personKey] ?: personKey}",
                correct = correct,
                distractors = distractorPool.shuffled().take(3),
                context = "$personKey · $tenseLabel"
            )
        }
    }

    /**
     * Pronoun case-form drill — for each pronoun × case (nom/acc/dat), prompt the
     * English subject + case label and expect the Polish form. Pulls from lesson 2's
     * `pronouns` list. Distractors are other case forms of any pronoun — this is
     * where confusion lives ("mi" vs "mnie" vs "mu").
     */
    fun pronounCaseForms(repo: LessonRepository): List<QuizQuestion> {
        val pronouns = repo.lesson2().pronouns
        val allForms = pronouns.flatMap { p ->
            p.case_forms.values.filter { it.isNotBlank() }
        }.distinct()
        if (allForms.size < 4) return emptyList()
        val caseLabel: (String) -> String = { c ->
            when (c) {
                "nom" -> "nominative"
                "acc" -> "accusative"
                "dat" -> "dative"
                "gen" -> "genitive"
                "ins" -> "instrumental"
                "loc" -> "locative"
                else -> c
            }
        }
        return pronouns.flatMap { p ->
            p.case_forms.entries.mapNotNull { (caseKey, form) ->
                if (form.isBlank()) return@mapNotNull null
                val pool = allForms.filter { it != form }
                if (pool.size < 3) return@mapNotNull null
                QuizQuestion(
                    prompt = "${p.en} — ${caseLabel(caseKey)}",
                    correct = form,
                    distractors = pool.shuffled().take(3),
                    context = "Pronoun · ${p.lemma}"
                )
            }
        }
    }
}
