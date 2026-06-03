package com.sponic.langbang.ui.quizzes

import com.sponic.langbang.ui.theme.LbColors

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sponic.langbang.LangbangApplication
import com.sponic.langbang.data.VerbSentenceStore
import com.sponic.langbang.data.model.VerbEntry
import com.sponic.langbang.domain.NowVoicingBus
import com.sponic.langbang.domain.PlaybackController

/**
 * Tab-root for the new tap-to-answer quiz section. Lists the available drill modes
 * as Cards; selecting one swaps the body for [MultipleChoiceQuiz] until the user
 * exits. Separate from the existing audio "Conj quiz" / "Recall quiz" buttons in
 * VerbsTab — those drive playback; this is silent, fast, tablet-on-couch friendly.
 *
 * Five modes:
 *   1. Verb forms (one verb, all 6 persons in selected tense)
 *   2. Verb forms (one person, across every verb)
 *   3. Adjectives (EN ↔ PL toggle)
 *   4. Adverbs (EN ↔ PL toggle)
 *   5. Pronouns (case forms — ja/mnie/mi etc)
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuizzesScreen(
    app: LangbangApplication,
    nowVoicing: @Composable () -> Unit = {},
    resetToken: Int = 0
) {
    var mode by remember { mutableStateOf<QuizMode>(QuizMode.Hub) }
    val returnToHub = {
        PlaybackController.stop()
        NowVoicingBus.clear()
        mode = QuizMode.Hub
    }
    BackHandler(enabled = mode != QuizMode.Hub) {
        returnToHub()
    }
    LaunchedEffect(resetToken) {
        if (resetToken > 0) returnToHub()
    }
    Column(modifier = Modifier.fillMaxSize()) {
        nowVoicing()
        Box(modifier = Modifier.weight(1f)) {
            when (val m = mode) {
                QuizMode.Hub -> Hub(onPick = { mode = it })
                QuizMode.Practice -> PracticeQuiz(
                    app = app,
                    onExit = returnToHub
                )
                QuizMode.HelperInfinitives -> PracticeQuiz(
                    app = app,
                    initialScope = PracticeScope(
                        auto = true,
                        wordTypes = setOf(PracticeWordType.HELPERS),
                        personKeys = setOf("1sg", "2sg", "3sg", "1pl", "2pl", "3pl"),
                        tenses = setOf(VerbSentenceStore.TENSE_PRESENT)
                    ),
                    title = "Helper + infinitive",
                    onExit = returnToHub
                )
                is QuizMode.PerVerbPick -> PerVerbPicker(app = app,
                    onPick = { v, t -> mode = QuizMode.PerVerb(v, t) },
                    onBack = returnToHub)
                is QuizMode.CrossVerbPick -> CrossVerbPicker(
                    onPick = { p, t -> mode = QuizMode.CrossVerb(p, t) },
                    onBack = returnToHub)
                is QuizMode.PerVerb -> MultipleChoiceQuiz(
                    app = app,
                    title = "Verb forms · ${m.verb.lemma} (${m.tenseLabel()})",
                    questions = remember(m.verb.lemma, m.tense) {
                        QuizGenerators.perVerbConjugation(m.verb, m.tense)
                    },
                    onExit = returnToHub
                )
                is QuizMode.CrossVerb -> MultipleChoiceQuiz(
                    app = app,
                    title = "All verbs · ${m.personLabel()} (${m.tenseLabel()})",
                    questions = remember(m.personKey, m.tense) {
                        QuizGenerators.crossVerbConjugation(
                            app.lessonRepo, m.personKey, m.tense
                        )
                    },
                    onExit = returnToHub
                )
                is QuizMode.Adjectives -> MultipleChoiceQuiz(
                    app = app,
                    title = if (m.enToPl) "Adjectives · EN → PL" else "Adjectives · PL → EN",
                    questions = remember(m.enToPl) {
                        QuizGenerators.adjectiveVocab(app.lessonRepo, enToPl = m.enToPl)
                    },
                    onExit = returnToHub
                )
                is QuizMode.Adverbs -> MultipleChoiceQuiz(
                    app = app,
                    title = if (m.enToPl) "Adverbs · EN → PL" else "Adverbs · PL → EN",
                    questions = remember(m.enToPl) {
                        QuizGenerators.adverbVocab(app.lessonRepo, enToPl = m.enToPl)
                    },
                    onExit = returnToHub
                )
                QuizMode.Pronouns -> MultipleChoiceQuiz(
                    app = app,
                    title = "Pronoun case forms",
                    questions = remember { QuizGenerators.pronounCaseForms(app.lessonRepo) },
                    onExit = returnToHub
                )
            }
        }
    }
}

private sealed interface QuizMode {
    data object Hub : QuizMode
    data object Practice : QuizMode
    data object HelperInfinitives : QuizMode
    data object PerVerbPick : QuizMode
    data object CrossVerbPick : QuizMode
    data class PerVerb(val verb: VerbEntry, val tense: String) : QuizMode {
        fun tenseLabel() = if (tense == VerbSentenceStore.TENSE_PAST) "past" else "present"
    }
    data class CrossVerb(val personKey: String, val tense: String) : QuizMode {
        fun tenseLabel() = if (tense == VerbSentenceStore.TENSE_PAST) "past" else "present"
        fun personLabel() = when (personKey) {
            "1sg" -> "I"; "2sg" -> "you"; "3sg" -> "he / she / it"
            "1pl" -> "we"; "2pl" -> "y'all"; "3pl" -> "they"
            else -> personKey
        }
    }
    data class Adjectives(val enToPl: Boolean) : QuizMode
    data class Adverbs(val enToPl: Boolean) : QuizMode
    data object Pronouns : QuizMode
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Hub(onPick: (QuizMode) -> Unit) {
    val cards = listOf(
        QuizCardSpec(
            title = "Practice",
            subtitle = "Adaptive self-grade cards. Misses repeat once, then return as variations.",
            mode = QuizMode.Practice
        ),
        QuizCardSpec(
            title = "Helper + infinitive",
            subtitle = "Common want/need/can/could/should patterns with everyday infinitives.",
            mode = QuizMode.HelperInfinitives
        ),
        QuizCardSpec(
            title = "One verb",
            subtitle = "Pick a verb, drill all 6 person forms in present or past.",
            mode = QuizMode.PerVerbPick
        ),
        QuizCardSpec(
            title = "One person, all verbs",
            subtitle = "Lock a pronoun, then drill that form across every verb.",
            mode = QuizMode.CrossVerbPick
        ),
        QuizCardSpec(
            title = "Adjectives EN -> PL",
            subtitle = "Pick the Polish word for each English adjective.",
            mode = QuizMode.Adjectives(enToPl = true)
        ),
        QuizCardSpec(
            title = "Adjectives PL -> EN",
            subtitle = "Pick the English meaning for each Polish adjective.",
            mode = QuizMode.Adjectives(enToPl = false)
        ),
        QuizCardSpec(
            title = "Adverbs EN -> PL",
            subtitle = "Pick the Polish word for each English adverb.",
            mode = QuizMode.Adverbs(enToPl = true)
        ),
        QuizCardSpec(
            title = "Adverbs PL -> EN",
            subtitle = "Pick the English meaning for each Polish adverb.",
            mode = QuizMode.Adverbs(enToPl = false)
        ),
        QuizCardSpec(
            title = "Pronouns",
            subtitle = "ja/mnie/mi, on/go/mu: drill nom / acc / dat across pronouns.",
            mode = QuizMode.Pronouns
        )
    )
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            "Quizzes", fontSize = 20.sp, fontWeight = FontWeight.SemiBold,
            color = LbColors.Primary
        )
        cards.chunked(4).forEach { rowCards ->
            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                rowCards.forEach { card ->
                    QuizCard(
                        title = card.title,
                        subtitle = card.subtitle,
                        onClick = { onPick(card.mode) },
                        modifier = Modifier
                            .weight(1f)
                            .height(108.dp)
                    )
                }
                repeat(4 - rowCards.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

private data class QuizCardSpec(
    val title: String,
    val subtitle: String,
    val mode: QuizMode
)

@Composable
private fun QuizCard(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier
            .clickable { onClick() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 14.dp, top = 9.dp, end = 14.dp, bottom = 9.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
                maxLines = 1
            )
            Text(
                subtitle,
                fontSize = 10.sp,
                lineHeight = 12.sp,
                color = LbColors.TextSecondary,
                maxLines = 3
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PerVerbPicker(
    app: LangbangApplication,
    onPick: (VerbEntry, String) -> Unit,
    onBack: () -> Unit
) {
    var tense by remember { mutableStateOf(VerbSentenceStore.TENSE_PRESENT) }
    val verbs = remember { app.lessonRepo.lesson2().verbs.sortedBy { it.lemma } }
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Pick a verb", fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
                color = LbColors.Primary, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = onBack, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                Text("Back", fontSize = 12.sp)
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = tense == VerbSentenceStore.TENSE_PRESENT,
                onClick = { tense = VerbSentenceStore.TENSE_PRESENT },
                label = { Text("Present") })
            FilterChip(selected = tense == VerbSentenceStore.TENSE_PAST,
                onClick = { tense = VerbSentenceStore.TENSE_PAST },
                label = { Text("Past") })
        }
        LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(verbs) { v ->
                Box(modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(LbColors.Sheet)
                    .clickable { onPick(v, tense) }
                    .padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(v.lemma, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                            color = LbColors.Primary, modifier = Modifier.width(160.dp))
                        Text(v.en, fontSize = 13.sp, color = LbColors.TextSecondary)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CrossVerbPicker(onPick: (String, String) -> Unit, onBack: () -> Unit) {
    var tense by remember { mutableStateOf(VerbSentenceStore.TENSE_PRESENT) }
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Pick a person", fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
                color = LbColors.Primary, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = onBack, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                Text("Back", fontSize = 12.sp)
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = tense == VerbSentenceStore.TENSE_PRESENT,
                onClick = { tense = VerbSentenceStore.TENSE_PRESENT },
                label = { Text("Present") })
            FilterChip(selected = tense == VerbSentenceStore.TENSE_PAST,
                onClick = { tense = VerbSentenceStore.TENSE_PAST },
                label = { Text("Past") })
        }
        val people = listOf(
            "1sg" to "ja (I)",
            "2sg" to "ty (you)",
            "3sg" to "on / ona",
            "1pl" to "my (we)",
            "2pl" to "wy (y'all)",
            "3pl" to "oni / one"
        )
        people.forEach { (key, label) ->
            Surface(
                color = LbColors.Sheet,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().clickable { onPick(key, tense) }
            ) {
                Text(label, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                    color = LbColors.Primary,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
            }
        }
    }
}
