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
import com.sponic.langbang.data.PracticePrefsStore
import com.sponic.langbang.data.model.AdverbEntry
import com.sponic.langbang.data.model.SentenceExample
import com.sponic.langbang.domain.NowVoicing
import com.sponic.langbang.domain.NowVoicingBus
import com.sponic.langbang.domain.PlaybackController
import com.sponic.langbang.domain.PlaybackTransport
import com.sponic.langbang.domain.PrefetchProgress
import com.sponic.langbang.domain.ensureCachedAudio
import com.sponic.langbang.domain.sourceAudioVoice
import com.sponic.langbang.domain.targetAudioVoice
import com.sponic.langbang.domain.targetSlowVoice
import com.sponic.langbang.ui.common.CompactLessonListCard
import com.sponic.langbang.ui.common.StudyQueuePlayer
import com.sponic.langbang.ui.common.CompactLessonListDefaults
import com.sponic.langbang.ui.common.DelayedEnglishTranslation
import com.sponic.langbang.ui.common.LbButton
import com.sponic.langbang.ui.common.SelectionNavButtons
import com.sponic.langbang.ui.common.SubtleCheckbox
import com.sponic.langbang.ui.common.WordListPlaybackHeader
import com.sponic.langbang.ui.common.WordPlayLimitControl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Per-adverb state hoisted to screen root so the play/regenerate controls live in the
 * top bar (mirrors VerbsTabState / AdjectivesScreenState).
 */
internal class AdverbsScreenState(
    private val app: LangbangApplication,
    private val scope: CoroutineScope
) {
    var selected: AdverbEntry? by mutableStateOf(null)
        private set
    var sentences: List<SentenceExample> by mutableStateOf(emptyList())
        private set
    var busy: Boolean by mutableStateOf(false)
        private set
    var error: String? by mutableStateOf(null)
        private set
    var checkedLemmas: Set<String> by mutableStateOf(
        app.practicePrefs.checkedWordLemmas(PracticePrefsStore.CATEGORY_ADVERBS)
    )
        private set
    var playLimitText: String by mutableStateOf(
        app.practicePrefs.wordPlayLimit(PracticePrefsStore.CATEGORY_ADVERBS).toString()
    )
        private set
    var randomOrder: Boolean by mutableStateOf(
        app.practicePrefs.wordPlayRandom(PracticePrefsStore.CATEGORY_ADVERBS)
    )
        private set
    private var checkedDefaultsLoaded = false

    private val player = StudyQueuePlayer(app, scope)
    val playingIndex: Int get() = player.playingIndex
    val playing: Boolean get() = player.hasQueue

    fun select(adv: AdverbEntry?) {
        if (selected?.lemma == adv?.lemma) return
        stop()
        selected = adv
        sentences = adv?.let { app.lessonRepo.adverbSentencesFor(it.lemma) } ?: emptyList()
        error = null
    }

    fun generate() {
        if (busy) return
        val adv = selected ?: return
        busy = true; error = null
        scope.launch {
            app.gemini.generateAdverbSentences(adv)
                .onSuccess { list ->
                    app.lessonRepo.saveAdverbSentences(adv.lemma, list)
                    if (selected?.lemma == adv.lemma) sentences = list
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
        if (!app.practicePrefs.hasCheckedWordLemmas(PracticePrefsStore.CATEGORY_ADVERBS)) {
            checkedLemmas = allLemmas.toSet()
            app.practicePrefs.setCheckedWordLemmas(
                PracticePrefsStore.CATEGORY_ADVERBS,
                checkedLemmas
            )
        } else {
            checkedLemmas = checkedLemmas.intersect(allLemmas.toSet())
        }
    }

    fun toggleChecked(lemma: String, checked: Boolean) {
        checkedLemmas = if (checked) checkedLemmas + lemma else checkedLemmas - lemma
        app.practicePrefs.setCheckedWordLemmas(PracticePrefsStore.CATEGORY_ADVERBS, checkedLemmas)
    }

    fun setAllChecked(lemmas: List<String>, checked: Boolean) {
        checkedLemmas = if (checked) lemmas.toSet() else emptySet()
        app.practicePrefs.setCheckedWordLemmas(PracticePrefsStore.CATEGORY_ADVERBS, checkedLemmas)
    }

    fun updatePlayLimitText(value: String) {
        val cleaned = value.filter { it.isDigit() }.take(2)
        playLimitText = cleaned
        cleaned.toIntOrNull()?.let {
            app.practicePrefs.setWordPlayLimit(PracticePrefsStore.CATEGORY_ADVERBS, it)
        }
    }

    fun updateRandomOrder(enabled: Boolean) {
        randomOrder = enabled
        app.practicePrefs.setWordPlayRandom(PracticePrefsStore.CATEGORY_ADVERBS, enabled)
    }

    private fun playLimit(): Int =
        playLimitText.toIntOrNull()
            ?.coerceIn(PracticePrefsStore.MIN_WORD_PLAY_LIMIT, PracticePrefsStore.MAX_WORD_PLAY_LIMIT)
            ?: 0

    fun playCount(adverbs: List<AdverbEntry>): Int {
        val limit = playLimit()
        if (limit <= 0) return 0
        return adverbs
            .filter { it.lemma in checkedLemmas }
            .sumOf { app.lessonRepo.adverbSentencesFor(it.lemma).size.coerceAtMost(limit) }
    }

    fun playAll(adverbs: List<AdverbEntry>, quiz: Boolean) {
        if (player.hasQueue) { stop(); return }
        val items = buildCheckedSentenceQueue(adverbs, quiz)
        if (items.isEmpty()) return
        startQueue(items, quiz)
    }

    private fun buildCheckedSentenceQueue(
        adverbs: List<AdverbEntry>,
        quiz: Boolean
    ): List<SentenceExample> {
        val limit = playLimit()
        if (limit <= 0) return emptyList()
        val items = adverbs
            .filter { it.lemma in checkedLemmas }
            .flatMap { adv ->
                val pool = app.lessonRepo.adverbSentencesFor(adv.lemma)
                if (randomOrder) pool.shuffled().take(limit) else pool.take(limit)
            }
        return if (randomOrder || quiz) items.shuffled() else items
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
                pub("en")
                say(s.en, app.sourceAudioVoice().locale, app.sourceAudioVoice().voice)
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
private fun rememberAdverbsScreenState(app: LangbangApplication): AdverbsScreenState {
    val scope = rememberCoroutineScope()
    return remember { AdverbsScreenState(app, scope) }
}

@Composable
fun AdverbsScreen(
    app: LangbangApplication,
    prefetch: PrefetchProgress,
    nowVoicing: @Composable () -> Unit = {}
) {
    val lesson = remember { app.lessonRepo.lesson4() }
    val scope = rememberCoroutineScope()
    val state = rememberAdverbsScreenState(app)
    var generateAllBusy by remember { mutableStateOf(false) }
    var generateAllProgress by remember { mutableStateOf<String?>(null) }
    var generateAllError by remember { mutableStateOf<String?>(null) }

    if (state.selected == null ||
        lesson.adverbs.none { it.lemma == state.selected?.lemma }
    ) {
        state.select(lesson.adverbs.firstOrNull())
    }

    val generateAll: () -> Unit = {
        if (!generateAllBusy) {
            generateAllBusy = true
            generateAllError = null
            scope.launch {
                val errors = mutableListOf<String>()
                try {
                    lesson.adverbs.forEachIndexed { i, a ->
                        generateAllProgress =
                            "Gemini ${i + 1}/${lesson.adverbs.size} · ${a.lemma}"
                        val existing = app.lessonRepo.adverbSentencesFor(a.lemma)
                        if (existing.isNotEmpty()) return@forEachIndexed
                        app.gemini.generateAdverbSentences(a)
                            .onSuccess { app.lessonRepo.saveAdverbSentences(a.lemma, it) }
                            .onFailure { errors += "${a.lemma}: ${it.message}" }
                    }
                    generateAllProgress = "Kicking audio prefetch…"
                    kickPrefetchWorker(app)
                } finally {
                    if (errors.isNotEmpty()) {
                        generateAllError =
                            "${errors.size} adverb(s) failed: ${errors.first()}"
                    }
                    generateAllProgress = null
                    generateAllBusy = false
                }
            }
        }
    }
    state.ensureCheckedDefaults(lesson.adverbs.map { it.lemma })

    // Left list flush to the top; right column = Now Voicing band, controls, examples.
    Row(modifier = Modifier.fillMaxSize()) {
        AdverbList(
            adverbs = lesson.adverbs,
            selected = state.selected,
            onSelect = { state.select(it) },
            checkedLemmas = state.checkedLemmas,
            onToggleAdverb = { lemma, checked -> state.toggleChecked(lemma, checked) },
            onToggleAll = { checked ->
                state.setAllChecked(lesson.adverbs.map { it.lemma }, checked)
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
            AdvControlsBar(
                onGenerateAll = generateAll,
                generateAllBusy = generateAllBusy,
                generateAllProgress = generateAllProgress,
                state = state,
                adverbs = lesson.adverbs
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
                    AdverbSentences(
                        app = app,
                        adv = it,
                        state = state,
                        adverbs = lesson.adverbs
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AdvControlsBar(
    onGenerateAll: () -> Unit,
    generateAllBusy: Boolean,
    generateAllProgress: String?,
    state: AdverbsScreenState,
    adverbs: List<AdverbEntry>
) {
    Surface(color = LbColors.SurfaceRaised, modifier = Modifier.fillMaxWidth()) {
        Column {
            FlowRow(
                Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (state.selected != null) {
                    AdvExamplesControls(state = state, adverbs = adverbs)
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
private fun AdvExamplesControls(
    state: AdverbsScreenState,
    adverbs: List<AdverbEntry>
) {
    val playCount = state.playCount(adverbs)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (playCount > 0) {
                if (state.playing) {
                    LbButton.Stop("Stop", onClick = { state.stop() }, icon = Icons.Default.Stop)
                } else {
                    LbButton.Audio("Play", onClick = { state.playAll(adverbs, quiz = false) }, count = playCount)
                    WordPlayLimitControl(
                        limitText = state.playLimitText,
                        onLimitTextChange = { state.updatePlayLimitText(it) },
                        leadingLabel = "groups of",
                        trailingLabel = null
                    )
                }
            }
            if (state.sentences.isNotEmpty() && !state.playing) {
                LbButton.Ghost("Sent. quiz", onClick = { state.playAll(adverbs, quiz = true) }, icon = Icons.Default.PlayArrow)
            }
            if (state.busy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp), strokeWidth = 2.dp
                )
            } else {
                val isRegen = state.sentences.isNotEmpty()
                LbButton.Ghost(
                    label = if (isRegen) "Refresh examples" else "Generate examples",
                    icon = if (isRegen) Icons.Default.Refresh else Icons.Default.Add,
                    onClick = { state.generate() }
                )
            }
        }
    }
}

// AdvCacheBadge moved to AppHeader (single source) — removed duplicate.

@Composable
private fun AdverbList(
    adverbs: List<AdverbEntry>,
    selected: AdverbEntry?,
    onSelect: (AdverbEntry) -> Unit,
    checkedLemmas: Set<String>,
    onToggleAdverb: (String, Boolean) -> Unit,
    onToggleAll: (Boolean) -> Unit,
    randomOrder: Boolean,
    onRandomOrderChange: (Boolean) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    val allLemmas = adverbs.map { it.lemma }
    LazyColumn(
        modifier = modifier,
        contentPadding = CompactLessonListDefaults.ContentPadding,
        verticalArrangement = Arrangement.spacedBy(CompactLessonListDefaults.ItemGap)
    ) {
        item(key = "all-adverbs-master") {
            WordListPlaybackHeader(
                allChecked = allLemmas.isNotEmpty() && checkedLemmas.containsAll(allLemmas),
                onAllCheckedChange = onToggleAll,
                random = randomOrder,
                onRandomChange = onRandomOrderChange,
                enabled = enabled
            )
        }
        itemsIndexed(adverbs, key = { _, a -> "adv-${a.lemma}" }) { index, a ->
            val isSel = a == selected
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                SubtleCheckbox(
                    checked = a.lemma in checkedLemmas,
                    onCheckedChange = { onToggleAdverb(a.lemma, it) },
                    enabled = enabled,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                CompactLessonListCard(
                    selected = isSel,
                    onClick = { onSelect(a) },
                    modifier = Modifier.weight(1f),
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
}

@Composable
private fun AdverbSentences(
    app: LangbangApplication,
    adv: AdverbEntry,
    state: AdverbsScreenState,
    adverbs: List<AdverbEntry>
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
            Text(adv.lemma, fontSize = 26.sp, fontWeight = FontWeight.Bold,
                color = LbColors.Primary)
            Spacer(Modifier.width(12.dp))
            DelayedEnglishTranslation(text = adv.en, fontSize = 14.sp, color = LbColors.TextSecondary,
                modifier = Modifier.padding(bottom = 4.dp))
            Spacer(Modifier.weight(1f))
            SelectionNavButtons(
                items = adverbs,
                selected = adv,
                onSelect = { state.select(it) },
                previousContentDescription = "Previous adverb",
                nextContentDescription = "Next adverb"
            )
        }
        if (state.sentences.isEmpty()) {
            Text(
                "No examples yet — tap Generate above to make 20 short sentences using " +
                    "this adverb with common verbs and beginner vocabulary.",
                fontSize = 12.sp,
                color = LbColors.TextMuted
            )
        } else {
            state.sentences.forEachIndexed { i, s ->
                AdvSentenceRow(
                    sentence = s,
                    highlighted = i == state.playingIndex,
                    onPlay = { playSentenceAdv(app, s) }
                )
            }
        }
        state.error?.let {
            Text("Error: $it", color = Color.Red, fontSize = 11.sp)
        }
    }
}

@Composable
private fun AdvSentenceRow(
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

// ── Shared helpers (renamed to avoid clash with AdjectivesScreen private fns) ────

private fun playFormAdv(app: LangbangApplication, form: String) {
    if (form.isEmpty()) return
    val f = app.audioCache.fileFor(
        app.targetAudioVoice().locale, app.targetAudioVoice().voice, form
    )
    app.audioPlayer.play(f)
}

private fun playSentenceAdv(app: LangbangApplication, sentence: SentenceExample) {
    playFormAdv(app, sentence.pl)
}
