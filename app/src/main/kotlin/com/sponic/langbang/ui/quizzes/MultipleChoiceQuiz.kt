package com.sponic.langbang.ui.quizzes

import com.sponic.langbang.ui.theme.LbColors

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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

/**
 * Generic multiple-choice quiz runner. Takes a fixed [questions] list, shows them
 * one at a time with 4 shuffled options (correct + 3 distractors), tracks score,
 * and renders an end screen with retry / exit.
 *
 * Deliberately UI-only — no playback, no audio, no Gemini calls. Pure text-tap so
 * a quiz session is portable to any quiet context (parked car, library, etc) and
 * the user doesn't need headphones.
 *
 * Options grid: 2 columns × 2 rows on the tablet width. Tap → highlights green if
 * correct, red if wrong; reveal mode pauses 1.2s before auto-advancing so the
 * learner sees the right answer when they miss. Caller controls reshuffle by
 * passing fresh [questions] on each invocation.
 */
@Composable
fun MultipleChoiceQuiz(
    title: String,
    questions: List<QuizQuestion>,
    onExit: () -> Unit
) {
    if (questions.isEmpty()) {
        EmptyQuiz(title = title, onExit = onExit)
        return
    }

    // Shuffled once per mount; "Retry" remounts via a key change in the caller.
    val ordered = remember { questions.shuffled() }
    var index by remember { mutableStateOf(0) }
    var correctCount by remember { mutableStateOf(0) }
    var pickedIndex by remember { mutableStateOf<Int?>(null) }
    var pickedCorrect by remember { mutableStateOf<Boolean?>(null) }

    val q = ordered.getOrNull(index)
    if (q == null) {
        QuizSummary(
            title = title,
            score = correctCount,
            total = ordered.size,
            onRetry = {
                index = 0
                correctCount = 0
                pickedIndex = null
                pickedCorrect = null
            },
            onExit = onExit
        )
        return
    }

    // Per-question stable shuffle of options so tapping doesn't re-roll positions
    // when state updates trigger recomposition.
    val options = remember(index) { (q.distractors + q.correct).shuffled() }
    val correctOption = q.correct

    // Auto-advance after the reveal pause when the user picks an option.
    LaunchedEffect(pickedIndex) {
        if (pickedIndex != null) {
            kotlinx.coroutines.delay(if (pickedCorrect == true) 700L else 1500L)
            if (pickedCorrect == true) correctCount += 1
            index += 1
            pickedIndex = null
            pickedCorrect = null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ── Header: title, score, exit ─────────────────────────────────────
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                color = LbColors.Primary, modifier = Modifier.weight(1f))
            Text("${index + 1} / ${ordered.size}",
                fontSize = 13.sp, color = LbColors.Label)
            Spacer(Modifier.width(12.dp))
            OutlinedButton(
                onClick = onExit,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text("Exit", fontSize = 12.sp)
            }
        }

        LinearProgressIndicator(
            progress = { (index + 1).toFloat() / ordered.size },
            modifier = Modifier.fillMaxWidth().height(4.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = LbColors.SurfaceTint
        )

        // ── Prompt ─────────────────────────────────────────────────────────
        Column(horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()) {
            if (q.context.isNotBlank()) {
                Text(q.context, fontSize = 12.sp,
                    color = LbColors.Label, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
            }
            Text(
                q.prompt, fontSize = 32.sp, fontWeight = FontWeight.Bold,
                color = LbColors.TextPrimary, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // ── 2x2 options grid ──────────────────────────────────────────────
        val pairs = options.chunked(2)
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            pairs.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()) {
                    row.forEach { opt ->
                        val i = options.indexOf(opt)
                        val tapped = pickedIndex == i
                        val isCorrect = opt == correctOption
                        val bg = when {
                            tapped && isCorrect -> LbColors.SuccessSoft
                            tapped && !isCorrect -> LbColors.DangerSoft
                            pickedIndex != null && isCorrect -> LbColors.SuccessSoft // reveal the right one even when wrong picked
                            else -> LbColors.Sheet
                        }
                        val border = when {
                            tapped && isCorrect -> LbColors.Success
                            tapped && !isCorrect -> LbColors.Danger
                            else -> LbColors.SurfaceTint
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(72.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(bg)
                                .border(2.dp, border, RoundedCornerShape(12.dp))
                                .clickable(enabled = pickedIndex == null) {
                                    pickedIndex = i
                                    pickedCorrect = isCorrect
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                opt, fontSize = 22.sp, fontWeight = FontWeight.SemiBold,
                                color = LbColors.TextPrimary, textAlign = TextAlign.Center
                            )
                        }
                    }
                    // Pad odd row out to keep the 2-col grid alignment.
                    if (row.size == 1) Box(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun EmptyQuiz(title: String, onExit: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
            color = LbColors.Primary)
        Text(
            "No questions available for this quiz yet — the data set is empty or " +
                "the sentence cache hasn't downloaded. Try again after the top-bar " +
                "banner finishes syncing.",
            fontSize = 13.sp, color = LbColors.TextSecondary
        )
        Button(onClick = onExit) { Text("Back") }
    }
}

@Composable
private fun QuizSummary(
    title: String,
    score: Int,
    total: Int,
    onRetry: () -> Unit,
    onExit: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
            color = LbColors.Primary)
        Spacer(Modifier.height(16.dp))
        Text("$score / $total", fontSize = 56.sp, fontWeight = FontWeight.Bold,
            color = if (score * 2 >= total) LbColors.Success else LbColors.Danger)
        val pct = if (total > 0) score * 100 / total else 0
        Text("$pct% correct", fontSize = 16.sp, color = LbColors.TextSecondary)
        Spacer(Modifier.height(16.dp))
        Surface(color = LbColors.Sheet, shape = RoundedCornerShape(12.dp)) {
            Row(modifier = Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onRetry,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Retry (new order)")
                }
                OutlinedButton(onClick = onExit) { Text("Back to quizzes") }
            }
        }
    }
}
