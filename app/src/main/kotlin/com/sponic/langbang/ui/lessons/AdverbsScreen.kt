package com.sponic.langbang.ui.lessons

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
import com.sponic.langbang.data.model.AdverbLesson
import com.sponic.langbang.data.model.SentenceExample
import com.sponic.langbang.domain.NowVoicing
import com.sponic.langbang.domain.NowVoicingBus
import com.sponic.langbang.domain.PrefetchProgress
import com.sponic.langbang.integrations.AzureTtsClient
import android.media.MediaMetadataRetriever
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

private enum class AdvMode(val label: String) {
    Single("Single adverb"),
    Add("+ Add adverb")
}

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
        app.audioPlayer.stop()
        NowVoicingBus.clear()
    }

    fun playAll(quiz: Boolean) {
        if (playJob?.isActive == true) { stop(); return }
        if (sentences.isEmpty()) return
        val list = sentences
        playJob = scope.launch {
            try {
                list.forEachIndexed { i, s ->
                    playingIndex = i
                    val pos = "${i + 1}/${list.size}"
                    fun pub(lang: String) {
                        NowVoicingBus.publish(
                            NowVoicing(s.en, s.pl, s.literal, lang, pos, s.words)
                        )
                    }
                    if (quiz) {
                        pub("en")
                        playAndAwaitAdv(app, s.en, AzureTtsClient.LOCALE_EN,
                            AzureTtsClient.EN_US_F)
                        pub("pause")
                        val plFile = app.audioCache.fileFor(
                            AzureTtsClient.LOCALE_PL, AzureTtsClient.PL_PL_F, s.pl
                        )
                        val pauseMs = mp3DurationMsAdv(plFile) + 2000L
                        delay(pauseMs)
                        pub("pl")
                        playAndAwaitAdv(app, s.pl, AzureTtsClient.LOCALE_PL,
                            AzureTtsClient.PL_PL_F)
                    } else if (slowFirst) {
                        pub("en")
                        playAndAwaitAdv(app, s.en, AzureTtsClient.LOCALE_EN,
                            AzureTtsClient.EN_US_F)
                        pub("pl-slow")
                        playAndAwaitAdv(app, s.pl, AzureTtsClient.LOCALE_PL,
                            AzureTtsClient.PL_PL_F_SLOW_V2)
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
                }
            } finally {
                playingIndex = -1
                playJob = null
                NowVoicingBus.clear()
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
fun AdverbsScreen(app: LangbangApplication, prefetch: PrefetchProgress) {
    var reloadKey by remember { mutableStateOf(0) }
    val lesson = remember(reloadKey) { app.lessonRepo.lesson4() }
    var mode by remember { mutableStateOf(AdvMode.Single) }
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

    Column(modifier = Modifier.fillMaxSize()) {
        AdvModeBar(
            mode = mode,
            onSelect = { mode = it },
            prefetch = prefetch,
            onGenerateAll = generateAll,
            generateAllBusy = generateAllBusy,
            generateAllProgress = generateAllProgress,
            state = state,
            showControls = mode == AdvMode.Single && state.selected != null
        )
        generateAllError?.let {
            Text(
                "Generate-all error: $it",
                fontSize = 11.sp, color = Color.Red,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
            )
        }
        Box(modifier = Modifier.fillMaxSize()) {
            when (mode) {
                AdvMode.Single -> SingleAdverbMode(app, lesson, state)
                AdvMode.Add -> AddAdverbMode(app) { reloadKey++; mode = AdvMode.Single }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AdvModeBar(
    mode: AdvMode,
    onSelect: (AdvMode) -> Unit,
    prefetch: PrefetchProgress,
    onGenerateAll: () -> Unit,
    generateAllBusy: Boolean,
    generateAllProgress: String?,
    state: AdverbsScreenState,
    showControls: Boolean
) {
    Surface(color = Color(0xFFF7F3EA), modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AdvMode.values().forEach { m ->
                    FilterChip(
                        selected = mode == m,
                        onClick = { onSelect(m) },
                        label = { Text(m.label, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = Color.White
                        )
                    )
                }
                Spacer(Modifier.weight(1f))
                AdvCacheBadge(prefetch)
                if (showControls) {
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
                    color = Color(0xFF7A5A1F),
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
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7A5A1F)),
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
private fun AdvExamplesControls(
    state: AdverbsScreenState,
    onGenerateAll: () -> Unit,
    generateAllBusy: Boolean
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
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
            Text("Slow first", fontSize = 11.sp, color = Color(0xFF555555))
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
                        containerColor = Color(0xFF7A5A1F)
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
                    containerColor = if (isRegen) Color(0xFFEFE8D8)
                    else MaterialTheme.colorScheme.primary
                ),
                shape = RoundedCornerShape(16.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
            ) {
                Icon(
                    if (isRegen) Icons.Default.Refresh else Icons.Default.Add,
                    contentDescription = if (isRegen) "Regenerate" else "Generate",
                    tint = if (isRegen) Color(0xFF7A5A1F) else Color.White,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    if (isRegen) "Regen" else "Generate",
                    fontSize = 12.sp,
                    color = if (isRegen) Color(0xFF7A5A1F) else Color.White
                )
            }
        }
    }
}

@Composable
private fun AdvCacheBadge(prefetch: PrefetchProgress) {
    val done = prefetch.done
    val total = prefetch.total
    if (total == 0 && !prefetch.finished) return
    val complete = prefetch.finished || (total > 0 && done >= total)
    val bg = if (complete) Color(0xFFE5F2E6) else Color(0xFFFFF3DA)
    val fg = if (complete) Color(0xFF2E7D32) else Color(0xFF8A5A1F)
    Surface(color = bg, shape = RoundedCornerShape(12.dp)) {
        Row(
            Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (!complete) {
                CircularProgressIndicator(
                    modifier = Modifier.size(10.dp),
                    strokeWidth = 1.5.dp,
                    color = fg
                )
            }
            Text(
                "audio $done/$total",
                fontSize = 11.sp,
                color = fg,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun SingleAdverbMode(
    app: LangbangApplication,
    lesson: AdverbLesson,
    state: AdverbsScreenState
) {
    Row(Modifier.fillMaxSize()) {
        AdverbList(
            adverbs = lesson.adverbs,
            selected = state.selected,
            onSelect = { state.select(it) },
            modifier = Modifier
                .width(224.dp)
                .fillMaxHeight()
                .background(Color(0xFFF3EFE6))
        )
        Box(Modifier.weight(1f).fillMaxHeight()) {
            state.selected?.let { AdverbSentences(app, it, state) }
        }
    }
}

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
                    Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            a.lemma,
                            color = if (isSel) Color.White else Color(0xFF0F4C81),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp
                        )
                        Text(
                            a.en,
                            color = if (isSel) Color.White.copy(alpha = 0.85f)
                            else Color(0xFF666666),
                            fontSize = 12.sp
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
                color = Color(0xFF0F4C81))
            Spacer(Modifier.width(12.dp))
            Text(adv.en, fontSize = 14.sp, color = Color(0xFF666666),
                modifier = Modifier.padding(bottom = 4.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Examples",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF7A5A1F)
        )
        if (state.sentences.isEmpty()) {
            Text(
                "No examples yet — tap Generate above to make 20 short sentences using " +
                    "this adverb with common verbs and beginner vocabulary.",
                fontSize = 12.sp,
                color = Color(0xFF888888)
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
            containerColor = if (highlighted) Color(0xFFE6F0FA) else Color(0xFFFBF7EC)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    sentence.pl,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF0F4C81)
                )
                Text(
                    sentence.en,
                    fontSize = 12.sp,
                    color = Color(0xFF777777)
                )
            }
            Icon(Icons.Default.PlayArrow, contentDescription = "Play",
                tint = Color(0xFF0F4C81), modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun AddAdverbMode(app: LangbangApplication, onAdded: () -> Unit) {
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var preview by remember { mutableStateOf<AdverbEntry?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Add an adverb", fontSize = 24.sp, fontWeight = FontWeight.SemiBold,
            color = Color(0xFF0F4C81))
        Text(
            "Type an English adverb (e.g. \"often\"). Gemini will translate it to Polish " +
                "(no inflection — adverbs are uninflected), then we'll generate audio.",
            fontSize = 13.sp, color = Color(0xFF666666)
        )
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            label = { Text("English adverb") },
            singleLine = true,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth()
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = {
                    val trimmed = input.trim()
                    if (trimmed.isEmpty()) return@Button
                    busy = true; error = null; preview = null
                    scope.launch {
                        app.gemini.translateAdverb(trimmed)
                            .onSuccess { a ->
                                preview = a
                                app.lessonRepo.addUserAdverb(a)
                                onAdded()
                            }
                            .onFailure { error = it.message }
                        busy = false
                    }
                },
                enabled = !busy && input.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                shape = RoundedCornerShape(24.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = Color.White)
                Spacer(Modifier.width(8.dp))
                Text(if (busy) "Translating…" else "Translate & add", color = Color.White)
            }
            if (busy) {
                Spacer(Modifier.width(12.dp))
                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
            }
        }
        error?.let {
            Text("Error: $it", color = Color.Red, fontSize = 12.sp)
        }
        preview?.let {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F4EA)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Added ✓ — ${it.lemma}", fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF2E7D32))
                    Text(it.en, fontSize = 12.sp, color = Color(0xFF555555))
                }
            }
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
