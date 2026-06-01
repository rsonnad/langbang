package com.sponic.langbang.ui.quizzes

import com.sponic.langbang.ui.theme.LbColors

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
    nowVoicing: @Composable () -> Unit = {}
) {
    var mode by remember { mutableStateOf<QuizMode>(QuizMode.Hub) }
    Column(modifier = Modifier.fillMaxSize()) {
        nowVoicing()
        Box(modifier = Modifier.weight(1f)) {
            when (val m = mode) {
                QuizMode.Hub -> Hub(onPick = { mode = it })
                QuizMode.Practice -> PracticeQuiz(
                    app = app,
                    onExit = { mode = QuizMode.Hub }
                )
                is QuizMode.PerVerbPick -> PerVerbPicker(app = app,
                    onPick = { v, t -> mode = QuizMode.PerVerb(v, t) },
                    onBack = { mode = QuizMode.Hub })
                is QuizMode.CrossVerbPick -> CrossVerbPicker(
                    onPick = { p, t -> mode = QuizMode.CrossVerb(p, t) },
                    onBack = { mode = QuizMode.Hub })
                is QuizMode.PerVerb -> MultipleChoiceQuiz(
                    app = app,
                    title = "Verb forms · ${m.verb.lemma} (${m.tenseLabel()})",
                    questions = QuizGenerators.perVerbConjugation(m.verb, m.tense),
                    onExit = { mode = QuizMode.Hub }
                )
                is QuizMode.CrossVerb -> MultipleChoiceQuiz(
                    app = app,
                    title = "All verbs · ${m.personLabel()} (${m.tenseLabel()})",
                    questions = QuizGenerators.crossVerbConjugation(
                        app.lessonRepo, m.personKey, m.tense
                    ),
                    onExit = { mode = QuizMode.Hub }
                )
                is QuizMode.Adjectives -> MultipleChoiceQuiz(
                    app = app,
                    title = if (m.enToPl) "Adjectives · EN → PL" else "Adjectives · PL → EN",
                    questions = QuizGenerators.adjectiveVocab(app.lessonRepo, enToPl = m.enToPl),
                    onExit = { mode = QuizMode.Hub }
                )
                is QuizMode.Adverbs -> MultipleChoiceQuiz(
                    app = app,
                    title = if (m.enToPl) "Adverbs · EN → PL" else "Adverbs · PL → EN",
                    questions = QuizGenerators.adverbVocab(app.lessonRepo, enToPl = m.enToPl),
                    onExit = { mode = QuizMode.Hub }
                )
                QuizMode.Pronouns -> MultipleChoiceQuiz(
                    app = app,
                    title = "Pronoun case forms",
                    questions = QuizGenerators.pronounCaseForms(app.lessonRepo),
                    onExit = { mode = QuizMode.Hub }
                )
            }
        }
    }
}

private sealed interface QuizMode {
    data object Hub : QuizMode
    data object Practice : QuizMode
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

@Composable
private fun Hub(onPick: (QuizMode) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "Quizzes", fontSize = 22.sp, fontWeight = FontWeight.SemiBold,
            color = LbColors.Primary
        )
        Text(
            "Practice is the main self-graded drill. Multiple choice remains below " +
                "as a lightweight recognition test.",
            fontSize = 13.sp, color = LbColors.TextSecondary
        )
        QuizCard(
            title = "Practice — adaptive check / X",
            subtitle = "Self-grade cards. Misses repeat once, then return as four variations.",
            onClick = { onPick(QuizMode.Practice) }
        )
        QuizCard(
            title = "Verb forms — one verb",
            subtitle = "Pick a verb, drill all 6 person forms in present or past.",
            onClick = { onPick(QuizMode.PerVerbPick) }
        )
        QuizCard(
            title = "Verb forms — one person, all verbs",
            subtitle = "Lock a pronoun (e.g. 3sg), drill across every verb.",
            onClick = { onPick(QuizMode.CrossVerbPick) }
        )
        QuizCard(
            title = "Adjectives — EN → PL",
            subtitle = "Pick the Polish word for each English adjective.",
            onClick = { onPick(QuizMode.Adjectives(enToPl = true)) }
        )
        QuizCard(
            title = "Adjectives — PL → EN",
            subtitle = "Pick the English meaning for each Polish adjective.",
            onClick = { onPick(QuizMode.Adjectives(enToPl = false)) }
        )
        QuizCard(
            title = "Adverbs — EN → PL",
            subtitle = "Pick the Polish word for each English adverb.",
            onClick = { onPick(QuizMode.Adverbs(enToPl = true)) }
        )
        QuizCard(
            title = "Adverbs — PL → EN",
            subtitle = "Pick the English meaning for each Polish adverb.",
            onClick = { onPick(QuizMode.Adverbs(enToPl = false)) }
        )
        QuizCard(
            title = "Pronoun case forms",
            subtitle = "ja/mnie/mi, on/go/mu — drill nom / acc / dat across all pronouns.",
            onClick = { onPick(QuizMode.Pronouns) }
        )
    }
}

@Composable
private fun QuizCard(title: String, subtitle: String, onClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.Black)
            Text(subtitle, fontSize = 12.sp, color = LbColors.TextSecondary)
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
