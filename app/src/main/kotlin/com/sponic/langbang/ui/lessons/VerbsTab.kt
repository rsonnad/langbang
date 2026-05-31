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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sponic.langbang.LangbangApplication
import com.sponic.langbang.domain.PrefetchProgress
import com.sponic.langbang.domain.PrefetchWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Constraints
import androidx.work.NetworkType
import com.sponic.langbang.data.PronounFilterStore
import com.sponic.langbang.data.VerbSentenceStore
import com.sponic.langbang.data.model.ConjugationClass
import com.sponic.langbang.data.model.Lesson
import com.sponic.langbang.data.model.PERSON_KEYS
import com.sponic.langbang.data.model.SentenceExample
import com.sponic.langbang.data.model.VerbEntry
import com.sponic.langbang.data.model.audioPronoun
import com.sponic.langbang.data.model.conjugationClass
import com.sponic.langbang.integrations.GeminiClient
import com.sponic.langbang.domain.NowVoicing
import com.sponic.langbang.domain.NowVoicingBus
import com.sponic.langbang.domain.PlaybackController
import com.sponic.langbang.domain.PlaybackTransport
import com.sponic.langbang.domain.englishConjugate
import com.sponic.langbang.integrations.AzureTtsClient
import android.media.MediaMetadataRetriever
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

private enum class VerbMode(val label: String) {
    Single("Single verb"),
    ByPronoun("By pronoun")
}

/**
 * Holds the sentence-generation + playback state for the Verbs tab. Lives at the tab root
 * so the control strip (Generate / Play all / Regenerate / All verbs) can render in the
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
     * Lemmas the learner has ticked in the left verb list. When non-empty, Play all and
     * the quizzes run across exactly these verbs (in shuffled order); when empty they
     * fall back to the single selected verb. Persisted so the selection is sticky.
     */
    var checkedLemmas: Set<String> by mutableStateOf(app.practicePrefs.checkedVerbs())
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

    private var playJob: Job? = null
    var playingIndex: Int by mutableStateOf(-1)
        private set
    var playingLemma: String? by mutableStateOf(null)
        private set
    var playingPl: String? by mutableStateOf(null)
        private set
    var playingEn: String? by mutableStateOf(null)
        private set
    var playingLiteral: String? by mutableStateOf(null)
        private set
    var playingLang: String? by mutableStateOf(null)
        private set
    val playing: Boolean get() = playJob?.isActive == true

    // Holds the current quiz/play-all queue so rewind/restart can re-enter at any index
    // without rebuilding the list. Set by playAll(), cleared by stop().
    private var queue: List<SentenceExample> = emptyList()
    private var queueLemma: String? = null
    private var queueQuiz: Boolean = false
    private var currentItemIndex: Int = 0

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

    /**
     * The verbs a multi-verb action should run over: the ticked verbs (shuffled, so the
     * learner gets them in random order), or just the selected verb when nothing's ticked.
     */
    private fun resolveTargets(allVerbs: List<VerbEntry>): List<VerbEntry> {
        val checked = allVerbs.filter { it.lemma in checkedLemmas }
        return if (checked.isNotEmpty()) checked.shuffled() else listOfNotNull(selected)
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
        playJob?.cancel()
        playJob = null
        playingIndex = -1
        playingLemma = null
        playingPl = null
        playingEn = null
        playingLiteral = null
        playingLang = null
        queue = emptyList()
        queueLemma = null
        currentItemIndex = 0
        app.audioPlayer.stop()
        NowVoicingBus.clear()
        PlaybackController.unregister()
    }

    /** Re-play the previous sentence in the active quiz/play-all queue. */
    fun rewind() {
        if (queue.isEmpty()) return
        playJob?.cancel()
        playJob = null
        app.audioPlayer.stop()
        currentItemIndex = (currentItemIndex - 1).coerceAtLeast(0)
        relaunchFromCurrent()
    }

    fun restartQueue() {
        if (queue.isEmpty()) return
        playJob?.cancel()
        playJob = null
        app.audioPlayer.stop()
        currentItemIndex = 0
        relaunchFromCurrent()
    }

    /**
     * Plays one item from the queue at [i]. The outer loop in [relaunchFromCurrent]
     * drives advance/rewind via [currentItemIndex] so we can re-enter the same item
     * after a rewind tap. Returns after the item completes (or the job is cancelled).
     */
    private suspend fun playOneItem(s: SentenceExample, i: Int, total: Int, quiz: Boolean) {
        playingIndex = i
        playingPl = s.pl
        playingEn = s.en
        playingLiteral = s.literal
        val position = "${i + 1}/$total"
        if (quiz) {
            // Floor the pre-reveal pause at 2s so the learner always gets a real
            // recall window before the Polish text appears, even if the user dialled
            // the configurable delay below that.
            val configMs = (app.randomConfig.load().quizDelaySeconds * 1000).toLong()
            val delayMs = configMs.coerceAtLeast(2000L)
            // EN audio with PL text hidden so the learner can't peek.
            publishQuiz("en", s, position, plHidden = true)
            playAndAwait(app, s.en, AzureTtsClient.LOCALE_EN, AzureTtsClient.EN_US_F)
            // Recall window — PL still hidden.
            publishQuiz("pause", s, position, plHidden = true)
            delay(delayMs)
            // Reveal PL text, hold so eye + brain can compare.
            publishQuiz("pause", s, position, plHidden = false)
            delay(delayMs)
            // Now speak the PL.
            publishQuiz("pl", s, position, plHidden = false)
            playAndAwait(app, s.pl, AzureTtsClient.LOCALE_PL, AzureTtsClient.PL_PL_F)
        } else if (slowFirst) {
            // First pass: slow Polish for clarity, then normal-rate.
            setLang("en", s, position)
            playAndAwait(app, s.en, AzureTtsClient.LOCALE_EN, AzureTtsClient.EN_US_F)
            setLang("pl-slow", s, position)
            playAndAwait(app, s.pl, AzureTtsClient.LOCALE_PL, app.audioPrefs.slowPlVoice())
            setLang("en", s, position)
            playAndAwait(app, s.en, AzureTtsClient.LOCALE_EN, AzureTtsClient.EN_US_F)
            setLang("pl", s, position)
            playAndAwait(app, s.pl, AzureTtsClient.LOCALE_PL, AzureTtsClient.PL_PL_F)
        } else {
            // Fast-only: single EN then single PL at normal rate.
            setLang("en", s, position)
            playAndAwait(app, s.en, AzureTtsClient.LOCALE_EN, AzureTtsClient.EN_US_F)
            setLang("pl", s, position)
            playAndAwait(app, s.pl, AzureTtsClient.LOCALE_PL, AzureTtsClient.PL_PL_F)
        }
    }

    private fun setLang(l: String, s: SentenceExample, position: String) {
        playingLang = l
        NowVoicingBus.publish(NowVoicing(s.en, s.pl, s.literal, l, position, s.words))
    }

    /** Quiz-flavored variant — stamps quizMode + plHidden so the NV panel can mask + show the delay slider. */
    private fun publishQuiz(l: String, s: SentenceExample, position: String, plHidden: Boolean) {
        playingLang = l
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

    fun playAll(allVerbs: List<VerbEntry>, quiz: Boolean = false) {
        if (playJob?.isActive == true) {
            stop()
            return
        }
        val targets = resolveTargets(allVerbs)
        val v = targets.firstOrNull() ?: return
        queueQuiz = quiz
        queueLemma = v.lemma
        scope.launch {
            // Build the queue up-front so rewind/restart can re-enter any item.
            val built: List<SentenceExample> = if (checkedLemmas.isNotEmpty()) {
                targets.flatMap { vv ->
                    val list = ensureSentencesFor(vv).let { all ->
                        if (quiz) filterByTense(vv, all) else all
                    }
                    list
                }
            } else {
                if (quiz) filterByTense(v, sentences) else sentences
            }
            queue = if (quiz) built.shuffled().sortedBy { tokenCountOf(it.pl) } else built
            if (queue.isEmpty()) return@launch
            currentItemIndex = 0
            relaunchFromCurrent()
        }
    }

    /**
     * (Re)starts playback from [currentItemIndex]. Used initially by playAll and again
     * by rewind/restart after they reposition the index. Registers a [PlaybackTransport]
     * so the NV panel's 2x2 transport renders for this source.
     */
    private fun relaunchFromCurrent() {
        PlaybackController.register(
            PlaybackTransport(
                stop = { stop() },
                rewind = { rewind() },
                restart = { restartQueue() },
                pauseResume = null,
                isPaused = { false }
            )
        )
        playJob = scope.launch {
            try {
                while (currentItemIndex < queue.size) {
                    val i = currentItemIndex
                    val s = queue[i]
                    playOneItem(s, i, queue.size, queueQuiz)
                    if (currentItemIndex == i) {
                        currentItemIndex = i + 1
                        // +1s breathing room between items (only in non-quiz mode —
                        // quiz already has its own reveal pauses).
                        if (!queueQuiz && currentItemIndex < queue.size) delay(1000)
                    }
                }
            } finally {
                playingIndex = -1
                playingLemma = null
                playingPl = null
                playingEn = null
                playingLiteral = null
                playingLang = null
                playJob = null
                NowVoicingBus.clear()
                PlaybackController.unregister()
            }
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
        if (playJob?.isActive == true) {
            stop()
            return
        }
        val targets = resolveTargets(allVerbs)
        if (targets.isEmpty()) return
        PlaybackController.register(
            PlaybackTransport(stop = { stop() })
        )
        playJob = scope.launch {
            try {
                targets.forEach { vv ->
                    playingLemma = vv.lemma
                    suspend fun playFormsForTense(
                        formMap: Map<String, String>,
                        included: Set<String>,
                        tenseLabel: String
                    ) {
                        PERSON_KEYS.forEach { k ->
                            if (k !in included) return@forEach
                            val form = formMap[k].orEmpty()
                            if (form.isBlank()) return@forEach
                            val pron = audioPronoun(k)
                            val combined = "$pron $form".trim()
                            val englishSubject = englishSubjectFor(k)
                            // Render the actual conjugated form ("I am" / "she goes" / "we were")
                            // instead of "$subject — to be". Same shared helper as Random Play —
                            // see domain/EnglishConjugator.kt. No "(past)" suffix: "was" / "were"
                            // already convey past tense, so the parenthetical was redundant noise
                            // once the gloss stopped being the infinitive.
                            val isPast = tenseLabel.equals("past", ignoreCase = true)
                            val englishVerbForm = englishConjugate(vv.en, k, isPast)
                            val englishGloss = "$englishSubject $englishVerbForm"
                            playingPl = combined
                            playingEn = englishGloss
                            playingLiteral = null
                            // Publish to NV so the sticky panel shows ONLY pronoun + verb
                            // (no surrounding sentence). Two tokens, two glosses.
                            val tokens = listOf(
                                com.sponic.langbang.data.model.TokenPair(pron, englishSubject),
                                com.sponic.langbang.data.model.TokenPair(form, englishVerbForm)
                            )
                            fun publishLang(l: String, plHidden: Boolean = false,
                                            enHiddenLabel: String? = null) {
                                playingLang = l
                                NowVoicingBus.publish(
                                    NowVoicing(
                                        en = enHiddenLabel ?: englishGloss,
                                        pl = combined, literal = null,
                                        lang = l, position = null, words = tokens,
                                        plHidden = plHidden,
                                        quizMode = mode != "play"
                                    )
                                )
                            }
                            when (mode) {
                                "conjQuiz" -> {
                                    // EN cue spoken with PL hidden — user must recall the form.
                                    publishLang("en", plHidden = true)
                                    playAndAwait(app, englishGloss, AzureTtsClient.LOCALE_EN,
                                        AzureTtsClient.EN_US_F)
                                    publishLang("pause", plHidden = true)
                                    delay(2000L)
                                    // Reveal PL, hold so the eye + ear can compare.
                                    publishLang("pause", plHidden = false)
                                    delay(2000L)
                                    publishLang("pl", plHidden = false)
                                    playAndAwait(app, combined, AzureTtsClient.LOCALE_PL,
                                        AzureTtsClient.PL_PL_F)
                                }
                                "recall" -> {
                                    // EN spoken FIRST with the EN text shown (only the PL is
                                    // hidden) so the learner reads + hears the meaning, recalls
                                    // the Polish form from memory, then the PL is revealed and
                                    // spoken. EN cue stays visible throughout the recall window.
                                    publishLang("en", plHidden = true)
                                    playAndAwait(app, englishGloss, AzureTtsClient.LOCALE_EN,
                                        AzureTtsClient.EN_US_F)
                                    publishLang("pause", plHidden = true)
                                    delay(2000L)
                                    // Reveal the PL too.
                                    publishLang("pause", plHidden = false)
                                    delay(2000L)
                                    publishLang("pl", plHidden = false)
                                    playAndAwait(app, combined, AzureTtsClient.LOCALE_PL,
                                        AzureTtsClient.PL_PL_F)
                                }
                                else -> {
                                    if (slowFirst) {
                                        publishLang("pl-slow")
                                        playAndAwait(app, combined, AzureTtsClient.LOCALE_PL,
                                            app.audioPrefs.slowPlVoice())
                                    }
                                    publishLang("pl")
                                    playAndAwait(app, combined, AzureTtsClient.LOCALE_PL,
                                        AzureTtsClient.PL_PL_F)
                                }
                            }
                            // 1s gap between items so each conjugation has a moment to land.
                            delay(1000)
                        }
                    }
                    playFormsForTense(vv.forms, includedPresentKeys, "")
                    vv.past_forms?.let { past ->
                        playFormsForTense(past, includedPastKeys, "past")
                    }
                }
            } finally {
                playingIndex = -1
                playingLemma = null
                playingPl = null
                playingEn = null
                playingLiteral = null
                playingLang = null
                playJob = null
                NowVoicingBus.clear()
                PlaybackController.unregister()
            }
        }
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

private fun mp3DurationMs(file: java.io.File): Long {
    if (!file.exists()) return 0L
    return try {
        val r = MediaMetadataRetriever()
        r.setDataSource(file.absolutePath)
        val v = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        r.release()
        v
    } catch (_: Throwable) { 0L }
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

    val grouped = remember(lesson) {
        lesson.verbs
            .groupBy { it.conjugationClass() }
            .toSortedMap(compareBy { it.ordinal })
    }

    Row(modifier = Modifier.fillMaxSize()) {
        // Verb list runs flush to the top (nothing above it). The right column stacks:
        // Now Voicing band, then the Verbs/Phrases toggle + mode chips + play controls,
        // then the conjugation paradigm.
        if (mode == VerbMode.Single) {
            VerbList(
                grouped = grouped,
                selected = state.selected,
                onSelect = { state.selectVerb(it) },
                checkedLemmas = state.checkedLemmas,
                allLemmas = lesson.verbs.map { it.lemma },
                onToggleVerb = { lemma, c -> state.toggleVerbChecked(lemma, c) },
                onToggleVerbs = { lemmas, c -> state.setVerbsChecked(lemmas, c) },
                modifier = Modifier
                    .width(256.dp)
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
                showControls = mode == VerbMode.Single && state.selected != null
            )
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when (mode) {
                    VerbMode.Single ->
                        state.selected?.let { VerbParadigm(app, it, state) }
                    VerbMode.ByPronoun -> ByPronounMode(app, lesson)
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
    showControls: Boolean
) {
    Surface(color = LbColors.SurfaceRaised, modifier = Modifier.fillMaxWidth()) {
        Column {
            // Chips + controls share one FlowRow so they wrap into one or two lines
            // inside the right pane. The Verbs/Phrases toggle leads (it used to be a
            // full-width band above the list), then the verb mode chips, then controls.
            FlowRow(
                Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                L2TabChips(tab, onTabChange)
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
                if (showControls) {
                    ExamplesControls(state = state, allVerbs = allVerbs)
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
    // ticked verbs now drive Play all / the quizzes. "Slow first" stays here.
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
            checked = state.slowFirst,
            onCheckedChange = { state.slowFirst = it },
            enabled = !state.playing,
            colors = CheckboxDefaults.colors(
                checkedColor = MaterialTheme.colorScheme.primary
            ),
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(4.dp))
        Text("Slow first", fontSize = 11.sp, color = LbColors.TextSecondary)
    }

    // "Play all" is the conjugation reciter — always actionable (no sentence cache
    // required). Quiz button still plays the Gemini example sentences in test mode
    // and so depends on the sentence cache being non-empty.
    Button(
        onClick = { state.playAllConjugations(allVerbs) },
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary
        ),
        shape = RoundedCornerShape(16.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp)
    ) {
        Icon(
            if (state.playing) Icons.Default.Stop else Icons.Default.PlayArrow,
            contentDescription = if (state.playing) "Stop" else "Play all",
            tint = Color.White,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            if (state.playing) "Stop" else "Play all",
            fontSize = 12.sp,
            color = Color.White
        )
    }
    if (!state.playing) {
        // Conjugation drill quiz — EN cue, hide PL, reveal. No sentence cache needed.
        Button(
            onClick = { state.playAllConjugations(allVerbs, mode = "conjQuiz") },
            colors = ButtonDefaults.buttonColors(
                containerColor = LbColors.Primary
            ),
            shape = RoundedCornerShape(16.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp)
        ) {
            Text("?? Conj", fontSize = 12.sp, color = Color.White)
        }
        // Recollection drill — speak PL, hide everything, reveal. Tests "which form is this?"
        Button(
            onClick = { state.playAllConjugations(allVerbs, mode = "recall") },
            colors = ButtonDefaults.buttonColors(
                containerColor = LbColors.Accent
            ),
            shape = RoundedCornerShape(16.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp)
        ) {
            Text("?? Recall", fontSize = 12.sp, color = Color.White)
        }
    }
    val hasSentences = state.checkedLemmas.isNotEmpty() || state.sentences.isNotEmpty()
    if (hasSentences && !state.playing) {
        Button(
            onClick = { state.playAll(allVerbs, quiz = true) },
            colors = ButtonDefaults.buttonColors(
                containerColor = LbColors.Label
            ),
            shape = RoundedCornerShape(16.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp)
        ) {
            Icon(
                Icons.Default.PlayArrow,
                contentDescription = "Sentence quiz",
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text("?? Sent.", fontSize = 12.sp, color = Color.White)
        }
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
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        // Master "All verbs" toggle — sits above the first class label and ticks /
        // unticks every verb. Ticked verbs are what Play all + the quizzes run over.
        item(key = "all-verbs-master") {
            val allChecked = allLemmas.isNotEmpty() && checkedLemmas.containsAll(allLemmas)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp)
            ) {
                Checkbox(
                    checked = allChecked,
                    onCheckedChange = { onToggleVerbs(allLemmas, it) },
                    colors = CheckboxDefaults.colors(
                        checkedColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "All verbs",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = LbColors.TextSecondary
                )
            }
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
                    modifier = Modifier.padding(top = 4.dp, bottom = 1.dp)
                ) {
                    Checkbox(
                        checked = classChecked,
                        onCheckedChange = { onToggleVerbs(classLemmas, it) },
                        colors = CheckboxDefaults.colors(
                            checkedColor = MaterialTheme.colorScheme.primary
                        ),
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
            items(verbs, key = { "v-${it.lemma}" }) { v ->
                val isSel = v == selected
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = v.lemma in checkedLemmas,
                        onCheckedChange = { onToggleVerb(v.lemma, it) },
                        colors = CheckboxDefaults.colors(
                            checkedColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Card(
                        onClick = { onSelect(v) },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSel) MaterialTheme.colorScheme.primary
                            else Color.White
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            Modifier.padding(horizontal = 12.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                v.lemma,
                                color = if (isSel) Color.White else LbColors.Primary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 16.sp
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                v.en,
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
    app: LangbangApplication,
    verb: VerbEntry,
    state: VerbsTabState
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
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
                    .clickable { playPolish(app, verb.lemma) }
            )
            Spacer(Modifier.width(12.dp))
            Text(verb.en, fontSize = 14.sp, color = LbColors.TextSecondary)
            Spacer(Modifier.width(12.dp))
            Text(
                verb.conjugationClass().label,
                fontSize = 11.sp,
                color = LbColors.Label,
                fontWeight = FontWeight.SemiBold
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
                    val spoken = "${audioPronoun(k)} $form".trim()
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
                        spoken = spoken,
                        englishGloss = englishGloss,
                        included = k in state.includedPresentKeys,
                        onToggleIncluded = { now ->
                            state.toggleIncluded(k, now, PronounFilterStore.TENSE_PRESENT)
                        },
                        onPlay = { playConjugation(app, k, form) }
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
                        val spoken = "${audioPronoun(k)} $form".trim()
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
                            spoken = spoken,
                            englishGloss = englishGloss,
                            included = k in state.includedPastKeys,
                            onToggleIncluded = { now ->
                                state.toggleIncluded(k, now, PronounFilterStore.TENSE_PAST)
                            },
                            onPlay = { playConjugation(app, k, form) }
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
        SentencesList(app, verb, state)
    }
}

@Composable
private fun SentencesList(
    app: LangbangApplication,
    verb: VerbEntry,
    state: VerbsTabState
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            "Examples",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = LbColors.Label
        )

        if (state.sentences.isEmpty()) {
            Text(
                "No examples yet — tap Generate examples above to make 10 short sentences using the checked pronouns.",
                fontSize = 12.sp,
                color = LbColors.TextMuted
            )
        } else {
            state.sentences.forEachIndexed { i, s ->
                SentenceRow(
                    sentence = s,
                    highlighted = i == state.playingIndex &&
                        state.playingLemma == verb.lemma,
                    onPlay = { playPolish(app, s.pl) }
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
            Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    sentence.en,
                    fontSize = 12.sp,
                    color = LbColors.TextMuted
                )
                com.sponic.langbang.ui.common.WordAlignedPolish(
                    sentence = sentence,
                    plFontSize = 16.sp,
                    plFontWeight = FontWeight.Medium,
                    glossFontSize = 10.sp
                )
            }
            Icon(Icons.Default.PlayArrow, contentDescription = "Play",
                tint = LbColors.Primary, modifier = Modifier.size(20.dp))
        }
    }
}

/**
 * Ensures the TTS file for [text] exists (synthesising on demand), then plays it and
 * suspends until playback completes. Cancellation stops the player.
 */
private suspend fun playAndAwait(
    app: LangbangApplication,
    text: String,
    locale: String,
    voice: String
) {
    if (text.isEmpty()) return
    val file = app.audioCache.fileFor(locale, voice, text)
    if (!app.audioCache.has(file)) {
        app.tts.synthesize(text, voice, locale, file)
    }
    if (!app.audioCache.has(file)) return
    suspendCancellableCoroutine<Unit> { cont ->
        app.audioPlayer.play(file) {
            if (cont.isActive) cont.resume(Unit)
        }
        cont.invokeOnCancellation { app.audioPlayer.stop() }
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
        Checkbox(
            checked = allChecked,
            onCheckedChange = onToggleAll,
            colors = CheckboxDefaults.colors(
                checkedColor = MaterialTheme.colorScheme.primary
            ),
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(2.dp))
        Text("all", fontSize = 11.sp, color = LbColors.TextMuted)
    }
}

@Composable
private fun ConjRow(
    spoken: String,
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
            Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = included,
                onCheckedChange = onToggleIncluded,
                colors = CheckboxDefaults.colors(
                    checkedColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.size(28.dp)
            )
            Spacer(Modifier.width(6.dp))
            // PL form and its English gloss share one line — the gloss sits directly to
            // the right (no stacked line break) to save vertical room.
            Text(spoken, fontSize = 18.sp, fontWeight = FontWeight.Medium,
                color = LbColors.Primary)
            Spacer(Modifier.width(8.dp))
            Text(englishGloss, fontSize = 11.sp, color = LbColors.TextMuted,
                modifier = Modifier.weight(1f))
            Icon(Icons.Default.PlayArrow, contentDescription = "Play",
                tint = LbColors.Primary, modifier = Modifier.size(20.dp))
        }
    }
}

// ── Mode 2 — pick a pronoun, see every verb conjugated for it ─────────────────

@Composable
private fun ByPronounMode(app: LangbangApplication, lesson: Lesson) {
    var personKey by remember { mutableStateOf("1sg") }
    var tense by remember { mutableStateOf(PronounFilterStore.TENSE_PRESENT) }

    Column(Modifier.fillMaxSize()) {
        Surface(color = LbColors.SurfaceRaised, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PERSON_KEYS.forEach { k ->
                        FilterChip(
                            selected = personKey == k,
                            onClick = { personKey = k },
                            label = { Text(personLabel(k), fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }
                Row(
                    Modifier.padding(top = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf(
                        PronounFilterStore.TENSE_PRESENT to "Present",
                        PronounFilterStore.TENSE_PAST to "Past"
                    ).forEach { (t, label) ->
                        FilterChip(
                            selected = tense == t,
                            onClick = { tense = t },
                            label = { Text(label, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = LbColors.Label,
                                selectedLabelColor = Color.White
                            )
                        )
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
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            grouped.forEach { (cls, verbs) ->
                item(key = "h-${cls.name}-$personKey-$tense") {
                    Text(
                        cls.label,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = LbColors.Label,
                        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
                    )
                }
                items(verbs, key = { "p-${it.lemma}-$personKey-$tense" }) { v ->
                    val form = if (tense == PronounFilterStore.TENSE_PAST) {
                        v.past_forms?.get(personKey).orEmpty()
                    } else {
                        v.forms[personKey].orEmpty()
                    }
                    Card(
                        onClick = { playConjugation(app, personKey, form) },
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.width(160.dp)) {
                                Text(v.lemma, fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = LbColors.Primary)
                                Text(v.en, fontSize = 10.sp, color = LbColors.TextMuted)
                            }
                            val spoken = if (form.isEmpty()) "—"
                            else "${audioPronoun(personKey)} $form".trim()
                            Text(spoken, fontSize = 18.sp, fontWeight = FontWeight.Medium,
                                color = if (form.isEmpty()) LbColors.TextMuted.copy(alpha = 0.5f) else LbColors.TextPrimary,
                                modifier = Modifier.weight(1f))
                            if (form.isNotEmpty()) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "Play",
                                    tint = LbColors.Primary, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Plays any Polish text from the cache (no-op if file missing). */
private fun playPolish(app: LangbangApplication, text: String) {
    if (text.isEmpty()) return
    val f = app.audioCache.fileFor(
        AzureTtsClient.LOCALE_PL, AzureTtsClient.PL_PL_F, text
    )
    app.audioPlayer.play(f)
}

/** Plays "pronoun form" together (e.g. "ja jestem") so the spoken text matches the row. */
private fun playConjugation(app: LangbangApplication, personKey: String, form: String) {
    if (form.isEmpty()) return
    val combined = "${audioPronoun(personKey)} $form".trim()
    playPolish(app, combined)
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
