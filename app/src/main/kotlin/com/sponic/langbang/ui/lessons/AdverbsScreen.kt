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
import com.sponic.langbang.data.model.AdverbEntry
import com.sponic.langbang.data.model.SentenceExample
import com.sponic.langbang.domain.NowVoicing
import com.sponic.langbang.domain.NowVoicingBus
import com.sponic.langbang.domain.PlaybackController
import com.sponic.langbang.domain.PlaybackTransport
import com.sponic.langbang.domain.PrefetchProgress
import com.sponic.langbang.integrations.AzureTtsClient
import android.media.MediaMetadataRetriever
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

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
    var slowFirst: Boolean by mutableStateOf(true)

    private var playJob: Job? = null
    var playingIndex: Int by mutableStateOf(-1)
        private set
    val playing: Boolean get() = playJob?.isActive == true

    private var queue: List<SentenceExample> = emptyList()
    private var queueQuiz: Boolean = false
    private var currentItemIndex: Int = 0

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
        if (playJob?.isActive == true) { stop(); return }
        if (sentences.isEmpty()) return
        queue = sentences.shuffled()
        queueQuiz = quiz
        currentItemIndex = 0
        relaunchFromCurrent()
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
                        playAndAwaitAdv(app, s.en, AzureTtsClient.LOCALE_EN,
                            AzureTtsClient.EN_US_F)
                        pub("pause", plHidden = true)
                        delay(2000L)
                        pub("pause", plHidden = false)
                        delay(2000L)
                        pub("pl", plHidden = false)
                        playAndAwaitAdv(app, s.pl, AzureTtsClient.LOCALE_PL,
                            AzureTtsClient.PL_PL_F)
                    } else if (slowFirst) {
                        pub("en")
                        playAndAwaitAdv(app, s.en, AzureTtsClient.LOCALE_EN,
                            AzureTtsClient.EN_US_F)
                        pub("pl-slow")
                        playAndAwaitAdv(app, s.pl, AzureTtsClient.LOCALE_PL,
                            app.audioPrefs.slowPlVoice())
                        pub("en")
                        playAndAwaitAdv(app, s.en, AzureTtsClient.LOCALE_EN,
                            AzureTtsClient.EN_US_F)
                        pub("pl")
                        playAndAwaitAdv(app, s.pl, AzureTtsClient.LOCALE_PL,
                            AzureTtsClient.PL_PL_F)
                    } else {
                        pub("en")
                        playAndAwaitAdv(app, s.en, AzureTtsClient.LOCALE_EN,
                            AzureTtsClient.EN_US_F)
                        pub("pl")
                        playAndAwaitAdv(app, s.pl, AzureTtsClient.LOCALE_PL,
                            AzureTtsClient.PL_PL_F)
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

    // Left list flush to the top; right column = Now Voicing band, controls, examples.
    Row(modifier = Modifier.fillMaxSize()) {
        AdverbList(
            adverbs = lesson.adverbs,
            selected = state.selected,
            onSelect = { state.select(it) },
            modifier = Modifier
                .width(224.dp)
                .fillMaxHeight()
                .background(LbColors.Canvas)
        )
        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
            nowVoicing()
            AdvControlsBar(
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
                state.selected?.let { AdverbSentences(app, it, state) }
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
    state: AdverbsScreenState
) {
    Surface(color = LbColors.SurfaceRaised, modifier = Modifier.fillMaxWidth()) {
        Column {
            FlowRow(
                Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (state.selected != null) {
                    AdvExamplesControls(
                        state = state,
                        onGenerateAll = onGenerateAll,
                        generateAllBusy = generateAllBusy
                    )
                } else {
                    AdvGenerateAllButton(onGenerateAll, generateAllBusy)
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
private fun AdvGenerateAllButton(onClick: () -> Unit, busy: Boolean) {
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

// Emits its controls directly into the caller's FlowRow (no own layout wrapper) so chips
// and buttons wrap together as one band.
@Composable
private fun AdvExamplesControls(
    state: AdverbsScreenState,
    onGenerateAll: () -> Unit,
    generateAllBusy: Boolean
) {
    AdvGenerateAllButton(onGenerateAll, generateAllBusy)
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
                    contentDescription = "Quiz",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text("Quiz >", fontSize = 12.sp, color = Color.White)
            }
        }
    }
    if (state.busy) {
        CircularProgressIndicator(
            modifier = Modifier.size(18.dp), strokeWidth = 2.dp
        )
    } else {
        val isRegen = state.sentences.isNotEmpty()
        Button(
            onClick = { state.generate() },
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isRegen) LbColors.SurfaceTint
                else MaterialTheme.colorScheme.primary
            ),
            shape = RoundedCornerShape(16.dp),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
        ) {
            Icon(
                if (isRegen) Icons.Default.Refresh else Icons.Default.Add,
                contentDescription = if (isRegen) "Regenerate" else "Generate",
                tint = if (isRegen) LbColors.Label else Color.White,
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                if (isRegen) "Regen" else "Generate",
                fontSize = 12.sp,
                color = if (isRegen) LbColors.Label else Color.White
            )
        }
    }
}

// AdvCacheBadge moved to AppHeader (single source) — removed duplicate.

@Composable
private fun AdverbList(
    adverbs: List<AdverbEntry>,
    selected: AdverbEntry?,
    onSelect: (AdverbEntry) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(adverbs, key = { "adv-${it.lemma}" }) { a ->
            val isSel = a == selected
            Card(
                onClick = { onSelect(a) },
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
                        a.lemma,
                        color = if (isSel) Color.White else LbColors.Primary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        a.en,
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
private fun AdverbSentences(
    app: LangbangApplication,
    adv: AdverbEntry,
    state: AdverbsScreenState
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(adv.lemma, fontSize = 26.sp, fontWeight = FontWeight.Bold,
                color = LbColors.Primary)
            Spacer(Modifier.width(12.dp))
            Text(adv.en, fontSize = 14.sp, color = LbColors.TextSecondary,
                modifier = Modifier.padding(bottom = 4.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Examples",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = LbColors.Label
        )
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
                    onPlay = { playFormAdv(app, s.pl) }
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

// ── Shared helpers (renamed to avoid clash with AdjectivesScreen private fns) ────

private suspend fun playAndAwaitAdv(
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

private fun mp3DurationMsAdv(file: java.io.File): Long {
    if (!file.exists()) return 0L
    return try {
        val r = MediaMetadataRetriever()
        r.setDataSource(file.absolutePath)
        val v = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        r.release()
        v
    } catch (_: Throwable) { 0L }
}

private fun playFormAdv(app: LangbangApplication, form: String) {
    if (form.isEmpty()) return
    val f = app.audioCache.fileFor(
        AzureTtsClient.LOCALE_PL, AzureTtsClient.PL_PL_F, form
    )
    app.audioPlayer.play(f)
}
