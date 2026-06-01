package com.sponic.langbang.ui.quizzes

import com.sponic.langbang.data.LessonRepository
import com.sponic.langbang.data.VerbSentenceStore
import com.sponic.langbang.data.model.AdjectiveEntry
import com.sponic.langbang.data.model.NounEntry
import com.sponic.langbang.data.model.PERSON_KEYS
import com.sponic.langbang.data.model.SentenceExample
import com.sponic.langbang.data.model.VerbEntry
import com.sponic.langbang.data.model.audioPronoun
import com.sponic.langbang.domain.englishConjugate
import com.sponic.langbang.domain.englishSubjectFor

object PracticeGenerators {

    fun buildItems(
        repo: LessonRepository,
        scope: PracticeScope,
        stage: AutoPracticeStage
    ): List<PracticeItem> {
        val effectiveStage = if (scope.auto) stage else AutoPracticeStage.MIXED
        val items = buildList {
            if (PracticeWordType.VERBS in scope.wordTypes) {
                addAll(verbFormItems(repo, scope, effectiveStage))
                addAll(verbSentenceItems(repo, scope, effectiveStage))
            }
            if (PracticeWordType.NOUNS in scope.wordTypes) {
                addAll(nounFormItems(repo, effectiveStage))
                addAll(nounSentenceItems(repo, effectiveStage))
            }
            if (PracticeWordType.ADJECTIVES in scope.wordTypes) {
                addAll(adjectiveFormItems(repo, effectiveStage))
                addAll(adjectiveSentenceItems(repo, effectiveStage))
            }
            if (PracticeWordType.ADVERBS in scope.wordTypes) {
                addAll(adverbItems(repo, effectiveStage))
            }
            if (PracticeWordType.PHRASES in scope.wordTypes) {
                addAll(phraseItems(repo, effectiveStage))
            }
        }
        return interleaveByKind(items)
    }

    fun missBurst(
        repo: LessonRepository,
        scope: PracticeScope,
        stage: AutoPracticeStage,
        missed: PracticeItem
    ): List<PracticeItem> {
        val all = buildItems(repo, scope, stage)
        val exact = missed.copy(id = "${missed.id}-miss-repeat", context = "${missed.context} · repeat")
        val sameTarget = all
            .filter { it.id != missed.id }
            .filter { candidate ->
                candidate.targetLemma == missed.targetLemma &&
                    candidate.targetLemma != null &&
                    (candidate.targetForm == missed.targetForm ||
                        candidate.kind.name.contains("SENTENCE"))
            }
        val nearbyForms = all
            .filter { it.id != missed.id }
            .filter { candidate ->
                candidate.targetLemma == missed.targetLemma &&
                    candidate.targetLemma != null &&
                    candidate.targetForm != missed.targetForm
            }
        val combined = (listOf(exact) + sameTarget + nearbyForms)
            .distinctBy { "${it.prompt}|${it.answerPl}" }
            .take(5)
        if (combined.size >= 5) return combined
        return combined + List(5 - combined.size) { i ->
            missed.copy(
                id = "${missed.id}-miss-fallback-$i",
                context = "${missed.context} · repeat ${i + 2}"
            )
        }
    }

    private fun verbFormItems(
        repo: LessonRepository,
        scope: PracticeScope,
        stage: AutoPracticeStage
    ): List<PracticeItem> {
        val persons = effectivePersons(scope, stage)
        val tenses = effectiveTenses(scope, stage)
        return repo.lesson2().verbs.flatMap { verb ->
            tenses.flatMap { tense ->
                val forms = formsFor(verb, tense)
                persons.mapNotNull { person ->
                    val form = forms[person]?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    val subject = englishSubjectFor(person)
                    val past = tense == VerbSentenceStore.TENSE_PAST
                    val enVerb = englishConjugate(verb.en, person, past)
                    val prompt = "$subject $enVerb"
                    val pl = "${audioPronoun(person)} $form".trim()
                    PracticeItem(
                        id = "verb-form:${verb.lemma}:$tense:$person",
                        kind = PracticeKind.VERB_FORM,
                        prompt = prompt,
                        answerPl = pl,
                        answerEn = prompt,
                        context = "${verb.lemma} · $tense · ${personLabel(person)}",
                        targetLemma = verb.lemma,
                        targetForm = form,
                        personKey = person,
                        tense = tense,
                        grammarKey = "$tense:$person"
                    )
                }
            }
        }
    }

    private fun verbSentenceItems(
        repo: LessonRepository,
        scope: PracticeScope,
        stage: AutoPracticeStage
    ): List<PracticeItem> {
        if (stage < AutoPracticeStage.FULL) return emptyList()
        val persons = effectivePersons(scope, stage)
        val tenses = effectiveTenses(scope, stage)
        val maxWords = if (stage == AutoPracticeStage.FULL) 6 else 10
        return repo.lesson2().verbs.flatMap { verb ->
            tenses.flatMap { tense ->
                val forms = formsFor(verb, tense)
                val allowedForms = forms
                    .filterKeys { it in persons }
                    .values
                    .filter { it.isNotBlank() }
                    .map { it.lowercase() }
                    .toSet()
                if (allowedForms.isEmpty()) emptyList()
                else repo.sentencesFor(verb.lemma, tense)
                    .filter { tokenCount(it.pl) <= maxWords }
                    .filter { containsAnyToken(it.pl, allowedForms) }
                    .take(6)
                    .mapIndexed { index, sentence ->
                        val form = firstMatchingToken(sentence.pl, allowedForms)
                        PracticeItem(
                            id = "verb-sentence:${verb.lemma}:$tense:$index:${sentence.pl.hashCode()}",
                            kind = PracticeKind.VERB_SENTENCE,
                            prompt = sentence.en,
                            answerPl = sentence.pl,
                            answerEn = sentence.en,
                            context = "${verb.lemma} · sentence · $tense",
                            targetLemma = verb.lemma,
                            targetForm = form,
                            tense = tense
                        )
                    }
            }
        }
    }

    private fun nounFormItems(
        repo: LessonRepository,
        stage: AutoPracticeStage
    ): List<PracticeItem> {
        val cases = when (stage) {
            AutoPracticeStage.CORE -> listOf("acc")
            AutoPracticeStage.WIDER -> listOf("acc", "nom")
            else -> listOf("nom", "acc", "gen")
        }
        val numbers = if (stage < AutoPracticeStage.FULL) listOf("sg") else listOf("sg", "pl")
        return repo.lesson6().nouns.flatMap { noun ->
            cases.flatMap { caseKey ->
                val forms = noun.caseMap(caseKey)
                numbers.mapNotNull { numberKey ->
                    val form = forms[numberKey]?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    val cue = "${noun.en} · ${numberLabel(numberKey)} ${caseLabel(caseKey)}"
                    PracticeItem(
                        id = "noun-form:${noun.lemma}:$caseKey:$numberKey",
                        kind = PracticeKind.NOUN_FORM,
                        prompt = cue,
                        answerPl = form,
                        answerEn = noun.en,
                        context = "${noun.lemma} · ${genderLabel(noun.gender)}",
                        targetLemma = noun.lemma,
                        targetForm = form,
                        grammarKey = "$caseKey:$numberKey:${noun.gender}"
                    )
                }
            }
        }
    }

    private fun nounSentenceItems(
        repo: LessonRepository,
        stage: AutoPracticeStage
    ): List<PracticeItem> {
        if (stage < AutoPracticeStage.MIXED) return emptyList()
        return repo.lesson6().nouns.flatMap { noun ->
            repo.nounSentencesFor(noun.lemma).take(3).mapIndexed { index, sentence ->
                PracticeItem(
                    id = "noun-sentence:${noun.lemma}:$index:${sentence.pl.hashCode()}",
                    kind = PracticeKind.PHRASE,
                    prompt = sentence.en,
                    answerPl = sentence.pl,
                    answerEn = sentence.en,
                    context = "${noun.lemma} · noun sentence",
                    targetLemma = noun.lemma
                )
            }
        }
    }

    private fun adjectiveFormItems(
        repo: LessonRepository,
        stage: AutoPracticeStage
    ): List<PracticeItem> {
        val cases = if (stage == AutoPracticeStage.CORE) listOf("nom") else listOf("nom", "acc")
        val genders = when (stage) {
            AutoPracticeStage.CORE -> listOf("m", "f")
            AutoPracticeStage.WIDER -> listOf("m", "f", "n")
            else -> listOf("m", "f", "n", "mp", "other")
        }
        return repo.lesson3().adjectives.flatMap { adj ->
            cases.flatMap { caseKey ->
                val forms = adj.caseMap(caseKey)
                genders.mapNotNull { gender ->
                    val form = forms[gender]?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    PracticeItem(
                        id = "adj-form:${adj.lemma}:$caseKey:$gender",
                        kind = PracticeKind.ADJECTIVE_FORM,
                        prompt = "${adj.en} · ${genderLabel(gender)} ${caseLabel(caseKey)}",
                        answerPl = form,
                        answerEn = adj.en,
                        context = adj.lemma,
                        targetLemma = adj.lemma,
                        targetForm = form,
                        grammarKey = "$caseKey:$gender"
                    )
                }
            }
        }
    }

    private fun adjectiveSentenceItems(
        repo: LessonRepository,
        stage: AutoPracticeStage
    ): List<PracticeItem> {
        if (stage < AutoPracticeStage.MIXED) return emptyList()
        return repo.lesson3().adjectives.flatMap { adj ->
            repo.adjectiveSentencesFor(adj.lemma).take(3).mapIndexed { index, sentence ->
                PracticeItem(
                    id = "adj-sentence:${adj.lemma}:$index:${sentence.pl.hashCode()}",
                    kind = PracticeKind.PHRASE,
                    prompt = sentence.en,
                    answerPl = sentence.pl,
                    answerEn = sentence.en,
                    context = "${adj.lemma} · adjective sentence",
                    targetLemma = adj.lemma
                )
            }
        }
    }

    private fun adverbItems(
        repo: LessonRepository,
        stage: AutoPracticeStage
    ): List<PracticeItem> {
        val lemmaItems = repo.lesson4().adverbs.map { adv ->
            PracticeItem(
                id = "adv-lemma:${adv.lemma}",
                kind = PracticeKind.ADVERB_LEMMA,
                prompt = adv.en,
                answerPl = adv.lemma,
                answerEn = adv.en,
                context = "Adverb",
                targetLemma = adv.lemma,
                targetForm = adv.lemma
            )
        }
        if (stage < AutoPracticeStage.FULL) return lemmaItems
        val sentences = repo.lesson4().adverbs.flatMap { adv ->
            repo.adverbSentencesFor(adv.lemma).take(4).mapIndexed { index, sentence ->
                PracticeItem(
                    id = "adv-sentence:${adv.lemma}:$index:${sentence.pl.hashCode()}",
                    kind = PracticeKind.ADVERB_SENTENCE,
                    prompt = sentence.en,
                    answerPl = sentence.pl,
                    answerEn = sentence.en,
                    context = "${adv.lemma} · adverb sentence",
                    targetLemma = adv.lemma
                )
            }
        }
        return lemmaItems + sentences
    }

    private fun phraseItems(
        repo: LessonRepository,
        stage: AutoPracticeStage
    ): List<PracticeItem> {
        if (stage < AutoPracticeStage.MIXED) return emptyList()
        return repo.lesson5().groups.flatMap { group ->
            group.sentences.mapIndexed { index, sentence ->
                PracticeItem(
                    id = "phrase:${group.id}:$index:${sentence.pl.hashCode()}",
                    kind = PracticeKind.PHRASE,
                    prompt = sentence.en,
                    answerPl = sentence.pl,
                    answerEn = sentence.en,
                    context = group.title,
                    targetLemma = group.id
                )
            }
        }
    }

    private fun effectivePersons(
        scope: PracticeScope,
        stage: AutoPracticeStage
    ): List<String> {
        val allowed = PERSON_KEYS.filter { it in scope.personKeys }
        if (allowed.isEmpty()) return emptyList()
        if (!scope.auto) return allowed
        val core = allowed.filter { it in setOf("1sg", "2sg", "3sg") }
            .ifEmpty { allowed.take(1) }
        return when (stage) {
            AutoPracticeStage.CORE -> core
            AutoPracticeStage.WIDER -> (core + allowed.filter { it !in core }.take(2)).distinct()
            else -> allowed
        }
    }

    private fun effectiveTenses(
        scope: PracticeScope,
        stage: AutoPracticeStage
    ): List<String> {
        val allowed = RandomTenses.filter { it in scope.tenses }
        if (allowed.isEmpty()) return emptyList()
        if (!scope.auto) return allowed
        val present = listOf(VerbSentenceStore.TENSE_PRESENT).filter { it in allowed }
        return when (stage) {
            AutoPracticeStage.CORE,
            AutoPracticeStage.WIDER -> present.ifEmpty { allowed.take(1) }
            else -> allowed
        }
    }

    private fun formsFor(verb: VerbEntry, tense: String): Map<String, String> =
        if (tense == VerbSentenceStore.TENSE_PAST) verb.past_forms.orEmpty() else verb.forms

    private fun NounEntry.caseMap(key: String): Map<String, String> = when (key) {
        "nom" -> nom
        "acc" -> acc
        "gen" -> gen
        else -> emptyMap()
    }

    private fun AdjectiveEntry.caseMap(key: String): Map<String, String> = when (key) {
        "nom" -> nom
        "acc" -> acc
        else -> emptyMap()
    }

    private fun interleaveByKind(items: List<PracticeItem>): List<PracticeItem> {
        val pools = items.groupBy { it.kind }.values.map { it.shuffled().toMutableList() }
        return buildList {
            while (pools.any { it.isNotEmpty() }) {
                pools.forEach { pool ->
                    if (pool.isNotEmpty()) add(pool.removeAt(0))
                }
            }
        }
    }

    private fun containsAnyToken(text: String, allowed: Set<String>): Boolean =
        tokens(text).any { it in allowed }

    private fun firstMatchingToken(text: String, allowed: Set<String>): String? =
        tokens(text).firstOrNull { it in allowed }

    private fun tokens(text: String): List<String> =
        text.lowercase().split(Regex("[^\\p{L}]+")).filter { it.isNotBlank() }

    private fun tokenCount(text: String): Int =
        text.trim().split(Regex("\\s+")).count { it.isNotBlank() }

    private fun personLabel(k: String): String = when (k) {
        "1sg" -> "ja"
        "2sg" -> "ty"
        "3sg" -> "on/ona"
        "1pl" -> "my"
        "2pl" -> "wy"
        "3pl" -> "oni/one"
        else -> k
    }

    private fun caseLabel(k: String): String = when (k) {
        "nom" -> "nominative"
        "acc" -> "accusative"
        "gen" -> "genitive"
        else -> k
    }

    private fun numberLabel(k: String): String = when (k) {
        "sg" -> "singular"
        "pl" -> "plural"
        else -> k
    }

    private fun genderLabel(k: String): String = when (k) {
        "m" -> "masculine"
        "f" -> "feminine"
        "n" -> "neuter"
        "mp" -> "men/mixed plural"
        "other" -> "other plural"
        else -> k
    }

    private val RandomTenses = listOf(
        VerbSentenceStore.TENSE_PRESENT,
        VerbSentenceStore.TENSE_PAST
    )
}
