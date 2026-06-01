package com.sponic.langbang.ui.numbers

import com.sponic.langbang.ui.theme.LbColors

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sponic.langbang.LangbangApplication
import com.sponic.langbang.integrations.AzureTtsClient
import com.sponic.langbang.ui.common.CompactLessonListDefaults
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Polish cardinal numbers 0–100. The list holds the memorizable building blocks —
 * 0–20 individually, then the tens (20, 30 … 100). 21–99 are just tens + ones
 * (dwadzieścia jeden = 21), noted at the top, so we don't ship 100 near-identical
 * rows. Tap any row to hear it; "Play all" reads the whole list top to bottom.
 *
 * Audio isn't part of the bulk R2/prefetch set, so each number is synthesized via
 * Azure TTS on first play and cached locally (same fileFor key as everywhere else),
 * so replays — and offline use after the first pass — are instant.
 */
@Composable
fun NumbersScreen(app: LangbangApplication) {
    val scope = rememberCoroutineScope()
    val online by app.network.online.collectAsState()
    var playingAll by remember { mutableStateOf(false) }
    var playingPl by remember { mutableStateOf<String?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            job?.cancel()
            job = null
            app.audioPlayer.stop()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Numbers · Liczby",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = LbColors.Primary
                )
                Text(
                    "0–100. For 21–99, combine tens + ones — e.g. dwadzieścia jeden (21), " +
                        "trzydzieści pięć (35).",
                    fontSize = 12.sp,
                    color = LbColors.TextSecondary
                )
            }
            Button(
                onClick = {
                    if (playingAll) {
                        job?.cancel(); job = null
                        playingAll = false; playingPl = null
                        app.audioPlayer.stop()
                    } else {
                        playingAll = true
                        job = scope.launch {
                            try {
                                NUMBERS.forEach { n ->
                                    playingPl = n.pl
                                    playAndAwait(app, n.pl)
                                    delay(200)
                                }
                            } finally {
                                playingAll = false; playingPl = null
                            }
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (playingAll) LbColors.Danger
                    else MaterialTheme.colorScheme.primary
                ),
                shape = RoundedCornerShape(16.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)
            ) {
                Icon(
                    if (playingAll) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = if (playingAll) "Stop" else "Play all",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    if (playingAll) "Stop" else "Play all",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        if (!online) {
            Text(
                "Offline — only numbers you've already played will have audio.",
                fontSize = 11.sp,
                color = LbColors.Danger
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            itemsIndexed(NUMBERS) { index, n ->
                val isPlaying = playingPl == n.pl
                val rowColor = when {
                    isPlaying -> LbColors.GoldSoft
                    index % 2 == 1 -> CompactLessonListDefaults.AlternateItemColor
                    else -> LbColors.Sheet
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(rowColor)
                        .clickable {
                            // Single tap plays just this number (cancels any queue).
                            job?.cancel()
                            playingAll = false
                            job = scope.launch {
                                playingPl = n.pl
                                try {
                                    playAndAwait(app, n.pl)
                                } finally {
                                    if (playingPl == n.pl) playingPl = null
                                }
                            }
                        }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        n.digit,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = LbColors.Label,
                        modifier = Modifier.width(56.dp)
                    )
                    Text(
                        n.pl,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = LbColors.Primary,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        n.en,
                        fontSize = 14.sp,
                        color = LbColors.TextSecondary,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = "Play ${n.en}",
                        tint = if (isPlaying) LbColors.Danger else LbColors.Primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

private data class Num(val digit: String, val pl: String, val en: String)

/** Memorizable set: 0–20 + tens up to 100. 21–99 compose from these. */
private val NUMBERS: List<Num> = listOf(
    Num("0", "zero", "zero"),
    Num("1", "jeden", "one"),
    Num("2", "dwa", "two"),
    Num("3", "trzy", "three"),
    Num("4", "cztery", "four"),
    Num("5", "pięć", "five"),
    Num("6", "sześć", "six"),
    Num("7", "siedem", "seven"),
    Num("8", "osiem", "eight"),
    Num("9", "dziewięć", "nine"),
    Num("10", "dziesięć", "ten"),
    Num("11", "jedenaście", "eleven"),
    Num("12", "dwanaście", "twelve"),
    Num("13", "trzynaście", "thirteen"),
    Num("14", "czternaście", "fourteen"),
    Num("15", "piętnaście", "fifteen"),
    Num("16", "szesnaście", "sixteen"),
    Num("17", "siedemnaście", "seventeen"),
    Num("18", "osiemnaście", "eighteen"),
    Num("19", "dziewiętnaście", "nineteen"),
    Num("20", "dwadzieścia", "twenty"),
    Num("30", "trzydzieści", "thirty"),
    Num("40", "czterdzieści", "forty"),
    Num("50", "pięćdziesiąt", "fifty"),
    Num("60", "sześćdziesiąt", "sixty"),
    Num("70", "siedemdziesiąt", "seventy"),
    Num("80", "osiemdziesiąt", "eighty"),
    Num("90", "dziewięćdziesiąt", "ninety"),
    Num("100", "sto", "one hundred")
)

/**
 * Synth-on-miss + play, mirroring RandomPlayer.playAndAwait. fileFor's key is
 * (locale, voice, text) so it shares the cache with the rest of the app.
 */
private suspend fun playAndAwait(app: LangbangApplication, text: String) {
    if (text.isEmpty()) return
    val file = app.audioCache.fileFor(AzureTtsClient.LOCALE_PL, AzureTtsClient.PL_PL_F, text)
    if (!app.audioCache.has(file)) {
        runCatching { app.tts.synthesize(text, AzureTtsClient.PL_PL_F, AzureTtsClient.LOCALE_PL, file) }
    }
    if (!app.audioCache.has(file)) return
    suspendCancellableCoroutine<Unit> { cont ->
        app.audioPlayer.play(file) { if (cont.isActive) cont.resume(Unit) }
        cont.invokeOnCancellation { app.audioPlayer.stop() }
    }
}
