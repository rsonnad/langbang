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
import com.sponic.langbang.domain.PrefetchProgress
import com.sponic.langbang.data.model.AdjectiveEntry
import com.sponic.langbang.data.model.AdjectiveLesson
import com.sponic.langbang.data.model.SentenceExample
import com.sponic.langbang.domain.NowVoicing
import com.sponic.langbang.domain.NowVoicingBus
import com.sponic.langbang.integrations.AzureTtsClient
import android.media.MediaMetadataRetriever
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

private enum class AdjMode(val label: String) {
    Single("Single adjective"),
    Add("+ Add adjective")
}

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
    var slowFirst: Boolean by mutableStateOf(true)

    private var playJob: Job? = null
    var playingIndex: Int by mutableStateOf(-1)
        private set
    val playing: Boolean get() = playJob?.isActive == true

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
        playJob?.cancel()
        playJob = null
        playingIndex = -1
        app.audioPlayer.stop()
        NowVoicingBus.clear()
    }

    fun playAll(quiz: Boolean) {
        if (playJob?.isActive == true) {
            stop()
            return
        }
        if (sentences.isEmpty()) return
        val list = sentences
        playJob = scope.launch {
            try {
                list.forEachIndexed { i, s ->
                    playingIndex = i
                    val pos = "${i + 1}/${list.size}"
                    fun pub(lang: String) {
                        NowVoicingBus.publish(NowVoicing(s.en, s.pl, null, lang, pos))
                    }
                    if (quiz) {
                        pub("en")
                        playAndAwait(app, s.en, AzureTtsClient.LOCALE_EN, AzureTtsClient.EN_US_F)
                        pub("pause")
                        val plFile = app.audioCache.fileFor(
                            AzureTtsClient.LOCALE_PL, AzureTtsClient.PL_PL_F, s.pl
                        )
                        val pauseMs = mp3DurationMs(plFile) + 2000L
                        delay(pauseMs)
                        pub("pl")
                        playAndAwait(app, s.pl, AzureTtsClient.LOCALE_PL, AzureTtsClient.PL_PL_F)
                    } else if (slowFirst) {
                        pub("en")
                        playAndAwait(app, s.en, AzureTtsClient.LOCALE_EN, AzureTtsClient.EN_US_F)
                        pub("pl-slow")
                        playAndAwait(app, s.pl, AzureTtsClient.LOCALE_PL,
                            AzureTtsClient.PL_PL_F_SLOW)
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
private fun rememberAdjectivesScreenState(app: LangbangApplication): AdjectivesScreenState {
    val scope = rememberCoroutineScope()
    return remember { AdjectivesScreenState(app, scope) }
}

@Composable
fun AdjectivesScreen(app: LangbangApplication, prefetch: PrefetchProgress) {
    var reloadKey by remember { mutableStateOf(0) }
    val lesson = remember(reloadKey) { app.lessonRepo.lesson3() }
    var mode by remember { mutableStateOf(AdjMode.Single) }
    val scope = rememberCoroutineScope()
    val state = rememberAdjectivesScreenState(app)
    var generateAllBusy by remember { mutableStateOf(false) }
    var generateAllProgress by remember { mutableStateOf<String?>(null) }
    var generateAllError by remember { mutableStateOf<String?>(null) }

    // Re-sync selection when the lesson list reloads after a user-add.
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

    Column(modifier = Modifier.fillMaxSize()) {
        ModeBar(
            mode = mode,
            onSelect = { mode = it },
            prefetch = prefetch,
            onGenerateAll = generateAll,
            generateAllBusy = generateAllBusy,
            generateAllProgress = generateAllProgress,
            state = state,
            showControls = mode == AdjMode.Single && state.selected != null
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
                AdjMode.Single -> SingleAdjectiveMode(app, lesson, state)
                AdjMode.Add -> AddAdjectiveMode(app) { reloadKey++; mode = AdjMode.Single }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModeBar(
    mode: AdjMode,
    onSelect: (AdjMode) -> Unit,
    prefetch: PrefetchProgress,
    onGenerateAll: () -> Unit,
    generateAllBusy: Boolean,
    generateAllProgress: String?,
    state: AdjectivesScreenState,
    showControls: Boolean
) {
    Surface(color = Color(0xFFF7F3EA), modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AdjMode.values().forEach { m ->
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
                CacheBadge(prefetch)
                if (showControls) {
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
                    color = Color(0xFF7A5A1F),
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
private fun ExamplesControls(
    state: AdjectivesScreenState,
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
            val isRegenerate = state.sentences.isNotEmpty()
            Button(
                onClick = { state.generate() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRegenerate) Color(0xFFEFE8D8)
                    else MaterialTheme.colorScheme.primary
                ),
                shape = RoundedCornerShape(16.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
            ) {
                Icon(
                    if (isRegenerate) Icons.Default.Refresh else Icons.Default.Add,
                    contentDescription = if (isRegenerate) "Regenerate" else "Generate",
                    tint = if (isRegenerate) Color(0xFF7A5A1F) else Color.White,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    if (isRegenerate) "Regen" else "Generate",
                    fontSize = 12.sp,
                    color = if (isRegenerate) Color(0xFF7A5A1F) else Color.White
                )
            }
        }
    }
}

@Composable
private fun CacheBadge(prefetch: PrefetchProgress) {
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

// ── Mode 1 — pick an adjective, see all 10 nom + acc forms + Gemini examples ───

@Composable
private fun SingleAdjectiveMode(
    app: LangbangApplication,
    lesson: AdjectiveLesson,
    state: AdjectivesScreenState
) {
    Row(Modifier.fillMaxSize()) {
        AdjectiveList(
            adjectives = lesson.adjectives,
            selected = state.selected,
            onSelect = { state.select(it) },
            modifier = Modifier
                .width(224.dp)
                .fillMaxHeight()
                .background(Color(0xFFF3EFE6))
        )
        Box(Modifier.weight(1f).fillMaxHeight()) {
            state.selected?.let { AdjectiveParadigm(app, it, state) }
        }
    }
}

@Composable
private fun AdjectiveList(
    adjectives: List<AdjectiveEntry>,
    selected: AdjectiveEntry?,
    onSelect: (AdjectiveEntry) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(adjectives, key = { "a-${it.lemma}" }) { a ->
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
private fun AdjectiveParadigm(
    app: LangbangApplication,
    adj: AdjectiveEntry,
    state: AdjectivesScreenState
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(adj.lemma, fontSize = 26.sp, fontWeight = FontWeight.Bold,
                color = Color(0xFF0F4C81))
            Spacer(Modifier.width(12.dp))
            Text(adj.en, fontSize = 14.sp, color = Color(0xFF666666),
                modifier = Modifier.padding(bottom = 4.dp))
        }

        ParadigmHeader("Nominative", "the form when the adjective + noun is the subject")
        GENDER_KEYS.forEach { k ->
            val form = adj.nom[k].orEmpty()
            FormRow(label = genderLabel(k), form = form) { playForm(app, form) }
        }

        Spacer(Modifier.height(4.dp))
        ParadigmHeader(
            "Accusative",
            "for direct objects — \"I see a big table\". m form is animate (-ego); " +
                "for inanimate m, accusative = nominative."
        )
        GENDER_KEYS.forEach { k ->
            val form = adj.acc[k].orEmpty()
            FormRow(label = genderLabel(k), form = form) { playForm(app, form) }
        }

        Spacer(Modifier.height(8.dp))
        SentencesSection(app, state)
    }
}

@Composable
private fun ParadigmHeader(label: String, hint: String) {
    Column(Modifier.padding(top = 6.dp, bottom = 2.dp)) {
        Text(
            label,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF7A5A1F)
        )
        Text(hint, fontSize = 10.sp, color = Color(0xFF999999))
    }
}

@Composable
private fun FormRow(label: String, form: String, onPlay: () -> Unit) {
    Card(
        onClick = onPlay,
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF6F2EA)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                label,
                fontSize = 12.sp,
                color = Color(0xFF777777),
                modifier = Modifier.width(170.dp)
            )
            Text(form, fontSize = 18.sp, fontWeight = FontWeight.Medium,
                color = Color(0xFF0F4C81), modifier = Modifier.weight(1f))
            Icon(Icons.Default.PlayArrow, contentDescription = "Play",
                tint = Color(0xFF0F4C81), modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun SentencesSection(app: LangbangApplication, state: AdjectivesScreenState) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            "Examples",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF7A5A1F)
        )

        if (state.sentences.isEmpty()) {
            Text(
                "No examples yet — tap Generate above to make 20 short sentences combining " +
                    "this adjective with common verbs and nouns.",
                fontSize = 12.sp,
                color = Color(0xFF888888)
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

// ── Mode 2 — add an adjective via Gemini ──────────────────────────────────────

@Composable
private fun AddAdjectiveMode(app: LangbangApplication, onAdded: () -> Unit) {
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var preview by remember { mutableStateOf<AdjectiveEntry?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Add an adjective", fontSize = 24.sp, fontWeight = FontWeight.SemiBold,
            color = Color(0xFF0F4C81))
        Text(
            "Type an English adjective (e.g. \"yellow\"). Gemini will translate to Polish " +
                "and produce the full nominative + accusative paradigm (m, f, n, virile-pl, " +
                "other-pl), then we'll generate audio.",
            fontSize = 13.sp, color = Color(0xFF666666)
        )

        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            label = { Text("English adjective") },
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
                        app.gemini.translateAdjective(trimmed)
                            .onSuccess { a ->
                                preview = a
                                app.lessonRepo.addUserAdjective(a)
                                app.prefetch.prefetchAdjective(a)
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
                    Text(
                        "nom: " + GENDER_KEYS.joinToString("  ·  ") { k -> "${k}: ${it.nom[k] ?: "—"}" },
                        fontSize = 12.sp, color = Color(0xFF555555)
                    )
                    Text(
                        "acc: " + GENDER_KEYS.joinToString("  ·  ") { k -> "${k}: ${it.acc[k] ?: "—"}" },
                        fontSize = 12.sp, color = Color(0xFF555555)
                    )
                }
            }
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

private fun playForm(app: LangbangApplication, form: String) {
    if (form.isEmpty()) return
    val f = app.audioCache.fileFor(
        AzureTtsClient.LOCALE_PL, AzureTtsClient.PL_PL_F, form
    )
    app.audioPlayer.play(f)
}
