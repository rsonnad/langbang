package com.sponic.langbang.ui.quizzes

import com.sponic.langbang.data.LessonRepository
import com.sponic.langbang.data.VerbSentenceStore
import com.sponic.langbang.data.model.AdjectiveEntry
import com.sponic.langbang.data.model.NounEntry
import com.sponic.langbang.data.model.PERSON_KEYS
import com.sponic.langbang.data.model.SentenceExample
import com.sponic.langbang.data.model.TokenPair
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
            if (PracticeWordType.HELPERS in scope.wordTypes) {
                addAll(helperInfinitiveItems(repo, scope, effectiveStage))
            }
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
        return spreadPracticeItems(interleaveByKind(items))
    }

    private fun helperInfinitiveItems(
        repo: LessonRepository,
        scope: PracticeScope,
        stage: AutoPracticeStage
    ): List<PracticeItem> {
        val persons = effectivePersons(scope, stage)
        val tenses = effectiveTenses(scope, stage)
        if (persons.isEmpty() || tenses.isEmpty()) return emptyList()
        val verbsByLemma = repo.lesson2().verbs.associateBy { it.lemma.lowercase() }
        val patterns = helperPatterns(verbsByLemma, tenses)
        val infinitives = helperInfinitives(repo, stage)
        return patterns.flatMap { pattern ->
            val commonInfinitives = infinitives.filter { it.pl in pattern.infinitiveLemmas }
            persons.flatMap personCards@{ person ->
                val form = pattern.formFor(person)?.takeIf { it.isNotBlank() }
                    ?: return@personCards emptyList()
                val subject = englishSubjectFor(person)
                commonInfinitives.map { infinitive ->
                    val prompt = pattern.prompt(subject, person, infinitive.enBase)
                    val answerPl = "${audioPronoun(person)} $form ${infinitive.pl}".trim()
                    PracticeItem(
                        id = "helper-inf:${pattern.key}:${infinitive.pl}:$person",
                        kind = PracticeKind.HELPER_INFINITIVE,
                        prompt = prompt,
                        answerPl = answerPl,
                        answerEn = prompt,
                        context = "${pattern.context} · ${infinitive.pl} · ${personLabel(person)}",
                        words = listOf(
                            TokenPair(audioPronoun(person), subject),
                            TokenPair(form, pattern.gloss(person)),
                            TokenPair(infinitive.pl, pattern.infinitiveGloss(infinitive.enBase))
                        ),
                        targetLemma = "${pattern.lemma}+${infinitive.pl}",
                        targetForm = form,
                        personKey = person,
                        tense = pattern.tense,
                        grammarKey = "${pattern.key}:$person"
                    )
                }
            }
        }
    }

    fun missBurst(
        repo: LessonRepository,
        scope: PracticeScope,
        stage: AutoPracticeStage,
        missed: PracticeItem
    ): List<PracticeItem> {
        val all = buildItems(repo, scope, stage)
        val repeatedMiss = missed.id.contains("-miss-") || missed.context.contains("repeat")
        val sameCard: (PracticeItem) -> Boolean = { candidate ->
            candidate.prompt == missed.prompt && candidate.answerPl == missed.answerPl
        }
        val sameAnswer: (PracticeItem) -> Boolean = { candidate ->
            candidate.answerPl == missed.answerPl
        }
        val sameTarget = all
            .filter { it.id != missed.id }
            .filterNot { repeatedMiss && sameCard(it) }
            .filter { candidate ->
                candidate.targetLemma == missed.targetLemma &&
                    candidate.targetLemma != null &&
                    !sameAnswer(candidate) &&
                    (candidate.targetForm == missed.targetForm ||
                        candidate.kind.name.contains("SENTENCE"))
            }
        val nearbyForms = all
            .filter { it.id != missed.id }
            .filterNot { repeatedMiss && sameCard(it) }
            .filter { candidate ->
                candidate.targetLemma == missed.targetLemma &&
                    candidate.targetLemma != null &&
                    !sameAnswer(candidate) &&
                    candidate.targetForm != missed.targetForm
            }
        val combined = (sameTarget + nearbyForms)
            .distinctBy { "${it.prompt}|${it.answerPl}" }
            .take(5)
        if (combined.size >= 5) return spreadPracticeItems(combined)
        if (repeatedMiss) return spreadPracticeItems(combined)
        val filler = all
            .filterNot(sameCard)
            .filterNot(sameAnswer)
            .filter { it.targetLemma != missed.targetLemma }
            .take(5 - combined.size)
        return spreadPracticeItems(combined + filler)
    }

    fun successExpansion(
        repo: LessonRepository,
        scope: PracticeScope,
        stage: AutoPracticeStage,
        mastered: PracticeItem
    ): List<PracticeItem> {
        val target = mastered.targetLemma ?: return emptyList()
        val laterStages = AutoPracticeStage.entries
            .drop(stage.ordinal + 1)
        return laterStages
            .flatMap { buildItems(repo, scope, it) }
            .filter { it.targetLemma == target }
            .filter { it.answerPl != mastered.answerPl }
            .filter { it.id != mastered.id }
            .distinctBy { "${it.prompt}|${it.answerPl}" }
            .let(::spreadPracticeItems)
            .take(2)
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
                        words = listOf(TokenPair(audioPronoun(person), subject), TokenPair(form, enVerb)),
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
                            literal = sentence.literal,
                            words = sentence.words,
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
                        words = listOf(TokenPair(form, noun.en)),
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
                    literal = sentence.literal,
                    words = sentence.words,
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
                        words = listOf(TokenPair(form, adj.en)),
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
                    literal = sentence.literal,
                    words = sentence.words,
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
                words = listOf(TokenPair(adv.lemma, adv.en)),
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
                    literal = sentence.literal,
                    words = sentence.words,
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
        if (stage < AutoPracticeStage.FULL) return emptyList()
        return repo.lesson5().groups.flatMap { group ->
            group.sentences.mapIndexed { index, sentence ->
                PracticeItem(
                    id = "phrase:${group.id}:$index:${sentence.pl.hashCode()}",
                    kind = PracticeKind.PHRASE,
                    prompt = sentence.en,
                    answerPl = sentence.pl,
                    answerEn = sentence.en,
                    context = group.title,
                    literal = sentence.literal,
                    words = sentence.words,
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

    private fun helperPatterns(
        verbsByLemma: Map<String, VerbEntry>,
        tenses: List<String>
    ): List<HelperPattern> = buildList {
        if (VerbSentenceStore.TENSE_PRESENT in tenses) {
            lessonHelper(
                verbsByLemma = verbsByLemma,
                lemma = "chcieć",
                key = "want-present",
                tense = VerbSentenceStore.TENSE_PRESENT,
                context = "chcieć · want",
                englishVerb = "to want",
                takesTo = true,
                infinitiveLemmas = WantInfinitives
            )?.let(::add)
            lessonHelper(
                verbsByLemma = verbsByLemma,
                lemma = "musieć",
                key = "need-present",
                tense = VerbSentenceStore.TENSE_PRESENT,
                context = "musieć · need/have to",
                englishVerb = "to need",
                takesTo = true,
                infinitiveLemmas = NeedInfinitives
            )?.let(::add)
            lessonHelper(
                verbsByLemma = verbsByLemma,
                lemma = "móc",
                key = "can-present",
                tense = VerbSentenceStore.TENSE_PRESENT,
                context = "móc · can",
                englishVerb = "can",
                takesTo = false,
                infinitiveLemmas = CanInfinitives
            )?.let(::add)
            lessonHelper(
                verbsByLemma = verbsByLemma,
                lemma = "lubić",
                key = "like-present",
                tense = VerbSentenceStore.TENSE_PRESENT,
                context = "lubić · like to",
                englishVerb = "to like",
                takesTo = true,
                infinitiveLemmas = LikeInfinitives
            )?.let(::add)
            add(
                HelperPattern(
                    key = "could-conditional",
                    lemma = "móc",
                    tense = VerbSentenceStore.TENSE_PRESENT,
                    context = "móc · could",
                    formFor = { person -> ConditionalMocForms[person] },
                    prompt = { subject, _, infinitive -> "$subject could $infinitive" },
                    gloss = { "could" },
                    infinitiveGloss = { it },
                    infinitiveLemmas = CouldMightInfinitives
                )
            )
            add(
                HelperPattern(
                    key = "might-conditional",
                    lemma = "móc",
                    tense = VerbSentenceStore.TENSE_PRESENT,
                    context = "móc · might",
                    formFor = { person -> ConditionalMocForms[person] },
                    prompt = { subject, _, infinitive -> "$subject might $infinitive" },
                    gloss = { "might" },
                    infinitiveGloss = { it },
                    infinitiveLemmas = CouldMightInfinitives
                )
            )
            add(
                HelperPattern(
                    key = "should",
                    lemma = "powinien",
                    tense = VerbSentenceStore.TENSE_PRESENT,
                    context = "powinien · should",
                    formFor = { person -> ShouldForms[person] },
                    prompt = { subject, _, infinitive -> "$subject should $infinitive" },
                    gloss = { "should" },
                    infinitiveGloss = { it },
                    infinitiveLemmas = ShouldInfinitives
                )
            )
            add(
                HelperPattern(
                    key = "would-like",
                    lemma = "chcieć",
                    tense = VerbSentenceStore.TENSE_PRESENT,
                    context = "chcieć · would like",
                    formFor = { person -> WouldLikeForms[person] },
                    prompt = { subject, _, infinitive -> "$subject would like to $infinitive" },
                    gloss = { "would like" },
                    infinitiveGloss = { "to $it" },
                    infinitiveLemmas = WouldLikeInfinitives
                )
            )
        }
        if (VerbSentenceStore.TENSE_PAST in tenses) {
            lessonHelper(
                verbsByLemma = verbsByLemma,
                lemma = "chcieć",
                key = "want-past",
                tense = VerbSentenceStore.TENSE_PAST,
                context = "chcieć · wanted",
                englishVerb = "to want",
                takesTo = true,
                infinitiveLemmas = WantInfinitives
            )?.let(::add)
            lessonHelper(
                verbsByLemma = verbsByLemma,
                lemma = "musieć",
                key = "need-past",
                tense = VerbSentenceStore.TENSE_PAST,
                context = "musieć · needed",
                englishVerb = "to need",
                takesTo = true,
                infinitiveLemmas = NeedInfinitives
            )?.let(::add)
        }
    }

    private fun lessonHelper(
        verbsByLemma: Map<String, VerbEntry>,
        lemma: String,
        key: String,
        tense: String,
        context: String,
        englishVerb: String,
        takesTo: Boolean,
        infinitiveLemmas: Set<String>
    ): HelperPattern? {
        val verb = verbsByLemma[lemma] ?: return null
        val forms = formsFor(verb, tense)
        val past = tense == VerbSentenceStore.TENSE_PAST
        return HelperPattern(
            key = key,
            lemma = lemma,
            tense = tense,
            context = context,
            formFor = { person -> forms[person] },
            prompt = { subject, person, infinitive ->
                val helper = englishConjugate(englishVerb, person, past)
                val joiner = if (takesTo) " to " else " "
                "$subject $helper$joiner$infinitive"
            },
            gloss = { person -> englishConjugate(englishVerb, person, past) },
            infinitiveGloss = { infinitive -> if (takesTo) "to $infinitive" else infinitive },
            infinitiveLemmas = infinitiveLemmas
        )
    }

    private fun helperInfinitives(
        repo: LessonRepository,
        stage: AutoPracticeStage
    ): List<HelperInfinitive> {
        val wanted = when (stage) {
            AutoPracticeStage.CORE -> listOf("jeść", "iść", "pić", "czytać")
            AutoPracticeStage.WIDER -> listOf(
                "jeść", "iść", "pić", "czytać", "spać", "mówić", "pisać", "pracować"
            )
            else -> HelperInfinitiveLemmas
        }.toSet()
        return repo.lesson2().verbs
            .filter { it.lemma in wanted }
            .sortedBy { HelperInfinitiveLemmas.indexOf(it.lemma).takeIf { index -> index >= 0 } ?: Int.MAX_VALUE }
            .map { HelperInfinitive(pl = it.lemma, enBase = englishInfinitiveBase(it.en)) }
    }

    private fun englishInfinitiveBase(verbEn: String): String =
        verbEn.lowercase().trim()
            .substringBefore("/")
            .substringBefore("(")
            .trim()
            .removePrefix("to ")
            .trim()

    private data class HelperInfinitive(
        val pl: String,
        val enBase: String
    )

    private data class HelperPattern(
        val key: String,
        val lemma: String,
        val tense: String,
        val context: String,
        val formFor: (String) -> String?,
        val prompt: (subject: String, person: String, infinitive: String) -> String,
        val gloss: (person: String) -> String,
        val infinitiveGloss: (String) -> String,
        val infinitiveLemmas: Set<String>
    )

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

    private fun spreadPracticeItems(items: List<PracticeItem>): List<PracticeItem> {
        val remaining = items.toMutableList()
        val ordered = mutableListOf<PracticeItem>()
        val recentAnswers = ArrayDeque<String>()
        val recentTargets = ArrayDeque<String>()
        while (remaining.isNotEmpty()) {
            val bestIndex = remaining.indexOfFirst { candidate ->
                candidate.answerPl !in recentAnswers &&
                    candidate.spacingTarget() !in recentTargets
            }.takeIf { it >= 0 }
            val answerIndex = remaining.indexOfFirst { candidate ->
                candidate.answerPl !in recentAnswers
            }.takeIf { it >= 0 }
            val targetIndex = remaining.indexOfFirst { candidate ->
                candidate.spacingTarget() !in recentTargets
            }.takeIf { it >= 0 }
            val pickIndex = bestIndex ?: answerIndex ?: targetIndex ?: 0
            val next = remaining.removeAt(pickIndex)
            ordered += next
            recentAnswers.addLast(next.answerPl)
            recentTargets.addLast(next.spacingTarget())
            while (recentAnswers.size > 5) recentAnswers.removeFirst()
            while (recentTargets.size > 5) recentTargets.removeFirst()
        }
        return ordered
    }

    private fun PracticeItem.spacingTarget(): String =
        targetLemma ?: answerPl

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

    private val ConditionalMocForms = mapOf(
        "1sg" to "mógłbym",
        "2sg" to "mógłbyś",
        "3sg" to "mógłby",
        "1pl" to "moglibyśmy",
        "2pl" to "moglibyście",
        "3pl" to "mogliby"
    )

    private val ShouldForms = mapOf(
        "1sg" to "powinienem",
        "2sg" to "powinieneś",
        "3sg" to "powinien",
        "1pl" to "powinniśmy",
        "2pl" to "powinniście",
        "3pl" to "powinni"
    )

    private val WouldLikeForms = mapOf(
        "1sg" to "chciałbym",
        "2sg" to "chciałbyś",
        "3sg" to "chciałby",
        "1pl" to "chcielibyśmy",
        "2pl" to "chcielibyście",
        "3pl" to "chcieliby"
    )

    private val HelperInfinitiveLemmas = listOf(
        "jeść",
        "pić",
        "iść",
        "spać",
        "czytać",
        "pisać",
        "mówić",
        "pracować",
        "dzwonić",
        "czekać",
        "wracać",
        "płacić",
        "kupować",
        "pomagać",
        "pytać",
        "tańczyć"
    )

    private val WantInfinitives = setOf(
        "jeść", "pić", "iść", "spać", "czytać", "pisać", "mówić", "pracować",
        "dzwonić", "wracać", "kupować", "tańczyć"
    )
    private val NeedInfinitives = setOf(
        "iść", "jeść", "pić", "spać", "pracować", "płacić", "dzwonić", "czekać",
        "wracać", "pisać", "pytać", "pomagać"
    )
    private val CanInfinitives = setOf(
        "jeść", "pić", "iść", "czytać", "pisać", "mówić", "pracować", "spać",
        "tańczyć", "dzwonić", "czekać", "wracać", "płacić", "pomagać"
    )
    private val CouldMightInfinitives = setOf(
        "iść", "jeść", "pić", "czytać", "pisać", "dzwonić", "wracać", "pracować",
        "spać", "pomagać"
    )
    private val ShouldInfinitives = setOf(
        "iść", "jeść", "pić", "spać", "pracować", "czytać", "pisać", "dzwonić",
        "wracać", "płacić", "pomagać", "czekać", "pytać"
    )
    private val WouldLikeInfinitives = setOf(
        "jeść", "pić", "iść", "czytać", "mówić", "spać", "tańczyć", "kupować",
        "pracować"
    )
    private val LikeInfinitives = setOf(
        "jeść", "pić", "czytać", "pisać", "mówić", "pracować", "spać", "tańczyć",
        "pomagać"
    )
}
