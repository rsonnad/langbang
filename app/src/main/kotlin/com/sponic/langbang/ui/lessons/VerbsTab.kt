package com.sponic.langbang.ui.lessons

import com.sponic.langbang.ui.theme.LbColors

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sponic.langbang.LangbangApplication
import com.sponic.langbang.ui.common.SelectionNavButtons
import com.sponic.langbang.domain.PrefetchProgress
import com.sponic.langbang.domain.PrefetchWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Constraints
import androidx.work.NetworkType
import com.sponic.langbang.data.PracticePrefsStore
import com.sponic.langbang.data.PronounFilterStore
import com.sponic.langbang.data.VerbSentenceStore
import com.sponic.langbang.data.model.ConjugationClass
import com.sponic.langbang.data.model.AdjectiveEntry
import com.sponic.langbang.data.model.AdverbEntry
import com.sponic.langbang.data.model.NounEntry
import com.sponic.langbang.data.model.PERSON_KEYS
import com.sponic.langbang.data.model.SentenceExample
import com.sponic.langbang.data.model.TokenPair
import com.sponic.langbang.data.model.VerbEntry
import com.sponic.langbang.data.model.audioPronoun
import com.sponic.langbang.data.model.conjugationClass
import com.sponic.langbang.integrations.GeminiClient
import com.sponic.langbang.domain.NowVoicing
import com.sponic.langbang.domain.NowVoicingBus
import com.sponic.langbang.domain.PlaybackController
import com.sponic.langbang.domain.ensureCachedAudio
import com.sponic.langbang.domain.englishConjugate
import com.sponic.langbang.domain.playAudioAndAwait
import com.sponic.langbang.domain.sourceAudioVoice
import com.sponic.langbang.domain.targetAudioVoice
import com.sponic.langbang.domain.targetSlowVoice
import com.sponic.langbang.domain.targetSubjectFor
import com.sponic.langbang.integrations.AzureTtsClient
import com.sponic.langbang.ui.common.CompactLessonListCard
import com.sponic.langbang.ui.common.StudyQueuePlayer
import com.sponic.langbang.ui.common.CompactLessonListDefaults
import com.sponic.langbang.ui.common.DelayedEnglishTranslation
import com.sponic.langbang.ui.common.GrammarVisuals
import com.sponic.langbang.ui.common.LbButton
import com.sponic.langbang.ui.common.SubtleCheckbox
import com.sponic.langbang.ui.common.VariablePolishText
import com.sponic.langbang.ui.common.WordListPlaybackHeader
import com.sponic.langbang.ui.common.WordPlayLimitControl
import com.sponic.langbang.ui.common.variableEndForPolishForm
import com.sponic.langbang.ui.common.variableStartForPolishForm
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val WhitespaceRegex = Regex("\\s+")
private val NonLetterRegex = Regex("[^\\p{L}]+")
private val NonAlphaNumericRegex = Regex("[^\\p{L}\\p{N}]+")
private val AwkwardVerbPhraseEnglishRegexes = listOf(
    Regex("\\b(well|quickly|fast|gladly|willingly) wanted\\b"),
    Regex("\\b(well|quickly|fast|gladly|willingly) want\\b"),
    Regex("\\bwanted a (good|bad|big|small|new) (cat|dog)\\b"),
    Regex("\\bwant a (good|bad|big|small|new) (cat|dog)\\b"),
    Regex("\\bcan to \\w+\\b"),
    Regex("\\bmust to \\w+\\b"),
    Regex("\\bfeel like to \\w+\\b")
)

private data class ConjugationCue(
    val lemma: String,
    val combined: String,
    val englishGloss: String,
    val tokens: List<TokenPair>
)

private data class PhraseVerbCue(
    val personKey: String,
    val form: String,
    val englishVerb: String,
    val past: Boolean
)

private data class PhrasePart(
    val words: List<TokenPair>,
    val en: String,
    val literal: String
)

private data class HelperPhrasePart(
    val lemma: String,
    val words: List<TokenPair>,
    val en: String,
    val literal: String
)

private data class PhraseRequirementSnapshot(
    val includePronouns: Boolean,
    val includeHelperVerb: Boolean,
    val includeAdjectives: Boolean,
    val includeAdverbs: Boolean,
    val includeNouns: Boolean,
    val minimumWordCount: Int,
    val pronounTokens: Set<String>,
    val adjectiveTokens: Set<String>,
    val adverbTokens: Set<String>,
    val nounTokens: Set<String>,
    val helperForms: Set<String>,
    val knownInfinitives: Set<String>,
    val rejectedQualityKeys: Set<String>,
    val randomOrder: Boolean
)

private enum class EnglishComplementStyle {
    ToInfinitive,
    BareInfinitive,
    Gerund
}

/**
 * Holds the sentence-generation + playback state for the Verbs tab. Lives at the tab root
 * so the control strip (Generate / Play / Regenerate / All verbs) can render in the
 * top bar — always visible — while the sentence list lives further down in the scroll.
 */
internal class VerbsTabState(
    private val app: LangbangApplication,
    private val scope: CoroutineScope
) {
    var selected: VerbEntry? by mutableStateOf(null)
        private set
    /** Sentences for the currently selected verb, present-tense first then past-tense. */
    var sentences: List<SentenceExample> by mutableStateOf(emptyList())
        private set
    var busy: Boolean by mutableStateOf(false)
        private set
    var error: String? by mutableStateOf(null)
        private set
    var slowFirst: Boolean by mutableStateOf(app.practicePrefs.slowFirst())
        private set
    /**
     * Lemmas the learner has ticked in the left verb list. When non-empty, Play and
     * the quizzes run across exactly these verbs (in shuffled order); when empty they
     * fall back to the single selected verb. Persisted so the selection is sticky.
     */
    var checkedLemmas: Set<String> by mutableStateOf(app.practicePrefs.checkedVerbs())
        private set
    var playLimitText: String by mutableStateOf(
        app.practicePrefs.wordPlayLimit(PracticePrefsStore.CATEGORY_VERBS).toString()
    )
        private set
    var randomOrder: Boolean by mutableStateOf(
        app.practicePrefs.wordPlayRandom(PracticePrefsStore.CATEGORY_VERBS)
    )
        private set
    var includePronouns: Boolean by mutableStateOf(app.practicePrefs.verbPhraseIncludePronouns())
        private set
    var includeHelperVerb: Boolean by mutableStateOf(app.practicePrefs.verbPhraseIncludeHelperVerb())
        private set
    var includeAdjectives: Boolean by mutableStateOf(app.practicePrefs.verbPhraseIncludeAdjectives())
        private set
    var includeAdverbs: Boolean by mutableStateOf(app.practicePrefs.verbPhraseIncludeAdverbs())
        private set
    var includeNouns: Boolean by mutableStateOf(app.practicePrefs.verbPhraseIncludeNouns())
        private set
    var generateProgress: String? by mutableStateOf(null)
        private set
    var phraseQualityNotice: String? by mutableStateOf(null)
        private set
    var includedPresentKeys: Set<String> by mutableStateOf(
        app.pronounFilter.allIncluded(PERSON_KEYS, PronounFilterStore.TENSE_PRESENT)
    )
        private set
    var includedPastKeys: Set<String> by mutableStateOf(
        app.pronounFilter.allIncluded(PERSON_KEYS, PronounFilterStore.TENSE_PAST)
    )
        private set

    private val player = StudyQueuePlayer(app, scope)
    val playingIndex: Int get() = player.playingIndex
    /** Set only during the conjugation drill so the per-verb sentence list highlights the
     *  right row; sentence playback leaves it null. */
    var playingLemma: String? by mutableStateOf(null)
        private set
    var preparingPlayback: Boolean by mutableStateOf(false)
        private set
    val playing: Boolean get() = player.hasQueue || preparingPlayback

    fun selectVerb(verb: VerbEntry?) {
        if (selected?.lemma == verb?.lemma) return
        selected = verb
        sentences = verb?.let { loadCombinedSentences(it) } ?: emptyList()
        error = null
    }

    private fun loadCombinedSentences(verb: VerbEntry): List<SentenceExample> {
        val present = app.lessonRepo.sentencesFor(verb.lemma, VerbSentenceStore.TENSE_PRESENT)
        val past = if (verb.past_forms.isNullOrEmpty()) emptyList()
        else app.lessonRepo.sentencesFor(verb.lemma, VerbSentenceStore.TENSE_PAST)
        return present + past
    }

    fun toggleVerbChecked(lemma: String, checked: Boolean) {
        checkedLemmas = if (checked) checkedLemmas + lemma else checkedLemmas - lemma
        app.practicePrefs.setCheckedVerbs(checkedLemmas)
    }

    /** Bulk tick/untick (class "all" header, or the master "All verbs" box). */
    fun setVerbsChecked(lemmas: List<String>, checked: Boolean) {
        checkedLemmas = if (checked) checkedLemmas + lemmas else checkedLemmas - lemmas.toSet()
        app.practicePrefs.setCheckedVerbs(checkedLemmas)
    }

    fun updatePlayLimitText(value: String) {
        val cleaned = value.filter { it.isDigit() }.take(2)
        playLimitText = cleaned
        cleaned.toIntOrNull()?.let {
            app.practicePrefs.setWordPlayLimit(PracticePrefsStore.CATEGORY_VERBS, it)
        }
    }

    fun updateRandomOrder(enabled: Boolean) {
        randomOrder = enabled
        app.practicePrefs.setWordPlayRandom(PracticePrefsStore.CATEGORY_VERBS, enabled)
    }

    fun updateSlowFirst(enabled: Boolean) {
        slowFirst = enabled
        app.practicePrefs.setSlowFirst(enabled)
    }

    fun updateIncludePronouns(enabled: Boolean) {
        includePronouns = enabled
        app.practicePrefs.setVerbPhraseIncludePronouns(enabled)
    }

    fun updateIncludeHelperVerb(enabled: Boolean) {
        includeHelperVerb = enabled
        app.practicePrefs.setVerbPhraseIncludeHelperVerb(enabled)
    }

    fun updateIncludeAdjectives(enabled: Boolean) {
        includeAdjectives = enabled
        app.practicePrefs.setVerbPhraseIncludeAdjectives(enabled)
    }

    fun updateIncludeAdverbs(enabled: Boolean) {
        includeAdverbs = enabled
        app.practicePrefs.setVerbPhraseIncludeAdverbs(enabled)
    }

    fun updateIncludeNouns(enabled: Boolean) {
        includeNouns = enabled
        app.practicePrefs.setVerbPhraseIncludeNouns(enabled)
    }

    private fun playLimit(): Int =
        playLimitText.toIntOrNull()
            ?.coerceIn(PracticePrefsStore.MIN_WORD_PLAY_LIMIT, PracticePrefsStore.MAX_WORD_PLAY_LIMIT)
            ?: 0

    /**
     * The verbs a multi-verb action should run over: the ticked verbs, or just the
     * selected verb when nothing's ticked. The random checkbox owns whether that target
     * list is shuffled or left in lesson order.
     */
    private fun resolveTargets(allVerbs: List<VerbEntry>): List<VerbEntry> {
        val checked = allVerbs.filter { it.lemma in checkedLemmas }
        val targets = if (checked.isNotEmpty()) checked else listOfNotNull(selected)
        return if (randomOrder) targets.shuffled() else targets
    }

    fun targetPlayCount(allVerbs: List<VerbEntry>): Int {
        val targetCount = resolveTargets(allVerbs).size
        if (!wordTypeVariationsEnabled()) return targetCount
        return targetCount * playLimit()
    }

    fun wordTypeVariationsEnabled(): Boolean = selectedPhraseComponentCount() > 0

    private fun selectedPhraseComponentCount(): Int =
        listOf(includePronouns, includeAdjectives, includeAdverbs, includeNouns).count { it }

    private fun minimumPhraseWordCount(): Int =
        1 + selectedPhraseComponentCount()

    private fun phraseRequirements(): Set<String> = buildSet {
        if (includePronouns) add("pronoun")
        if (includeHelperVerb) add("helperVerb")
        if (includeAdjectives) add("adjective")
        if (includeAdverbs) add("adverb")
        if (includeNouns) add("noun")
    }

    fun toggleIncluded(
        key: String,
        included: Boolean,
        tense: String = PronounFilterStore.TENSE_PRESENT
    ) {
        app.pronounFilter.setIncluded(key, included, tense)
        if (tense == PronounFilterStore.TENSE_PAST) {
            includedPastKeys = if (included) includedPastKeys + key else includedPastKeys - key
        } else {
            includedPresentKeys = if (included) includedPresentKeys + key else includedPresentKeys - key
        }
    }

    /** Tick/untick every person for one tense at once — backs the "all" box per column. */
    fun setTenseIncluded(included: Boolean, tense: String = PronounFilterStore.TENSE_PRESENT) {
        PERSON_KEYS.forEach { app.pronounFilter.setIncluded(it, included, tense) }
        val all = if (included) PERSON_KEYS.toSet() else emptySet()
        if (tense == PronounFilterStore.TENSE_PAST) includedPastKeys = all
        else includedPresentKeys = all
    }

    fun generate(allVerbs: List<VerbEntry> = emptyList()) {
        if (busy) return
        if (includedPresentKeys.isEmpty() && includedPastKeys.isEmpty()) {
            error = "Check at least one pronoun (present or past) to generate sentences."
            return
        }
        val checkedTargets = allVerbs.filter { it.lemma in checkedLemmas }
        if (checkedTargets.isNotEmpty()) {
            busy = true; error = null; generateProgress = "starting…"
            scope.launch {
                val errors = mutableListOf<String>()
                try {
                    // Gemini-only loop — fast (one call per verb × tense). Audio is left
                    // to the background prefetch worker so a slow/throttled TTS never
                    // stalls us.
                    checkedTargets.forEachIndexed { i, vv ->
                        val tag = "${i + 1}/${checkedTargets.size} · ${vv.lemma}"
                        // Present.
                        if (includedPresentKeys.isNotEmpty()) {
                            generateProgress = "Present $tag"
                            val existing = app.lessonRepo.sentencesFor(
                                vv.lemma, VerbSentenceStore.TENSE_PRESENT
                            )
                            if (existing.isEmpty()) {
                                app.gemini.generateSentences(
                                    vv, includedPresentKeys, GeminiClient.TENSE_PRESENT
                                ).onSuccess { list ->
                                    app.lessonRepo.saveSentences(
                                        vv.lemma, list, VerbSentenceStore.TENSE_PRESENT
                                    )
                                }.onFailure { err ->
                                    errors += "${vv.lemma} (present): ${err.message}"
                                }
                            }
                        }
                        // Past — only verbs that actually have past_forms.
                        if (includedPastKeys.isNotEmpty() && !vv.past_forms.isNullOrEmpty()) {
                            generateProgress = "Past $tag"
                            val existing = app.lessonRepo.sentencesFor(
                                vv.lemma, VerbSentenceStore.TENSE_PAST
                            )
                            if (existing.isEmpty()) {
                                app.gemini.generateSentences(
                                    vv, includedPastKeys, GeminiClient.TENSE_PAST
                                ).onSuccess { list ->
                                    app.lessonRepo.saveSentences(
                                        vv.lemma, list, VerbSentenceStore.TENSE_PAST
                                    )
                                }.onFailure { err ->
                                    errors += "${vv.lemma} (past): ${err.message}"
                                }
                            }
                        }
                        if (vv.lemma == selected?.lemma) sentences = loadCombinedSentences(vv)
                    }
                    generateProgress = "Kicking audio prefetch…"
                    kickPrefetchWorker(app)
                } finally {
                    if (errors.isNotEmpty()) {
                        error = "${errors.size} call(s) failed: ${errors.first()}"
                    }
                    generateProgress = null
                    busy = false
                }
            }
            return
        }
        val v = selected ?: return
        busy = true; error = null; generateProgress = "Generating sentences…"
        scope.launch {
            val errors = mutableListOf<String>()
            try {
                if (includedPresentKeys.isNotEmpty()) {
                    generateProgress = "Generating present-tense examples…"
                    app.gemini.generateSentences(
                        v, includedPresentKeys, GeminiClient.TENSE_PRESENT
                    ).onSuccess { list ->
                        app.lessonRepo.saveSentences(
                            v.lemma, list, VerbSentenceStore.TENSE_PRESENT
                        )
                        app.prefetch.prefetchSentences(list)
                    }.onFailure { errors += "present: ${it.message}" }
                }
                if (includedPastKeys.isNotEmpty() && !v.past_forms.isNullOrEmpty()) {
                    generateProgress = "Generating past-tense examples…"
                    app.gemini.generateSentences(
                        v, includedPastKeys, GeminiClient.TENSE_PAST
                    ).onSuccess { list ->
                        app.lessonRepo.saveSentences(
                            v.lemma, list, VerbSentenceStore.TENSE_PAST
                        )
                        app.prefetch.prefetchSentences(list)
                    }.onFailure { errors += "past: ${it.message}" }
                }
                if (selected?.lemma == v.lemma) sentences = loadCombinedSentences(v)
                if (errors.isNotEmpty()) error = errors.joinToString("; ")
            } finally {
                generateProgress = null
                busy = false
            }
        }
    }

    fun stop() {
        player.stop()
        preparingPlayback = false
        generateProgress = null
        phraseQualityNotice = null
        playingLemma = null
    }

    fun playPolishOnce(text: String) {
        playPolish(app, scope, text)
    }

    fun playSentenceOnce(sentence: SentenceExample) {
        playPolishOnce(sentence.pl)
    }

    fun playConjugationOnce(personKey: String, form: String) {
        if (form.isEmpty()) return
        playPolishOnce("${audioPronoun(personKey)} $form".trim())
    }

    private fun setLang(l: String, s: SentenceExample, position: String) {
        NowVoicingBus.publish(NowVoicing(s.en, s.pl, s.literal, l, position, s.words))
    }

    /** Quiz-flavored variant — stamps quizMode + plHidden so the NV panel can mask. */
    private fun publishQuiz(l: String, s: SentenceExample, position: String, plHidden: Boolean) {
        NowVoicingBus.publish(
            NowVoicing(
                en = s.en, pl = s.pl, literal = s.literal,
                lang = l, position = position, words = s.words,
                plHidden = plHidden, quizMode = true
            )
        )
    }

    private fun tokenCountOf(s: String): Int =
        s.trim().split(WhitespaceRegex).count { it.isNotEmpty() }

    /**
     * Quiz uses [sentences] (present+past combined) by default — but if the user has
     * turned off all past pronouns we must keep past sentences out of the queue (and
     * vice versa). We don't have a per-sentence tense flag stored, so we re-derive it
     * by checking whether the sentence contains any allowed form for that tense.
     * A sentence with no recognized verb form passes through (safer than dropping it).
     */
    private fun filterByTense(
        verb: VerbEntry,
        list: List<SentenceExample>
    ): List<SentenceExample> {
        val keepPresent = includedPresentKeys.isNotEmpty()
        val keepPast = includedPastKeys.isNotEmpty() && !verb.past_forms.isNullOrEmpty()
        if (keepPresent && keepPast) return list
        val presentForms = verb.forms.values
            .filter { it.isNotBlank() }.map { it.lowercase() }.toSet()
        val pastForms = verb.past_forms.orEmpty().values
            .filter { it.isNotBlank() }.map { it.lowercase() }.toSet()
        return list.filter { s ->
            val tokens = s.pl.lowercase().split(NonLetterRegex)
                .filter { it.isNotEmpty() }
            val hitsPresent = tokens.any { it in presentForms }
            val hitsPast = tokens.any { it in pastForms }
            when {
                hitsPresent && !hitsPast -> keepPresent
                hitsPast && !hitsPresent -> keepPast
                hitsPresent && hitsPast -> keepPresent || keepPast // ambiguous → allow
                else -> true // unknown verb form → leave it in
            }
        }
    }

    private suspend fun ensureSentencesFor(target: VerbEntry): List<SentenceExample> {
        // Present.
        if (includedPresentKeys.isNotEmpty()) {
            val existing = app.lessonRepo.sentencesFor(
                target.lemma, VerbSentenceStore.TENSE_PRESENT
            )
            if (existing.isEmpty()) {
                app.gemini.generateSentences(
                    target, includedPresentKeys, GeminiClient.TENSE_PRESENT
                ).onSuccess { list ->
                    app.lessonRepo.saveSentences(
                        target.lemma, list, VerbSentenceStore.TENSE_PRESENT
                    )
                    app.prefetch.prefetchSentences(list)
                }
            }
        }
        // Past — only if the verb has past_forms.
        if (includedPastKeys.isNotEmpty() && !target.past_forms.isNullOrEmpty()) {
            val existing = app.lessonRepo.sentencesFor(
                target.lemma, VerbSentenceStore.TENSE_PAST
            )
            if (existing.isEmpty()) {
                app.gemini.generateSentences(
                    target, includedPastKeys, GeminiClient.TENSE_PAST
                ).onSuccess { list ->
                    app.lessonRepo.saveSentences(
                        target.lemma, list, VerbSentenceStore.TENSE_PAST
                    )
                    app.prefetch.prefetchSentences(list)
                }
            }
        }
        val combined = loadCombinedSentences(target)
        if (target.lemma == selected?.lemma) sentences = combined
        return combined
    }

    private suspend fun wordTypeVariationSentencesFor(
        verb: VerbEntry,
        limitPerType: Int
    ): List<SentenceExample> {
        val requirements = phraseRequirementSnapshot()
        val allowedForms = allowedFormsFor(verb, requirements)
        if (allowedForms.isEmpty() || limitPerType <= 0) return emptyList()

        generateProgress = "Building phrase variants · ${verb.lemma}"
        var matching = mergePhraseCandidates(
            existing = emptyList(),
            local = buildLocalPhraseSentences(verb, limitPerType),
            randomOrder = requirements.randomOrder
        )
        if (matching.size < limitPerType) {
            val cached = matchingPhraseCandidates(
                verb = verb,
                candidates = phraseCandidateSentencesFor(verb),
                allowedForms = allowedForms,
                requirements = requirements
            )
            matching = mergePhraseCandidates(matching, cached, requirements.randomOrder)
        }
        if (matching.size < limitPerType) app.sentenceRegen.startIfNeeded()
        return matching.take(limitPerType)
    }

    private suspend fun matchingPhraseCandidates(
        verb: VerbEntry,
        candidates: List<SentenceExample>,
        allowedForms: Set<String>,
        requirements: PhraseRequirementSnapshot
    ): List<SentenceExample> = withContext(Dispatchers.Default) {
        candidates.asSequence()
            .map { sentence -> sentence to sentence.pl.lessonPolishTokens().toSet() }
            .filter { (_, tokens) ->
                sentenceMatchesPhraseRequirements(allowedForms, requirements, tokens)
            }
            .filter { (sentence, tokens) ->
                sentencePassesCommonUsageGate(verb, sentence, allowedForms, requirements, tokens)
            }
            .map { (sentence, _) -> sentence }
            .distinctBy { it.pl }
            .toList()
            .let { if (requirements.randomOrder) it.shuffled() else it }
    }

    private suspend fun mergePhraseCandidates(
        existing: List<SentenceExample>,
        local: List<SentenceExample>,
        randomOrder: Boolean
    ): List<SentenceExample> = withContext(Dispatchers.Default) {
        (existing + local)
            .distinctBy { it.pl }
            .let { if (randomOrder) it.shuffled() else it }
    }

    private fun phraseCandidateSentencesFor(verb: VerbEntry): List<SentenceExample> {
        val verbSentences = loadCombinedSentences(verb)
        val adjectiveSentences = if (!includeAdjectives) emptyList() else {
            val adjectives = app.lessonRepo.lesson3().adjectives
            val selected = selectedCategoryLemmas(
                PracticePrefsStore.CATEGORY_ADJECTIVES,
                adjectives.map { it.lemma }
            )
            adjectives.filter { it.lemma in selected }
                .flatMap { app.lessonRepo.adjectiveSentencesFor(it.lemma) }
        }
        val adverbSentences = if (!includeAdverbs) emptyList() else {
            val adverbs = app.lessonRepo.lesson4().adverbs
            val selected = selectedCategoryLemmas(
                PracticePrefsStore.CATEGORY_ADVERBS,
                adverbs.map { it.lemma }
            )
            adverbs.filter { it.lemma in selected }
                .flatMap { app.lessonRepo.adverbSentencesFor(it.lemma) }
        }
        val nounSentences = if (!includeNouns) emptyList() else {
            val nouns = app.lessonRepo.lesson6().nouns
            val selected = selectedCategoryLemmas(
                PracticePrefsStore.CATEGORY_NOUNS,
                nouns.map { it.lemma }
            )
            nouns.filter { it.lemma in selected }
                .flatMap { app.lessonRepo.nounSentencesFor(it.lemma) }
        }
        return verbSentences + adjectiveSentences + adverbSentences + nounSentences
    }

    private fun buildLocalPhraseSentences(
        verb: VerbEntry,
        limit: Int
    ): List<SentenceExample> {
        val cues = phraseVerbCuesFor(verb)
        if (cues.isEmpty() || limit <= 0) return emptyList()

        val cuePool = if (randomOrder) cues.shuffled() else cues
        var rejected = 0
        val candidates = (0 until (limit * 4).coerceAtLeast(limit)).mapNotNull { index ->
            val cue = cuePool[index % cuePool.size]
            val sentence = if (includeHelperVerb) {
                helperVerbPhraseSentence(verb, cue, index)
                    ?: directObjectPhraseSentence(verb, cue, index)
            } else {
                directObjectPhraseSentence(verb, cue, index)
            }
            when {
                sentence == null -> null
                sentencePassesCommonUsageGate(verb, sentence) -> sentence
                else -> {
                    rejected += 1
                    app.practicePrefs.addRejectedVerbPhraseKey(sentence.qualityKey())
                    null
                }
            }
        }.distinctBy { it.pl }
        if (rejected > 0) {
            phraseQualityNotice = "Skipped $rejected awkward phrase variant(s)."
        }
        return candidates.take(limit)
    }

    private fun phraseVerbCuesFor(verb: VerbEntry): List<PhraseVerbCue> = buildList {
        PERSON_KEYS.forEach { key ->
            if (key in includedPresentKeys) {
                val form = verb.forms[key].orEmpty()
                if (form.isNotBlank()) {
                    add(PhraseVerbCue(key, form, englishConjugate(verb.en, key, past = false), past = false))
                }
            }
            if (key in includedPastKeys) {
                val form = verb.past_forms.orEmpty()[key].orEmpty()
                if (form.isNotBlank()) {
                    add(PhraseVerbCue(key, form, englishConjugate(verb.en, key, past = true), past = true))
                }
            }
        }
    }

    private fun directObjectPhraseSentence(
        verb: VerbEntry,
        cue: PhraseVerbCue,
        index: Int
    ): SentenceExample? {
        val noun = phraseNounForVerb(verb, index) ?: return null
        val adjective = phraseAdjectiveForNoun(noun, index)
        val adverb = phraseAdverbFor(verb, helperLemma = null, index = index)
        if (includeAdverbs && adverb == null) return null
        return assembleVerbObjectPhrase(
            cue = cue,
            verbPart = PhrasePart(
                words = listOf(verbToken(verb, cue.form, cue.englishVerb)),
                en = cue.englishVerb,
                literal = cue.englishVerb
            ),
            adjective = adjective,
            noun = noun,
            adverb = adverb,
            adverbBeforeVerb = adverb?.lemma in setOf("bardzo", "trochę", "teraz", "dzisiaj", "jutro")
        )
    }

    private fun helperVerbPhraseSentence(
        verb: VerbEntry,
        cue: PhraseVerbCue,
        index: Int
    ): SentenceExample? {
        return when {
            verb.lemma in setOf("chcieć", "móc", "musieć", "lubić") ->
                selectedVerbAsHelperPhrase(verb, cue, index)
            verb.lemma == "mieć" ->
                selectedMiecOchotePhrase(verb, cue, index)
            else ->
                externalHelperPhrase(verb, cue, index)
        }
    }

    private fun selectedVerbAsHelperPhrase(
        verb: VerbEntry,
        cue: PhraseVerbCue,
        index: Int
    ): SentenceExample? {
        val complement = complementForHelperVerb(verb, index) ?: return null
        val adverb = phraseAdverbFor(verb, helperLemma = verb.lemma, index = index)
        if (includeAdverbs && adverb == null) return null
        return assembleHelperPhrase(
            selectedCue = cue,
            helperWords = listOf(verbToken(verb, cue.form, cue.englishVerb)),
            helperEnglish = cue.englishVerb,
            helperLiteral = cue.englishVerb,
            complement = complement,
            adverb = adverb,
            adverbAfterHelper = adverb?.lemma != "bardzo"
        )
    }

    private fun selectedMiecOchotePhrase(
        verb: VerbEntry,
        cue: PhraseVerbCue,
        index: Int
    ): SentenceExample? {
        val complement = complementForInfinitive(
            verb = verbByLemma(listOf("iść", "pić", "czytać", "jeść"), index) ?: return null,
            index = index,
            englishStyle = EnglishComplementStyle.Gerund
        ) ?: return null
        val adverb = phraseAdverbFor(verb, helperLemma = "mieć_ochotę", index = index)
        if (includeAdverbs && adverb == null) return null
        return assembleHelperPhrase(
            selectedCue = cue,
            helperWords = listOf(
                verbToken(verb, cue.form, if (cue.past) "felt like" else "feel like"),
                TokenPair("ochotę", "desire", caseKey = "acc", caseLabel = "accusative")
            ),
            helperEnglish = if (cue.past) "felt like" else "feel like",
            helperLiteral = "${if (cue.past) "had" else "have"} desire",
            complement = complement,
            adverb = adverb,
            adverbAfterHelper = true
        )
    }

    private fun externalHelperPhrase(
        verb: VerbEntry,
        cue: PhraseVerbCue,
        index: Int
    ): SentenceExample? {
        val helper = helperVerbFor(verb, cue, index) ?: return null
        val complement = complementForSelectedInfinitive(verb, helper.lemma, index) ?: return null
        val adverb = phraseAdverbFor(verb, helperLemma = helper.lemma, index = index)
        if (includeAdverbs && adverb == null) return null
        return assembleHelperPhrase(
            selectedCue = cue,
            helperWords = helper.words,
            helperEnglish = helper.en,
            helperLiteral = helper.literal,
            complement = complement,
            adverb = adverb,
            adverbAfterHelper = adverb?.lemma != "bardzo"
        )
    }

    private fun assembleVerbObjectPhrase(
        cue: PhraseVerbCue,
        verbPart: PhrasePart,
        adjective: AdjectiveEntry?,
        noun: NounEntry?,
        adverb: AdverbEntry?,
        adverbBeforeVerb: Boolean
    ): SentenceExample {
        val subjectEn = englishSubjectFor(cue.personKey)
        val words = mutableListOf<TokenPair>()
        if (includePronouns) words += TokenPair(audioPronoun(cue.personKey), subjectEn)
        if (adverb != null && adverbBeforeVerb) {
            words += TokenPair(adverb.lemma, adverb.en.primaryGloss())
        }
        words += verbPart.words
        if (adverb != null && !adverbBeforeVerb) {
            words += TokenPair(adverb.lemma, adverb.en.primaryGloss())
        }
        adjective?.let {
            words += TokenPair(
                pl = adjectivePhraseForm(it, noun),
                en = if (noun == null) "${it.en.primaryGloss()} one" else it.en.primaryGloss(),
                gender = noun?.gender,
                caseKey = "acc",
                caseLabel = "accusative"
            )
        }
        noun?.let {
            words += TokenPair(
                pl = it.acc["sg"].orEmpty().ifBlank { it.lemma },
                en = it.en.primaryGloss(),
                gender = it.gender,
                caseKey = "acc",
                caseLabel = "accusative",
                numberLabel = "singular"
            )
        }
        val pl = words.joinToString(" ") { it.pl }
        return SentenceExample(
            pl = pl,
            en = englishPhrase(subjectEn, verbPart.en, adjective, adverb, noun, adverbBeforeVerb),
            literal = words.joinToString(" ") { it.en },
            words = words
        )
    }

    private fun assembleHelperPhrase(
        selectedCue: PhraseVerbCue,
        helperWords: List<TokenPair>,
        helperEnglish: String,
        helperLiteral: String,
        complement: PhrasePart,
        adverb: AdverbEntry?,
        adverbAfterHelper: Boolean
    ): SentenceExample {
        val subjectEn = englishSubjectFor(selectedCue.personKey)
        val words = mutableListOf<TokenPair>()
        if (includePronouns) words += TokenPair(audioPronoun(selectedCue.personKey), subjectEn)
        if (adverb != null && !adverbAfterHelper) {
            words += TokenPair(adverb.lemma, adverb.en.primaryGloss())
        }
        words += helperWords
        if (adverb != null && adverbAfterHelper) {
            words += TokenPair(adverb.lemma, adverb.en.primaryGloss())
        }
        words += complement.words
        val pl = words.joinToString(" ") { it.pl }
        val adverbEnglish = adverb?.en?.primaryGloss()
        val enParts = mutableListOf(subjectEn)
        if (adverbEnglish != null && !adverbAfterHelper) enParts += adverbEnglish
        enParts += helperEnglish
        if (adverbEnglish != null && adverbAfterHelper) enParts += adverbEnglish
        enParts += complement.en
        return SentenceExample(
            pl = pl,
            en = enParts.joinToString(" "),
            literal = listOfNotNull(
                if (includePronouns) subjectEn else null,
                if (adverb != null && !adverbAfterHelper) adverb.en.primaryGloss() else null,
                helperLiteral,
                if (adverb != null && adverbAfterHelper) adverb.en.primaryGloss() else null,
                complement.literal
            ).joinToString(" "),
            words = words
        )
    }

    private fun helperVerbFor(
        verb: VerbEntry,
        cue: PhraseVerbCue,
        index: Int
    ): HelperPhrasePart? {
        if (verb.lemma in setOf("być", "mieć")) return null
        if (index % 3 == 0) {
            ochoteHelperFor(cue)?.let { return it }
        }
        val helperLemma = when (verb.lemma) {
            "iść", "jechać", "wracać", "spać", "pracować" -> "musieć"
            "pomagać", "tańczyć", "dzwonić" -> "móc"
            else -> "chcieć"
        }
        val helper = app.lessonRepo.lesson2().verbs.firstOrNull { it.lemma == helperLemma } ?: return null
        val form = if (cue.past) helper.past_forms.orEmpty()[cue.personKey] else helper.forms[cue.personKey]
        if (form.isNullOrBlank()) return null
        return HelperPhrasePart(
            lemma = helperLemma,
            words = listOf(verbToken(helper, form, englishConjugate(helper.en, cue.personKey, cue.past))),
            en = englishConjugate(helper.en, cue.personKey, cue.past),
            literal = englishConjugate(helper.en, cue.personKey, cue.past)
        )
    }

    private fun ochoteHelperFor(cue: PhraseVerbCue): HelperPhrasePart? {
        val miec = app.lessonRepo.lesson2().verbs.firstOrNull { it.lemma == "mieć" } ?: return null
        val form = if (cue.past) miec.past_forms.orEmpty()[cue.personKey] else miec.forms[cue.personKey]
        if (form.isNullOrBlank()) return null
        return HelperPhrasePart(
            lemma = "mieć_ochotę",
            words = listOf(
                verbToken(miec, form, if (cue.past) "felt like" else "feel like"),
                TokenPair("ochotę", "desire", caseKey = "acc", caseLabel = "accusative")
            ),
            en = if (cue.past) "felt like" else "feel like",
            literal = "${if (cue.past) "had" else "have"} desire"
        )
    }

    private fun complementForHelperVerb(verb: VerbEntry, index: Int): PhrasePart? {
        val mainVerb = when (verb.lemma) {
            "chcieć" -> verbByLemma(listOf("iść", "pić", "czytać", "jeść"), index)
            "móc" -> verbByLemma(listOf("pomagać", "iść", "pić"), index)
            "musieć" -> verbByLemma(listOf("iść", "pracować", "dzwonić"), index)
            "lubić" -> verbByLemma(listOf("czytać", "tańczyć", "spać"), index)
            else -> null
        } ?: return null
        val style = when (verb.lemma) {
            "móc", "musieć" -> EnglishComplementStyle.BareInfinitive
            "lubić" -> EnglishComplementStyle.Gerund
            else -> EnglishComplementStyle.ToInfinitive
        }
        return complementForInfinitive(mainVerb, index, style)
    }

    private fun complementForSelectedInfinitive(
        verb: VerbEntry,
        helperLemma: String,
        index: Int
    ): PhrasePart? {
        val style = when (helperLemma) {
            "móc", "musieć" -> EnglishComplementStyle.BareInfinitive
            "mieć_ochotę" -> EnglishComplementStyle.Gerund
            else -> EnglishComplementStyle.ToInfinitive
        }
        return complementForInfinitive(verb, index, style)
    }

    private fun complementForInfinitive(
        verb: VerbEntry,
        index: Int,
        englishStyle: EnglishComplementStyle
    ): PhrasePart? {
        val words = mutableListOf<TokenPair>()
        words += TokenPair(
            pl = verb.lemma,
            en = englishComplement(verb, englishStyle),
            variableKind = "infinitive"
        )
        val objectPart = when (verb.lemma) {
            "iść", "jechać", "wracać" -> destinationPart(index)
            "pić" -> objectPartFor(
                nounLemmas = listOf("kawa", "herbata", "woda"),
                index = index
            )
            "jeść" -> objectPartFor(
                nounLemmas = listOf("jedzenie", "chleb"),
                index = index
            )
            "czytać" -> objectPartFor(
                nounLemmas = listOf("książka"),
                index = index
            )
            "kupować" -> objectPartFor(
                nounLemmas = listOf("kawa", "chleb", "książka"),
                index = index
            )
            else -> null
        }
        if (includeNouns && objectPart == null) return null
        objectPart?.let { words += it.words }
        val base = englishComplement(verb, englishStyle)
        return PhrasePart(
            words = words,
            en = listOfNotNull(base, objectPart?.en).joinToString(" "),
            literal = listOfNotNull(base, objectPart?.literal).joinToString(" ")
        )
    }

    private fun destinationPart(index: Int): PhrasePart? {
        val noun = nounByLemma(listOf("sklep", "szkoła", "praca", "dom", "miasto"), index) ?: return null
        val adjective = phraseAdjectiveForNoun(noun, index)
        if (includeAdjectives && adjective == null) return null
        val words = mutableListOf<TokenPair>()
        words += TokenPair("do", "to")
        adjective?.let {
            words += TokenPair(
                pl = adjectiveGenitiveLikeForm(it, noun),
                en = it.en.primaryGloss(),
                gender = noun.gender,
                caseKey = "gen",
                caseLabel = "genitive"
            )
        }
        words += TokenPair(
            pl = noun.gen["sg"].orEmpty().ifBlank { noun.lemma },
            en = noun.en.primaryGloss(),
            gender = noun.gender,
            caseKey = "gen",
            caseLabel = "genitive",
            numberLabel = "singular"
        )
        val objectEn = englishObjectPhrase(adjective, null, noun, false)
            ?: noun.en.primaryGloss()
        return PhrasePart(
            words = words,
            en = "to $objectEn",
            literal = words.joinToString(" ") { it.en }
        )
    }

    private fun objectPartFor(nounLemmas: List<String>, index: Int): PhrasePart? {
        val noun = nounByLemma(nounLemmas, index) ?: return null
        val adjective = phraseAdjectiveForNoun(noun, index)
        if (includeAdjectives && adjective == null) return null
        val words = mutableListOf<TokenPair>()
        adjective?.let {
            words += TokenPair(
                pl = adjectivePhraseForm(it, noun),
                en = it.en.primaryGloss(),
                gender = noun.gender,
                caseKey = "acc",
                caseLabel = "accusative"
            )
        }
        words += TokenPair(
            pl = noun.acc["sg"].orEmpty().ifBlank { noun.lemma },
            en = noun.en.primaryGloss(),
            gender = noun.gender,
            caseKey = "acc",
            caseLabel = "accusative",
            numberLabel = "singular"
        )
        val objectEn = englishObjectPhrase(adjective, null, noun, false)
            ?: noun.en.primaryGloss()
        return PhrasePart(
            words = words,
            en = objectEn,
            literal = words.joinToString(" ") { it.en }
        )
    }

    private fun phraseNounForVerb(verb: VerbEntry, index: Int): NounEntry? {
        val lemmas = when (verb.lemma) {
            "mieć", "widzieć", "lubić", "kochać" -> listOf("dom", "kawa", "książka", "telefon", "samochód")
            "pić" -> listOf("kawa", "herbata", "woda")
            "jeść" -> listOf("chleb", "jedzenie")
            "czytać" -> listOf("książka")
            "kupować" -> listOf("kawa", "chleb", "książka", "telefon")
            else -> emptyList()
        }
        return nounByLemma(lemmas, index)
    }

    private fun phraseAdjectiveForNoun(noun: NounEntry, index: Int): AdjectiveEntry? {
        if (!includeAdjectives) return null
        val lemmas = when (noun.lemma) {
            "kawa", "herbata" -> listOf("dobry", "ciepły", "gorący", "mały")
            "woda" -> listOf("zimny", "czysty")
            "książka" -> listOf("dobry", "nowy", "ciekawy", "krótki")
            "sklep", "telefon", "samochód" -> listOf("nowy", "mały", "dobry")
            "dom", "mieszkanie" -> listOf("nowy", "duży", "mały")
            "chleb", "jedzenie" -> listOf("dobry", "świeży", "ciepły")
            else -> listOf("dobry", "nowy", "mały")
        }
        return adjectiveByLemma(lemmas, index)
    }

    private fun phraseAdverbFor(
        verb: VerbEntry,
        helperLemma: String?,
        index: Int
    ): AdverbEntry? {
        if (!includeAdverbs) return null
        val lemmas = when (helperLemma ?: verb.lemma) {
            "chcieć", "lubić" -> listOf("bardzo", "teraz", "dzisiaj", "jutro")
            "musieć" -> listOf("teraz", "dzisiaj", "szybko")
            "móc" -> listOf("teraz", "dzisiaj", "szybko")
            "mieć_ochotę" -> listOf("dzisiaj", "teraz")
            "pić", "jeść", "czytać", "kupować" -> listOf("teraz", "dzisiaj")
            "spać" -> listOf("dobrze", "teraz")
            "czuć" -> listOf("dobrze", "trochę")
            else -> listOf("dzisiaj", "teraz", "szybko")
        }
        return adverbByLemma(lemmas, index)
    }

    private fun verbByLemma(lemmas: List<String>, index: Int): VerbEntry? {
        val all = app.lessonRepo.lesson2().verbs
        val candidates = lemmas.mapNotNull { lemma -> all.firstOrNull { it.lemma == lemma } }
        return candidates.getOrNull(index % candidates.size.coerceAtLeast(1))
    }

    private fun nounByLemma(lemmas: List<String>, index: Int): NounEntry? {
        val selected = selectedNouns()
        val candidates = lemmas.mapNotNull { lemma -> selected.firstOrNull { it.lemma == lemma } }
        return candidates.getOrNull(index % candidates.size.coerceAtLeast(1))
    }

    private fun adjectiveByLemma(lemmas: List<String>, index: Int): AdjectiveEntry? {
        val selected = selectedAdjectives()
        val candidates = lemmas.mapNotNull { lemma -> selected.firstOrNull { it.lemma == lemma } }
        return candidates.getOrNull(index % candidates.size.coerceAtLeast(1))
    }

    private fun adverbByLemma(lemmas: List<String>, index: Int): AdverbEntry? {
        val selected = selectedAdverbs()
        val candidates = lemmas.mapNotNull { lemma -> selected.firstOrNull { it.lemma == lemma } }
        return candidates.getOrNull(index % candidates.size.coerceAtLeast(1))
    }

    private fun verbToken(verb: VerbEntry, form: String, en: String): TokenPair =
        TokenPair(
            pl = form,
            en = en,
            variableStart = variableStartForPolishForm(verb.lemma, form),
            variableEnd = variableEndForPolishForm(verb.lemma, form),
            variableKind = "conjugation"
        )

    private fun adjectivePhraseForm(adjective: AdjectiveEntry, noun: NounEntry?): String {
        if (noun?.gender == "m" && noun.acc["sg"] == noun.nom["sg"]) {
            return adjective.nom["m"].orEmpty()
                .ifBlank { adjective.acc["m"].orEmpty() }
                .ifBlank { adjective.lemma }
        }
        val key = noun?.gender?.takeIf { it in setOf("m", "f", "n") } ?: "m"
        return adjective.acc[key].orEmpty()
            .ifBlank { adjective.nom[key].orEmpty() }
            .ifBlank { adjective.lemma }
    }

    private fun englishPhrase(
        subject: String,
        verb: String,
        adjective: AdjectiveEntry?,
        adverb: AdverbEntry?,
        noun: NounEntry?,
        adverbModifiesAdjective: Boolean
    ): String {
        val beforeVerb = adverb?.takeUnless { adverbModifiesAdjective }?.en?.primaryGloss()
        val objectPhrase = englishObjectPhrase(adjective, adverb, noun, adverbModifiesAdjective)
        return listOfNotNull(subject, beforeVerb, verb, objectPhrase)
            .joinToString(" ")
            .replace(WhitespaceRegex, " ")
            .trim()
    }

    private fun englishObjectPhrase(
        adjective: AdjectiveEntry?,
        adverb: AdverbEntry?,
        noun: NounEntry?,
        adverbModifiesAdjective: Boolean
    ): String? {
        val adjectiveGloss = adjective?.en?.primaryGloss()
        val adjectiveModifier = adverb?.takeIf { adverbModifiesAdjective }?.en?.primaryGloss()
        val nounGloss = noun?.en?.primaryGloss()
        val descriptor = listOfNotNull(adjectiveModifier, adjectiveGloss)
            .joinToString(" ")
            .ifBlank { null }
        return when {
            nounGloss != null && descriptor != null ->
                listOf(articleFor(nounGloss), descriptor, nounGloss)
                    .filter { it.isNotBlank() }
                    .joinToString(" ")
            nounGloss != null ->
                listOf(articleFor(nounGloss), nounGloss)
                    .filter { it.isNotBlank() }
                    .joinToString(" ")
            descriptor != null -> "something $descriptor"
            else -> null
        }
    }

    private fun articleFor(noun: String): String {
        val base = noun.primaryGloss()
        if (base in setOf("coffee", "tea", "water", "bread", "food", "work", "love")) return ""
        return if (base.firstOrNull()?.lowercaseChar() in setOf('a', 'e', 'i', 'o', 'u')) "an" else "a"
    }

    private fun selectedAdjectives(): List<AdjectiveEntry> {
        val adjectives = app.lessonRepo.lesson3().adjectives
        val selected = selectedCategoryLemmas(
            PracticePrefsStore.CATEGORY_ADJECTIVES,
            adjectives.map { it.lemma }
        )
        return adjectives.filter { it.lemma in selected }
    }

    private fun selectedAdverbs(): List<AdverbEntry> {
        val adverbs = app.lessonRepo.lesson4().adverbs
        val selected = selectedCategoryLemmas(
            PracticePrefsStore.CATEGORY_ADVERBS,
            adverbs.map { it.lemma }
        )
        return adverbs.filter { it.lemma in selected }
    }

    private fun selectedNouns(): List<NounEntry> {
        val nouns = app.lessonRepo.lesson6().nouns
        val selected = selectedCategoryLemmas(
            PracticePrefsStore.CATEGORY_NOUNS,
            nouns.map { it.lemma }
        )
        return nouns.filter { it.lemma in selected }
    }

    private fun phraseRequirementSnapshot(): PhraseRequirementSnapshot =
        PhraseRequirementSnapshot(
            includePronouns = includePronouns,
            includeHelperVerb = includeHelperVerb,
            includeAdjectives = includeAdjectives,
            includeAdverbs = includeAdverbs,
            includeNouns = includeNouns,
            minimumWordCount = minimumPhraseWordCount(),
            pronounTokens = if (includePronouns) selectedPronounTokens() else emptySet(),
            adjectiveTokens = if (includeAdjectives) selectedAdjectiveTokens() else emptySet(),
            adverbTokens = if (includeAdverbs) selectedAdverbTokens() else emptySet(),
            nounTokens = if (includeNouns) selectedNounTokens() else emptySet(),
            helperForms = if (includeHelperVerb) helperVerbPolishForms() else emptySet(),
            knownInfinitives = if (includeHelperVerb) {
                app.lessonRepo.lesson2().verbs.map { it.lemma.lowercase() }.toSet()
            } else {
                emptySet()
            },
            rejectedQualityKeys = app.practicePrefs.rejectedVerbPhraseKeys(),
            randomOrder = randomOrder
        )

    private fun sentenceMatchesPhraseRequirements(
        allowedForms: Set<String>,
        requirements: PhraseRequirementSnapshot,
        tokens: Set<String>
    ): Boolean {
        if (tokens.none { it in allowedForms }) return false
        if (tokens.size < requirements.minimumWordCount) return false
        if (requirements.includePronouns && tokens.none { it in requirements.pronounTokens }) return false
        if (requirements.includeHelperVerb &&
            helperStructurePossibleFor(tokens, allowedForms, requirements) &&
            !sentenceHasHelperStructure(tokens, allowedForms, requirements)
        ) return false
        if (requirements.includeAdjectives && tokens.none { it in requirements.adjectiveTokens }) return false
        if (requirements.includeAdverbs && tokens.none { it in requirements.adverbTokens }) return false
        if (requirements.includeNouns && tokens.none { it in requirements.nounTokens }) return false
        return true
    }

    private fun selectedPronounTokens(): Set<String> =
        (includedPresentKeys + includedPastKeys)
            .flatMap { audioPronoun(it).lessonPolishTokens() }
            .toSet()

    private fun selectedAdjectiveTokens(): Set<String> {
        val adjectives = app.lessonRepo.lesson3().adjectives
        val selected = selectedCategoryLemmas(
            PracticePrefsStore.CATEGORY_ADJECTIVES,
            adjectives.map { it.lemma }
        )
        return adjectives.filter { it.lemma in selected }
            .flatMap { it.polishAdjectiveTokens() }
            .toSet()
    }

    private fun selectedAdverbTokens(): Set<String> {
        val adverbs = app.lessonRepo.lesson4().adverbs
        val selected = selectedCategoryLemmas(
            PracticePrefsStore.CATEGORY_ADVERBS,
            adverbs.map { it.lemma }
        )
        return adverbs.filter { it.lemma in selected }
            .flatMap { it.polishAdverbTokens() }
            .toSet()
    }

    private fun selectedNounTokens(): Set<String> {
        val nouns = app.lessonRepo.lesson6().nouns
        val selected = selectedCategoryLemmas(
            PracticePrefsStore.CATEGORY_NOUNS,
            nouns.map { it.lemma }
        )
        return nouns.filter { it.lemma in selected }
            .flatMap { it.polishNounTokens() }
            .toSet()
    }

    private fun selectedCategoryLemmas(category: String, allLemmas: List<String>): Set<String> {
        val all = allLemmas.toSet()
        if (!app.practicePrefs.hasCheckedWordLemmas(category)) return all
        return app.practicePrefs.checkedWordLemmas(category).intersect(all)
    }

    private fun allowedFormsFor(
        verb: VerbEntry,
        requirements: PhraseRequirementSnapshot
    ): Set<String> {
        val forms = mutableSetOf<String>()
        verb.forms.filterKeys { it in includedPresentKeys }.values.forEach {
            if (it.isNotBlank()) forms += it.lowercase()
        }
        verb.past_forms.orEmpty().filterKeys { it in includedPastKeys }.values.forEach {
            if (it.isNotBlank()) forms += it.lowercase()
        }
        if (requirements.includeHelperVerb) forms += verb.lemma.lowercase()
        return forms
    }

    private fun sentencePassesCommonUsageGate(
        verb: VerbEntry,
        sentence: SentenceExample
    ): Boolean {
        val requirements = phraseRequirementSnapshot()
        val allowedForms = allowedFormsFor(verb, requirements)
        val plTokens = sentence.pl.lessonPolishTokens().toSet()
        return sentencePassesCommonUsageGate(
            verb = verb,
            sentence = sentence,
            allowedForms = allowedForms,
            requirements = requirements,
            plTokens = plTokens
        )
    }

    private fun sentencePassesCommonUsageGate(
        verb: VerbEntry,
        sentence: SentenceExample,
        allowedForms: Set<String>,
        requirements: PhraseRequirementSnapshot,
        plTokens: Set<String>
    ): Boolean {
        if (sentence.qualityKey() in requirements.rejectedQualityKeys) return false
        val en = sentence.en.normalizedQualityText()
        if (requirements.includeHelperVerb &&
            verb.lemma != "być" &&
            !sentenceHasHelperStructure(plTokens, allowedForms, requirements)
        ) return false
        if (AwkwardVerbPhraseEnglishRegexes.any { it.containsMatchIn(en) }) return false
        if (verb.lemma == "chcieć" &&
            plTokens.any { it in setOf("dobrze", "szybko", "chętnie") }
        ) return false
        if ("chciałem" in plTokens || "chcę" in plTokens) {
            if (plTokens.any { it in setOf("kota", "psa") } &&
                plTokens.any { it in setOf("dobrego", "złego", "dużego", "małego", "nowego") }
            ) return false
        }
        return true
    }

    private fun helperStructurePossibleFor(
        tokens: Set<String>,
        allowedForms: Set<String>,
        requirements: PhraseRequirementSnapshot
    ): Boolean {
        if (!requirements.includeHelperVerb) return false
        return tokens.any { it in allowedForms } || tokens.any { it in requirements.helperForms }
    }

    private fun sentenceHasHelperStructure(
        tokens: Set<String>,
        allowedForms: Set<String>,
        requirements: PhraseRequirementSnapshot
    ): Boolean {
        val helperForms = requirements.helperForms
        val knownInfinitives = requirements.knownInfinitives
        val hasSelected = tokens.any { it in allowedForms }
        val hasHelper = tokens.any { it in helperForms } ||
            ("ochotę" in tokens && tokens.any { it in setOf("mam", "masz", "ma", "mamy", "macie", "mają", "miałem", "miałeś", "miał", "mieliśmy", "mieliście", "mieli") })
        val hasInfinitive = tokens.any { it in knownInfinitives }
        return hasSelected && hasHelper && hasInfinitive
    }

    private fun helperVerbPolishForms(): Set<String> =
        app.lessonRepo.lesson2().verbs
            .filter { it.lemma in setOf("chcieć", "móc", "musieć", "lubić", "mieć") }
            .flatMap { helper ->
                helper.forms.values + helper.past_forms.orEmpty().values + helper.lemma
            }
            .filter { it.isNotBlank() }
            .map { it.lowercase() }
            .toSet() + "ochotę"

    fun playAll(
        allVerbs: List<VerbEntry>,
        quiz: Boolean = false,
        includeWordTypeVariations: Boolean = false
    ) {
        if (player.hasQueue || preparingPlayback) {
            stop()
            return
        }
        val targets = resolveTargets(allVerbs)
        if (targets.isEmpty()) return
        scope.launch {
            preparingPlayback = true
            try {
                val limit = playLimit()
                if (limit <= 0) return@launch
                // Build the queue up-front so rewind/restart can re-enter any item.
                val built: List<SentenceExample> = targets.flatMap { vv ->
                    val all = if (includeWordTypeVariations) {
                        wordTypeVariationSentencesFor(vv, limit)
                    } else {
                        ensureSentencesFor(vv)
                    }
                    val filtered = if (quiz) filterByTense(vv, all) else all
                    if (includeWordTypeVariations) {
                        filtered
                    } else if (randomOrder || quiz) {
                        filtered.shuffled().take(limit)
                    } else {
                        filtered.take(limit)
                    }
                }
                val items = when {
                    randomOrder -> built.shuffled()
                    quiz -> built.sortedBy { tokenCountOf(it.pl) }
                    else -> built
                }
                if (items.isEmpty()) {
                    error = "No phrases match the selected word types yet."
                    return@launch
                } else {
                    error = null
                }
                startSentenceQueue(items, quiz)
            } finally {
                preparingPlayback = false
                generateProgress = null
            }
        }
    }

    private fun startSentenceQueue(items: List<SentenceExample>, quiz: Boolean) {
        // Sentence playback doesn't set playingLemma (only the conjugation drill does), so
        // the per-verb sentence-list highlight stays scoped to the conjugation flow.
        playingLemma = null
        val slowPlVoice = app.targetSlowVoice()
        player.start(
            total = items.size,
            publishParked = { i -> publishQuiz("pause", items[i], "${i + 1}/${items.size}", plHidden = quiz) },
            prefetchItem = { i ->
                val s = items[i]
                app.ensureCachedAudio(s.en, app.sourceAudioVoice().locale, app.sourceAudioVoice().voice)
                app.ensureCachedAudio(s.pl, app.targetAudioVoice().locale, app.targetAudioVoice().voice)
                if (slowFirst && !quiz) app.ensureCachedAudio(s.pl, app.targetAudioVoice().locale, slowPlVoice)
            },
        ) { i ->
            val s = items[i]
            val position = "${i + 1}/${items.size}"
            if (quiz) {
                // Floor the pre-reveal pause at 2s even if the configured delay is lower.
                val configMs = (app.randomConfig.load().quizDelaySeconds * 1000).toLong()
                val delayMs = configMs.coerceAtLeast(2000L)
                publishQuiz("en", s, position, plHidden = true)
                say(s.en, app.sourceAudioVoice().locale, app.sourceAudioVoice().voice)
                publishQuiz("pause", s, position, plHidden = true)
                reveal(delayMs)
                publishQuiz("pause", s, position, plHidden = false)
                reveal(delayMs)
                publishQuiz("pl", s, position, plHidden = false)
                say(s.pl, app.targetAudioVoice().locale, app.targetAudioVoice().voice)
            } else if (slowFirst) {
                setLang("en", s, position)
                say(s.en, app.sourceAudioVoice().locale, app.sourceAudioVoice().voice)
                setLang("pl-slow", s, position)
                say(s.pl, app.targetAudioVoice().locale, slowPlVoice)
                setLang("pl", s, position)
                say(s.pl, app.targetAudioVoice().locale, app.targetAudioVoice().voice)
            } else {
                setLang("en", s, position)
                say(s.en, app.sourceAudioVoice().locale, app.sourceAudioVoice().voice)
                setLang("pl", s, position)
                say(s.pl, app.targetAudioVoice().locale, app.targetAudioVoice().voice)
            }
            if (!quiz && i < items.size - 1) reveal(500L)
        }
    }

    /**
     * Plays the verb's conjugated forms only — "ja jestem", "ty jesteś", … — not the
     * Gemini-generated example sentences. Honors the per-tense pronoun filters.
     *
     * Modes:
     *   - [mode] == "play": EN gloss text + speak the PL form (slow optionally, then fast).
     *     Plain study mode, no quizzing.
     *   - [mode] == "conjQuiz": speak EN ("I am") + hide PL text, 2s pause, reveal PL,
     *     2s pause, speak PL. User has to recall the conjugated form before reveal.
     */
    fun playAllConjugations(allVerbs: List<VerbEntry>, mode: String = "play") {
        if (player.hasQueue) {
            stop()
            return
        }
        val targets = resolveTargets(allVerbs)
        if (targets.isEmpty()) return
        val cues = buildConjugationQueue(targets)
        if (cues.isEmpty()) return
        startConjugationQueue(cues, mode)
    }

    private fun buildConjugationQueue(targets: List<VerbEntry>): List<ConjugationCue> {
        val limit = playLimit()
        if (limit <= 0) return emptyList()
        val cues = mutableListOf<ConjugationCue>()
        fun addForms(
            out: MutableList<ConjugationCue>,
            vv: VerbEntry,
            formMap: Map<String, String>,
            included: Set<String>,
            isPast: Boolean
        ) {
            PERSON_KEYS.forEach { key ->
                if (key !in included) return@forEach
                val form = formMap[key].orEmpty()
                conjugationCue(vv, key, form, isPast)?.let { out += it }
            }
        }
        targets.forEach { vv ->
            val perVerb = mutableListOf<ConjugationCue>()
            addForms(perVerb, vv, vv.forms, includedPresentKeys, isPast = false)
            vv.past_forms?.let { addForms(perVerb, vv, it, includedPastKeys, isPast = true) }
            cues += if (randomOrder) perVerb.shuffled().take(limit) else perVerb.take(limit)
        }
        return if (randomOrder) cues.shuffled() else cues
    }

    private fun conjugationCue(
        verb: VerbEntry,
        personKey: String,
        form: String,
        isPast: Boolean
    ): ConjugationCue? {
        if (form.isBlank()) return null
        val targetIsEnglish = app.targetAudioVoice().locale == AzureTtsClient.LOCALE_EN
        val pron = if (targetIsEnglish) app.targetSubjectFor(personKey) else audioPronoun(personKey)
        val combined = "$pron $form".trim()
        val sourceSubject = if (targetIsEnglish) audioPronoun(personKey) else englishSubjectFor(personKey)
        val sourceVerbForm = if (targetIsEnglish) verb.en else englishConjugate(verb.en, personKey, isPast)
        val englishGloss = "$sourceSubject $sourceVerbForm".trim()
        val tokens = listOf(
            TokenPair(pron, sourceSubject),
            TokenPair(
                pl = form,
                en = sourceVerbForm,
                variableStart = variableStartForPolishForm(verb.lemma, form),
                variableEnd = variableEndForPolishForm(verb.lemma, form),
                variableKind = "conjugation"
            )
        )
        return ConjugationCue(
            lemma = verb.lemma,
            combined = combined,
            englishGloss = englishGloss,
            tokens = tokens
        )
    }

    private fun startConjugationQueue(cues: List<ConjugationCue>, mode: String) {
        val quiz = mode != "play"
        val slowPlVoice = app.targetSlowVoice()
        player.start(
            total = cues.size,
            publishParked = { i -> publishConjugation(cues[i], i, cues.size, mode, "pause", plHidden = quiz) },
            prefetchItem = { i ->
                val cue = cues[i]
                app.ensureCachedAudio(cue.englishGloss, app.sourceAudioVoice().locale, app.sourceAudioVoice().voice)
                app.ensureCachedAudio(cue.combined, app.targetAudioVoice().locale, app.targetAudioVoice().voice)
                if (slowFirst && !quiz) app.ensureCachedAudio(cue.combined, app.targetAudioVoice().locale, slowPlVoice)
            },
        ) { i ->
            val cue = cues[i]
            playingLemma = cue.lemma
            val total = cues.size
            when (mode) {
                "conjQuiz" -> {
                    publishConjugation(cue, i, total, mode, "en", plHidden = true)
                    say(cue.englishGloss, app.sourceAudioVoice().locale, app.sourceAudioVoice().voice)
                    publishConjugation(cue, i, total, mode, "pause", plHidden = true)
                    reveal(2000L)
                    publishConjugation(cue, i, total, mode, "pause", plHidden = false)
                    reveal(2000L)
                    publishConjugation(cue, i, total, mode, "pl", plHidden = false)
                    say(cue.combined, app.targetAudioVoice().locale, app.targetAudioVoice().voice)
                }
                else -> {
                    publishConjugation(cue, i, total, mode, "en", plHidden = false)
                    say(cue.englishGloss, app.sourceAudioVoice().locale, app.sourceAudioVoice().voice)
                    if (slowFirst) {
                        publishConjugation(cue, i, total, mode, "pl-slow", plHidden = false)
                        say(cue.combined, app.targetAudioVoice().locale, slowPlVoice)
                    }
                    publishConjugation(cue, i, total, mode, "pl", plHidden = false)
                    say(cue.combined, app.targetAudioVoice().locale, app.targetAudioVoice().voice)
                }
            }
            if (i < total - 1) reveal(500L)
        }
    }

    private fun publishConjugation(
        cue: ConjugationCue, i: Int, total: Int, mode: String, lang: String, plHidden: Boolean
    ) {
        NowVoicingBus.publish(
            NowVoicing(
                en = cue.englishGloss,
                pl = cue.combined,
                literal = null,
                lang = lang,
                position = "${i + 1}/$total",
                words = cue.tokens,
                plHidden = plHidden,
                quizMode = mode != "play"
            )
        )
    }
}

/**
 * Re-enqueue PrefetchWorker after Generate-all so freshly saved Gemini sentences
 * get their audio synthesised in the background. REPLACE cancels any in-flight
 * worker (which is idempotent — cache.has() skips done files) and starts a new
 * pass that includes the new sentences.
 */
internal fun kickPrefetchWorker(app: LangbangApplication) {
    WorkManager.getInstance(app).enqueueUniqueWork(
        PrefetchWorker.UNIQUE_NAME,
        ExistingWorkPolicy.REPLACE,
        OneTimeWorkRequestBuilder<PrefetchWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()
    )
}


@Composable
private fun rememberVerbsTabState(app: LangbangApplication): VerbsTabState {
    val scope = rememberCoroutineScope()
    return remember { VerbsTabState(app, scope) }
}

@Composable
internal fun VerbsTab(
    app: LangbangApplication,
    @Suppress("UNUSED_PARAMETER")
    prefetch: PrefetchProgress,
    nowVoicing: @Composable () -> Unit = {}
) {
    val lesson = remember { app.lessonRepo.lesson2() }
    val state = rememberVerbsTabState(app)
    val activeNowVoicing by NowVoicingBus.state.collectAsState()

    // Initialise/refresh the selection when the lesson list changes.
    if (state.selected == null || lesson.verbs.none { it.lemma == state.selected?.lemma }) {
        state.selectVerb(lesson.verbs.firstOrNull())
    }

    LaunchedEffect(activeNowVoicing?.pl, activeNowVoicing?.words, lesson.verbs) {
        nowVoicingVerb(activeNowVoicing, lesson.verbs)?.let { state.selectVerb(it) }
    }

    val grouped = remember(lesson) {
        lesson.verbs
            .groupBy { it.conjugationClass() }
            .toSortedMap(compareBy { it.ordinal })
    }

    Row(modifier = Modifier.fillMaxSize()) {
        // Verb list runs flush to the top (nothing above it). The right column stacks:
        // Now Voicing band, then the word-type play controls, then the verb paradigm.
        VerbList(
            grouped = grouped,
            selected = state.selected,
            onSelect = { state.selectVerb(it) },
            checkedLemmas = state.checkedLemmas,
            allLemmas = lesson.verbs.map { it.lemma },
            onToggleVerb = { lemma, c -> state.toggleVerbChecked(lemma, c) },
            onToggleVerbs = { lemmas, c -> state.setVerbsChecked(lemmas, c) },
            randomOrder = state.randomOrder,
            onRandomOrderChange = { state.updateRandomOrder(it) },
            enabled = !state.playing,
            modifier = Modifier
                .width(307.dp)
                .fillMaxHeight()
                .background(LbColors.Canvas)
        )
        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
            nowVoicing()
            TopBar(
                state = state,
                allVerbs = lesson.verbs,
                showControls = state.selected != null
            )
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                state.selected?.let {
                    VerbParadigm(
                        verb = it,
                        state = state,
                        allVerbs = lesson.verbs
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TopBar(
    state: VerbsTabState,
    allVerbs: List<VerbEntry>,
    showControls: Boolean
) {
    Surface(color = LbColors.SurfaceRaised, modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FlowRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (showControls) {
                        PhraseCategoryToggle(
                            label = "Pronoun",
                            checked = state.includePronouns,
                            enabled = !state.playing,
                            onCheckedChange = { state.updateIncludePronouns(it) }
                        )
                        PhraseCategoryToggle(
                            label = "Hlp verb",
                            checked = state.includeHelperVerb,
                            enabled = !state.playing,
                            onCheckedChange = { state.updateIncludeHelperVerb(it) }
                        )
                        PhraseCategoryToggle(
                            label = "Adj",
                            checked = state.includeAdjectives,
                            enabled = !state.playing,
                            onCheckedChange = { state.updateIncludeAdjectives(it) }
                        )
                        PhraseCategoryToggle(
                            label = "Adv",
                            checked = state.includeAdverbs,
                            enabled = !state.playing,
                            onCheckedChange = { state.updateIncludeAdverbs(it) }
                        )
                        PhraseCategoryToggle(
                            label = "Nouns",
                            checked = state.includeNouns,
                            enabled = !state.playing,
                            onCheckedChange = { state.updateIncludeNouns(it) }
                        )
                        if (!state.playing) {
                            LbButton.Ghost("Conj quiz", onClick = { state.playAllConjugations(allVerbs, mode = "conjQuiz") })
                        }
                    }
                }
                if (showControls) {
                    Spacer(Modifier.width(8.dp))
                    PhraseCategoryToggle(
                        label = "+ Slow",
                        checked = state.slowFirst,
                        enabled = !state.playing,
                        onCheckedChange = { state.updateSlowFirst(it) }
                    )
                    if (state.preparingPlayback) {
                        Spacer(Modifier.width(8.dp))
                        LbButton.Ghost("Generating", onClick = {}, enabled = false)
                    } else if (!state.playing) {
                        Spacer(Modifier.width(8.dp))
                        VerbPlayButton(
                            count = state.targetPlayCount(allVerbs),
                            onPlay = {
                                if (state.wordTypeVariationsEnabled()) {
                                    state.playAll(allVerbs, includeWordTypeVariations = true)
                                } else {
                                    state.playAllConjugations(allVerbs)
                                }
                            }
                        )
                        Spacer(Modifier.width(8.dp))
                        WordPlayLimitControl(
                            limitText = state.playLimitText,
                            onLimitTextChange = { state.updatePlayLimitText(it) },
                            leadingLabel = "with",
                            trailingLabel = "vars"
                        )
                    }
                }
            }
            state.generateProgress?.let {
                Text(
                    "Generating · $it",
                    fontSize = 10.sp,
                    color = LbColors.Label,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 1.dp)
                )
            }
            state.phraseQualityNotice?.let {
                Text(
                    it,
                    fontSize = 10.sp,
                    color = LbColors.TextMuted,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 1.dp)
                )
            }
        }
    }
}

// CacheBadge moved to AppHeader in LangbangApp.kt — single source instead of one per tab.

@Composable
private fun PhraseCategoryToggle(
    label: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        SubtleCheckbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            label,
            modifier = Modifier.clickable(enabled = enabled) { onCheckedChange(!checked) },
            fontSize = 11.sp,
            color = if (enabled) LbColors.TextSecondary else LbColors.TextMuted
        )
    }
}

@Composable
private fun VerbPlayButton(
    count: Int,
    onPlay: () -> Unit,
    playLabel: String? = null
) {
    val label = playLabel ?: "Play $count"
    Surface(
        color = LbColors.Audio,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .height(30.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onPlay)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "Play $count",
                tint = Color.White,
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1
            )
        }
    }
}

// ── Mode 1 — single verb, all 6 forms ─────────────────────────────────────────

@Composable
private fun VerbList(
    grouped: Map<ConjugationClass, List<VerbEntry>>,
    selected: VerbEntry?,
    onSelect: (VerbEntry) -> Unit,
    checkedLemmas: Set<String>,
    allLemmas: List<String>,
    onToggleVerb: (String, Boolean) -> Unit,
    onToggleVerbs: (List<String>, Boolean) -> Unit,
    randomOrder: Boolean,
    onRandomOrderChange: (Boolean) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = CompactLessonListDefaults.ContentPadding,
        verticalArrangement = Arrangement.spacedBy(CompactLessonListDefaults.ItemGap)
    ) {
        // Master "All verbs" toggle — sits above the first class label and ticks /
        // unticks every verb. Ticked verbs are what Play + the quizzes run over.
        item(key = "all-verbs-master") {
            val allChecked = allLemmas.isNotEmpty() && checkedLemmas.containsAll(allLemmas)
            WordListPlaybackHeader(
                allChecked = allChecked,
                onAllCheckedChange = { onToggleVerbs(allLemmas, it) },
                random = randomOrder,
                onRandomChange = onRandomOrderChange,
                enabled = enabled
            )
        }
        grouped.forEach { (cls, verbs) ->
            item(key = "h-${cls.name}") {
                // Compact class header — class label + an "all" checkbox that ticks every
                // verb in this class. The longer description was dropped to save room.
                val classLemmas = verbs.map { it.lemma }
                val classChecked = classLemmas.isNotEmpty() &&
                    checkedLemmas.containsAll(classLemmas)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 3.dp, bottom = 0.dp)
                ) {
                    SubtleCheckbox(
                        checked = classChecked,
                        onCheckedChange = { onToggleVerbs(classLemmas, it) },
                        enabled = enabled,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        cls.label,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = LbColors.Label
                    )
                }
            }
            itemsIndexed(verbs, key = { _, v -> "v-${v.lemma}" }) { index, v ->
                val isSel = v == selected
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    SubtleCheckbox(
                        checked = v.lemma in checkedLemmas,
                        onCheckedChange = { onToggleVerb(v.lemma, it) },
                        enabled = enabled,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    CompactLessonListCard(
                        selected = isSel,
                        onClick = { onSelect(v) },
                        modifier = Modifier.weight(1f),
                        alternate = index % 2 == 1
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                v.lemma,
                                color = if (isSel) Color.White else LbColors.Primary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Spacer(Modifier.width(10.dp))
                            DelayedEnglishTranslation(
                                text = v.en,
                                color = if (isSel) Color.White.copy(alpha = 0.85f)
                                else LbColors.TextSecondary,
                                fontSize = 12.sp,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VerbParadigm(
    verb: VerbEntry,
    state: VerbsTabState,
    allVerbs: List<VerbEntry>
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(verb.lemma, fontSize = 26.sp, fontWeight = FontWeight.Bold,
                color = LbColors.Primary)
            Spacer(Modifier.width(8.dp))
            // Play the infinitive aloud — same affordance as the conjugation rows below.
            Icon(
                Icons.Default.PlayArrow,
                contentDescription = "Play ${verb.lemma}",
                tint = LbColors.Primary,
                modifier = Modifier
                    .size(28.dp)
                    .clickable { state.playPolishOnce(verb.lemma) }
            )
            Spacer(Modifier.width(12.dp))
            DelayedEnglishTranslation(text = verb.en, fontSize = 14.sp, color = LbColors.TextSecondary)
            Spacer(Modifier.width(12.dp))
            Text(
                verb.conjugationClass().label,
                fontSize = 11.sp,
                color = LbColors.Label,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.weight(1f))
            SelectionNavButtons(
                items = allVerbs,
                selected = verb,
                onSelect = { state.selectVerb(it) }
            )
        }
        // Present + Past side-by-side. Each column has its tense header + 6 ConjRows.
        // When the verb has no past_forms, the past column drops out and present
        // takes the full width — keeps the layout sane for irregular verbs.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                TenseHeader(
                    label = "Present",
                    allChecked = state.includedPresentKeys.containsAll(PERSON_KEYS),
                    onToggleAll = {
                        state.setTenseIncluded(it, PronounFilterStore.TENSE_PRESENT)
                    }
                )
                PERSON_KEYS.forEach { k ->
                    val form = verb.forms[k].orEmpty()
                    val englishGloss = when (k) {
                        "1sg" -> "I"
                        "2sg" -> "you"
                        "3sg" -> "he / she / it"
                        "1pl" -> "we"
                        "2pl" -> "y'all"
                        "3pl" -> "they"
                        else -> ""
                    }
                    ConjRow(
                        pronoun = audioPronoun(k),
                        form = form,
                        baseForm = verb.lemma,
                        englishGloss = englishGloss,
                        included = k in state.includedPresentKeys,
                        onToggleIncluded = { now ->
                            state.toggleIncluded(k, now, PronounFilterStore.TENSE_PRESENT)
                        },
                        onPlay = { state.playConjugationOnce(k, form) }
                    )
                }
            }
            verb.past_forms?.let { past ->
                Column(modifier = Modifier.weight(1f)) {
                    TenseHeader(
                        label = "Past",
                        allChecked = state.includedPastKeys.containsAll(PERSON_KEYS),
                        onToggleAll = {
                            state.setTenseIncluded(it, PronounFilterStore.TENSE_PAST)
                        }
                    )
                    PERSON_KEYS.forEach { k ->
                        val form = past[k].orEmpty()
                        if (form.isEmpty()) {
                            // Keep rows aligned with the present column even when a past
                            // form is missing (rare — usually the whole map is populated).
                            Spacer(Modifier.height(32.dp))
                            return@forEach
                        }
                        val englishGloss = when (k) {
                            "1sg" -> "I (did)"
                            "2sg" -> "you (did)"
                            "3sg" -> "he (did)"
                            "1pl" -> "we (did)"
                            "2pl" -> "y'all (did)"
                            "3pl" -> "they (did)"
                            else -> ""
                        }
                        ConjRow(
                            pronoun = audioPronoun(k),
                            form = form,
                            baseForm = verb.lemma,
                            englishGloss = englishGloss,
                            included = k in state.includedPastKeys,
                            onToggleIncluded = { now ->
                                state.toggleIncluded(k, now, PronounFilterStore.TENSE_PAST)
                            },
                            onPlay = { state.playConjugationOnce(k, form) }
                        )
                    }
                }
            }
        }
        verb.past_forms?.let {
            Text(
                "Past forms shown as masculine-singular (1sg/2sg/3sg) + virile-plural " +
                    "(1pl/2pl/3pl). Feminine / non-virile variants are deferred.",
                fontSize = 10.sp,
                color = LbColors.TextMuted,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        Spacer(Modifier.height(8.dp))
        SentencesList(verb, state)
    }
}

@Composable
private fun SentencesList(
    verb: VerbEntry,
    state: VerbsTabState,
    sentences: List<SentenceExample> = state.sentences,
    emptyMessage: String = "No prepared examples cached yet."
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (sentences.isEmpty()) {
            Text(
                emptyMessage,
                fontSize = 12.sp,
                color = LbColors.TextMuted
            )
        } else {
            sentences.forEachIndexed { i, s ->
                SentenceRow(
                    sentence = s,
                    highlighted = i == state.playingIndex &&
                        state.playingLemma == verb.lemma,
                    onPlay = { state.playSentenceOnce(s) }
                )
            }
        }

        state.error?.let {
            Text("Error: $it", color = Color.Red, fontSize = 11.sp)
        }
    }
}

@Composable
private fun SentenceRow(
    sentence: SentenceExample,
    highlighted: Boolean = false,
    onPlay: () -> Unit
) {
    Card(
        onClick = onPlay,
        colors = CardDefaults.cardColors(
            containerColor = if (highlighted) LbColors.PrimarySoft else LbColors.SurfaceRaised
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = "Play",
                tint = LbColors.Primary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                DelayedEnglishTranslation(
                    text = sentence.en,
                    fontSize = 12.sp,
                    color = LbColors.TextMuted
                )
                com.sponic.langbang.ui.common.WordAlignedPolish(
                    sentence = sentence,
                    plFontSize = 16.sp,
                    plFontWeight = FontWeight.Bold,
                    glossFontSize = 10.sp
                )
            }
        }
    }
}

/** Tense column header ("Present" / "Past") with an inline "all" checkbox for that tense. */
@Composable
private fun TenseHeader(
    label: String,
    allChecked: Boolean,
    onToggleAll: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
    ) {
        Text(
            label,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = LbColors.Label
        )
        Spacer(Modifier.width(8.dp))
        SubtleCheckbox(
            checked = allChecked,
            onCheckedChange = onToggleAll,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(2.dp))
        Text("all", fontSize = 11.sp, color = LbColors.TextMuted)
    }
}

@Composable
private fun ConjRow(
    pronoun: String,
    form: String,
    baseForm: String,
    englishGloss: String,
    included: Boolean,
    onToggleIncluded: (Boolean) -> Unit,
    onPlay: () -> Unit
) {
    Card(
        onClick = onPlay,
        colors = CardDefaults.cardColors(containerColor = LbColors.SurfaceTint),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SubtleCheckbox(
                checked = included,
                onCheckedChange = onToggleIncluded,
                modifier = Modifier.size(28.dp)
            )
            Spacer(Modifier.width(6.dp))
            Icon(Icons.Default.PlayArrow, contentDescription = "Play",
                tint = LbColors.Primary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            // PL form and its delayed English gloss share one line to save vertical room.
            Text(
                pronoun,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = LbColors.TextPrimary
            )
            Spacer(Modifier.width(4.dp))
            VariablePolishText(
                text = form,
                fixedColor = LbColors.TextPrimary,
                variableColor = GrammarVisuals.Variable.Conjugation,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                baseText = baseForm,
                fallbackWholeWord = true
            )
            Spacer(Modifier.width(8.dp))
            DelayedEnglishTranslation(text = englishGloss, fontSize = 11.sp, color = LbColors.TextMuted,
                modifier = Modifier.weight(1f))
        }
    }
}

/** Plays any Polish text, synthesizing into the cache when needed. */
private fun playPolish(app: LangbangApplication, scope: CoroutineScope, text: String) {
    if (text.isEmpty()) return
    PlaybackController.stop()
    app.audioPlayer.stop()
    scope.launch {
        app.playAudioAndAwait(text, app.targetAudioVoice().locale, app.targetAudioVoice().voice)
    }
}

private fun String.primaryGloss(): String =
    substringBefore("/")
        .substringBefore("(")
        .trim()
        .ifBlank { trim() }

private fun englishVerbBase(verb: VerbEntry): String =
    verb.en
        .replace("can / to be able", "be able")
        .replace("must / have to", "have to")
        .removePrefix("to ")
        .substringBefore("/")
        .substringBefore("(")
        .trim()
        .ifBlank { verb.en }

private fun englishComplement(
    verb: VerbEntry,
    style: EnglishComplementStyle
): String {
    val base = englishVerbBase(verb)
    return when (style) {
        EnglishComplementStyle.ToInfinitive -> "to $base"
        EnglishComplementStyle.BareInfinitive -> base
        EnglishComplementStyle.Gerund -> englishGerund(verb)
    }
}

private fun englishInfinitive(verb: VerbEntry): String =
    englishComplement(verb, EnglishComplementStyle.ToInfinitive)

private fun englishGerund(verb: VerbEntry): String {
    val base = englishVerbBase(verb)
    val irregular = mapOf(
        "be" to "being",
        "go" to "going",
        "have" to "having",
        "write" to "writing",
        "take" to "taking",
        "give" to "giving",
        "come back" to "coming back",
        "live" to "living",
        "dance" to "dancing"
    )
    irregular[base]?.let { return it }
    return when {
        base.endsWith("ie") -> base.dropLast(2) + "ying"
        base.endsWith("e") && base.length > 2 -> base.dropLast(1) + "ing"
        else -> base + "ing"
    }
}

private fun adjectiveGenitiveLikeForm(
    adjective: AdjectiveEntry,
    noun: NounEntry
): String {
    val key = noun.gender.takeIf { it in setOf("m", "f", "n") } ?: "m"
    return adjective.acc[key].orEmpty()
        .ifBlank { adjective.nom[key].orEmpty() }
        .ifBlank { adjective.lemma }
}

private fun SentenceExample.qualityKey(): String =
    "${pl.normalizedQualityText()}|${en.normalizedQualityText()}"

private fun String.normalizedQualityText(): String =
    lowercase()
        .replace(NonAlphaNumericRegex, " ")
        .trim()
        .replace(WhitespaceRegex, " ")

/** Short English subject pronoun for NV panel gloss. */
private fun englishSubjectFor(personKey: String): String = when (personKey) {
    "1sg" -> "I"
    "2sg" -> "you"
    "3sg" -> "he"
    "1pl" -> "we"
    "2pl" -> "y'all"
    "3pl" -> "they"
    else -> personKey
}
