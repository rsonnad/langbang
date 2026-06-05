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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.sponic.langbang.LangbangApplication
import com.sponic.langbang.data.VerbSentenceStore
import com.sponic.langbang.data.model.SentenceExample
import com.sponic.langbang.data.model.TokenPair
import com.sponic.langbang.domain.NowVoicing
import com.sponic.langbang.domain.NowVoicingBus
import com.sponic.langbang.domain.PlaybackController
import com.sponic.langbang.integrations.AzureTtsClient
import com.sponic.langbang.ui.common.rememberDelayedTranslationVisible
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

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
    app: LangbangApplication,
    title: String,
    questions: List<QuizQuestion>,
    onExit: () -> Unit
) {
    if (questions.isEmpty()) {
        EmptyQuiz(title = title, onExit = onExit)
        return
    }

    val quizKey = remember(title, questions) { title to questions }
    var runId by remember(quizKey) { mutableStateOf(0) }
    val ordered = remember(quizKey, runId) { questions.shuffled() }
    var index by remember(quizKey, runId) { mutableStateOf(0) }
    var correctCount by remember(quizKey, runId) { mutableStateOf(0) }
    var pickedIndex by remember(quizKey, runId) { mutableStateOf<Int?>(null) }
    var pickedCorrect by remember(quizKey, runId) { mutableStateOf<Boolean?>(null) }
    var heardAnswer by remember(quizKey, runId, index) { mutableStateOf(false) }
    var hearAnswerRequest by remember(quizKey, runId, index) { mutableStateOf(0) }

    val q = ordered.getOrNull(index)
    if (q == null) {
        QuizSummary(
            title = title,
            score = correctCount,
            total = ordered.size,
            onRetry = {
                runId += 1
            },
            onExit = onExit
        )
        return
    }

    // Per-question stable shuffle of options so tapping doesn't re-roll positions
    // when state updates trigger recomposition.
    val options = remember(quizKey, runId, index) { (q.distractors + q.correct).shuffled() }
    val correctOption = q.correct
    val contextVisible = rememberDelayedTranslationVisible("${q.prompt}\n${q.context}\n$index")

    fun stopCurrentPlayback() {
        PlaybackController.stop()
        app.audioPlayer.stop()
        NowVoicingBus.clear()
    }

    // Auto-advance after the reveal pause when the user picks an option.
    LaunchedEffect(pickedIndex) {
        if (pickedIndex != null) {
            PlaybackController.register {
                app.audioPlayer.stop()
                NowVoicingBus.clear()
                pickedIndex = null
                pickedCorrect = null
            }
            try {
                voiceQuizReveal(app, q, pickedCorrect == true)
                kotlinx.coroutines.delay(if (pickedCorrect == true) 700L else 1500L)
                if (pickedIndex != null) {
                    if (pickedCorrect == true) correctCount += 1
                    index += 1
                    pickedIndex = null
                    pickedCorrect = null
                }
            } finally {
                PlaybackController.unregister()
            }
        }
    }

    LaunchedEffect(hearAnswerRequest) {
        if (hearAnswerRequest == 0) return@LaunchedEffect
        PlaybackController.register {
            app.audioPlayer.stop()
            NowVoicingBus.clear()
        }
        try {
            publishQuizAnswer(q)
            speakQuizAnswer(app, q.correct, q.correctLocale)
        } finally {
            PlaybackController.unregister()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // ── Header: title, score, exit ─────────────────────────────────────
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                color = LbColors.Primary, modifier = Modifier.weight(1f))
            Text("${index + 1} / ${ordered.size}",
                fontSize = 12.sp, color = LbColors.Label)
            Spacer(Modifier.width(12.dp))
            EndQuizButton(onClick = onExit)
            Spacer(Modifier.width(8.dp))
            OutlinedButton(
                onClick = {
                    if (pickedIndex == null) {
                        stopCurrentPlayback()
                        heardAnswer = true
                        hearAnswerRequest += 1
                    }
                },
                enabled = pickedIndex == null,
                contentPadding = PaddingValues(horizontal = 9.dp, vertical = 2.dp)
            ) {
                Text(if (heardAnswer) "Replay answer" else "Hear answer", fontSize = 11.sp)
            }
        }

        LinearProgressIndicator(
            progress = { (index + 1).toFloat() / ordered.size },
            modifier = Modifier.fillMaxWidth().height(3.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = LbColors.SurfaceTint
        )

        // ── Prompt ─────────────────────────────────────────────────────────
        Column(horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()) {
            if (q.context.isNotBlank()) {
                if (contextVisible) {
                    Text(q.context, fontSize = 12.sp,
                        color = LbColors.Label, fontWeight = FontWeight.SemiBold)
                } else {
                    Spacer(Modifier.height(16.dp))
                }
                Spacer(Modifier.height(2.dp))
            }
            Text(
                q.prompt, fontSize = 24.sp, lineHeight = 27.sp, fontWeight = FontWeight.Bold,
                color = LbColors.TextPrimary, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // ── 2x2 options grid ──────────────────────────────────────────────
        val pairs = options.chunked(2)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            pairs.forEachIndexed { pairIndex, row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()) {
                    row.forEachIndexed { rowIndex, opt ->
                        val i = pairIndex * 2 + rowIndex
                        val tapped = pickedIndex == i
                        val isCorrect = opt == correctOption
                        val bg = when {
                            tapped && isCorrect -> LbColors.SuccessSoft
                            tapped && !isCorrect -> LbColors.DangerSoft
                            pickedIndex != null && isCorrect -> LbColors.SuccessSoft // reveal the right one even when wrong picked
                            heardAnswer && isCorrect -> LbColors.SuccessSoft
                            else -> LbColors.Sheet
                        }
                        val border = when {
                            tapped && isCorrect -> LbColors.Success
                            tapped && !isCorrect -> LbColors.Danger
                            heardAnswer && isCorrect -> LbColors.Success
                            else -> LbColors.SurfaceTint
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(bg)
                                .border(1.dp, border, RoundedCornerShape(10.dp))
                                .clickable(enabled = pickedIndex == null) {
                                    stopCurrentPlayback()
                                    pickedIndex = i
                                    pickedCorrect = isCorrect
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                opt, fontSize = 18.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold,
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

private suspend fun voiceQuizReveal(
    app: LangbangApplication,
    question: QuizQuestion,
    wasCorrect: Boolean
) {
    publishQuizAnswer(question)
    speakQuizAnswer(app, question.correct, question.correctLocale)
    if (!wasCorrect) {
        findPracticeSentence(app, question.polishPracticeWord)?.let { sentence ->
            NowVoicingBus.publish(
                NowVoicing(
                    en = sentence.en,
                    pl = sentence.pl,
                    literal = sentence.literal,
                    lang = "pl",
                    position = "extra phrase",
                    words = sentence.words,
                    quizMode = true
                )
            )
            speakQuizAnswer(app, sentence.pl, AzureTtsClient.LOCALE_PL)
        }
    }
}

private fun publishQuizAnswer(question: QuizQuestion) {
    val isEnglishAnswer = question.correctLocale == AzureTtsClient.LOCALE_EN
    val pl = if (isEnglishAnswer) question.polishPracticeWord else question.correct
    val en = if (isEnglishAnswer) question.correct else question.prompt
    NowVoicingBus.publish(
        NowVoicing(
            en = en,
            pl = pl,
            literal = null,
            lang = if (isEnglishAnswer) "en" else "pl",
            position = "quiz answer",
            words = listOf(TokenPair(pl, en)),
            quizMode = true
        )
    )
}

private suspend fun speakQuizAnswer(
    app: LangbangApplication,
    text: String,
    locale: String
) {
    if (text.isBlank()) return
    val voice = if (locale == AzureTtsClient.LOCALE_EN) {
        AzureTtsClient.EN_US_F
    } else {
        AzureTtsClient.PL_PL_F
    }
    val file = app.audioCache.fileFor(locale, voice, text)
    if (!app.audioCache.has(file)) {
        app.tts.synthesize(text, voice, locale, file)
    }
    if (!app.audioCache.has(file)) return
    suspendCancellableCoroutine<Unit> { cont ->
        app.audioPlayer.play(file) { if (cont.isActive) cont.resume(Unit) }
        cont.invokeOnCancellation { app.audioPlayer.stop() }
    }
}

private fun findPracticeSentence(app: LangbangApplication, polishWord: String): SentenceExample? {
    val word = polishWord.trim().lowercase()
    if (word.isEmpty()) return null
    val repo = app.lessonRepo
    val candidates = buildList {
        repo.lesson2().verbs.forEach { verb ->
            addAll(repo.sentencesFor(verb.lemma, VerbSentenceStore.TENSE_PRESENT))
            addAll(repo.sentencesFor(verb.lemma, VerbSentenceStore.TENSE_PAST))
        }
        repo.lesson3().adjectives.forEach { addAll(repo.adjectiveSentencesFor(it.lemma)) }
        repo.lesson4().adverbs.forEach { addAll(repo.adverbSentencesFor(it.lemma)) }
        repo.lesson5().groups.forEach { addAll(it.sentences) }
        repo.lesson6().nouns.forEach { addAll(repo.nounSentencesFor(it.lemma)) }
    }
    return candidates.firstOrNull { sentence ->
        sentence.pl.trim().lowercase() != word && containsPolishToken(sentence.pl, word)
    }
}

private fun containsPolishToken(sentence: String, word: String): Boolean {
    val tokens = sentence.lowercase()
        .split(Regex("[^\\p{L}]+"))
        .filter { it.isNotBlank() }
    return word in tokens
}

@Composable
private fun EndQuizButton(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = LbColors.Stop),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Text("End quiz", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
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
        Button(onClick = onExit) { Text("Back to quizzes") }
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
