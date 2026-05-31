package com.sponic.langbang.ui

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
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.sponic.langbang.data.IncludeMode
import com.sponic.langbang.data.RandomConfig
import com.sponic.langbang.data.VerbSentenceStore
import com.sponic.langbang.domain.NowVoicingBus
import com.sponic.langbang.ui.common.GrammarVisuals
import kotlinx.coroutines.launch

/**
 * Full-screen modal sheet — appears when the user taps the "Play random" pill while
 * playback is idle. Two-column compact layout: every control fits on the landscape
 * tablet without scrolling. Play button lives top-right; the X icon next to it cancels.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RandomConfigSheet(
    app: LangbangApplication,
    initial: RandomConfig,
    initialMustContain: String = initial.mustContainWord,
    randomPlayer: RandomPlayerState,
    onCancel: () -> Unit,
    onPlay: (RandomConfig) -> Unit,
) {
    var mustContain by remember { mutableStateOf(initialMustContain) }
    var persons by remember { mutableStateOf(initial.personKeys) }
    var tenses by remember { mutableStateOf(initial.tenses) }
    var preps by remember { mutableStateOf(initial.prepositions.toUiPrepositions()) }
    var adjMode by remember { mutableStateOf(initial.adjectiveMode) }
    var advMode by remember { mutableStateOf(initial.adverbMode) }
    val scope = rememberCoroutineScope()

    // Preflight: count what's eligible right now under the current settings, so the
    // user knows BEFORE tapping Play whether the queue will be empty / sparse /
    // missing-words. Surfaces a generate prompt when "All" mode skips adjectives or
    // when must-contain filter has < 8 hits.
    var preflight by remember { mutableStateOf(Preflight()) }
    var preflightBusy by remember { mutableStateOf(false) }
    val config = initial.copy(
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
            .background(LbColors.Scrim)
    ) {
        Surface(
            color = LbColors.Sheet,
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .align(Alignment.TopCenter),
            shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)
        ) {
            Column(
                Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // ── Header: title (left) · cancel · Play (top-right) ───────────
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Play Phrases",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = LbColors.PrimaryDeep,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onCancel, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Cancel",
                            tint = LbColors.Label
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { onPlay(config) },
                        enabled = preflight.eligibleCount > 0,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        shape = RoundedCornerShape(20.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            Icons.Filled.PlayArrow,
                            contentDescription = null,
                            tint = LbColors.OnPrimary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "Play (${preflight.eligibleCount})",
                            color = LbColors.OnPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // ── Now Playing (only when a queue is loaded) ──────────────────
                if (randomPlayer.hasQueue) {
                    NowPlayingPanel(player = randomPlayer)
                }

                // ── Two-column body — labels inline with chips/field ───────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        InlineRow("Words") {
                            OutlinedTextField(
                                value = mustContain,
                                onValueChange = { mustContain = it },
                                placeholder = {
                                    Text("e.g. kawa duża", fontSize = 12.sp)
                                },
                                singleLine = true,
                                textStyle = LocalTextStyle.current.copy(fontSize = 13.sp),
                                modifier = Modifier
                                    .width(280.dp)
                                    .defaultMinSize(minHeight = 44.dp)
                            )
                        }
                        InlineRow("Persons") {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                RandomConfig.PERSONS.forEach { k ->
                                    ToggleChip(
                                        label = personChipLabel(k),
                                        selected = k in persons,
                                        onToggle = {
                                            persons = if (k in persons) persons - k
                                            else persons + k
                                        }
                                    )
                                }
                            }
                        }
                        InlineRow("Tense") {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                ToggleChip(
                                    label = "Present",
                                    selected = VerbSentenceStore.TENSE_PRESENT in tenses,
                                    onToggle = {
                                        tenses = if (VerbSentenceStore.TENSE_PRESENT in tenses)
                                            tenses - VerbSentenceStore.TENSE_PRESENT
                                        else tenses + VerbSentenceStore.TENSE_PRESENT
                                    }
                                )
                                ToggleChip(
                                    label = "Past",
                                    selected = VerbSentenceStore.TENSE_PAST in tenses,
                                    onToggle = {
                                        tenses = if (VerbSentenceStore.TENSE_PAST in tenses)
                                            tenses - VerbSentenceStore.TENSE_PAST
                                        else tenses + VerbSentenceStore.TENSE_PAST
                                    }
                                )
                            }
                        }
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        InlineRow("Preps") {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                ToggleChip(
                                    label = "None",
                                    selected = RandomConfig.PREPOSITION_NONE in preps,
                                    onToggle = {
                                        preps = if (RandomConfig.PREPOSITION_NONE in preps) {
                                            val next = preps - RandomConfig.PREPOSITION_NONE
                                            next.ifEmpty { setOf(RandomConfig.PREPOSITION_NONE) }
                                        } else {
                                            preps + RandomConfig.PREPOSITION_NONE
                                        }
                                    }
                                )
                                RandomConfig.PREPOSITIONS.forEach { p ->
                                    ToggleChip(
                                        label = p,
                                        selected = p in preps,
                                        onToggle = {
                                            preps = if (p in preps) preps - p else preps + p
                                            preps = preps.toUiPrepositions()
                                        }
                                    )
                                }
                            }
                        }
                        InlineRow("Adj") {
                            TriStateRow(selected = adjMode, onSelect = { adjMode = it })
                        }
                        InlineRow("Adv") {
                            TriStateRow(selected = advMode, onSelect = { advMode = it })
                        }
                    }
                }

                // ── Preflight (compact, only renders when there's something to say) ──
                CompactPreflight(
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
            }
        }
    }
}

/**
 * In-sheet Now Voicing panel. Delegates the body to the shared
 * [com.sponic.langbang.ui.common.NowVoicingBody] so it stays in lockstep with the
 * sticky-header panel. The sheet adds its own 2x2 transport on the right (these
 * transport callbacks bypass PlaybackController because the sheet has direct
 * access to RandomPlayerState and needs sheet-local pause-state semantics).
 */
@Composable
private fun NowPlayingPanel(player: RandomPlayerState) {
    val nv by NowVoicingBus.state.collectAsState()
    val statusLabel = when {
        player.playing -> "NOW PLAYING"
        player.paused -> "PAUSED"
        else -> "QUEUE"
    }
    Surface(
        color = GrammarVisuals.NowVoicingPanel.Background,
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(
            GrammarVisuals.NowVoicingPanel.BorderWidth,
            GrammarVisuals.NowVoicingPanel.Border
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(modifier = Modifier.weight(1f)) {
                com.sponic.langbang.ui.common.NowVoicingBody(
                    pinned = nv,
                    live = nv,
                    statusText = "$statusLabel · ${player.position} / ${player.queueSize}",
                    idlePlaceholder = "—"
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    TransportButton(
                        icon = Icons.Filled.Replay,
                        label = "Start over",
                        onClick = { player.restart() }
                    )
                    TransportButton(
                        icon = Icons.Filled.SkipPrevious,
                        label = "Rewind",
                        onClick = { player.rewind() }
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    TransportButton(
                        icon = if (player.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        label = if (player.playing) "Pause" else "Play",
                        onClick = {
                            if (player.playing) player.pause() else player.resume()
                        }
                    )
                    TransportButton(
                        icon = Icons.Filled.Stop,
                        label = "Stop",
                        onClick = { player.stop() }
                    )
                }
            }
        }
    }
}

@Composable
private fun TransportButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(36.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = LbColors.Primary,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
private fun InlineRow(label: String, content: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = LbColors.Label,
            modifier = Modifier.width(60.dp)
        )
        content()
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
        label = {
            Text(
                label,
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
            )
        },
        modifier = Modifier.height(30.dp),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = LbColors.ChipIdle,
            labelColor = LbColors.TextSecondary,
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = LbColors.OnPrimary,
            disabledContainerColor = LbColors.ChipIdle.copy(alpha = 0.45f),
            disabledLabelColor = LbColors.TextMuted
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
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
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
    "1sg" -> "ja"
    "2sg" -> "ty"
    "3sg" -> "on/ona"
    "1pl" -> "my"
    "2pl" -> "wy"
    "3pl" -> "oni/one"
    else -> key
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
    // Multi-token "must contain": split the input on whitespace and require every
    // token to appear (substring match) in the sentence. Lets the user narrow with
    // e.g. "kawa duża" — both must appear, in any order, in any morphological form.
    val mustContainTokens = config.mustContainWord
        .lowercase()
        .split(Regex("\\s+"))
        .filter { it.isNotEmpty() }
        .takeIf { it.isNotEmpty() }
    val prepFilter = config.prepositions.toUiPrepositions()

    fun passesPreps(pl: String): Boolean {
        val tokens = pl.lowercase().split(Regex("[^\\p{L}]+"))
        val tracked = tokens.filter { it in RandomConfig.PREPOSITIONS }
        val allowsNone = RandomConfig.PREPOSITION_NONE in prepFilter
        if (tracked.isEmpty()) return allowsNone
        return tracked.any { it in prepFilter }
    }
    fun passesMustContain(pl: String): Boolean {
        if (mustContainTokens == null) return true
        val hay = pl.lowercase()
        return mustContainTokens.all { hay.contains(it) }
    }

    var total = 0
    val verbLesson = app.lessonRepo.lesson2()

    fun countForTense(tense: String, formsFor: (com.sponic.langbang.data.model.VerbEntry) -> Map<String, String>) {
        verbLesson.verbs.forEach { verb ->
            val src = formsFor(verb)
            if (src.isEmpty()) return@forEach
            val allowedForms = src
                .filterKeys { it in config.personKeys }
                .values.filter { it.isNotBlank() }.map { it.lowercase() }.toSet()
            if (allowedForms.isEmpty()) return@forEach
            total += app.lessonRepo.sentencesFor(verb.lemma, tense).count { s ->
                val tokens = s.pl.lowercase().split(Regex("[^\\p{L}]+"))
                tokens.any { it.isNotEmpty() && it in allowedForms } &&
                    passesPreps(s.pl) && passesMustContain(s.pl)
            }
        }
    }

    if (VerbSentenceStore.TENSE_PRESENT in config.tenses) {
        countForTense(VerbSentenceStore.TENSE_PRESENT) { it.forms }
    }
    if (VerbSentenceStore.TENSE_PAST in config.tenses) {
        countForTense(VerbSentenceStore.TENSE_PAST) { it.past_forms.orEmpty() }
    }

    // Same gating set the player uses: only verb forms for the selected persons ×
    // tenses count. Keeps the preflight tally honest for adj/adv sentences.
    val allowedVerbForms = mutableSetOf<String>()
    verbLesson.verbs.forEach { verb ->
        if (VerbSentenceStore.TENSE_PRESENT in config.tenses) {
            verb.forms.filterKeys { it in config.personKeys }.values.forEach {
                if (it.isNotBlank()) allowedVerbForms += it.lowercase()
            }
        }
        if (VerbSentenceStore.TENSE_PAST in config.tenses) {
            verb.past_forms.orEmpty().filterKeys { it in config.personKeys }.values.forEach {
                if (it.isNotBlank()) allowedVerbForms += it.lowercase()
            }
        }
    }
    fun matchesAllowedVerb(pl: String): Boolean {
        val tokens = pl.lowercase().split(Regex("[^\\p{L}]+"))
        return tokens.any { it.isNotEmpty() && it in allowedVerbForms }
    }

    val missingAdj = mutableListOf<String>()
    val mustContainActive = mustContainTokens != null
    if (config.adjectiveMode != IncludeMode.OFF) {
        val adjLesson = app.lessonRepo.lesson3()
        adjLesson.adjectives.forEach { adj ->
            val sentences = app.lessonRepo.adjectiveSentencesFor(adj.lemma)
                .filter { matchesAllowedVerb(it.pl) }
            val eligible = sentences.count { passesPreps(it.pl) && passesMustContain(it.pl) }
            if (!mustContainActive && eligible == 0 && config.adjectiveMode == IncludeMode.ALL) {
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
                .filter { matchesAllowedVerb(it.pl) }
            total += sentences.count { passesPreps(it.pl) && passesMustContain(it.pl) }
        }
    }

    total += app.lessonRepo.lesson5().groups.sumOf { group ->
        group.sentences.count { s -> passesPreps(s.pl) && passesMustContain(s.pl) }
    }

    val hits = if (mustContainTokens != null) total else null
    return Preflight(eligibleCount = total, mustContainHits = hits, missingAdjectives = missingAdj)
}

private fun Set<String>.toUiPrepositions(): Set<String> {
    val allowed = RandomConfig.PREPOSITIONS.toSet() + RandomConfig.PREPOSITION_NONE
    val cleaned = intersect(allowed)
    return cleaned.ifEmpty { setOf(RandomConfig.PREPOSITION_NONE) }
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
private suspend fun regenerateForMustContain(app: LangbangApplication, words: String) {
    if (words.isEmpty()) return
    // Lemma lookup uses the first token; the rest narrow the must-contain filter at
    // playback time but don't usefully steer verb selection here.
    val firstToken = words.split(Regex("\\s+")).firstOrNull { it.isNotEmpty() } ?: return
    val verbLesson = app.lessonRepo.lesson2()
    val verb = verbLesson.verbs.firstOrNull { it.lemma.contains(firstToken, ignoreCase = true) }
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CompactPreflight(
    preflight: Preflight,
    busy: Boolean,
    isOnline: Boolean,
    onGenerateMissingAdjectives: () -> Unit,
    onRegenerateForWord: () -> Unit
) {
    val hits = preflight.mustContainHits
    val showMustContainCta = hits != null && hits < MUST_CONTAIN_HIT_THRESHOLD
    val showMissingAdjCta = preflight.missingAdjectives.isNotEmpty()
    val showEmpty = preflight.eligibleCount == 0
    if (!showEmpty && hits == null && !showMissingAdjCta) return

    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (showEmpty) {
            Text(
                "No phrases match — loosen filters.",
                fontSize = 11.sp,
                color = LbColors.Danger
            )
        }
        if (hits != null) {
            Text(
                "“$hits” cached hits",
                fontSize = 11.sp,
                color = if (showMustContainCta) LbColors.Danger else LbColors.TextSecondary
            )
            if (showMustContainCta) {
                Button(
                    onClick = onRegenerateForWord,
                    enabled = !busy && isOnline,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = LbColors.SurfaceTint,
                        contentColor = LbColors.Label
                    ),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                ) {
                    Text(if (busy) "Generating…" else "Generate more", fontSize = 11.sp)
                }
            }
        }
        if (showMissingAdjCta) {
            Text(
                "${preflight.missingAdjectives.size} adj. missing " +
                    "(${preflight.missingAdjectives.take(3).joinToString(", ")}" +
                    if (preflight.missingAdjectives.size > 3) ", …)" else ")",
                fontSize = 11.sp,
                color = LbColors.Danger
            )
            Button(
                onClick = onGenerateMissingAdjectives,
                enabled = !busy && isOnline,
                colors = ButtonDefaults.buttonColors(
                    containerColor = LbColors.SurfaceTint,
                    contentColor = LbColors.Label
                ),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
            ) {
                Text(if (busy) "Generating…" else "Generate missing", fontSize = 11.sp)
            }
        }
        if (!isOnline && (showMustContainCta || showMissingAdjCta)) {
            Text("Offline — cached only", fontSize = 10.sp, color = LbColors.Danger)
        }
    }
}
