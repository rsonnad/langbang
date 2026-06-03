package com.sponic.langbang.ui.word

import com.sponic.langbang.ui.theme.LbColors

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sponic.langbang.LangbangApplication
import com.sponic.langbang.domain.targetAudioVoice

@Composable
fun WordSheet(
    app: LangbangApplication,
    polishToken: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val lesson = remember { app.lessonRepo.lesson2() }

    // Find what the tapped token matches: a verb form, a pronoun form, or unknown.
    val verbMatch = remember(polishToken) {
        lesson.verbs.firstOrNull { v ->
            v.forms.values.any { it.equals(polishToken, ignoreCase = true) }
        }
    }
    val pronounMatch = remember(polishToken) {
        lesson.pronouns.firstOrNull { p ->
            p.case_forms.values.any { it.equals(polishToken, ignoreCase = true) }
        }
    }

    Surface(
        modifier = modifier
            .width(420.dp)
            .fillMaxHeight()
            .background(Color.White),
        shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp),
        shadowElevation = 8.dp
    ) {
        Column(Modifier.fillMaxHeight().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(polishToken, fontSize = 26.sp, fontWeight = FontWeight.SemiBold,
                    color = LbColors.Primary)
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            when {
                verbMatch != null -> {
                    Text(
                        "${verbMatch.lemma}  —  ${verbMatch.en}",
                        fontSize = 14.sp, color = LbColors.TextSecondary
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("Present tense", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        items(verbMatch.forms.entries.toList()) { (k, v) ->
                            FormRow(label = personLabel(k), form = v) {
                                val f = app.audioCache.fileFor(
                                    app.targetAudioVoice().locale, app.targetAudioVoice().voice, v
                                )
                                app.audioPlayer.play(f)
                            }
                        }
                    }
                }
                pronounMatch != null -> {
                    Text(
                        "${pronounMatch.lemma}  —  ${pronounMatch.en}",
                        fontSize = 14.sp, color = LbColors.TextSecondary
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("Case forms", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(pronounMatch.case_forms.entries.toList()) { (k, v) ->
                            FormRow(label = caseLabel(k), form = v) {
                                val f = app.audioCache.fileFor(
                                    app.targetAudioVoice().locale, app.targetAudioVoice().voice, v
                                )
                                app.audioPlayer.play(f)
                            }
                        }
                    }
                }
                else -> {
                    Text(
                        "No paradigm found for this word in Lesson 1. Tap support coming with the Worker pipeline.",
                        fontSize = 13.sp, color = LbColors.TextMuted,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun FormRow(label: String, form: String, onPlay: () -> Unit) {
    Card(
        onClick = onPlay,
        colors = CardDefaults.cardColors(containerColor = LbColors.SurfaceTint),
        modifier = Modifier.height(44.dp)
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp).fillMaxHeight(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, fontSize = 12.sp, color = LbColors.TextMuted,
                modifier = Modifier.width(70.dp))
            Text(form, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.width(8.dp))
            Text("▶", fontSize = 12.sp, color = LbColors.Primary)
        }
    }
}

private fun personLabel(k: String): String = when (k) {
    "1sg" -> "I"; "2sg" -> "you"; "3sg" -> "he/she/it"
    "1pl" -> "we"; "2pl" -> "y'all"; "3pl" -> "they"
    else -> k
}

private fun caseLabel(k: String): String = when (k) {
    "nom" -> "subject"; "acc" -> "accusative"; "dat" -> "dative"
    "gen" -> "genitive"; "ins" -> "instrumental"; "loc" -> "locative"; "voc" -> "vocative"
    else -> k
}
