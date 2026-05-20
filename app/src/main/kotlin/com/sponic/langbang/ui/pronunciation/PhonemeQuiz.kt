package com.sponic.langbang.ui.pronunciation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.sponic.langbang.LangbangApplication
import com.sponic.langbang.data.model.PhonemeEntry
import com.sponic.langbang.integrations.AzureTtsClient

@Composable
fun PhonemeQuiz(
    app: LangbangApplication,
    phonemes: List<PhonemeEntry>,
    onDismiss: () -> Unit
) {
    val deck = remember { mutableStateListOf<PhonemeEntry>().apply { addAll(phonemes.shuffled()) } }
    val total = remember { phonemes.size }
    var known by remember { mutableStateOf(0) }
    var flipped by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = Color(0xFFFDFBF6),
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.92f),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(Modifier.fillMaxSize().padding(20.dp)) {
                QuizHeader(
                    remaining = deck.size,
                    known = known,
                    total = total,
                    onClose = onDismiss,
                    onRestart = {
                        deck.clear()
                        deck.addAll(phonemes.shuffled())
                        known = 0
                        flipped = false
                    }
                )

                Spacer(Modifier.height(16.dp))

                Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    val current = deck.firstOrNull()
                    if (current == null) {
                        QuizFinished(
                            known = known,
                            total = total,
                            onRestart = {
                                deck.addAll(phonemes.shuffled())
                                known = 0
                                flipped = false
                            },
                            onClose = onDismiss
                        )
                    } else {
                        FlashCard(
                            app = app,
                            phoneme = current,
                            flipped = flipped,
                            onFlip = { flipped = !flipped },
                            onKnown = {
                                known += 1
                                deck.removeAt(0)
                                flipped = false
                            },
                            onUnknown = {
                                val card = deck.removeAt(0)
                                // Re-insert near the back so it cycles again.
                                val insertAt = (deck.size).coerceAtMost(deck.size)
                                deck.add(insertAt, card)
                                flipped = false
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuizHeader(
    remaining: Int,
    known: Int,
    total: Int,
    onClose: () -> Unit,
    onRestart: () -> Unit
) {
    val progress = if (total == 0) 0f else known / total.toFloat()
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Flashcard quiz",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF0F4C81),
                modifier = Modifier.weight(1f)
            )
            Text(
                "$known / $total known",
                fontSize = 13.sp,
                color = Color(0xFF555555)
            )
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = onRestart) {
                Icon(Icons.Default.Refresh, contentDescription = "Restart",
                    tint = Color(0xFF555555))
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Close")
            }
        }
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = progress,
            modifier = Modifier.fillMaxWidth().height(4.dp),
            color = MaterialTheme.colorScheme.secondary,
            trackColor = Color(0xFFE5E0D6)
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "$remaining cards left in deck",
            fontSize = 11.sp,
            color = Color(0xFF888888)
        )
    }
}

@Composable
private fun FlashCard(
    app: LangbangApplication,
    phoneme: PhonemeEntry,
    flipped: Boolean,
    onFlip: () -> Unit,
    onKnown: () -> Unit,
    onUnknown: () -> Unit
) {
    val firstExample = phoneme.examples.firstOrNull()

    // Auto-play the example word audio when the card is flipped to the back.
    LaunchedEffect(phoneme, flipped) {
        if (flipped && firstExample != null) {
            val f = app.audioCache.fileFor(
                AzureTtsClient.LOCALE_PL, AzureTtsClient.PL_PL_F, firstExample.pl
            )
            app.audioPlayer.play(f)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            onClick = onFlip,
            colors = CardDefaults.cardColors(
                containerColor = if (flipped) Color(0xFFFFF8EE) else Color.White
            ),
            modifier = Modifier.fillMaxWidth().weight(1f)
        ) {
            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                if (!flipped) {
                    FlashCardFront(phoneme)
                } else {
                    FlashCardBack(app, phoneme)
                }
            }
        }

        if (!flipped) {
            Button(
                onClick = onFlip,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Text("Reveal", color = Color.White, fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold)
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onUnknown,
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier.weight(1f).height(56.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null,
                        tint = Color(0xFFC62828))
                    Spacer(Modifier.width(8.dp))
                    Text("Again", color = Color(0xFFC62828), fontWeight = FontWeight.SemiBold)
                }
                Button(
                    onClick = onKnown,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2E7D32)
                    ),
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier.weight(1f).height(56.dp)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text("Got it", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun FlashCardFront(phoneme: PhonemeEntry) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            phoneme.letter,
            fontSize = 140.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF0F4C81)
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "How is this pronounced?",
            fontSize = 14.sp,
            color = Color(0xFF888888)
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Tap card or Reveal to check",
            fontSize = 11.sp,
            color = Color(0xFFAAAAAA)
        )
    }
}

@Composable
private fun FlashCardBack(app: LangbangApplication, phoneme: PhonemeEntry) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                phoneme.letter,
                fontSize = 64.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0F4C81)
            )
            Spacer(Modifier.width(16.dp))
            Column {
                Text(phoneme.name, fontSize = 12.sp, color = Color(0xFF888888))
                Text(phoneme.ipa, fontSize = 18.sp, fontWeight = FontWeight.Medium)
            }
        }
        Text(
            phoneme.englishApproximation,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF7A5A1F)
        )
        Text(
            phoneme.description,
            fontSize = 12.sp,
            color = Color(0xFF555555)
        )
        phoneme.examples.firstOrNull()?.let { ex ->
            Spacer(Modifier.height(8.dp))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFFEAF1F7),
                onClick = {
                    val f = app.audioCache.fileFor(
                        AzureTtsClient.LOCALE_PL, AzureTtsClient.PL_PL_F, ex.pl
                    )
                    app.audioPlayer.play(f)
                }
            ) {
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.PlayArrow, contentDescription = null,
                        tint = Color(0xFF0F4C81), modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(
                            ex.pl,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF0F4C81)
                        )
                        Text(ex.en, fontSize = 13.sp, color = Color(0xFF555555))
                    }
                }
            }
        }
    }
}

@Composable
private fun QuizFinished(
    known: Int,
    total: Int,
    onRestart: () -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Done!", fontSize = 36.sp, fontWeight = FontWeight.Bold,
            color = Color(0xFF2E7D32))
        Spacer(Modifier.height(8.dp))
        Text(
            "You marked $known of $total as known.",
            fontSize = 15.sp, color = Color(0xFF555555)
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onRestart,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            ),
            shape = RoundedCornerShape(28.dp)
        ) {
            Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.White)
            Spacer(Modifier.width(8.dp))
            Text("Shuffle and go again", color = Color.White)
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onClose, shape = RoundedCornerShape(28.dp)) {
            Text("Close")
        }
    }
}
