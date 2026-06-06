package com.sponic.langbang.ui.lessons

import com.sponic.langbang.ui.theme.LbColors

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sponic.langbang.LangbangApplication
import com.sponic.langbang.data.PracticePrefsStore
import com.sponic.langbang.domain.PrefetchProgress
import com.sponic.langbang.data.model.NounEntry
import com.sponic.langbang.data.model.SentenceExample
import com.sponic.langbang.domain.NowVoicing
import com.sponic.langbang.domain.NowVoicingBus
import com.sponic.langbang.domain.PlaybackController
import com.sponic.langbang.domain.PlaybackTransport
import com.sponic.langbang.domain.ensureCachedAudio
import com.sponic.langbang.domain.sourceAudioVoice
import com.sponic.langbang.domain.targetAudioVoice
import com.sponic.langbang.domain.targetSlowVoice
import com.sponic.langbang.ui.common.CompactLessonListCard
import com.sponic.langbang.ui.common.StudyQueuePlayer
import com.sponic.langbang.ui.common.CompactLessonListDefaults
import com.sponic.langbang.ui.common.DelayedEnglishTranslation
import com.sponic.langbang.ui.common.GrammarVisuals
import com.sponic.langbang.ui.common.FilterGroup
import com.sponic.langbang.ui.common.LbButton
import com.sponic.langbang.ui.common.LbChip
import com.sponic.langbang.ui.common.SelectionNavButtons
import com.sponic.langbang.ui.common.SubtleCheckbox
import com.sponic.langbang.ui.common.VariablePolishText
import com.sponic.langbang.ui.common.WordListPlaybackHeader
import com.sponic.langbang.ui.common.WordPlayLimitControl
import com.sponic.langbang.ui.common.variableEndForPolishForm
import com.sponic.langbang.ui.common.variableStartForPolishForm
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Each case map is keyed "sg" / "pl"; we always render in this order.
private val NUMBER_KEYS = listOf("sg", "pl")
private fun numberLabel(k: String): String = when (k) {
    "sg" -> "singular"
    "pl" -> "plural"
    else -> k
}

private fun genderName(g: String): String = when (g.lowercase()) {
    "m" -> "masculine"
    "f" -> "feminine"
    "n" -> "neuter"
    else -> g
}

/**
 * The three cases shown for each noun, in display order. Each pair is (key, label).
 * Mirrors the Adjectives screen's nom/acc blocks but adds genitive — the third
 * workhorse case for beginners ('of' / negation / after most prepositions).
 */
private data class CaseBlock(val key: String, val title: String, val hint: String)

private val CASE_BLOCKS = listOf(
    CaseBlock("nom", "Nominative", "subject"),
    CaseBlock("acc", "Accusative", "direct object"),
    CaseBlock("gen", "Genitive", "of / negation")
)

private fun NounEntry.caseMap(key: String): Map<String, String> = when (key) {
    "nom" -> nom
    "acc" -> acc
    "gen" -> gen
    else -> emptyMap()
}

/**
 * Hoisted state for the Nouns screen — mirrors [AdjectivesScreenState] so the play /
 * regenerate controls live in the top bar.
 */
internal class NounsScreenState(
    private val app: LangbangApplication,
    private val scope: CoroutineScope
) {
    var selected: NounEntry? by mutableStateOf(null)
        private set
    var sentences: List<SentenceExample> by mutableStateOf(emptyList())
        private set
    var busy: Boolean by mutableStateOf(false)
        private set
    var error: String? by mutableStateOf(null)
        private set
    var selectedCaseKeys: Set<String> by mutableStateOf(CASE_BLOCKS.map { it.key }.toSet())
        private set
    var selectedNumberKeys: Set<String> by mutableStateOf(NUMBER_KEYS.toSet())
        private set
    var checkedLemmas: Set<String> by mutableStateOf(
        app.practicePrefs.checkedWordLemmas(PracticePrefsStore.CATEGORY_NOUNS)
    )
        private set
    var playLimitText: String by mutableStateOf(
        app.practicePrefs.wordPlayLimit(PracticePrefsStore.CATEGORY_NOUNS).toString()
    )
        private set
    var randomOrder: Boolean by mutableStateOf(
        app.practicePrefs.wordPlayRandom(PracticePrefsStore.CATEGORY_NOUNS)
    )
        private set
    private var checkedDefaultsLoaded = false
    val canPlayForms: Boolean
        get() = selectedCaseKeys.isNotEmpty() && selectedNumberKeys.isNotEmpty()

    private val player = StudyQueuePlayer(app, scope)
    val playingIndex: Int get() = player.playingIndex
    val playing: Boolean get() = player.hasQueue

    fun select(noun: NounEntry?) {
        if (selected?.lemma == noun?.lemma) return
        stop()
        selected = noun
        sentences = noun?.let { app.lessonRepo.nounSentencesFor(it.lemma) } ?: emptyList()
        error = null
    }

    fun generate() {
        if (busy) return
        val noun = selected ?: return
        busy = true; error = null
        scope.launch {
            app.gemini.generateNounSentences(noun)
                .onSuccess { list ->
                    app.lessonRepo.saveNounSentences(noun.lemma, list)
                    if (selected?.lemma == noun.lemma) sentences = list
                    app.prefetch.prefetchSentences(list)
                }
                .onFailure { error = it.message }
            busy = false
        }
    }

    fun stop() {
        player.stop()
    }

    fun ensureCheckedDefaults(allLemmas: List<String>) {
        if (checkedDefaultsLoaded) return
        checkedDefaultsLoaded = true
        if (!app.practicePrefs.hasCheckedWordLemmas(PracticePrefsStore.CATEGORY_NOUNS)) {
            checkedLemmas = allLemmas.toSet()
            app.practicePrefs.setCheckedWordLemmas(
                PracticePrefsStore.CATEGORY_NOUNS,
                checkedLemmas
            )
        } else {
            checkedLemmas = checkedLemmas.intersect(allLemmas.toSet())
        }
    }

    fun toggleChecked(lemma: String, checked: Boolean) {
        checkedLemmas = if (checked) checkedLemmas + lemma else checkedLemmas - lemma
        app.practicePrefs.setCheckedWordLemmas(PracticePrefsStore.CATEGORY_NOUNS, checkedLemmas)
    }

    fun setAllChecked(lemmas: List<String>, checked: Boolean) {
        checkedLemmas = if (checked) lemmas.toSet() else emptySet()
        app.practicePrefs.setCheckedWordLemmas(PracticePrefsStore.CATEGORY_NOUNS, checkedLemmas)
    }

    fun updatePlayLimitText(value: String) {
        val cleaned = value.filter { it.isDigit() }.take(2)
        playLimitText = cleaned
        cleaned.toIntOrNull()?.let {
            app.practicePrefs.setWordPlayLimit(PracticePrefsStore.CATEGORY_NOUNS, it)
        }
    }

    fun updateRandomOrder(enabled: Boolean) {
        randomOrder = enabled
        app.practicePrefs.setWordPlayRandom(PracticePrefsStore.CATEGORY_NOUNS, enabled)
    }

    private fun playLimit(): Int =
        playLimitText.toIntOrNull()
            ?.coerceIn(PracticePrefsStore.MIN_WORD_PLAY_LIMIT, PracticePrefsStore.MAX_WORD_PLAY_LIMIT)
            ?: 0

    fun playCount(nouns: List<NounEntry>): Int {
        val limit = playLimit()
        if (limit <= 0) return 0
        return nouns
            .filter { it.lemma in checkedLemmas }
            .sumOf { app.lessonRepo.nounSentencesFor(it.lemma).size.coerceAtMost(limit) }
    }

    fun playAll(nouns: List<NounEntry>, quiz: Boolean) {
        if (player.hasQueue) { stop(); return }
        val items = buildCheckedSentenceQueue(nouns, quiz)
        if (items.isEmpty()) return
        startQueue(items, quiz)
    }

    private fun buildCheckedSentenceQueue(
        nouns: List<NounEntry>,
        quiz: Boolean
    ): List<SentenceExample> {
        val limit = playLimit()
        if (limit <= 0) return emptyList()
        val items = nouns
            .filter { it.lemma in checkedLemmas }
            .flatMap { noun ->
                val pool = app.lessonRepo.nounSentencesFor(noun.lemma)
                if (randomOrder) pool.shuffled().take(limit) else pool.take(limit)
            }
        return if (randomOrder || quiz) items.shuffled() else items
    }

    /**
     * Recall drill across the selected noun's full paradigm (nom + acc + gen × sg/pl).
     * Each entry is a synthetic SentenceExample where pl = the bare inflected form and
     * en = the case+number label. Quiz semantics match the adjective recall quiz: speak
     * EN cue → hide PL → 2s → reveal → 2s → speak PL.
     */
    fun recallQuiz() {
        if (player.hasQueue) { stop(); return }
        val items = nounFormItems() ?: return
        startQueue(items.shuffled(), quiz = true)
    }

    fun playForms() {
        if (player.hasQueue) { stop(); return }
        val items = nounFormItems() ?: return
        startQueue(items, quiz = false)
    }

    fun setCaseEnabled(caseKey: String, enabled: Boolean) {
        selectedCaseKeys = if (enabled) selectedCaseKeys + caseKey else selectedCaseKeys - caseKey
    }

    fun setNumberEnabled(numberKey: String, enabled: Boolean) {
        selectedNumberKeys = if (enabled) {
            selectedNumberKeys + numberKey
        } else {
            selectedNumberKeys - numberKey
        }
    }

    private fun nounFormItems(): List<SentenceExample>? {
        val noun = selected ?: return null
        val items = buildList {
            CASE_BLOCKS.filter { it.key in selectedCaseKeys }.forEach { block ->
                val map = noun.caseMap(block.key)
                NUMBER_KEYS.filter { it in selectedNumberKeys }.forEach { num ->
                    val form = map[num].orEmpty()
                    if (form.isNotBlank()) {
                        add(makeRecallItem(noun.en, block.title, block.key, num, form, noun.gender, noun.lemma))
                    }
                }
            }
        }
        return items.takeIf { it.isNotEmpty() }
    }

    private fun makeRecallItem(
        nounEn: String,
        case: String,
        caseKey: String,
        number: String,
        form: String,
        gender: String,
        baseForm: String
    ): SentenceExample {
        val numLabel = numberLabel(number)
        return SentenceExample(
            pl = form,
            en = nounEn,
            literal = null,
            words = listOf(
                com.sponic.langbang.data.model.TokenPair(
                    pl = form,
                    en = nounEn,
                    gender = gender,
                    caseKey = caseKey,
                    caseLabel = case,
                    numberLabel = numLabel,
                    variableStart = variableStartForPolishForm(baseForm, form),
                    variableEnd = variableEndForPolishForm(baseForm, form),
                    variableKind = "case"
                )
            )
        )
    }

    private fun startQueue(items: List<SentenceExample>, quiz: Boolean) {
        val slowFirst = app.practicePrefs.slowFirst()
        val slowPlVoice = app.targetSlowVoice()
        player.start(
            total = items.size,
            publishParked = { i ->
                val s = items[i]
                NowVoicingBus.publish(
                    NowVoicing(
                        en = s.en, pl = s.pl, literal = s.literal, lang = "pause",
                        position = "${i + 1}/${items.size}", words = s.words,
                        plHidden = quiz, quizMode = quiz
                    )
                )
            },
            prefetchItem = { i ->
                val s = items[i]
                app.ensureCachedAudio(s.en, app.sourceAudioVoice().locale, app.sourceAudioVoice().voice)
                app.ensureCachedAudio(s.pl, app.targetAudioVoice().locale, app.targetAudioVoice().voice)
                if (slowFirst && !quiz) app.ensureCachedAudio(s.pl, app.targetAudioVoice().locale, slowPlVoice)
            },
        ) { i ->
            val s = items[i]
            val pos = "${i + 1}/${items.size}"
            fun pub(lang: String, plHidden: Boolean = false) {
                NowVoicingBus.publish(
                    NowVoicing(
                        en = s.en, pl = s.pl, literal = s.literal,
                        lang = lang, position = pos, words = s.words,
                        plHidden = plHidden, quizMode = quiz
                    )
                )
            }
            if (quiz) {
                pub("en", plHidden = true)
                say(s.en, app.sourceAudioVoice().locale, app.sourceAudioVoice().voice)
                pub("pause", plHidden = true)
                reveal(2000L)
                pub("pause", plHidden = false)
                reveal(2000L)
                pub("pl", plHidden = false)
                say(s.pl, app.targetAudioVoice().locale, app.targetAudioVoice().voice)
            } else if (slowFirst) {
                pub("en")
                say(s.en, app.sourceAudioVoice().locale, app.sourceAudioVoice().voice)
                pub("pl-slow")
                say(s.pl, app.targetAudioVoice().locale, slowPlVoice)
                pub("pl")
                say(s.pl, app.targetAudioVoice().locale, app.targetAudioVoice().voice)
            } else {
                pub("en")
                say(s.en, app.sourceAudioVoice().locale, app.sourceAudioVoice().voice)
                pub("pl")
                say(s.pl, app.targetAudioVoice().locale, app.targetAudioVoice().voice)
            }
            if (!quiz && i < items.size - 1) reveal(500L)
        }
    }
}

@Composable
private fun rememberNounsScreenState(app: LangbangApplication): NounsScreenState {
    val scope = rememberCoroutineScope()
    return remember { NounsScreenState(app, scope) }
}

@Composable
fun NounsScreen(
    app: LangbangApplication,
    prefetch: PrefetchProgress,
    nowVoicing: @Composable () -> Unit = {}
) {
    val lesson = remember { app.lessonRepo.lesson6() }
    val state = rememberNounsScreenState(app)
    val activeNowVoicing by NowVoicingBus.state.collectAsState()
    state.ensureCheckedDefaults(lesson.nouns.map { it.lemma })

    if (state.selected == null ||
        lesson.nouns.none { it.lemma == state.selected?.lemma }
    ) {
        state.select(lesson.nouns.firstOrNull())
    }

    LaunchedEffect(activeNowVoicing?.pl, activeNowVoicing?.words, lesson.nouns) {
        nowVoicingNoun(activeNowVoicing, lesson.nouns)?.let { state.select(it) }
    }

    // Noun list flush to the top-left (the controls used to sit in a full-width band
    // above it). Right column = Now Voicing band, controls, then the paradigm.
    Row(modifier = Modifier.fillMaxSize()) {
        NounList(
            nouns = lesson.nouns,
            selected = state.selected,
            onSelect = { state.select(it) },
            checkedLemmas = state.checkedLemmas,
            onToggleNoun = { lemma, checked -> state.toggleChecked(lemma, checked) },
            onToggleAll = { checked ->
                state.setAllChecked(lesson.nouns.map { it.lemma }, checked)
            },
            randomOrder = state.randomOrder,
            onRandomOrderChange = { state.updateRandomOrder(it) },
            enabled = !state.playing,
            modifier = Modifier
                .width(269.dp)
                .fillMaxHeight()
                .background(LbColors.Canvas)
        )
        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
            nowVoicing()
            NounControlsBar(
                state = state,
                nouns = lesson.nouns
            )
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                state.selected?.let {
                    NounParadigm(
                        app = app,
                        noun = it,
                        state = state,
                        nouns = lesson.nouns
                    )
                }
            }
        }
    }
}

@Composable
private fun NounControlsBar(
    state: NounsScreenState,
    nouns: List<NounEntry>
) {
    Surface(color = LbColors.SurfaceRaised, modifier = Modifier.fillMaxWidth()) {
        Column {
            // ExamplesControls supplies its own compact layout, so just pad the wrapper.
            Box(Modifier.padding(horizontal = 12.dp, vertical = 3.dp)) {
                if (state.selected != null) {
                    ExamplesControls(state = state, nouns = nouns)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExamplesControls(state: NounsScreenState, nouns: List<NounEntry>) {
    var filtersExpanded by remember { mutableStateOf(false) }
    val formCount = state.selectedCaseKeys.size * state.selectedNumberKeys.size
    val playCount = state.playCount(nouns)

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LbButton.Ghost(
                label = if (filtersExpanded) "Hide filters" else "Filters",
                onClick = { filtersExpanded = !filtersExpanded }
            )
            if (!state.playing) {
                LbButton.Ghost(
                    label = "Recall quiz",
                    onClick = { state.recallQuiz() }
                )
                if (state.sentences.isNotEmpty()) {
                    LbButton.Ghost(
                        label = "Sent. quiz",
                        icon = Icons.Default.PlayArrow,
                        enabled = playCount > 0,
                        onClick = { state.playAll(nouns, quiz = true) }
                    )
                }
            }
            if (state.busy || state.sentences.isEmpty()) {
                LbButton.Ghost(
                    label = if (state.busy) "Generating" else "Generate examples",
                    icon = Icons.Default.Add,
                    enabled = !state.busy && !state.playing,
                    onClick = { state.generate() }
                )
            }
            Spacer(Modifier.weight(1f))
            if (state.playing) {
                LbButton.Stop("Stop", onClick = { state.stop() }, icon = Icons.Default.Stop)
            } else {
                LbButton.Ghost(
                    label = "Forms",
                    count = formCount,
                    enabled = state.canPlayForms,
                    onClick = { state.playForms() }
                )
                LbButton.Audio(
                    label = "Play",
                    count = playCount,
                    enabled = playCount > 0,
                    onClick = { state.playAll(nouns, quiz = false) }
                )
                WordPlayLimitControl(
                    limitText = state.playLimitText,
                    onLimitTextChange = { state.updatePlayLimitText(it) },
                    leadingLabel = "with",
                    trailingLabel = "vars"
                )
            }
        }

        if (filtersExpanded) {
            Surface(
                color = LbColors.SurfaceTint,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, LbColors.Line),
                modifier = Modifier.fillMaxWidth()
            ) {
                FlowRow(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    FilterGroup(label = "Case") {
                        CASE_BLOCKS.forEach { block ->
                            LbChip(
                                label = block.title.take(3),
                                selected = block.key in state.selectedCaseKeys,
                                enabled = !state.playing,
                                onClick = {
                                    state.setCaseEnabled(
                                        block.key,
                                        block.key !in state.selectedCaseKeys
                                    )
                                }
                            )
                        }
                    }
                    FilterGroup(label = "Number") {
                        LbChip(
                            label = "Sg",
                            selected = "sg" in state.selectedNumberKeys,
                            enabled = !state.playing,
                            onClick = {
                                state.setNumberEnabled(
                                    "sg",
                                    "sg" !in state.selectedNumberKeys
                                )
                            }
                        )
                        LbChip(
                            label = "Pl",
                            selected = "pl" in state.selectedNumberKeys,
                            enabled = !state.playing,
                            onClick = {
                                state.setNumberEnabled(
                                    "pl",
                                    "pl" !in state.selectedNumberKeys
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

// ── Pick a noun, see its nom + acc + gen (sg/pl) + Gemini examples ─────────────

@Composable
private fun NounList(
    nouns: List<NounEntry>,
    selected: NounEntry?,
    onSelect: (NounEntry) -> Unit,
    checkedLemmas: Set<String>,
    onToggleNoun: (String, Boolean) -> Unit,
    onToggleAll: (Boolean) -> Unit,
    randomOrder: Boolean,
    onRandomOrderChange: (Boolean) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    val allLemmas = nouns.map { it.lemma }
    LazyColumn(
        modifier = modifier,
        contentPadding = CompactLessonListDefaults.ContentPadding,
        verticalArrangement = Arrangement.spacedBy(CompactLessonListDefaults.ItemGap)
    ) {
        item(key = "all-nouns-master") {
            WordListPlaybackHeader(
                allChecked = allLemmas.isNotEmpty() && checkedLemmas.containsAll(allLemmas),
                onAllCheckedChange = onToggleAll,
                random = randomOrder,
                onRandomChange = onRandomOrderChange,
                enabled = enabled
            )
        }
        itemsIndexed(nouns, key = { _, n -> "n-${n.lemma}" }) { index, n ->
            val isSel = n == selected
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                SubtleCheckbox(
                    checked = n.lemma in checkedLemmas,
                    onCheckedChange = { onToggleNoun(n.lemma, it) },
                    enabled = enabled,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                CompactLessonListCard(
                    selected = isSel,
                    onClick = { onSelect(n) },
                    modifier = Modifier.weight(1f),
                    alternate = index % 2 == 1
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            n.lemma,
                            color = if (isSel) Color.White else LbColors.Primary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        // Gender tag (m/f/n) so the learner sees it at a glance.
                        Spacer(Modifier.width(6.dp))
                        Text(
                            n.gender.lowercase(),
                            color = if (isSel) Color.White.copy(alpha = 0.7f) else LbColors.Warning,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 3.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        DelayedEnglishTranslation(
                            text = n.en,
                            color = if (isSel) Color.White.copy(alpha = 0.85f)
                            else LbColors.TextSecondary,
                            fontSize = 12.sp,
                            modifier = Modifier
                                .weight(1f)
                                .padding(top = 2.dp)
                        )
                    }
                }
            }
        }
    }
}
@Composable
private fun NounParadigm(
    app: LangbangApplication,
    noun: NounEntry,
    state: NounsScreenState,
    nouns: List<NounEntry>
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 5.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom
        ) {
            Text(noun.lemma, fontSize = 26.sp, fontWeight = FontWeight.Bold,
                color = LbColors.Primary)
            Spacer(Modifier.width(12.dp))
            DelayedEnglishTranslation(
                text = "${noun.en}  ·  ${genderName(noun.gender)}",
                fontSize = 14.sp, color = LbColors.TextSecondary,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Spacer(Modifier.weight(1f))
            SelectionNavButtons(
                items = nouns,
                selected = noun,
                onSelect = { state.select(it) },
                previousContentDescription = "Previous noun",
                nextContentDescription = "Next noun"
            )
        }

        CASE_BLOCKS.filter { it.key in state.selectedCaseKeys }.forEach { block ->
            val map = noun.caseMap(block.key)
            CompactCaseRows(
                app = app,
                block = block,
                map = map,
                selectedNumberKeys = state.selectedNumberKeys,
                baseForm = noun.lemma,
                gender = noun.gender
            )
        }

        Spacer(Modifier.height(8.dp))
        SentencesSection(app, state)
    }
}

@Composable
private fun CompactCaseRows(
    app: LangbangApplication,
    block: CaseBlock,
    map: Map<String, String>,
    selectedNumberKeys: Set<String>,
    baseForm: String,
    gender: String
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = LbColors.Surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 6.dp, vertical = 2.dp)
                .height(IntrinsicSize.Min),
            verticalAlignment = Alignment.Top
        ) {
            Surface(
                color = LbColors.SurfaceTint,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .width(112.dp)
                    .fillMaxHeight()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(block.title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                        color = LbColors.Label)
                    Text(block.hint, fontSize = 10.sp, color = LbColors.TextMuted,
                        maxLines = 2)
                }
            }
            Spacer(Modifier.width(7.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                NUMBER_KEYS.filter { it in selectedNumberKeys }.forEach { num ->
                    val form = map[num].orEmpty()
                    val number = numberLabel(num)
                    FormRow(
                        label = number,
                        form = form,
                        baseForm = baseForm,
                        gender = gender
                    ) { playForm(app, form) }
                }
            }
        }
    }
}

@Composable
private fun FormRow(
    label: String,
    form: String,
    baseForm: String,
    gender: String,
    onPlay: () -> Unit
) {
    Card(
        onClick = onPlay,
        colors = CardDefaults.cardColors(containerColor = LbColors.SurfaceTint),
        modifier = Modifier
            .fillMaxWidth()
            .height(34.dp)
            .defaultMinSize(minHeight = 0.dp)
    ) {
        Row(
            Modifier.padding(horizontal = 7.dp, vertical = 1.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = "Play",
                tint = LbColors.Primary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                label,
                fontSize = 12.sp,
                color = LbColors.TextMuted,
                modifier = Modifier.width(72.dp)
            )
            VariablePolishText(
                text = form,
                fixedColor = LbColors.TextPrimary,
                variableColor = GrammarVisuals.Gender.color(gender),
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                baseText = baseForm,
                fallbackWholeWord = true,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun SentencesSection(app: LangbangApplication, state: NounsScreenState) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (state.sentences.isEmpty()) {
            Text(
                "No examples yet — tap Generate to make short sentences using this " +
                    "noun across all three cases (subject, object, and \"of\"/negation).",
                fontSize = 12.sp,
                color = LbColors.TextMuted
            )
        } else {
            state.sentences.forEachIndexed { i, s ->
                SentenceRow(
                    sentence = s,
                    highlighted = i == state.playingIndex,
                    onPlay = { playSentence(app, s) }
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

// ── Shared helpers ────────────────────────────────────────────────────────────

private fun playSentence(app: LangbangApplication, sentence: SentenceExample) {
    playAudio(app, sentence.pl)
}

private fun playForm(
    app: LangbangApplication,
    form: String
) {
    if (form.isEmpty()) return
    playAudio(app, form)
}

private fun playAudio(app: LangbangApplication, text: String) {
    if (text.isEmpty()) return
    val f = app.audioCache.fileFor(
        app.targetAudioVoice().locale, app.targetAudioVoice().voice, text
    )
    app.audioPlayer.play(f)
}
