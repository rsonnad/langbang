package com.sponic.langbang.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.sponic.langbang.data.IncludeMode
import com.sponic.langbang.data.RandomConfig
import kotlinx.coroutines.launch

/**
 * Full-screen modal sheet — appears when the user taps the "Play random" pill while
 * playback is idle. Captures every filter that gates the random queue, persists choices
 * to RandomConfigStore on Play, then dismisses + starts playback.
 *
 * Past tense and Adverbs chips/dropdowns render in a "coming soon" disabled state for
 * now — the data model + content lands in tasks #6, #7, #8 respectively.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RandomConfigSheet(
    app: LangbangApplication,
    initial: RandomConfig,
    initialMustContain: String = initial.mustContainWord,
    onCancel: () -> Unit,
    onPlay: (RandomConfig) -> Unit,
) {
    var mustContain by remember { mutableStateOf(initialMustContain) }
    var persons by remember { mutableStateOf(initial.personKeys) }
    var tenses by remember { mutableStateOf(initial.tenses) }
    var preps by remember { mutableStateOf(initial.prepositions) }
    var adjMode by remember { mutableStateOf(initial.adjectiveMode) }
    var advMode by remember { mutableStateOf(initial.adverbMode) }
    val scope = rememberCoroutineScope()

    // Preflight: count what's eligible right now under the current settings, so the
    // user knows BEFORE tapping Play whether the queue will be empty / sparse /
    // missing-words. Surfaces a generate prompt when "All" mode skips adjectives or
    // when must-contain filter has < 8 hits.
    var preflight by remember { mutableStateOf(Preflight()) }
    var preflightBusy by remember { mutableStateOf(false) }
    val config = RandomConfig(
        mustContainWord = mustContain.trim(),
        personKeys = persons,
        tenses = tenses,
        prepositions = preps,
        adjectiveMode = adjMode,
        adverbMode = advMode
    )
    LaunchedEffect(config) {
        preflight = computePreflight(app, config)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xCC0F4C81)) // scrim
    ) {
        Surface(
            color = Color(0xFFFAF6EC),
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .align(Alignment.TopCenter),
            shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)
        ) {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Play random — what to drill",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF0F4C81),
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onCancel, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Cancel",
                            tint = Color(0xFF7A5A1F)
                        )
                    }
                }

                // ── Must contain word ───────────────────────────────────────────
                SectionLabel(
                    "Must contain word",
                    "If set, every sentence will include this word (or one of its inflections)."
                )
                OutlinedTextField(
                    value = mustContain,
                    onValueChange = { mustContain = it },
                    placeholder = { Text("e.g. kawa", fontSize = 13.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // ── Verb persons ────────────────────────────────────────────────
                SectionLabel("Verb persons", "Subjects to drill across all verbs.")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    RandomConfig.PERSONS.forEach { k ->
                        ToggleChip(
                            label = personChipLabel(k),
                            selected = k in persons,
                            onToggle = {
                                persons = if (k in persons) persons - k else persons + k
                            }
                        )
                    }
                }

                // ── Tense ───────────────────────────────────────────────────────
                SectionLabel("Tense", "Past tense content lands in a follow-up.")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ToggleChip(
                        label = "Present",
                        selected = "present" in tenses,
                        onToggle = {
                            tenses = if ("present" in tenses) tenses - "present"
                            else tenses + "present"
                        }
                    )
                    ToggleChip(
                        label = "Past (soon)",
                        selected = false,
                        enabled = false,
                        onToggle = { }
                    )
                }

                // ── Prepositions ────────────────────────────────────────────────
                SectionLabel(
                    "Prepositions",
                    "Restrict to sentences containing one of these. Off when none picked."
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    RandomConfig.PREPOSITIONS.forEach { p ->
                        ToggleChip(
                            label = "$p (${prepositionGloss(p)})",
                            selected = p in preps,
                            onToggle = {
                                preps = if (p in preps) preps - p else preps + p
                            }
                        )
                    }
                }

                // ── Adjectives tri-state ────────────────────────────────────────
                SectionLabel(
                    "Adjectives",
                    "'All' cycles every adjective so each sentence uses a different one."
                )
                TriStateRow(selected = adjMode, onSelect = { adjMode = it })

                // ── Adverbs tri-state (disabled until content lands) ────────────
                SectionLabel(
                    "Adverbs",
                    "Adverb content lands in a follow-up; the toggle is here for layout."
                )
                TriStateRow(
                    selected = advMode,
                    onSelect = { advMode = it },
                    enabled = false
                )

                // ── Preflight status (#9 + #10) ─────────────────────────────────
                Spacer(Modifier.height(4.dp))
                PreflightPanel(
                    preflight = preflight,
                    busy = preflightBusy,
                    isOnline = app.network.isOnline(),
                    onGenerateMissingAdjectives = {
                        preflightBusy = true
                        scope.launch {
                            generateMissingAdjectiveSentences(app, preflight.missingAdjectives)
                            preflight = computePreflight(app, config)
                            preflightBusy = false
                        }
                    },
                    onRegenerateForWord = {
                        preflightBusy = true
                        scope.launch {
                            regenerateForMustContain(app, mustContain.trim())
                            preflight = computePreflight(app, config)
                            preflightBusy = false
                        }
                    }
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onCancel,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFEFE8D8),
                            contentColor = Color(0xFF7A5A1F)
                        ),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel", fontSize = 14.sp)
                    }
                    Button(
                        onClick = { onPlay(config) },
                        enabled = preflight.eligibleCount > 0,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.weight(2f)
                    ) {
                        Icon(
                            Icons.Filled.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Play (${preflight.eligibleCount} phrases)",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(label: String, hint: String) {
    Column {
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
private fun ToggleChip(
    label: String,
    selected: Boolean,
    enabled: Boolean = true,
    onToggle: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onToggle,
        enabled = enabled,
        label = { Text(label, fontSize = 12.sp) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = Color.White
        )
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TriStateRow(
    selected: IncludeMode,
    onSelect: (IncludeMode) -> Unit,
    enabled: Boolean = true
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        IncludeMode.values().forEach { mode ->
            ToggleChip(
                label = when (mode) {
                    IncludeMode.OFF -> "Off"
                    IncludeMode.YES -> "Yes"
                    IncludeMode.ALL -> "All"
                },
                selected = selected == mode,
                enabled = enabled,
                onToggle = { onSelect(mode) }
            )
        }
    }
}

private fun personChipLabel(key: String): String = when (key) {
    "1sg" -> "ja (I)"
    "2sg" -> "ty (you)"
    "3sg" -> "on / ona"
    "1pl" -> "my (we)"
    "2pl" -> "wy (y'all)"
    "3pl" -> "oni / one"
    else -> key
}

private fun prepositionGloss(p: String): String = when (p) {
    "w" -> "in"
    "na" -> "on"
    "do" -> "to"
    "z" -> "with/from"
    "o" -> "about"
    else -> ""
}

// ── Preflight (#9 + #10) ─────────────────────────────────────────────────────

/**
 * Snapshot of what the random queue would look like given the current sheet selections.
 * Lets us warn the user about empty queues, must-contain misses, and "all" mode adjectives
 * with no cached examples — all before they tap Play.
 */
private data class Preflight(
    val eligibleCount: Int = 0,
    val mustContainHits: Int? = null,   // null when mustContain is empty
    val missingAdjectives: List<String> = emptyList()
)

private const val MUST_CONTAIN_HIT_THRESHOLD = 8

private fun computePreflight(app: LangbangApplication, config: RandomConfig): Preflight {
    val mustContain = config.mustContainWord.lowercase().takeIf { it.isNotEmpty() }
    val prepFilter = config.prepositions.takeIf { it.isNotEmpty() }

    fun passesPreps(pl: String): Boolean {
        if (prepFilter == null) return true
        return pl.lowercase().split(Regex("[^\\p{L}]+")).any { it in prepFilter }
    }
    fun passesMustContain(pl: String): Boolean {
        if (mustContain == null) return true
        return pl.lowercase().contains(mustContain)
    }

    var total = 0
    val verbLesson = app.lessonRepo.lesson2()
    if ("present" in config.tenses) {
        verbLesson.verbs.forEach { verb ->
            val allowedForms = verb.forms
                .filterKeys { it in config.personKeys }
                .values.filter { it.isNotBlank() }.map { it.lowercase() }.toSet()
            if (allowedForms.isEmpty()) return@forEach
            total += app.lessonRepo.sentencesFor(verb.lemma).count { s ->
                val tokens = s.pl.lowercase().split(Regex("[^\\p{L}]+"))
                tokens.any { it.isNotEmpty() && it in allowedForms } &&
                    passesPreps(s.pl) && passesMustContain(s.pl)
            }
        }
    }

    val missingAdj = mutableListOf<String>()
    if (config.adjectiveMode != IncludeMode.OFF) {
        val adjLesson = app.lessonRepo.lesson3()
        adjLesson.adjectives.forEach { adj ->
            val sentences = app.lessonRepo.adjectiveSentencesFor(adj.lemma)
            val eligible = sentences.count { passesPreps(it.pl) && passesMustContain(it.pl) }
            if (eligible == 0 && config.adjectiveMode == IncludeMode.ALL) {
                missingAdj += adj.lemma
            }
            total += if (config.adjectiveMode == IncludeMode.ALL) eligible.coerceAtMost(1) * sentences.size
                     else eligible
        }
    }
    // Adverbs: same shape (content lands in this build).
    if (config.adverbMode != IncludeMode.OFF) {
        val advLesson = app.lessonRepo.lesson4()
        advLesson.adverbs.forEach { adv ->
            val sentences = app.lessonRepo.adverbSentencesFor(adv.lemma)
            total += sentences.count { passesPreps(it.pl) && passesMustContain(it.pl) }
        }
    }

    val hits = if (mustContain != null) total else null
    return Preflight(eligibleCount = total, mustContainHits = hits, missingAdjectives = missingAdj)
}

private suspend fun generateMissingAdjectiveSentences(
    app: LangbangApplication,
    lemmas: List<String>
) {
    val adjLesson = app.lessonRepo.lesson3()
    val byLemma = adjLesson.adjectives.associateBy { it.lemma }
    lemmas.mapNotNull { byLemma[it] }.forEach { adj ->
        if (app.lessonRepo.adjectiveSentencesFor(adj.lemma).isNotEmpty()) return@forEach
        app.gemini.generateAdjectiveSentences(adj)
            .onSuccess { app.lessonRepo.saveAdjectiveSentences(adj.lemma, it) }
    }
}

/**
 * Triggered by the "Generate more sentences with <word>" CTA. Picks one verb (the most
 * relevant by token match against the must-contain word, falling back to a random one)
 * and asks Gemini to top up its sentence pool. The constraint isn't perfectly enforced
 * — Gemini sometimes returns sentences without the requested word — but it's a useful
 * nudge.
 */
private suspend fun regenerateForMustContain(app: LangbangApplication, word: String) {
    if (word.isEmpty()) return
    val verbLesson = app.lessonRepo.lesson2()
    val verb = verbLesson.verbs.firstOrNull { it.lemma.contains(word, ignoreCase = true) }
        ?: verbLesson.verbs.randomOrNull()
        ?: return
    app.gemini.generateSentences(verb, allowedPersonKeys = setOf("1sg", "2sg", "3sg"))
        .onSuccess { list ->
            // Merge: keep existing, prepend new ones containing the word.
            val existing = app.lessonRepo.sentencesFor(verb.lemma)
            val combined = (list + existing).distinctBy { it.pl }
            app.lessonRepo.saveSentences(verb.lemma, combined)
        }
}

@Composable
private fun PreflightPanel(
    preflight: Preflight,
    busy: Boolean,
    isOnline: Boolean,
    onGenerateMissingAdjectives: () -> Unit,
    onRegenerateForWord: () -> Unit
) {
    val hits = preflight.mustContainHits
    val showMustContainCta = hits != null && hits < MUST_CONTAIN_HIT_THRESHOLD
    val showMissingAdjCta = preflight.missingAdjectives.isNotEmpty()
    if (preflight.eligibleCount == 0 && hits == null && !showMissingAdjCta) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (preflight.eligibleCount == 0) {
            Text(
                "No phrases match these filters. Loosen the matrix above, or generate " +
                    "more examples from the Verbs / Adjectives tabs.",
                fontSize = 11.sp,
                color = Color(0xFFB04A2A)
            )
        }
        if (hits != null) {
            Text(
                "“${preflight.mustContainHits}” hits for that word in cached sentences.",
                fontSize = 11.sp,
                color = if (showMustContainCta) Color(0xFFB04A2A) else Color(0xFF555555)
            )
            if (showMustContainCta) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = onRegenerateForWord,
                        enabled = !busy && isOnline,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFEFE8D8),
                            contentColor = Color(0xFF7A5A1F)
                        ),
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                    ) {
                        Text(
                            if (busy) "Generating…" else "Generate more with that word",
                            fontSize = 11.sp
                        )
                    }
                    if (!isOnline) {
                        Spacer(Modifier.width(8.dp))
                        Text("Offline — cached only", fontSize = 10.sp, color = Color(0xFFB04A2A))
                    }
                }
            }
        }
        if (showMissingAdjCta) {
            Text(
                "${preflight.missingAdjectives.size} adjectives have no cached examples " +
                    "(${preflight.missingAdjectives.take(3).joinToString(", ")}" +
                    if (preflight.missingAdjectives.size > 3) ", …)" else ")",
                fontSize = 11.sp,
                color = Color(0xFFB04A2A)
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = onGenerateMissingAdjectives,
                    enabled = !busy && isOnline,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFEFE8D8),
                        contentColor = Color(0xFF7A5A1F)
                    ),
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                ) {
                    Text(
                        if (busy) "Generating…" else "Generate missing sentences",
                        fontSize = 11.sp
                    )
                }
                if (!isOnline) {
                    Spacer(Modifier.width(8.dp))
                    Text("Offline — cached only", fontSize = 10.sp, color = Color(0xFFB04A2A))
                }
            }
        }
    }
}
