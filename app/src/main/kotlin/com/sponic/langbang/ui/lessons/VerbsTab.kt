package com.sponic.langbang.ui.lessons

import com.sponic.langbang.ui.theme.LbColors

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.style.TextOverflow
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
import com.sponic.langbang.data.model.Lesson
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class VerbMode(val label: String) {
    Single("Single verb"),
    ByPronoun("By pronoun")
}

private data class ConjugationCue(
    val lemma: String,
    val combined: String,
    val englishGloss: String,
    val tokens: List<TokenPair>
)

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
    var slowFirst: Boolean by mutableStateOf(true)
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
    var includeAdjectives: Boolean by mutableStateOf(app.practicePrefs.verbPhraseIncludeAdjectives())
        private set
    var includeAdverbs: Boolean by mutableStateOf(app.practicePrefs.verbPhraseIncludeAdverbs())
        private set
    var includeNouns: Boolean by mutableStateOf(app.practicePrefs.verbPhraseIncludeNouns())
        private set
    var generateProgress: String? by mutableStateOf(null)
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
    val playing: Boolean get() = player.hasQueue

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

    private fun resolveByPronounTargets(allVerbs: List<VerbEntry>): List<VerbEntry> {
        val checked = allVerbs.filter { it.lemma in checkedLemmas }
        val targets = if (checked.isNotEmpty()) checked else allVerbs
        return if (randomOrder) targets.shuffled() else targets
    }

    fun targetVerbCount(allVerbs: List<VerbEntry>): Int = resolveTargets(allVerbs).size

    fun byPronounPlayCount(
        allVerbs: List<VerbEntry>,
        personKey: String,
        tense: String
    ): Int = buildByPronounQueue(allVerbs, personKey, tense).size

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
        s.trim().split(Regex("\\s+")).count { it.isNotEmpty() }

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
            val tokens = s.pl.lowercase().split(Regex("[^\\p{L}]+"))
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

    private suspend fun ensurePhraseSentencesFor(target: VerbEntry): List<SentenceExample> {
        val base = ensureSentencesFor(target)
        return phraseSentencesFor(target, base)
    }

    fun phraseSentencesFor(verb: VerbEntry): List<SentenceExample> =
        phraseSentencesFor(verb, loadCombinedSentences(verb))

    private fun phraseSentencesFor(
        verb: VerbEntry,
        base: List<SentenceExample>
    ): List<SentenceExample> {
        val allowedForms = allowedFormsFor(verb)
        if (allowedForms.isEmpty()) return base.distinctBy { it.pl }

        val extras = mutableListOf<SentenceExample>()
        if (includeAdjectives) {
            val adjectives = app.lessonRepo.lesson3().adjectives
            val selected = selectedCategoryLemmas(
                PracticePrefsStore.CATEGORY_ADJECTIVES,
                adjectives.map { it.lemma }
            )
            extras += adjectives
                .filter { it.lemma in selected }
                .flatMap { app.lessonRepo.adjectiveSentencesFor(it.lemma) }
                .filter { sentenceContainsAllowedForm(it, allowedForms) }
        }
        if (includeAdverbs) {
            val adverbs = app.lessonRepo.lesson4().adverbs
            val selected = selectedCategoryLemmas(
                PracticePrefsStore.CATEGORY_ADVERBS,
                adverbs.map { it.lemma }
            )
            extras += adverbs
                .filter { it.lemma in selected }
                .flatMap { app.lessonRepo.adverbSentencesFor(it.lemma) }
                .filter { sentenceContainsAllowedForm(it, allowedForms) }
        }
        if (includeNouns) {
            val nouns = app.lessonRepo.lesson6().nouns
            val selected = selectedCategoryLemmas(
                PracticePrefsStore.CATEGORY_NOUNS,
                nouns.map { it.lemma }
            )
            extras += nouns
                .filter { it.lemma in selected }
                .flatMap { app.lessonRepo.nounSentencesFor(it.lemma) }
                .filter { sentenceContainsAllowedForm(it, allowedForms) }
        }
        return (base + extras).distinctBy { it.pl }
    }

    private fun selectedCategoryLemmas(category: String, allLemmas: List<String>): Set<String> {
        val all = allLemmas.toSet()
        if (!app.practicePrefs.hasCheckedWordLemmas(category)) return all
        return app.practicePrefs.checkedWordLemmas(category).intersect(all)
    }

    private fun allowedFormsFor(verb: VerbEntry): Set<String> {
        val forms = mutableSetOf<String>()
        verb.forms.filterKeys { it in includedPresentKeys }.values.forEach {
            if (it.isNotBlank()) forms += it.lowercase()
        }
        verb.past_forms.orEmpty().filterKeys { it in includedPastKeys }.values.forEach {
            if (it.isNotBlank()) forms += it.lowercase()
        }
        return forms
    }

    private fun sentenceContainsAllowedForm(
        sentence: SentenceExample,
        allowedForms: Set<String>
    ): Boolean = sentence.pl.lowercase()
        .split(Regex("[^\\p{L}]+"))
        .any { it.isNotEmpty() && it in allowedForms }

    fun playAll(
        allVerbs: List<VerbEntry>,
        quiz: Boolean = false,
        includeRelatedPhrases: Boolean = false
    ) {
        if (player.hasQueue) {
            stop()
            return
        }
        val targets = resolveTargets(allVerbs)
        if (targets.isEmpty()) return
        scope.launch {
            val limit = playLimit()
            if (limit <= 0) return@launch
            // Build the queue up-front so rewind/restart can re-enter any item.
            val built: List<SentenceExample> = targets.flatMap { vv ->
                val all = if (includeRelatedPhrases) ensurePhraseSentencesFor(vv)
                    else ensureSentencesFor(vv)
                val filtered = if (quiz) filterByTense(vv, all) else all
                if (randomOrder || quiz) {
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
            if (items.isEmpty()) return@launch
            startSentenceQueue(items, quiz)
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
                setLang("en", s, position)
                say(s.en, app.sourceAudioVoice().locale, app.sourceAudioVoice().voice)
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
     *   - [mode] == "recall": speak PL with PL text hidden + EN hidden, 2s pause,
     *     reveal both, 2s pause. User has to identify "which verb + which person" from
     *     hearing the form alone.
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

    fun playByPronounConjugations(
        allVerbs: List<VerbEntry>,
        personKey: String,
        tense: String
    ) {
        if (player.hasQueue) {
            stop()
            return
        }
        val cues = buildByPronounQueue(allVerbs, personKey, tense)
        if (cues.isEmpty()) return
        startConjugationQueue(cues, "play")
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

    private fun buildByPronounQueue(
        allVerbs: List<VerbEntry>,
        personKey: String,
        tense: String
    ): List<ConjugationCue> {
        val isPast = tense == PronounFilterStore.TENSE_PAST
        return resolveByPronounTargets(allVerbs).mapNotNull { verb ->
            val form = if (isPast) {
                verb.past_forms?.get(personKey).orEmpty()
            } else {
                verb.forms[personKey].orEmpty()
            }
            conjugationCue(verb, personKey, form, isPast)
        }
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
                "conjQuiz", "recall" -> {
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
                        publishConjugation(cue, i, total, mode, "en", plHidden = false)
                        say(cue.englishGloss, app.sourceAudioVoice().locale, app.sourceAudioVoice().voice)
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

private fun personLabel(k: String) = when (k) {
    "1sg" -> "ja (I)"
    "2sg" -> "ty (you)"
    "3sg" -> "on / ona / ono"
    "1pl" -> "my (we)"
    "2pl" -> "wy (y'all)"
    "3pl" -> "oni / one (they)"
    else -> k
}

@Composable
internal fun VerbsTab(
    app: LangbangApplication,
    @Suppress("UNUSED_PARAMETER")
    prefetch: PrefetchProgress,
    tab: L2Tab,
    onTabChange: (L2Tab) -> Unit,
    nowVoicing: @Composable () -> Unit = {}
) {
    val lesson = remember { app.lessonRepo.lesson2() }
    var mode by remember { mutableStateOf(VerbMode.Single) }
    val state = rememberVerbsTabState(app)

    // Initialise/refresh the selection when the lesson list changes.
    if (state.selected == null || lesson.verbs.none { it.lemma == state.selected?.lemma }) {
        state.selectVerb(lesson.verbs.firstOrNull())
    }
    val phraseMode = tab == L2Tab.Phrases

    val grouped = remember(lesson) {
        lesson.verbs
            .groupBy { it.conjugationClass() }
            .toSortedMap(compareBy { it.ordinal })
    }

    Row(modifier = Modifier.fillMaxSize()) {
        // Verb list runs flush to the top (nothing above it). The right column stacks:
        // Now Voicing band, then the Verbs/Phrases toggle + mode chips + play controls,
        // then the conjugation paradigm.
        if (phraseMode || mode == VerbMode.Single) {
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
        }
        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
            nowVoicing()
            TopBar(
                tab = tab,
                onTabChange = onTabChange,
                mode = mode,
                onModeSelect = { mode = it },
                state = state,
                allVerbs = lesson.verbs,
                showControls = (phraseMode || mode == VerbMode.Single) && state.selected != null,
                phraseMode = phraseMode
            )
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when {
                    phraseMode ->
                        state.selected?.let {
                            VerbPhrasesPanel(
                                verb = it,
                                state = state,
                                allVerbs = lesson.verbs
                            )
                        }
                    mode == VerbMode.Single ->
                        state.selected?.let {
                            VerbParadigm(
                                verb = it,
                                state = state,
                                allVerbs = lesson.verbs
                            )
                        }
                    else -> ByPronounMode(lesson, state)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TopBar(
    tab: L2Tab,
    onTabChange: (L2Tab) -> Unit,
    mode: VerbMode,
    onModeSelect: (VerbMode) -> Unit,
    state: VerbsTabState,
    allVerbs: List<VerbEntry>,
    showControls: Boolean,
    phraseMode: Boolean
) {
    Surface(color = LbColors.SurfaceRaised, modifier = Modifier.fillMaxWidth()) {
        Column {
            // Chips + controls share one FlowRow so they wrap into one or two lines
            // inside the right pane. The Verbs/Phrases toggle leads (it used to be a
            // full-width band above the list), then the verb mode chips, then controls.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.Top
            ) {
                FlowRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    L2TabChips(tab, onTabChange)
                    if (!phraseMode) {
                        VerbMode.values().forEach { m ->
                            FilterChip(
                                selected = mode == m,
                                onClick = { onModeSelect(m) },
                                label = { Text(m.label, fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = Color.White
                                ),
                                modifier = Modifier.height(30.dp)
                            )
                        }
                    }
                    if (showControls) {
                        if (phraseMode) {
                            PhraseExamplesControls(state = state, allVerbs = allVerbs)
                        } else {
                            ExamplesControls(state = state, allVerbs = allVerbs)
                        }
                    }
                }
                if (showControls) {
                    Spacer(Modifier.width(8.dp))
                    VerbPlayButton(
                        playing = state.playing,
                        count = state.targetVerbCount(allVerbs),
                        onPlay = {
                            if (phraseMode) state.playAll(allVerbs, includeRelatedPhrases = true)
                            else state.playAllConjugations(allVerbs)
                        },
                        onStop = { state.stop() }
                    )
                    if (!state.playing) {
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
        }
    }
}

// CacheBadge moved to AppHeader in LangbangApp.kt — single source instead of one per tab.

// Emits its controls directly into the caller's FlowRow (no own layout wrapper) so chips
// and buttons wrap together as one band.
@Composable
private fun ExamplesControls(state: VerbsTabState, allVerbs: List<VerbEntry>) {
    // The "All verbs" checkbox moved into the left verb list (above the first class);
    // ticked verbs now drive Play / the quizzes. "Slow first" stays here.
    Row(verticalAlignment = Alignment.CenterVertically) {
        SubtleCheckbox(
            checked = state.slowFirst,
            onCheckedChange = { state.slowFirst = it },
            enabled = !state.playing,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(4.dp))
        Text("Slow first", fontSize = 11.sp, color = LbColors.TextSecondary)
    }

    // The conjugation reciter's Play/Stop control sits in a fixed right slot so
    // it never drifts into the filter chips.
    if (!state.playing) {
        LbButton.Ghost("Conj quiz", onClick = { state.playAllConjugations(allVerbs, mode = "conjQuiz") })
        LbButton.Ghost("Recall quiz", onClick = { state.playAllConjugations(allVerbs, mode = "recall") })
    }
    val hasSentences = state.checkedLemmas.isNotEmpty() || state.sentences.isNotEmpty()
    if (hasSentences && !state.playing) {
        LbButton.Ghost("Sent. quiz", onClick = { state.playAll(allVerbs, quiz = true) }, icon = Icons.Default.PlayArrow)
    }

    // Generate / Regen button removed — it now lives on the Settings page (a single
    // "Generate cached sentences" action that hits every verb/adj/adv). Tiny busy
    // spinner kept so a generate-all kicked from Settings still shows progress
    // here while the user is on the Verbs tab.
    if (state.busy) {
        CircularProgressIndicator(
            modifier = Modifier.size(18.dp), strokeWidth = 2.dp
        )
    }
}

@Composable
private fun PhraseExamplesControls(state: VerbsTabState, allVerbs: List<VerbEntry>) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        SubtleCheckbox(
            checked = state.slowFirst,
            onCheckedChange = { state.slowFirst = it },
            enabled = !state.playing,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(4.dp))
        Text("Slow first", fontSize = 11.sp, color = LbColors.TextSecondary)
    }

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
        LbButton.Ghost(
            "Sent. quiz",
            onClick = {
                state.playAll(
                    allVerbs,
                    quiz = true,
                    includeRelatedPhrases = true
                )
            },
            icon = Icons.Default.PlayArrow
        )
    }

    if (state.busy) {
        CircularProgressIndicator(
            modifier = Modifier.size(18.dp), strokeWidth = 2.dp
        )
    }
}

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
    playing: Boolean,
    count: Int,
    onPlay: () -> Unit,
    onStop: () -> Unit,
    playLabel: String? = null
) {
    val bg = if (playing) LbColors.Stop else LbColors.Audio
    val label = if (playing) "Stop" else playLabel ?: "Play $count"
    Surface(
        color = bg,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .height(30.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = if (playing) onStop else onPlay)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = if (playing) Icons.Default.Stop else Icons.Default.PlayArrow,
                contentDescription = if (playing) "Stop" else "Play $count",
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
private fun VerbPhrasesPanel(
    verb: VerbEntry,
    state: VerbsTabState,
    allVerbs: List<VerbEntry>
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                verb.lemma,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = LbColors.Primary
            )
            Spacer(Modifier.width(8.dp))
            Icon(
                Icons.Default.PlayArrow,
                contentDescription = "Play ${verb.lemma}",
                tint = LbColors.Primary,
                modifier = Modifier
                    .size(28.dp)
                    .clickable { state.playPolishOnce(verb.lemma) }
            )
            Spacer(Modifier.width(12.dp))
            DelayedEnglishTranslation(
                text = verb.en,
                fontSize = 14.sp,
                color = LbColors.TextSecondary
            )
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
        SentencesList(
            verb = verb,
            state = state,
            sentences = state.phraseSentencesFor(verb),
            emptyMessage = "No prepared verb phrases cached yet."
        )
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

// ── Mode 2 — pick a pronoun, see every verb conjugated for it ─────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ByPronounMode(lesson: Lesson, state: VerbsTabState) {
    var personKey by remember { mutableStateOf("1sg") }
    var tense by remember { mutableStateOf(PronounFilterStore.TENSE_PRESENT) }
    val allLemmas = remember(lesson) { lesson.verbs.map { it.lemma } }

    Column(Modifier.fillMaxSize()) {
        Surface(color = LbColors.SurfaceRaised, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    FlowRow(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        PERSON_KEYS.forEach { k ->
                            FilterChip(
                                selected = personKey == k,
                                onClick = { personKey = k },
                                label = { Text(personLabel(k), fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = Color.White
                                ),
                                modifier = Modifier.height(30.dp)
                            )
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SubtleCheckbox(
                                checked = state.slowFirst,
                                onCheckedChange = { state.slowFirst = it },
                                enabled = !state.playing,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "Slow first",
                                fontSize = 11.sp,
                                color = LbColors.TextSecondary,
                                maxLines = 1
                            )
                        }
                        VerbPlayButton(
                            playing = state.playing,
                            count = state.byPronounPlayCount(lesson.verbs, personKey, tense),
                            onPlay = {
                                state.playByPronounConjugations(
                                    lesson.verbs,
                                    personKey,
                                    tense
                                )
                            },
                            onStop = { state.stop() },
                            playLabel = "Play All"
                        )
                        TenseToggleGroup(tense = tense, onTenseChange = { tense = it })
                    }
                }
            }
        }
        val grouped = remember(lesson) {
            lesson.verbs
                .groupBy { it.conjugationClass() }
                .toSortedMap(compareBy { it.ordinal })
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            item(key = "by-pronoun-master-$personKey-$tense") {
                val allChecked = allLemmas.isNotEmpty() &&
                    state.checkedLemmas.containsAll(allLemmas)
                WordListPlaybackHeader(
                    allChecked = allChecked,
                    onAllCheckedChange = { state.setVerbsChecked(allLemmas, it) },
                    random = state.randomOrder,
                    onRandomChange = { state.updateRandomOrder(it) },
                    enabled = !state.playing,
                    allLabel = "all verbs"
                )
            }
            grouped.forEach { (cls, verbs) ->
                item(key = "h-${cls.name}-$personKey-$tense") {
                    val classLemmas = verbs.map { it.lemma }
                    val classChecked = classLemmas.isNotEmpty() &&
                        state.checkedLemmas.containsAll(classLemmas)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp, bottom = 1.dp)
                    ) {
                        SubtleCheckbox(
                            checked = classChecked,
                            onCheckedChange = { state.setVerbsChecked(classLemmas, it) },
                            enabled = !state.playing,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            cls.label,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = LbColors.Label
                        )
                    }
                }
                itemsIndexed(verbs, key = { _, v -> "p-${v.lemma}-$personKey-$tense" }) { index, v ->
                    val form = if (tense == PronounFilterStore.TENSE_PAST) {
                        v.past_forms?.get(personKey).orEmpty()
                    } else {
                        v.forms[personKey].orEmpty()
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        SubtleCheckbox(
                            checked = v.lemma in state.checkedLemmas,
                            onCheckedChange = { state.toggleVerbChecked(v.lemma, it) },
                            enabled = !state.playing,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        CompactLessonListCard(
                            selected = state.playingLemma == v.lemma,
                            onClick = { state.playConjugationOnce(personKey, form) },
                            modifier = Modifier.weight(1f),
                            alternate = index % 2 == 1,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 3.dp)
                        ) {
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (form.isNotEmpty()) {
                                    Icon(
                                        Icons.Default.PlayArrow,
                                        contentDescription = "Play",
                                        tint = LbColors.Primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                } else {
                                    Spacer(Modifier.width(26.dp))
                                }
                                Row(
                                    Modifier.width(250.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        v.lemma,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = LbColors.Primary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    DelayedEnglishTranslation(
                                        text = v.en,
                                        fontSize = 10.sp,
                                        color = LbColors.TextMuted,
                                        maxLines = 1,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                Spacer(Modifier.width(10.dp))
                                if (form.isEmpty()) {
                                    Text(
                                        "-",
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = LbColors.TextMuted.copy(alpha = 0.5f),
                                        modifier = Modifier.weight(1f)
                                    )
                                } else {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            audioPronoun(personKey),
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
                                            baseText = v.lemma,
                                            fallbackWholeWord = true
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TenseToggleGroup(
    tense: String,
    onTenseChange: (String) -> Unit
) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = Modifier
            .clip(shape)
            .background(Color.White)
            .border(1.dp, LbColors.TextMuted.copy(alpha = 0.45f), shape)
    ) {
        TenseToggleSegment(
            label = "Present",
            selected = tense == PronounFilterStore.TENSE_PRESENT,
            onClick = { onTenseChange(PronounFilterStore.TENSE_PRESENT) }
        )
        TenseToggleSegment(
            label = "Past",
            selected = tense == PronounFilterStore.TENSE_PAST,
            onClick = { onTenseChange(PronounFilterStore.TENSE_PAST) }
        )
    }
}

@Composable
private fun TenseToggleSegment(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Text(
        label,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        color = if (selected) Color.White else LbColors.TextSecondary,
        modifier = Modifier
            .background(if (selected) LbColors.Label else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp)
    )
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

/** Plays "pronoun form" together (e.g. "ja jestem") so the spoken text matches the row. */
private fun playConjugation(
    app: LangbangApplication,
    scope: CoroutineScope,
    personKey: String,
    form: String
) {
    if (form.isEmpty()) return
    val combined = "${audioPronoun(personKey)} $form".trim()
    playPolish(app, scope, combined)
}

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
