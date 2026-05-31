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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sponic.langbang.LangbangApplication
import com.sponic.langbang.domain.PrefetchProgress
import com.sponic.langbang.data.model.NounEntry
import com.sponic.langbang.data.model.NounLesson
import com.sponic.langbang.data.model.SentenceExample
import com.sponic.langbang.domain.NowVoicing
import com.sponic.langbang.domain.NowVoicingBus
import com.sponic.langbang.domain.PlaybackController
import com.sponic.langbang.domain.PlaybackTransport
import com.sponic.langbang.integrations.AzureTtsClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

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
    CaseBlock("nom", "Nominative", "the subject — \"the dog is here\" / \"to jest pies\""),
    CaseBlock(
        "acc", "Accusative",
        "direct object — \"I see the dog\". Masculine animate takes -a (widzę psa); " +
            "inanimate masculine = nominative (widzę dom)."
    ),
    CaseBlock(
        "gen", "Genitive",
        "\"of\" / after nie ma + most prepositions (do, od, z, dla, bez) — " +
            "\"I don't have a dog\" / \"nie mam psa\""
    )
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
    var slowFirst: Boolean by mutableStateOf(true)

    private var playJob: Job? = null
    var playingIndex: Int by mutableStateOf(-1)
        private set
    val playing: Boolean get() = playJob?.isActive == true

    private var queue: List<SentenceExample> = emptyList()
    private var queueQuiz: Boolean = false
    private var currentItemIndex: Int = 0

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
        playJob?.cancel()
        playJob = null
        playingIndex = -1
        queue = emptyList()
        currentItemIndex = 0
        app.audioPlayer.stop()
        NowVoicingBus.clear()
        PlaybackController.unregister()
    }

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

    fun playAll(quiz: Boolean) {
        if (playJob?.isActive == true) {
            stop()
            return
        }
        if (sentences.isEmpty()) return
        queue = sentences.shuffled()
        queueQuiz = quiz
        currentItemIndex = 0
        relaunchFromCurrent()
    }

    /**
     * Recall drill across the selected noun's full paradigm (nom + acc + gen × sg/pl).
     * Each entry is a synthetic SentenceExample where pl = the bare inflected form and
     * en = the case+number label. Quiz semantics match the adjective recall quiz: speak
     * EN cue → hide PL → 2s → reveal → 2s → speak PL.
     */
    fun recallQuiz() {
        if (playJob?.isActive == true) { stop(); return }
        val noun = selected ?: return
        val items = buildList {
            CASE_BLOCKS.forEach { block ->
                val map = noun.caseMap(block.key)
                NUMBER_KEYS.forEach { num ->
                    val form = map[num].orEmpty()
                    if (form.isNotBlank()) {
                        add(makeRecallItem(noun.en, block.title, num, form))
                    }
                }
            }
        }
        if (items.isEmpty()) return
        queue = items.shuffled()
        queueQuiz = true
        currentItemIndex = 0
        relaunchFromCurrent()
    }

    private fun makeRecallItem(
        nounEn: String, case: String, number: String, form: String
    ): SentenceExample {
        val numLabel = numberLabel(number)
        val cue = "$nounEn — $numLabel — $case"
        return SentenceExample(
            pl = form,
            en = cue,
            literal = null,
            words = listOf(
                com.sponic.langbang.data.model.TokenPair(form, "$nounEn ($number, $case)")
            )
        )
    }

    private fun relaunchFromCurrent() {
        PlaybackController.register(
            PlaybackTransport(
                stop = { stop() },
                rewind = { rewind() },
                restart = { restartQueue() }
            )
        )
        playJob = scope.launch {
            try {
                while (currentItemIndex < queue.size) {
                    val i = currentItemIndex
                    val s = queue[i]
                    playingIndex = i
                    val pos = "${i + 1}/${queue.size}"
                    fun pub(lang: String, plHidden: Boolean = false) {
                        NowVoicingBus.publish(
                            NowVoicing(
                                en = s.en, pl = s.pl, literal = s.literal,
                                lang = lang, position = pos, words = s.words,
                                plHidden = plHidden, quizMode = queueQuiz
                            )
                        )
                    }
                    if (queueQuiz) {
                        pub("en", plHidden = true)
                        playAndAwait(app, s.en, AzureTtsClient.LOCALE_EN, AzureTtsClient.EN_US_F)
                        pub("pause", plHidden = true)
                        delay(2000L)
                        pub("pause", plHidden = false)
                        delay(2000L)
                        pub("pl", plHidden = false)
                        playAndAwait(app, s.pl, AzureTtsClient.LOCALE_PL, AzureTtsClient.PL_PL_F)
                    } else if (slowFirst) {
                        pub("en")
                        playAndAwait(app, s.en, AzureTtsClient.LOCALE_EN, AzureTtsClient.EN_US_F)
                        pub("pl-slow")
                        playAndAwait(app, s.pl, AzureTtsClient.LOCALE_PL,
                            app.audioPrefs.slowPlVoice())
                        pub("en")
                        playAndAwait(app, s.en, AzureTtsClient.LOCALE_EN, AzureTtsClient.EN_US_F)
                        pub("pl")
                        playAndAwait(app, s.pl, AzureTtsClient.LOCALE_PL, AzureTtsClient.PL_PL_F)
                    } else {
                        pub("en")
                        playAndAwait(app, s.en, AzureTtsClient.LOCALE_EN, AzureTtsClient.EN_US_F)
                        pub("pl")
                        playAndAwait(app, s.pl, AzureTtsClient.LOCALE_PL, AzureTtsClient.PL_PL_F)
                    }
                    if (currentItemIndex == i) {
                        currentItemIndex = i + 1
                        if (!queueQuiz && currentItemIndex < queue.size) delay(1000)
                    }
                }
            } finally {
                playingIndex = -1
                playJob = null
                NowVoicingBus.clear()
                PlaybackController.unregister()
            }
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
    val scope = rememberCoroutineScope()
    val state = rememberNounsScreenState(app)
    var generateAllBusy by remember { mutableStateOf(false) }
    var generateAllProgress by remember { mutableStateOf<String?>(null) }
    var generateAllError by remember { mutableStateOf<String?>(null) }

    if (state.selected == null ||
        lesson.nouns.none { it.lemma == state.selected?.lemma }
    ) {
        state.select(lesson.nouns.firstOrNull())
    }

    val generateAll: () -> Unit = {
        if (!generateAllBusy) {
            generateAllBusy = true
            generateAllError = null
            scope.launch {
                val errors = mutableListOf<String>()
                try {
                    lesson.nouns.forEachIndexed { i, n ->
                        generateAllProgress =
                            "Gemini ${i + 1}/${lesson.nouns.size} · ${n.lemma}"
                        val existing = app.lessonRepo.nounSentencesFor(n.lemma)
                        if (existing.isNotEmpty()) return@forEachIndexed
                        app.gemini.generateNounSentences(n)
                            .onSuccess { app.lessonRepo.saveNounSentences(n.lemma, it) }
                            .onFailure { errors += "${n.lemma}: ${it.message}" }
                    }
                    generateAllProgress = "Kicking audio prefetch…"
                    kickPrefetchWorker(app)
                } finally {
                    if (errors.isNotEmpty()) {
                        generateAllError =
                            "${errors.size} noun(s) failed: ${errors.first()}"
                    }
                    generateAllProgress = null
                    generateAllBusy = false
                }
            }
        }
    }

    // Noun list flush to the top-left (the controls used to sit in a full-width band
    // above it). Right column = Now Voicing band, controls, then the paradigm.
    Row(modifier = Modifier.fillMaxSize()) {
        NounList(
            nouns = lesson.nouns,
            selected = state.selected,
            onSelect = { state.select(it) },
            modifier = Modifier
                .width(224.dp)
                .fillMaxHeight()
                .background(LbColors.Canvas)
        )
        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
            nowVoicing()
            NounControlsBar(
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
                state.selected?.let { NounParadigm(app, it, state) }
            }
        }
    }
}

@Composable
private fun NounControlsBar(
    onGenerateAll: () -> Unit,
    generateAllBusy: Boolean,
    generateAllProgress: String?,
    state: NounsScreenState
) {
    Surface(color = LbColors.SurfaceRaised, modifier = Modifier.fillMaxWidth()) {
        Column {
            // ExamplesControls supplies its own FlowRow, so just pad the wrapper here.
            Box(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                if (state.selected != null) {
                    ExamplesControls(
                        state = state,
                        onGenerateAll = onGenerateAll,
                        generateAllBusy = generateAllBusy
                    )
                } else {
                    GenerateAllButton(
                        onClick = onGenerateAll,
                        busy = generateAllBusy
                    )
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

@Composable
private fun GenerateAllButton(onClick: () -> Unit, busy: Boolean) {
    Button(
        onClick = onClick,
        enabled = !busy,
        colors = ButtonDefaults.buttonColors(containerColor = LbColors.Label),
        shape = RoundedCornerShape(16.dp),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
    ) {
        if (busy) {
            CircularProgressIndicator(
                modifier = Modifier.size(12.dp),
                strokeWidth = 1.5.dp,
                color = Color.White
            )
            Spacer(Modifier.width(4.dp))
        }
        Text("Generate all", fontSize = 11.sp, color = Color.White)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExamplesControls(
    state: NounsScreenState,
    onGenerateAll: () -> Unit,
    generateAllBusy: Boolean
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        GenerateAllButton(onClick = onGenerateAll, busy = generateAllBusy)
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
        if (state.sentences.isNotEmpty()) {
            Button(
                onClick = { state.playAll(quiz = false) },
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
                Button(
                    onClick = { state.playAll(quiz = true) },
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
                    Text("Sent. quiz", fontSize = 12.sp, color = Color.White)
                }
            }
        }
        if (!state.playing) {
            Button(
                onClick = { state.recallQuiz() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = LbColors.Accent
                ),
                shape = RoundedCornerShape(16.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp)
            ) {
                Text("Recall quiz", fontSize = 12.sp, color = Color.White)
            }
        }
        if (state.busy) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp), strokeWidth = 2.dp
            )
        } else {
            val isRegenerate = state.sentences.isNotEmpty()
            Button(
                onClick = { state.generate() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRegenerate) LbColors.SurfaceTint
                    else MaterialTheme.colorScheme.primary
                ),
                shape = RoundedCornerShape(16.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
            ) {
                Icon(
                    if (isRegenerate) Icons.Default.Refresh else Icons.Default.Add,
                    contentDescription = if (isRegenerate) "Regenerate" else "Generate",
                    tint = if (isRegenerate) LbColors.Label else Color.White,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    if (isRegenerate) "Regen" else "Generate",
                    fontSize = 12.sp,
                    color = if (isRegenerate) LbColors.Label else Color.White
                )
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
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(nouns, key = { "n-${it.lemma}" }) { n ->
            val isSel = n == selected
            Card(
                onClick = { onSelect(n) },
                colors = CardDefaults.cardColors(
                    containerColor = if (isSel) MaterialTheme.colorScheme.primary
                    else Color.White
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        n.lemma,
                        color = if (isSel) Color.White else LbColors.Primary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp
                    )
                    // Gender tag (m/f/n) so the learner sees it at a glance.
                    Spacer(Modifier.width(6.dp))
                    Text(
                        n.gender.lowercase(),
                        color = if (isSel) Color.White.copy(alpha = 0.7f) else LbColors.Warning,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        n.en,
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

@Composable
private fun NounParadigm(
    app: LangbangApplication,
    noun: NounEntry,
    state: NounsScreenState
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(noun.lemma, fontSize = 26.sp, fontWeight = FontWeight.Bold,
                color = LbColors.Primary)
            Spacer(Modifier.width(12.dp))
            Text(
                "${noun.en}  ·  ${genderName(noun.gender)}",
                fontSize = 14.sp, color = LbColors.TextSecondary,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }

        CASE_BLOCKS.forEach { block ->
            Spacer(Modifier.height(4.dp))
            com.sponic.langbang.ui.common.CaseHeader(block.title, block.hint)
            val map = noun.caseMap(block.key)
            NUMBER_KEYS.forEach { num ->
                val form = map[num].orEmpty()
                FormRow(label = numberLabel(num), form = form) { playForm(app, form) }
            }
        }

        Spacer(Modifier.height(8.dp))
        SentencesSection(app, state)
    }
}

@Composable
private fun FormRow(label: String, form: String, onPlay: () -> Unit) {
    Card(
        onClick = onPlay,
        colors = CardDefaults.cardColors(containerColor = LbColors.SurfaceTint),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                label,
                fontSize = 12.sp,
                color = LbColors.TextMuted,
                modifier = Modifier.width(110.dp)
            )
            Text(form, fontSize = 18.sp, fontWeight = FontWeight.Medium,
                color = LbColors.Primary, modifier = Modifier.weight(1f))
            Icon(Icons.Default.PlayArrow, contentDescription = "Play",
                tint = LbColors.Primary, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun SentencesSection(app: LangbangApplication, state: NounsScreenState) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            "Examples",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = LbColors.Label
        )

        if (state.sentences.isEmpty()) {
            Text(
                "No examples yet — tap Generate above to make short sentences using this " +
                    "noun across all three cases (subject, object, and \"of\"/negation).",
                fontSize = 12.sp,
                color = LbColors.TextMuted
            )
        } else {
            state.sentences.forEachIndexed { i, s ->
                SentenceRow(
                    sentence = s,
                    highlighted = i == state.playingIndex,
                    onPlay = { playForm(app, s.pl) }
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

// ── Shared helpers ────────────────────────────────────────────────────────────

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

private fun playForm(app: LangbangApplication, form: String) {
    if (form.isEmpty()) return
    val f = app.audioCache.fileFor(
        AzureTtsClient.LOCALE_PL, AzureTtsClient.PL_PL_F, form
    )
    app.audioPlayer.play(f)
}
