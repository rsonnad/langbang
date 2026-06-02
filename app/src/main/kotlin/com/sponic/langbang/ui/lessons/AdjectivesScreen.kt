package com.sponic.langbang.ui.lessons

import com.sponic.langbang.ui.theme.LbColors

import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sponic.langbang.LangbangApplication
import com.sponic.langbang.domain.PrefetchProgress
import com.sponic.langbang.data.model.AdjectiveEntry
import com.sponic.langbang.data.model.SentenceExample
import com.sponic.langbang.domain.NowVoicing
import com.sponic.langbang.domain.NowVoicingBus
import com.sponic.langbang.domain.PlaybackController
import com.sponic.langbang.domain.PlaybackTransport
import com.sponic.langbang.domain.ensureCachedAudio
import com.sponic.langbang.integrations.AzureTtsClient
import com.sponic.langbang.ui.common.CompactLessonListCard
import com.sponic.langbang.ui.common.StudyQueuePlayer
import com.sponic.langbang.ui.common.CompactLessonListDefaults
import com.sponic.langbang.ui.common.DelayedEnglishTranslation
import com.sponic.langbang.ui.common.GrammarVisuals
import com.sponic.langbang.ui.common.LbButton
import com.sponic.langbang.ui.common.SelectionNavButtons
import com.sponic.langbang.ui.common.VariablePolishText
import com.sponic.langbang.ui.common.variableEndForPolishForm
import com.sponic.langbang.ui.common.variableStartForPolishForm
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val GENDER_KEYS = listOf("m", "f", "n", "mp", "other")
private fun genderLabel(k: String): String = when (k) {
    "m" -> "m  (table, dog)"
    "f" -> "f  (house, lamp)"
    "n" -> "n  (window, child)"
    "mp" -> "men / mixed plural"
    "other" -> "other plural"
    else -> k
}

/**
 * Hoisted state for the Adjectives screen — mirrors VerbsTabState so the play / regenerate
 * controls can live in the top bar instead of buried beside the sentences list.
 */
internal class AdjectivesScreenState(
    private val app: LangbangApplication,
    private val scope: CoroutineScope
) {
    var selected: AdjectiveEntry? by mutableStateOf(null)
        private set
    var sentences: List<SentenceExample> by mutableStateOf(emptyList())
        private set
    var busy: Boolean by mutableStateOf(false)
        private set
    var error: String? by mutableStateOf(null)
        private set

    private val player = StudyQueuePlayer(app, scope)
    val playingIndex: Int get() = player.playingIndex
    val playing: Boolean get() = player.hasQueue

    fun select(adj: AdjectiveEntry?) {
        if (selected?.lemma == adj?.lemma) return
        stop()
        selected = adj
        sentences = adj?.let { app.lessonRepo.adjectiveSentencesFor(it.lemma) } ?: emptyList()
        error = null
    }

    fun generate() {
        if (busy) return
        val adj = selected ?: return
        busy = true; error = null
        scope.launch {
            app.gemini.generateAdjectiveSentences(adj)
                .onSuccess { list ->
                    app.lessonRepo.saveAdjectiveSentences(adj.lemma, list)
                    if (selected?.lemma == adj.lemma) sentences = list
                    app.prefetch.prefetchSentences(list)
                }
                .onFailure { error = it.message }
            busy = false
        }
    }

    fun stop() {
        player.stop()
    }

    fun playAll(quiz: Boolean) {
        if (player.hasQueue) { stop(); return }
        if (sentences.isEmpty()) return
        // Shuffle every run so quiz/play-all isn't identical order each time.
        startQueue(sentences.shuffled(), quiz)
    }

    /**
     * Recall drill across the selected adjective's full paradigm (nom + acc × 5 genders).
     * Each entry is a synthetic SentenceExample where pl = the bare adjective form and
     * en = the literal English gloss. Gender/case context lives on TokenPair metadata so
     * Now Voicing can render it as small secondary text instead of speaking it as English.
     * Quiz semantics: speak EN cue → hide PL → 2s → reveal → 2s → speak PL. The learner
     * has to remember the inflected form from the cue. Built items missed during reveal
     * can be re-listened via the standard rewind button.
     */
    fun recallQuiz() {
        if (player.hasQueue) { stop(); return }
        val adj = selected ?: return
        val items = buildList {
            adj.nom.forEach { (g, form) ->
                if (form.isNotBlank()) {
                    add(makeRecallItem(adj.en, "nominative", g, form, adj.lemma))
                }
            }
            adj.acc.forEach { (g, form) ->
                if (form.isNotBlank()) {
                    add(makeRecallItem(adj.en, "accusative", g, form, adj.lemma))
                }
            }
        }
        if (items.isEmpty()) return
        startQueue(items.shuffled(), quiz = true)
    }

    private fun makeRecallItem(
        adjEn: String, case: String, gender: String, form: String, baseForm: String
    ): SentenceExample {
        return SentenceExample(
            pl = form,
            en = adjEn,
            literal = adjEn,
            words = listOf(
                com.sponic.langbang.data.model.TokenPair(
                    pl = form,
                    en = adjEn,
                    gender = gender,
                    caseKey = case,
                    caseLabel = case,
                    variableStart = variableStartForPolishForm(baseForm, form),
                    variableEnd = variableEndForPolishForm(baseForm, form),
                    variableKind = "case"
                )
            )
        )
    }

    private fun startQueue(items: List<SentenceExample>, quiz: Boolean) {
        val slowFirst = app.practicePrefs.slowFirst()
        val slowPlVoice = app.audioPrefs.slowPlVoice()
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
                app.ensureCachedAudio(s.en, AzureTtsClient.LOCALE_EN, AzureTtsClient.EN_US_F)
                app.ensureCachedAudio(s.pl, AzureTtsClient.LOCALE_PL, AzureTtsClient.PL_PL_F)
                if (slowFirst && !quiz) app.ensureCachedAudio(s.pl, AzureTtsClient.LOCALE_PL, slowPlVoice)
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
                // Hide PL during the EN clip + recall window so the learner can't peek.
                // Reveal it before speaking so eye + brain align.
                pub("en", plHidden = true)
                say(s.en, AzureTtsClient.LOCALE_EN, AzureTtsClient.EN_US_F)
                pub("pause", plHidden = true)
                reveal(2000L)
                pub("pause", plHidden = false)
                reveal(2000L)
                pub("pl", plHidden = false)
                say(s.pl, AzureTtsClient.LOCALE_PL, AzureTtsClient.PL_PL_F)
            } else if (slowFirst) {
                pub("en")
                say(s.en, AzureTtsClient.LOCALE_EN, AzureTtsClient.EN_US_F)
                pub("pl-slow")
                say(s.pl, AzureTtsClient.LOCALE_PL, slowPlVoice)
                pub("en")
                say(s.en, AzureTtsClient.LOCALE_EN, AzureTtsClient.EN_US_F)
                pub("pl")
                say(s.pl, AzureTtsClient.LOCALE_PL, AzureTtsClient.PL_PL_F)
            } else {
                pub("en")
                say(s.en, AzureTtsClient.LOCALE_EN, AzureTtsClient.EN_US_F)
                pub("pl")
                say(s.pl, AzureTtsClient.LOCALE_PL, AzureTtsClient.PL_PL_F)
            }
            // Short beat between items (down from 1s) — pause-aware. Skipped after last.
            if (!quiz && i < items.size - 1) reveal(500L)
        }
    }
}

@Composable
private fun rememberAdjectivesScreenState(app: LangbangApplication): AdjectivesScreenState {
    val scope = rememberCoroutineScope()
    return remember { AdjectivesScreenState(app, scope) }
}

@Composable
fun AdjectivesScreen(
    app: LangbangApplication,
    prefetch: PrefetchProgress,
    nowVoicing: @Composable () -> Unit = {}
) {
    val lesson = remember { app.lessonRepo.lesson3() }
    val scope = rememberCoroutineScope()
    val state = rememberAdjectivesScreenState(app)
    var generateAllBusy by remember { mutableStateOf(false) }
    var generateAllProgress by remember { mutableStateOf<String?>(null) }
    var generateAllError by remember { mutableStateOf<String?>(null) }

    if (state.selected == null ||
        lesson.adjectives.none { it.lemma == state.selected?.lemma }
    ) {
        state.select(lesson.adjectives.firstOrNull())
    }

    val generateAll: () -> Unit = {
        if (!generateAllBusy) {
            generateAllBusy = true
            generateAllError = null
            scope.launch {
                val errors = mutableListOf<String>()
                try {
                    lesson.adjectives.forEachIndexed { i, a ->
                        generateAllProgress =
                            "Gemini ${i + 1}/${lesson.adjectives.size} · ${a.lemma}"
                        val existing = app.lessonRepo.adjectiveSentencesFor(a.lemma)
                        if (existing.isNotEmpty()) return@forEachIndexed
                        app.gemini.generateAdjectiveSentences(a)
                            .onSuccess { app.lessonRepo.saveAdjectiveSentences(a.lemma, it) }
                            .onFailure { errors += "${a.lemma}: ${it.message}" }
                    }
                    generateAllProgress = "Kicking audio prefetch…"
                    kickPrefetchWorker(app)
                } finally {
                    if (errors.isNotEmpty()) {
                        generateAllError =
                            "${errors.size} adjective(s) failed: ${errors.first()}"
                    }
                    generateAllProgress = null
                    generateAllBusy = false
                }
            }
        }
    }

    // Left list runs flush to the top of the screen (nothing above it). The right
    // column stacks: Now Voicing band, then the play/quiz controls, then the paradigm.
    Row(modifier = Modifier.fillMaxSize()) {
        AdjectiveList(
            adjectives = lesson.adjectives,
            selected = state.selected,
            onSelect = { state.select(it) },
            modifier = Modifier
                .width(269.dp)
                .fillMaxHeight()
                .background(LbColors.Canvas)
        )
        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
            nowVoicing()
            ControlsBar(
                onGenerateAll = generateAll,
                generateAllBusy = generateAllBusy,
                generateAllProgress = generateAllProgress,
                state = state
            )
            generateAllError?.let {
                Text(
                    "Generate-all error: $it",
                    fontSize = 11.sp, color = Color.Red,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                )
            }
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                state.selected?.let {
                    AdjectiveParadigm(
                        app = app,
                        adj = it,
                        state = state,
                        adjectives = lesson.adjectives
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ControlsBar(
    onGenerateAll: () -> Unit,
    generateAllBusy: Boolean,
    generateAllProgress: String?,
    state: AdjectivesScreenState
) {
    Surface(color = LbColors.SurfaceRaised, modifier = Modifier.fillMaxWidth()) {
        Column {
            FlowRow(
                Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (state.selected != null) {
                    ExamplesControls(state = state)
                }
            }
            generateAllProgress?.let {
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


// Emits its controls directly into the caller's FlowRow (no own layout wrapper) so chips
// and buttons wrap together as one band.
@Composable
private fun ExamplesControls(
    state: AdjectivesScreenState
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (state.sentences.isNotEmpty()) {
                if (state.playing) {
                    LbButton.Stop("Stop", onClick = { state.stop() }, icon = Icons.Default.Stop)
                } else {
                    LbButton.Audio("Play", onClick = { state.playAll(quiz = false) }, count = state.sentences.size)
                }
            }
            if (!state.playing) {
                LbButton.Ghost("Recall quiz", onClick = { state.recallQuiz() })
            }
            if (state.sentences.isNotEmpty() && !state.playing) {
                LbButton.Ghost("Sent. quiz", onClick = { state.playAll(quiz = true) }, icon = Icons.Default.PlayArrow)
            }
            if (state.busy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp), strokeWidth = 2.dp
                )
            } else {
                val isRegenerate = state.sentences.isNotEmpty()
                LbButton.Ghost(
                    label = if (isRegenerate) "Refresh examples" else "Generate examples",
                    icon = if (isRegenerate) Icons.Default.Refresh else Icons.Default.Add,
                    onClick = { state.generate() }
                )
            }
        }
    }
}

// CacheBadge moved to AppHeader (single source) — removed duplicate from this tab.

// ── Mode 1 — pick an adjective, see all 10 nom + acc forms + Gemini examples ───

@Composable
private fun AdjectiveList(
    adjectives: List<AdjectiveEntry>,
    selected: AdjectiveEntry?,
    onSelect: (AdjectiveEntry) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = CompactLessonListDefaults.ContentPadding,
        verticalArrangement = Arrangement.spacedBy(CompactLessonListDefaults.ItemGap)
    ) {
        itemsIndexed(adjectives, key = { _, a -> "a-${a.lemma}" }) { index, a ->
            val isSel = a == selected
            CompactLessonListCard(
                selected = isSel,
                onClick = { onSelect(a) },
                alternate = index % 2 == 1
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        a.lemma,
                        color = if (isSel) Color.White else LbColors.Primary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Spacer(Modifier.width(10.dp))
                    DelayedEnglishTranslation(
                        text = a.en,
                        color = if (isSel) Color.White.copy(alpha = 0.85f)
                        else LbColors.TextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 14.sp,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun AdjectiveParadigm(
    app: LangbangApplication,
    adj: AdjectiveEntry,
    state: AdjectivesScreenState,
    adjectives: List<AdjectiveEntry>
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
            verticalAlignment = Alignment.Bottom
        ) {
            Text(adj.lemma, fontSize = 26.sp, fontWeight = FontWeight.Bold,
                color = LbColors.Primary)
            Spacer(Modifier.width(12.dp))
            DelayedEnglishTranslation(text = adj.en, fontSize = 14.sp, color = LbColors.TextSecondary,
                modifier = Modifier.padding(bottom = 4.dp))
            Spacer(Modifier.weight(1f))
            SelectionNavButtons(
                items = adjectives,
                selected = adj,
                onSelect = { state.select(it) },
                previousContentDescription = "Previous adjective",
                nextContentDescription = "Next adjective"
            )
        }

        com.sponic.langbang.ui.common.CaseHeader(
            "Nominative", "the form when the adjective + noun is the subject"
        )
        GENDER_KEYS.forEach { k ->
            val form = adj.nom[k].orEmpty()
            FormRow(label = genderLabel(k), form = form, baseForm = adj.lemma, gender = k) { playForm(app, form) }
        }

        Spacer(Modifier.height(4.dp))
        com.sponic.langbang.ui.common.CaseHeader(
            "Accusative",
            "for direct objects — \"I see a big table\". m form is animate (-ego); " +
                "for inanimate m, accusative = nominative."
        )
        GENDER_KEYS.forEach { k ->
            val form = adj.acc[k].orEmpty()
            FormRow(label = genderLabel(k), form = form, baseForm = adj.lemma, gender = k) { playForm(app, form) }
        }

        Spacer(Modifier.height(8.dp))
        SentencesSection(app, state)
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
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = "Play",
                tint = LbColors.Primary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                label,
                fontSize = 12.sp,
                color = LbColors.TextMuted,
                modifier = Modifier.width(150.dp)
            )
            VariablePolishText(
                text = form,
                fixedColor = LbColors.TextPrimary,
                variableColor = GrammarVisuals.Gender.color(gender),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                baseText = baseForm,
                fallbackWholeWord = true,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun SentencesSection(app: LangbangApplication, state: AdjectivesScreenState) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (state.sentences.isEmpty()) {
            Text(
                "No examples yet — tap Generate above to make 20 short sentences combining " +
                    "this adjective with common verbs and nouns.",
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

private fun playForm(app: LangbangApplication, form: String) {
    if (form.isEmpty()) return
    val f = app.audioCache.fileFor(
        AzureTtsClient.LOCALE_PL, AzureTtsClient.PL_PL_F, form
    )
    app.audioPlayer.play(f)
}

private fun playSentence(app: LangbangApplication, sentence: SentenceExample) {
    playForm(app, sentence.pl)
}
