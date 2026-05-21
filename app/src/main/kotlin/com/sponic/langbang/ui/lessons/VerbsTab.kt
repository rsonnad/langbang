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
import com.sponic.langbang.data.model.ConjugationClass
import com.sponic.langbang.data.model.Lesson
import com.sponic.langbang.data.model.PERSON_KEYS
import com.sponic.langbang.data.model.SentenceExample
import com.sponic.langbang.data.model.VerbEntry
import com.sponic.langbang.data.model.audioPronoun
import com.sponic.langbang.data.model.conjugationClass
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

private enum class VerbMode(val label: String) {
    Single("Single verb"),
    ByPronoun("By pronoun"),
    Add("+ Add verb")
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
    var sentences: List<SentenceExample> by mutableStateOf(emptyList())
        private set
    var busy: Boolean by mutableStateOf(false)
        private set
    var error: String? by mutableStateOf(null)
        private set
    var allVerbsMode: Boolean by mutableStateOf(false)
    var slowFirst: Boolean by mutableStateOf(true)
    var generateProgress: String? by mutableStateOf(null)
        private set
    var includedKeys: Set<String> by mutableStateOf(
        app.pronounFilter.allIncluded(PERSON_KEYS)
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

    fun selectVerb(verb: VerbEntry?) {
        if (selected?.lemma == verb?.lemma) return
        selected = verb
        sentences = verb?.let { app.lessonRepo.sentencesFor(it.lemma) } ?: emptyList()
        error = null
    }

    fun toggleIncluded(key: String, included: Boolean) {
        app.pronounFilter.setIncluded(key, included)
        includedKeys = if (included) includedKeys + key else includedKeys - key
    }

    fun generate(allVerbs: List<VerbEntry> = emptyList()) {
        if (busy) return
        if (includedKeys.isEmpty()) {
            error = "Check at least one pronoun to generate sentences."
            return
        }
        if (allVerbsMode) {
            if (allVerbs.isEmpty()) return
            busy = true; error = null; generateProgress = "starting…"
            scope.launch {
                val errors = mutableListOf<String>()
                try {
                    // Gemini-only loop — fast (one call per verb). Audio is left to the
                    // background prefetch worker so a slow/throttled TTS never stalls us.
                    allVerbs.forEachIndexed { i, vv ->
                        generateProgress = "Gemini ${i + 1}/${allVerbs.size} · ${vv.lemma}"
                        val existing = app.lessonRepo.sentencesFor(vv.lemma)
                        if (existing.isNotEmpty()) {
                            if (vv.lemma == selected?.lemma) sentences = existing
                            return@forEachIndexed
                        }
                        app.gemini.generateSentences(vv, includedKeys)
                            .onSuccess { list ->
                                app.lessonRepo.saveSentences(vv.lemma, list)
                                if (vv.lemma == selected?.lemma) sentences = list
                            }
                            .onFailure { err ->
                                errors += "${vv.lemma}: ${err.message}"
                            }
                    }
                    generateProgress = "Kicking audio prefetch…"
                    kickPrefetchWorker(app)
                } finally {
                    if (errors.isNotEmpty()) {
                        error = "${errors.size} verb(s) failed: ${errors.first()}"
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
            try {
                app.gemini.generateSentences(v, includedKeys)
                    .onSuccess { list ->
                        app.lessonRepo.saveSentences(v.lemma, list)
                        if (selected?.lemma == v.lemma) sentences = list
                        generateProgress = "Caching audio…"
                        app.prefetch.prefetchSentences(list)
                    }
                    .onFailure { error = it.message }
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
        app.audioPlayer.stop()
        NowVoicingBus.clear()
    }

    private suspend fun playVerbSentences(
        target: VerbEntry,
        list: List<SentenceExample>,
        quiz: Boolean = false
    ) {
        playingLemma = target.lemma
        list.forEachIndexed { i, s ->
            playingIndex = i
            playingPl = s.pl
            playingEn = s.en
            playingLiteral = s.literal
            val position = "${i + 1}/${list.size}"
            if (quiz) {
                setLang("en", s, position)
                playAndAwait(app, s.en, AzureTtsClient.LOCALE_EN, AzureTtsClient.EN_US_F)
                setLang("pause", s, position)
                val plFile = app.audioCache.fileFor(
                    AzureTtsClient.LOCALE_PL, AzureTtsClient.PL_PL_F, s.pl
                )
                val pauseMs = mp3DurationMs(plFile) + 2000L
                delay(pauseMs)
                setLang("pl", s, position)
                playAndAwait(app, s.pl, AzureTtsClient.LOCALE_PL, AzureTtsClient.PL_PL_F)
            } else if (slowFirst) {
                // First pass: slow Polish for clarity, then normal-rate.
                setLang("en", s, position)
                playAndAwait(app, s.en, AzureTtsClient.LOCALE_EN, AzureTtsClient.EN_US_F)
                setLang("pl-slow", s, position)
                playAndAwait(app, s.pl, AzureTtsClient.LOCALE_PL, AzureTtsClient.PL_PL_F_SLOW_V2)
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
    }

    private fun setLang(l: String, s: SentenceExample, position: String) {
        playingLang = l
        NowVoicingBus.publish(NowVoicing(s.en, s.pl, s.literal, l, position, s.words))
    }

    private suspend fun ensureSentencesFor(target: VerbEntry): List<SentenceExample> {
        val existing = app.lessonRepo.sentencesFor(target.lemma)
        if (existing.isNotEmpty()) return existing
        val generated = app.gemini.generateSentences(target, includedKeys)
            .getOrElse { return emptyList() }
        app.lessonRepo.saveSentences(target.lemma, generated)
        if (target.lemma == selected?.lemma) sentences = generated
        app.prefetch.prefetchSentences(generated)
        return generated
    }

    fun playAll(allVerbs: List<VerbEntry>, quiz: Boolean = false) {
        if (playJob?.isActive == true) {
            stop()
            return
        }
        val v = selected ?: return
        playJob = scope.launch {
            try {
                if (allVerbsMode) {
                    allVerbs.forEach { vv ->
                        val list = ensureSentencesFor(vv)
                        if (list.isNotEmpty()) playVerbSentences(vv, list, quiz)
                    }
                } else {
                    if (sentences.isEmpty()) return@launch
                    playVerbSentences(v, sentences, quiz)
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
fun VerbsTab(app: LangbangApplication, prefetch: PrefetchProgress) {
    // lesson is reloaded explicitly via [reloadKey] so a freshly-added user verb shows up.
    var reloadKey by remember { mutableStateOf(0) }
    val lesson = remember(reloadKey) { app.lessonRepo.lesson2() }
    var mode by remember { mutableStateOf(VerbMode.Single) }
    val state = rememberVerbsTabState(app)

    // Initialise/refresh the selection when the lesson list changes.
    if (state.selected == null || lesson.verbs.none { it.lemma == state.selected?.lemma }) {
        state.selectVerb(lesson.verbs.firstOrNull())
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopBar(
            mode = mode,
            onModeSelect = { mode = it },
            state = state,
            allVerbs = lesson.verbs,
            showControls = mode == VerbMode.Single && state.selected != null,
            prefetch = prefetch
        )
        Box(modifier = Modifier.fillMaxSize()) {
            when (mode) {
                VerbMode.Single -> SingleVerbMode(app, lesson, state)
                VerbMode.ByPronoun -> ByPronounMode(app, lesson)
                VerbMode.Add -> AddVerbMode(app) { reloadKey++; mode = VerbMode.Single }
            }
        }
    }
}

@Composable
private fun TopBar(
    mode: VerbMode,
    onModeSelect: (VerbMode) -> Unit,
    state: VerbsTabState,
    allVerbs: List<VerbEntry>,
    showControls: Boolean,
    prefetch: PrefetchProgress
) {
    Surface(color = Color(0xFFF7F3EA), modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                VerbMode.values().forEach { m ->
                    FilterChip(
                        selected = mode == m,
                        onClick = { onModeSelect(m) },
                        label = { Text(m.label, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = Color.White
                        )
                    )
                }
                Spacer(Modifier.weight(1f))
                if (showControls) {
                    CacheBadge(prefetch)
                    ExamplesControls(state = state, allVerbs = allVerbs)
                }
            }
            state.generateProgress?.let {
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
private fun CacheBadge(prefetch: PrefetchProgress) {
    val done = prefetch.done
    val total = prefetch.total
    if (total == 0 && !prefetch.finished) return
    val complete = prefetch.finished || (total > 0 && done >= total)
    val bg = if (complete) Color(0xFFE5F2E6) else Color(0xFFFFF3DA)
    val fg = if (complete) Color(0xFF2E7D32) else Color(0xFF8A5A1F)
    Surface(
        color = bg,
        shape = RoundedCornerShape(12.dp)
    ) {
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
                "audio ${if (total == 0) done else done}/${if (total == 0) done else total}",
                fontSize = 11.sp,
                color = fg,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExamplesControls(state: VerbsTabState, allVerbs: List<VerbEntry>) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = state.allVerbsMode,
                onCheckedChange = { state.allVerbsMode = it },
                enabled = !state.playing,
                colors = CheckboxDefaults.colors(
                    checkedColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text("All verbs", fontSize = 11.sp, color = Color(0xFF555555))
        }
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

        val canPlay = state.allVerbsMode || state.sentences.isNotEmpty()
        if (canPlay) {
            Button(
                onClick = { state.playAll(allVerbs, quiz = false) },
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
                    onClick = { state.playAll(allVerbs, quiz = true) },
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
                onClick = { state.generate(allVerbs) },
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

// ── Mode 1 — single verb, all 6 forms ─────────────────────────────────────────

@Composable
private fun SingleVerbMode(
    app: LangbangApplication,
    lesson: Lesson,
    state: VerbsTabState
) {
    val grouped = remember(lesson) {
        lesson.verbs
            .groupBy { it.conjugationClass() }
            .toSortedMap(compareBy { it.ordinal })
    }

    Row(Modifier.fillMaxSize()) {
        VerbList(
            grouped = grouped,
            selected = state.selected,
            onSelect = { state.selectVerb(it) },
            modifier = Modifier
                .width(256.dp)
                .fillMaxHeight()
                .background(Color(0xFFF3EFE6))
        )
        Box(Modifier.weight(1f).fillMaxHeight()) {
            state.selected?.let { VerbParadigm(app, it, state) }
        }
    }
}

@Composable
private fun VerbList(
    grouped: Map<ConjugationClass, List<VerbEntry>>,
    selected: VerbEntry?,
    onSelect: (VerbEntry) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        grouped.forEach { (cls, verbs) ->
            item(key = "h-${cls.name}") {
                Column(Modifier.padding(top = 8.dp, bottom = 4.dp)) {
                    Text(
                        cls.label,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF7A5A1F)
                    )
                    Text(cls.description, fontSize = 10.sp, color = Color(0xFF999999))
                }
            }
            items(verbs, key = { "v-${it.lemma}" }) { v ->
                val isSel = v == selected
                Card(
                    onClick = { onSelect(v) },
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
                                v.lemma,
                                color = if (isSel) Color.White else Color(0xFF0F4C81),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 16.sp
                            )
                            Text(
                                v.en,
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
        Row(verticalAlignment = Alignment.Bottom) {
            Text(verb.lemma, fontSize = 26.sp, fontWeight = FontWeight.Bold,
                color = Color(0xFF0F4C81))
            Spacer(Modifier.width(12.dp))
            Text(verb.en, fontSize = 14.sp, color = Color(0xFF666666),
                modifier = Modifier.padding(bottom = 4.dp))
            Spacer(Modifier.width(12.dp))
            Text(
                verb.conjugationClass().label,
                fontSize = 11.sp,
                color = Color(0xFF7A5A1F),
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }
        Text(
            "Present",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF7A5A1F),
            modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
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
                included = k in state.includedKeys,
                onToggleIncluded = { now -> state.toggleIncluded(k, now) },
                onPlay = { playConjugation(app, k, form) }
            )
        }
        verb.past_forms?.let { past ->
            Spacer(Modifier.height(8.dp))
            Text(
                "Past",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF7A5A1F)
            )
            Text(
                "Masculine-singular for 1sg/2sg/3sg, virile-plural for 1pl/2pl/3pl. " +
                    "Feminine/non-virile variants are coming.",
                fontSize = 10.sp,
                color = Color(0xFF999999),
                modifier = Modifier.padding(bottom = 2.dp)
            )
            PERSON_KEYS.forEach { k ->
                val form = past[k].orEmpty()
                if (form.isEmpty()) return@forEach
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
                    included = false,
                    onToggleIncluded = { /* past tense filter not yet plumbed */ },
                    onPlay = { playConjugation(app, k, form) }
                )
            }
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
            color = Color(0xFF7A5A1F)
        )

        if (state.sentences.isEmpty()) {
            Text(
                "No examples yet — tap Generate examples above to make 10 short sentences using the checked pronouns.",
                fontSize = 12.sp,
                color = Color(0xFF888888)
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
                sentence.literal?.let {
                    Text(
                        it,
                        fontSize = 10.sp,
                        fontStyle = FontStyle.Italic,
                        color = Color(0xFFA08868)
                    )
                }
            }
            Icon(Icons.Default.PlayArrow, contentDescription = "Play",
                tint = Color(0xFF0F4C81), modifier = Modifier.size(20.dp))
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
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF6F2EA)),
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
            Column(Modifier.weight(1f)) {
                Text(spoken, fontSize = 18.sp, fontWeight = FontWeight.Medium,
                    color = Color(0xFF0F4C81))
                Text(englishGloss, fontSize = 11.sp, color = Color(0xFF888888))
            }
            Icon(Icons.Default.PlayArrow, contentDescription = "Play",
                tint = Color(0xFF0F4C81), modifier = Modifier.size(20.dp))
        }
    }
}

// ── Mode 2 — pick a pronoun, see every verb conjugated for it ─────────────────

@Composable
private fun ByPronounMode(app: LangbangApplication, lesson: Lesson) {
    var personKey by remember { mutableStateOf("1sg") }

    Column(Modifier.fillMaxSize()) {
        Surface(color = Color(0xFFFFF8EE), modifier = Modifier.fillMaxWidth()) {
            Row(
                Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
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
                item(key = "h-${cls.name}-$personKey") {
                    Text(
                        cls.label,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF7A5A1F),
                        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
                    )
                }
                items(verbs, key = { "p-${it.lemma}-$personKey" }) { v ->
                    val form = v.forms[personKey].orEmpty()
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
                                    color = Color(0xFF0F4C81))
                                Text(v.en, fontSize = 10.sp, color = Color(0xFF888888))
                            }
                            val spoken = "${audioPronoun(personKey)} $form".trim()
                            Text(spoken, fontSize = 18.sp, fontWeight = FontWeight.Medium,
                                color = Color(0xFF2A2A2A), modifier = Modifier.weight(1f))
                            Icon(Icons.Default.PlayArrow, contentDescription = "Play",
                                tint = Color(0xFF0F4C81), modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }
    }
}

// ── Mode 3 — add a verb via Gemini ────────────────────────────────────────────

@Composable
private fun AddVerbMode(app: LangbangApplication, onAdded: () -> Unit) {
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var preview by remember { mutableStateOf<VerbEntry?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Add a verb", fontSize = 24.sp, fontWeight = FontWeight.SemiBold,
            color = Color(0xFF0F4C81))
        Text(
            "Type an English infinitive (e.g. \"to write\"). Gemini will translate to Polish " +
                "and conjugate all six present-tense forms, then we'll generate audio.",
            fontSize = 13.sp, color = Color(0xFF666666)
        )

        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            label = { Text("English verb") },
            singleLine = true,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth()
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = {
                    val trimmed = input.trim().removePrefix("to ").trim()
                    if (trimmed.isEmpty()) return@Button
                    busy = true; error = null; preview = null
                    scope.launch {
                        app.gemini.translateVerb(trimmed)
                            .onSuccess { v ->
                                preview = v
                                app.lessonRepo.addUserVerb(v)
                                app.prefetch.prefetchVerb(v)
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
                        PERSON_KEYS.joinToString("  ·  ") { k -> "${k}: ${it.forms[k] ?: "—"}" },
                        fontSize = 12.sp, color = Color(0xFF555555)
                    )
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
