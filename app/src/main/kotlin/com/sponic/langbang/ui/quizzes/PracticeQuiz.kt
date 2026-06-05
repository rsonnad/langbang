package com.sponic.langbang.ui.quizzes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sponic.langbang.LangbangApplication
import com.sponic.langbang.data.VerbSentenceStore
import com.sponic.langbang.data.model.TokenPair
import com.sponic.langbang.domain.AudioActivityBus
import com.sponic.langbang.domain.NowVoicingBus
import com.sponic.langbang.domain.PlaybackController
import com.sponic.langbang.domain.awaitAudioPlayback
import com.sponic.langbang.domain.ensureCachedAudio
import com.sponic.langbang.integrations.AzureTtsClient
import com.sponic.langbang.ui.common.rememberDelayedTranslationVisible
import com.sponic.langbang.ui.theme.LbColors
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PracticeQuiz(
    app: LangbangApplication,
    initialScope: PracticeScope = PracticeScope(),
    title: String? = null,
    onExit: () -> Unit
) {
    var scope by remember(initialScope) { mutableStateOf(initialScope) }
    var stageIndex by remember { mutableIntStateOf(0) }
    var recent by remember { mutableStateOf<List<Boolean>>(emptyList()) }
    var runId by remember { mutableIntStateOf(0) }
    val stage = AutoPracticeStage.entries[stageIndex.coerceIn(0, AutoPracticeStage.entries.lastIndex)]
    val items = remember(scope, stage, runId) {
        PracticeGenerators.buildItems(app.lessonRepo, scope, stage)
    }
    var queue by remember(items) { mutableStateOf(items) }
    var index by remember(items) { mutableIntStateOf(0) }
    var correctCount by remember(items) { mutableIntStateOf(0) }
    var missCount by remember(items) { mutableIntStateOf(0) }
    var expandedTargets by remember(items) { mutableStateOf<Set<String>>(emptySet()) }

    LaunchedEffect(scope.auto) {
        if (!scope.auto) {
            stageIndex = AutoPracticeStage.MIXED.ordinal
            recent = emptyList()
        } else if (stageIndex == AutoPracticeStage.MIXED.ordinal) {
            stageIndex = AutoPracticeStage.CORE.ordinal
            recent = emptyList()
        }
    }

    fun record(ok: Boolean): Boolean {
        recent = (recent + ok).takeLast(12)
        if (!scope.auto || recent.size < 6) return false
        val rate = recent.count { it }.toFloat() / recent.size
        return when {
            rate >= 0.84f && stageIndex < AutoPracticeStage.entries.lastIndex -> {
                stageIndex += 1
                recent = emptyList()
                runId += 1
                true
            }
            rate <= 0.66f && stageIndex > 0 -> {
                stageIndex -= 1
                recent = emptyList()
                runId += 1
                true
            }
            else -> false
        }
    }

    if (items.isEmpty()) {
        EmptyPractice(scope = scope, onScopeChange = { scope = it }, onExit = onExit)
        return
    }

    val current = queue.getOrNull(index)
    if (current == null) {
        PracticeSummary(
            correct = correctCount,
            misses = missCount,
            total = queue.size,
            onRetry = {
                app.audioPlayer.stop()
                PlaybackController.unregister()
                NowVoicingBus.clear()
                recent = emptyList()
                runId += 1
            },
            onExit = {
                app.audioPlayer.stop()
                PlaybackController.unregister()
                NowVoicingBus.clear()
                onExit()
            }
        )
        return
    }
    var revealed by remember { mutableStateOf(false) }
    var missedRevealed by remember { mutableStateOf(false) }
    var filtersExpanded by remember { mutableStateOf(false) }
    val playbackScope = rememberCoroutineScope()
    var answerPlaybackJob by remember { mutableStateOf<Job?>(null) }
    var answerVoiceBadgeVisible by remember { mutableStateOf(false) }
    var answerVoiceBadgeRequest by remember { mutableIntStateOf(0) }
    val answerPlaybackToken = remember { AtomicInteger(0) }
    val activeAnswerId = remember { AtomicReference<String?>(null) }

    LaunchedEffect(current.id) {
        filtersExpanded = false
    }

    LaunchedEffect(answerVoiceBadgeRequest) {
        val request = answerVoiceBadgeRequest
        if (request > 0) {
            delay(2800L)
            if (answerVoiceBadgeRequest == request) {
                answerVoiceBadgeVisible = false
            }
        }
    }

    fun stopPracticeAudio() {
        answerPlaybackToken.incrementAndGet()
        activeAnswerId.set(null)
        answerPlaybackJob?.cancel()
        answerPlaybackJob = null
        answerVoiceBadgeRequest += 1
        answerVoiceBadgeVisible = false
        app.audioPlayer.stop()
        NowVoicingBus.clear()
    }

    fun resetCardState() {
        revealed = false
        missedRevealed = false
        filtersExpanded = false
        stopPracticeAudio()
    }

    fun playAnswer(item: PracticeItem) {
        val token = answerPlaybackToken.incrementAndGet()
        val itemId = item.id
        activeAnswerId.set(itemId)
        answerPlaybackJob?.cancel()
        app.audioPlayer.stop()
        NowVoicingBus.clear()
        AudioActivityBus.markActive()
        answerVoiceBadgeRequest += 1
        answerVoiceBadgeVisible = true
        answerPlaybackJob = playbackScope.launch {
            val thisJob = coroutineContext[Job]
            val visibleStartedAt = System.currentTimeMillis()
            try {
                val file = app.ensureCachedAudio(
                    item.answerPl,
                    AzureTtsClient.LOCALE_PL,
                    AzureTtsClient.PL_PL_F
                )
                val stillCurrent = token == answerPlaybackToken.get() &&
                    activeAnswerId.get() == itemId &&
                    isActive
                if (file != null && stillCurrent) {
                    app.awaitAudioPlayback(file)
                } else {
                    AudioActivityBus.markInactiveSoon()
                }
            } finally {
                if (answerPlaybackJob == thisJob) {
                    val remainingVisibleMs = 1800L - (System.currentTimeMillis() - visibleStartedAt)
                    if (remainingVisibleMs > 0) delay(remainingVisibleMs)
                    answerPlaybackJob = null
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose { stopPracticeAudio() }
    }

    fun revealCurrent() {
        filtersExpanded = false
        revealed = true
        missedRevealed = false
        playAnswer(current)
    }

    fun revealMissedCurrent() {
        filtersExpanded = false
        revealed = true
        missedRevealed = true
        playAnswer(current)
    }

    fun advanceMissedCurrent() {
        resetCardState()
        missCount += 1
        val stageChanged = record(false)
        if (stageChanged) return
        val burst = PracticeGenerators.missBurst(app.lessonRepo, scope, stage, current)
        queue = queue.toMutableList().also { list ->
            list.addAll((index + 1).coerceAtMost(list.size), burst)
        }
        index += 1
    }

    fun markGotIt() {
        if (missedRevealed) {
            advanceMissedCurrent()
            return
        }
        val expansionKey = current.targetLemma ?: current.answerPl
        val expansion = if (scope.auto && expansionKey !in expandedTargets) {
            PracticeGenerators.successExpansion(app.lessonRepo, scope, stage, current)
        } else {
            emptyList()
        }
        resetCardState()
        correctCount += 1
        val stageChanged = record(true)
        if (stageChanged) return
        if (expansion.isNotEmpty()) {
            expandedTargets = expandedTargets + expansionKey
            queue = queue.toMutableList().also { list ->
                list.addAll((index + 1).coerceAtMost(list.size), expansion)
            }
        }
        index += 1
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(10.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 52.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    title ?: scope.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = LbColors.Primary,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    if (scope.auto) stage.label else "Manual",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (scope.auto) LbColors.Warning else LbColors.TextSecondary
                )
                Text("${index + 1}/${queue.size}", fontSize = 11.sp, color = LbColors.Label)
                CompactHeaderButton(
                    label = null,
                    onClick = {
                        resetCardState()
                        recent = emptyList()
                        runId += 1
                    }
                )
                EndQuizHeaderButton(
                    onClick = {
                        stopPracticeAudio()
                        onExit()
                    }
                )
                if (answerVoiceBadgeVisible) {
                    PracticeVoicingBadge()
                    Spacer(Modifier.width(8.dp))
                }
                FilterDisclosureButton(
                    expanded = filtersExpanded,
                    onClick = { filtersExpanded = !filtersExpanded }
                )
            }
            if (filtersExpanded) {
                PracticeScopeControls(
                    scope = scope,
                    onChange = {
                        resetCardState()
                        scope = it
                        recent = emptyList()
                        runId += 1
                    }
                )
            }

            PracticeCard(
                item = current,
                revealed = revealed,
                onHearAnswer = { playAnswer(current) }
            )
        }

        PracticeActionBar(
            revealed = revealed,
            onReveal = ::revealCurrent,
            onGotIt = ::markGotIt,
            onMissedIt = {
                if (revealed || missedRevealed) {
                    advanceMissedCurrent()
                } else {
                    revealMissedCurrent()
                }
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 4.dp, bottom = 4.dp)
        )
    }
}

@Composable
private fun PracticeVoicingBadge() {
    Surface(
        color = LbColors.Audio,
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, LbColors.AudioBright.copy(alpha = 0.55f))
    ) {
        Text(
            "Voicing",
            color = Color.White,
            fontSize = 11.sp,
            lineHeight = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun PracticeActionBar(
    revealed: Boolean,
    onReveal: () -> Unit,
    onGotIt: () -> Unit,
    onMissedIt: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = LbColors.Canvas.copy(alpha = 0.94f),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, LbColors.SurfaceTint),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (revealed) {
                Spacer(Modifier.width(106.dp))
            } else {
                RevealAnswerButton(
                    label = "Show",
                    onClick = onReveal,
                    modifier = Modifier.width(106.dp)
                )
            }
            Spacer(Modifier.width(28.dp))
            GradeIconButton(
                icon = Icons.Default.Check,
                contentDescription = "Got it",
                color = LbColors.Success,
                onClick = onGotIt
            )
            Spacer(Modifier.width(28.dp))
            GradeIconButton(
                icon = Icons.Default.Close,
                contentDescription = "Missed it",
                color = LbColors.Stop,
                onClick = onMissedIt
            )
        }
    }
}

@Composable
private fun EndQuizHeaderButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .height(28.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(LbColors.Stop)
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp),
        contentAlignment = Alignment.Center
    ) {
        Text("End quiz", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
    }
}

@Composable
private fun CompactHeaderButton(label: String?, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .height(24.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(Color.White)
            .border(1.dp, LbColors.SurfaceTint, RoundedCornerShape(7.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = if (label == null) 5.dp else 8.dp),
        contentAlignment = Alignment.Center
    ) {
        if (label == null) {
            Icon(
                Icons.Default.Refresh,
                contentDescription = "Restart",
                tint = LbColors.Primary,
                modifier = Modifier.size(14.dp)
            )
        } else {
            Text(label, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = LbColors.Primary)
        }
    }
}

@Composable
private fun FilterDisclosureButton(expanded: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .height(28.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (expanded) LbColors.PrimarySoft else Color.White)
            .border(
                1.dp,
                if (expanded) LbColors.Primary.copy(alpha = 0.45f) else LbColors.SurfaceTint,
                RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            if (expanded) "Hide filters" else "Filters",
            fontSize = 10.sp,
            fontWeight = FontWeight.ExtraBold,
            color = if (expanded) LbColors.Primary else LbColors.TextPrimary
        )
    }
}

@Composable
private fun PracticeCard(
    item: PracticeItem,
    revealed: Boolean,
    onHearAnswer: () -> Unit
) {
    val contextVisible = rememberDelayedTranslationVisible("${item.id}:${item.context}")
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "${item.kind.label} · ${item.context}",
                fontSize = 10.sp,
                lineHeight = 11.sp,
                fontWeight = FontWeight.Medium,
                color = if (contextVisible) LbColors.TextMuted else Color.Transparent,
                textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                item.prompt,
                fontSize = 28.sp,
                lineHeight = 31.sp,
                fontWeight = FontWeight.Bold,
                color = LbColors.TextPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            if (revealed) {
                Surface(
                    color = LbColors.PrimarySoft,
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, LbColors.ChipBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    PracticeAnswer(
                        item = item,
                        onHearAnswer = onHearAnswer,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun RevealAnswerButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = LbColors.PrimarySoft,
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, LbColors.ChipBorder),
        modifier = modifier
            .height(42.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = label,
                tint = LbColors.Primary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(5.dp))
            Text(
                label,
                fontSize = 13.sp,
                lineHeight = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                color = LbColors.Primary
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PracticeAnswer(
    item: PracticeItem,
    onHearAnswer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pairs = remember(item) { answerGlossPairs(item) }
    val plSize = when {
        pairs.size <= 2 -> 36.sp
        pairs.size <= 5 -> 31.sp
        else -> 26.sp
    }
    val plLine = when {
        pairs.size <= 2 -> 40.sp
        pairs.size <= 5 -> 35.sp
        else -> 30.sp
    }
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        InlineAnswerPlayButton(onClick = onHearAnswer)
        pairs.forEach { pair ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    pair.pl,
                    fontSize = plSize,
                    lineHeight = plLine,
                    fontWeight = FontWeight.Bold,
                    color = LbColors.Primary,
                    textAlign = TextAlign.Center
                )
                Text(
                    pair.en,
                    fontSize = 12.sp,
                    lineHeight = 13.sp,
                    fontWeight = FontWeight.Normal,
                    color = LbColors.TextSecondary,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun InlineAnswerPlayButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .height(48.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.PlayArrow,
            contentDescription = "Hear answer",
            tint = LbColors.Primary,
            modifier = Modifier.size(28.dp)
        )
    }
}

@Composable
private fun GradeIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    color: Color,
    onClick: () -> Unit
) {
    Surface(
        color = color.copy(alpha = 0.10f),
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.34f)),
        modifier = Modifier
            .size(42.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = contentDescription, tint = color, modifier = Modifier.size(22.dp))
        }
    }
}

private fun answerGlossPairs(item: PracticeItem): List<TokenPair> {
    item.words?.takeIf { it.isNotEmpty() }?.let { return it }
    val gloss = item.literal?.takeIf { it.isNotBlank() } ?: item.answerEn
    val plTokens = item.answerPl.split(Regex("\\s+")).filter { it.isNotBlank() }
    val enTokens = gloss.split(Regex("\\s+")).filter { it.isNotBlank() }
    return if (plTokens.size == enTokens.size && plTokens.isNotEmpty()) {
        plTokens.zip(enTokens).map { (pl, en) -> TokenPair(pl, en) }
    } else {
        listOf(TokenPair(item.answerPl, gloss))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PracticeScopeControls(
    scope: PracticeScope,
    onChange: (PracticeScope) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = LbColors.SurfaceRaised,
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, LbColors.SurfaceTint),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 3.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                CompactPracticeGroup(label = "Mode") {
                    PracticeChip(
                        label = "Auto",
                        selected = scope.auto,
                        onClick = { onChange(scope.copy(auto = true)) }
                    )
                    PracticeChip(
                        label = "Manual",
                        selected = !scope.auto,
                        onClick = { onChange(scope.copy(auto = false)) }
                    )
                }
                CompactPracticeGroup(label = "Types") {
                    PracticeWordType.entries.forEach { type ->
                        PracticeChip(
                            label = type.label,
                            selected = type in scope.wordTypes,
                            onClick = {
                                val next = if (type in scope.wordTypes) {
                                    scope.wordTypes - type
                                } else {
                                    scope.wordTypes + type
                                }
                                onChange(scope.copy(wordTypes = next.ifEmpty { setOf(type) }))
                            }
                        )
                    }
                }
                CompactPracticeGroup(label = "Tense") {
                    PracticeChip(
                        label = "Present",
                        selected = VerbSentenceStore.TENSE_PRESENT in scope.tenses,
                        onClick = {
                            val next = if (VerbSentenceStore.TENSE_PRESENT in scope.tenses) {
                                scope.tenses - VerbSentenceStore.TENSE_PRESENT
                            } else {
                                scope.tenses + VerbSentenceStore.TENSE_PRESENT
                            }
                            onChange(scope.copy(tenses = next.ifEmpty { setOf(VerbSentenceStore.TENSE_PRESENT) }))
                        }
                    )
                    PracticeChip(
                        label = "Past",
                        selected = VerbSentenceStore.TENSE_PAST in scope.tenses,
                        onClick = {
                            val next = if (VerbSentenceStore.TENSE_PAST in scope.tenses) {
                                scope.tenses - VerbSentenceStore.TENSE_PAST
                            } else {
                                scope.tenses + VerbSentenceStore.TENSE_PAST
                            }
                            onChange(scope.copy(tenses = next.ifEmpty { setOf(VerbSentenceStore.TENSE_PAST) }))
                        }
                    )
                }
                CompactPracticeGroup(label = "People") {
                    PersonKeys.forEach { key ->
                        PracticeChip(
                            label = personChipLabel(key),
                            selected = key in scope.personKeys,
                            onClick = {
                                val next = if (key in scope.personKeys) {
                                    scope.personKeys - key
                                } else {
                                    scope.personKeys + key
                                }
                                onChange(scope.copy(personKeys = next.ifEmpty { setOf(key) }))
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CompactPracticeGroup(
    label: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(7.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, LbColors.SurfaceTint),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                label.uppercase(Locale.US),
                fontSize = 7.sp,
                fontWeight = FontWeight.Bold,
                color = LbColors.TextMuted
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                content()
            }
        }
    }
}

@Composable
private fun PracticeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .height(18.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary else LbColors.ChipIdle)
            .border(
                width = 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else LbColors.ChipBorder,
                shape = RoundedCornerShape(999.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            fontSize = 8.sp,
            lineHeight = 8.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) Color.White else LbColors.TextSecondary
        )
    }
}

@Composable
private fun EmptyPractice(
    scope: PracticeScope,
    onScopeChange: (PracticeScope) -> Unit,
    onExit: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Practice", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = LbColors.Primary)
        Text(
            "No cards match these filters. Turn on at least one word type, person, and tense.",
            fontSize = 13.sp,
            color = LbColors.TextSecondary
        )
        PracticeScopeControls(scope = scope, onChange = onScopeChange)
        OutlinedButton(onClick = onExit) { Text("Back to quizzes") }
    }
}

@Composable
private fun PracticeSummary(
    correct: Int,
    misses: Int,
    total: Int,
    onRetry: () -> Unit,
    onExit: () -> Unit
) {
    val attempts = correct + misses
    val pct = if (attempts == 0) 0 else (correct * 100f / attempts).toInt()
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Practice complete", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = LbColors.Primary)
        Text("$pct%", fontSize = 56.sp, fontWeight = FontWeight.Bold, color = if (pct >= 80) LbColors.Success else LbColors.Warning)
        Text(
            "${formatInt(correct)} got it · ${formatInt(misses)} missed · ${formatInt(total)} cards shown",
            fontSize = 14.sp,
            color = LbColors.TextSecondary
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Practice again", color = Color.White)
            }
            OutlinedButton(onClick = onExit) { Text("Back to quizzes") }
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

private fun formatInt(n: Int): String = "%,d".format(Locale.US, n)

private val PersonKeys = listOf("1sg", "2sg", "3sg", "1pl", "2pl", "3pl")
