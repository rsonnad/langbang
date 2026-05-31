package com.sponic.langbang.ui.pronunciation

import com.sponic.langbang.ui.theme.LbColors

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
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
import com.sponic.langbang.data.model.ExampleWord
import com.sponic.langbang.data.model.PhonemeEntry
import com.sponic.langbang.integrations.AzureTtsClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume

/**
 * Two-pane phoneme tour: letter on the left, answer panel + 3 example words on the right.
 * Audio for all 3 examples auto-plays on each card; when the sequence finishes the deck
 * auto-advances. Back/Next arrows let the learner navigate manually; "Play all" replays
 * the current card's audio. No "Got it"/"Again" — this is a guided pass, not a quiz.
 */
@Composable
fun PhonemeQuiz(
    app: LangbangApplication,
    phonemes: List<PhonemeEntry>,
    onDismiss: () -> Unit
) {
    val deck = remember { phonemes.shuffled() }
    var index by remember { mutableStateOf(0) }
    // Bumped by "Play all" to retrigger the LaunchedEffect on the same index.
    var playToken by remember(index) { mutableStateOf(0) }
    val current = deck.getOrNull(index)

    // Stop any in-flight audio when this dialog leaves the composition.
    DisposableEffect(Unit) {
        onDispose { app.audioPlayer.stop() }
    }

    // Auto-play the 3 examples in sequence, then advance. Cancelling the coroutine
    // (back/next/Play all/close) stops audio; the next launch starts a fresh chain.
    LaunchedEffect(index, playToken) {
        app.audioPlayer.stop()
        val phoneme = current ?: return@LaunchedEffect
        // Tiny lead-in so the card has a beat to register before audio starts.
        delay(150)
        val examples = phoneme.examples.take(3)
        examples.forEachIndexed { i, ex ->
            playFileAndAwait(app, exampleFile(app, ex))
            if (i < examples.lastIndex) delay(220)
        }
        // Auto-advance only after a clean playthrough. End-of-deck stays put.
        if (index < deck.size - 1) {
            delay(450)
            index += 1
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = LbColors.Canvas,
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.92f),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                QuizHeader(
                    position = if (deck.isEmpty()) 0 else index + 1,
                    total = deck.size,
                    onClose = onDismiss,
                    onRestart = {
                        app.audioPlayer.stop()
                        index = 0
                        playToken = 0
                    }
                )

                Spacer(Modifier.height(12.dp))

                if (current == null) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No phonemes loaded.", color = LbColors.TextMuted)
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        NavArrow(
                            icon = Icons.AutoMirrored.Filled.ArrowBack,
                            label = "Previous",
                            enabled = index > 0,
                            onClick = {
                                app.audioPlayer.stop()
                                index = (index - 1).coerceAtLeast(0)
                            }
                        )
                        PhonemePane(
                            app = app,
                            phoneme = current,
                            onPlayAll = { playToken += 1 },
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        )
                        NavArrow(
                            icon = Icons.AutoMirrored.Filled.ArrowForward,
                            label = "Next",
                            enabled = index < deck.size - 1,
                            onClick = {
                                app.audioPlayer.stop()
                                index = (index + 1).coerceAtMost(deck.size - 1)
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
    position: Int,
    total: Int,
    onClose: () -> Unit,
    onRestart: () -> Unit
) {
    val progress = if (total == 0) 0f else position / total.toFloat()
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Pronunciation tour",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = LbColors.Primary,
                modifier = Modifier.weight(1f)
            )
            Text(
                "$position / $total",
                fontSize = 13.sp,
                color = LbColors.TextSecondary
            )
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = onRestart) {
                Icon(Icons.Default.Refresh, contentDescription = "Restart",
                    tint = LbColors.TextSecondary)
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Close")
            }
        }
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(4.dp),
            color = MaterialTheme.colorScheme.secondary,
            trackColor = LbColors.SurfaceTint
        )
    }
}

@Composable
private fun NavArrow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(48.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (enabled) LbColors.Primary else LbColors.TextMuted.copy(alpha = 0.45f),
            modifier = Modifier.size(32.dp)
        )
    }
}

@Composable
private fun PhonemePane(
    app: LangbangApplication,
    phoneme: PhonemeEntry,
    onPlayAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        modifier = modifier
    ) {
        Row(Modifier.fillMaxSize().padding(20.dp)) {
            // ── Left: giant letter ─────────────────────────────────────────
            Box(
                modifier = Modifier.weight(0.45f).fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    phoneme.letter,
                    fontSize = 180.sp,
                    fontWeight = FontWeight.Bold,
                    color = LbColors.Primary
                )
            }
            Spacer(Modifier.width(16.dp))
            // ── Right: answer panel ────────────────────────────────────────
            Column(
                modifier = Modifier.weight(0.55f).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Play-all button at the top of the right pane.
                Button(
                    onClick = onPlayAll,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth().height(40.dp)
                ) {
                    Icon(
                        Icons.Default.PlayArrow, contentDescription = null,
                        tint = Color.White, modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Play all", color = Color.White,
                        fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                }
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(phoneme.name, fontSize = 12.sp, color = LbColors.TextMuted)
                    Spacer(Modifier.width(8.dp))
                    Text(phoneme.ipa, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                }
                Text(
                    phoneme.englishApproximation,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = LbColors.Label
                )
                Text(
                    phoneme.description,
                    fontSize = 12.sp,
                    color = LbColors.TextSecondary
                )
                val examples = phoneme.examples.take(3)
                if (examples.isNotEmpty()) {
                    Spacer(Modifier.height(2.dp))
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        examples.forEach { ex ->
                            ExampleRow(app, ex)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExampleRow(app: LangbangApplication, ex: ExampleWord) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = LbColors.PrimarySoft,
        modifier = Modifier.fillMaxWidth(),
        onClick = {
            app.audioPlayer.play(exampleFile(app, ex))
        }
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.PlayArrow, contentDescription = null,
                tint = LbColors.Primary, modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                ex.pl,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = LbColors.Primary,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                ex.en,
                fontSize = 13.sp,
                color = LbColors.TextSecondary
            )
        }
    }
}

private fun exampleFile(app: LangbangApplication, ex: ExampleWord): File =
    app.audioCache.fileFor(AzureTtsClient.LOCALE_PL, AzureTtsClient.PL_PL_F, ex.pl)

/**
 * Suspends until [file] finishes playing, or returns immediately if the file is missing
 * (AudioPlayer.play silently no-ops on missing files without firing onDone, which would
 * otherwise hang the sequence). Cancellation stops playback.
 */
private suspend fun playFileAndAwait(app: LangbangApplication, file: File) {
    if (!file.exists()) return
    suspendCancellableCoroutine<Unit> { cont ->
        app.audioPlayer.play(file) { if (cont.isActive) cont.resume(Unit) }
        cont.invokeOnCancellation { app.audioPlayer.stop() }
    }
}
