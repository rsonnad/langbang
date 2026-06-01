package com.sponic.langbang.ui.quizzes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.sponic.langbang.ui.theme.LbColors
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PracticeQuiz(
    app: LangbangApplication,
    onExit: () -> Unit
) {
    var scope by remember { mutableStateOf(PracticeScope()) }
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

    LaunchedEffect(scope.auto) {
        if (!scope.auto) {
            stageIndex = AutoPracticeStage.MIXED.ordinal
            recent = emptyList()
        } else if (stageIndex == AutoPracticeStage.MIXED.ordinal) {
            stageIndex = AutoPracticeStage.CORE.ordinal
            recent = emptyList()
        }
    }

    fun record(ok: Boolean) {
        recent = (recent + ok).takeLast(20)
        if (!scope.auto || recent.size < 12) return
        val rate = recent.count { it }.toFloat() / recent.size
        when {
            rate >= 0.88f && stageIndex < AutoPracticeStage.entries.lastIndex -> {
                stageIndex += 1
                recent = emptyList()
                runId += 1
            }
            rate <= 0.66f && stageIndex > 0 -> {
                stageIndex -= 1
                recent = emptyList()
                runId += 1
            }
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
                recent = emptyList()
                runId += 1
            },
            onExit = onExit
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    scope.title,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = LbColors.Primary
                )
                Text(
                    if (scope.auto) stage.label else "Manual filters",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (scope.auto) LbColors.Warning else LbColors.TextSecondary
                )
            }
            Text("${index + 1} / ${queue.size}", fontSize = 13.sp, color = LbColors.Label)
            Spacer(Modifier.width(10.dp))
            IconButton(
                onClick = {
                    recent = emptyList()
                    runId += 1
                },
                modifier = Modifier.size(34.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Restart", tint = LbColors.Primary)
            }
            OutlinedButton(
                onClick = onExit,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text("Exit", fontSize = 12.sp)
            }
        }

        LinearProgressIndicator(
            progress = { (index + 1).toFloat() / queue.size },
            modifier = Modifier.fillMaxWidth().height(4.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = LbColors.SurfaceTint
        )

        PracticeScopeControls(
            scope = scope,
            onChange = {
                scope = it
                recent = emptyList()
                runId += 1
            }
        )

        PracticeCard(current)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = {
                    correctCount += 1
                    record(true)
                    index += 1
                },
                modifier = Modifier.weight(1f).height(58.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = LbColors.Success)
            ) {
                Icon(Icons.Default.Check, contentDescription = null, tint = Color.White)
                Spacer(Modifier.width(8.dp))
                Text("Got it", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Button(
                onClick = {
                    missCount += 1
                    record(false)
                    val burst = PracticeGenerators.missBurst(app.lessonRepo, scope, stage, current)
                    queue = queue.toMutableList().also { list ->
                        list.addAll((index + 1).coerceAtMost(list.size), burst)
                    }
                    index += 1
                },
                modifier = Modifier.weight(1f).height(58.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = LbColors.Warning)
            ) {
                Icon(Icons.Default.Close, contentDescription = null, tint = Color.White)
                Spacer(Modifier.width(8.dp))
                Text("Missed it", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }

        Text(
            "Orange X inserts exact repeat plus four variations before returning to the queue.",
            fontSize = 11.sp,
            color = LbColors.TextMuted
        )
    }
}

@Composable
private fun PracticeCard(item: PracticeItem) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    item.kind.label,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = LbColors.Label
                )
                Text(
                    item.context,
                    fontSize = 12.sp,
                    color = LbColors.TextMuted,
                    textAlign = TextAlign.End
                )
            }
            Text(
                item.prompt,
                fontSize = 34.sp,
                lineHeight = 38.sp,
                fontWeight = FontWeight.Bold,
                color = LbColors.TextPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(LbColors.PrimarySoft)
                    .border(1.dp, LbColors.ChipBorder, RoundedCornerShape(12.dp))
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    item.answerPl,
                    fontSize = 31.sp,
                    lineHeight = 35.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = LbColors.Primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Text(
                "Mark whether you remembered it before looking closely.",
                fontSize = 12.sp,
                color = LbColors.TextSecondary
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PracticeScopeControls(
    scope: PracticeScope,
    onChange: (PracticeScope) -> Unit
) {
    Surface(
        color = LbColors.SurfaceRaised,
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, LbColors.SurfaceTint),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Filters",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = LbColors.Label,
                    modifier = Modifier.width(76.dp)
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Types",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = LbColors.Label,
                    modifier = Modifier.width(76.dp)
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Persons",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = LbColors.Label,
                    modifier = Modifier.width(76.dp)
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Tense",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = LbColors.Label,
                    modifier = Modifier.width(76.dp)
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
            }
        }
    }
}

@Composable
private fun PracticeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                label,
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
            )
        },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = LbColors.ChipIdle,
            labelColor = LbColors.TextSecondary,
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = Color.White
        ),
        modifier = Modifier.height(30.dp)
    )
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
        OutlinedButton(onClick = onExit) { Text("Back") }
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
