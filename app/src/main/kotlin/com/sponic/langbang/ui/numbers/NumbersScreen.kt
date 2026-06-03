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
import com.sponic.langbang.data.model.TokenPair
import com.sponic.langbang.domain.NowVoicing
import com.sponic.langbang.domain.NowVoicingBus
import com.sponic.langbang.domain.PlaybackController
import com.sponic.langbang.domain.targetAudioVoice
import com.sponic.langbang.integrations.AzureTtsClient
import com.sponic.langbang.ui.common.CompactLessonListDefaults
import com.sponic.langbang.ui.common.LbButton
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Polish cardinal numbers 0–100. The list holds the memorizable building blocks —
 * 0–29 individually, then the tens (30 … 100). 30–99 are just tens + ones
 * (trzydzieści pięć = 35), noted at the top, so we don't ship 100 near-identical
 * rows. Tap any row to hear it; "Play" reads the whole list top to bottom.
 *
 * Audio isn't part of the bulk R2/prefetch set, so each number is synthesized via
 * Azure TTS on first play and cached locally (same fileFor key as everywhere else),
 * so replays — and offline use after the first pass — are instant.
 */
@Composable
fun NumbersScreen(app: LangbangApplication) {
    val scope = rememberCoroutineScope()
    val online by app.network.online.collectAsState()
    val cloudState by app.cloudConfig.state.collectAsState()
    val labels = cloudState.bootstrap?.labels.orEmpty()
    val targetVoice = app.targetAudioVoice()
    val targetIsEnglish = targetVoice.locale == AzureTtsClient.LOCALE_EN
    var playingAll by remember { mutableStateOf(false) }
    var playingTarget by remember { mutableStateOf<String?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }

    fun targetText(n: Num): String = if (targetIsEnglish) n.en else n.pl
    fun sourceText(n: Num): String = if (targetIsEnglish) n.pl else n.en

    fun publishNumber(n: Num) {
        val target = targetText(n)
        val source = sourceText(n)
        NowVoicingBus.publish(
            NowVoicing(
                en = source,
                pl = target,
                literal = null,
                lang = if (targetIsEnglish) "en" else "pl",
                words = listOf(TokenPair(target, source))
            )
        )
    }

    fun stopNumberPlayback() {
        job?.cancel()
        job = null
        playingAll = false
        playingTarget = null
        app.audioPlayer.stop()
        NowVoicingBus.clear()
        PlaybackController.unregister()
    }

    fun playOneNumber(n: Num) {
        stopNumberPlayback()
        job = scope.launch {
            val target = targetText(n)
            playingTarget = target
            try {
                playAndAwait(app, target, targetVoice.locale, targetVoice.voice)
            } finally {
                if (playingTarget == target) playingTarget = null
            }
        }
    }

    fun startAllNumbers() {
        playingAll = true
        PlaybackController.register { stopNumberPlayback() }
        job = scope.launch {
            try {
                NUMBERS.forEach { n ->
                    val target = targetText(n)
                    playingTarget = target
                    publishNumber(n)
                    playAndAwait(app, target, targetVoice.locale, targetVoice.voice)
                    delay(200)
                }
            } finally {
                playingAll = false
                playingTarget = null
                NowVoicingBus.clear()
                PlaybackController.unregister()
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            stopNumberPlayback()
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
                    label(
                        labels,
                        "numbers.title",
                        if (targetIsEnglish) "Liczby · Numbers" else "Numbers · Liczby"
                    ),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = LbColors.Primary
                )
                Text(
                    label(
                        labels,
                        "numbers.description",
                        if (targetIsEnglish) {
                            "0–100. Dla 30–99 łącz dziesiątki i jedności — np. thirty-five (35)."
                        } else {
                            "0–100. For 30–99, combine tens + ones — e.g. trzydzieści pięć (35)."
                        }
                    ),
                    fontSize = 12.sp,
                    color = LbColors.TextSecondary
                )
            }
            val togglePlayAll = {
                if (playingAll) {
                    stopNumberPlayback()
                } else {
                    startAllNumbers()
                }
            }
            if (playingAll) {
                LbButton.Stop(label(labels, "numbers.stop", if (targetIsEnglish) "Stop" else "Stop"), onClick = togglePlayAll, icon = Icons.Default.Stop)
            } else {
                LbButton.Audio(label(labels, "numbers.play", if (targetIsEnglish) "Odtwórz" else "Play"), onClick = togglePlayAll, count = NUMBERS.size)
            }
        }
        if (!online) {
            Text(
                label(
                    labels,
                    "numbers.offline",
                    if (targetIsEnglish) {
                        "Offline — audio działa tylko dla liczb odtworzonych wcześniej."
                    } else {
                        "Offline — only numbers you've already played will have audio."
                    }
                ),
                fontSize = 11.sp,
                color = LbColors.Danger
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            itemsIndexed(NUMBERS) { index, n ->
                val target = targetText(n)
                val source = sourceText(n)
                val isPlaying = playingTarget == target
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
                            playOneNumber(n)
                        }
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = label(labels, "numbers.play", if (targetIsEnglish) "Odtwórz" else "Play"),
                        tint = LbColors.Primary,
                        modifier = Modifier
                            .size(18.dp)
                            .clickable { playOneNumber(n) }
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        n.digit,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = LbColors.Label,
                        modifier = Modifier.width(56.dp)
                    )
                    Text(
                        target,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = LbColors.Primary,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        source,
                        fontSize = 14.sp,
                        color = LbColors.TextSecondary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

private data class Num(val digit: String, val pl: String, val en: String)

/** Memorizable set: 0–29 + tens up to 100. 30–99 compose from these. */
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
    Num("21", "dwadzieścia jeden", "twenty-one"),
    Num("22", "dwadzieścia dwa", "twenty-two"),
    Num("23", "dwadzieścia trzy", "twenty-three"),
    Num("24", "dwadzieścia cztery", "twenty-four"),
    Num("25", "dwadzieścia pięć", "twenty-five"),
    Num("26", "dwadzieścia sześć", "twenty-six"),
    Num("27", "dwadzieścia siedem", "twenty-seven"),
    Num("28", "dwadzieścia osiem", "twenty-eight"),
    Num("29", "dwadzieścia dziewięć", "twenty-nine"),
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
private suspend fun playAndAwait(app: LangbangApplication, text: String, locale: String, voice: String) {
    if (text.isEmpty()) return
    val file = app.audioCache.fileFor(locale, voice, text)
    if (!app.audioCache.has(file)) {
        runCatching { app.tts.synthesize(text, voice, locale, file) }
    }
    if (!app.audioCache.has(file)) return
    suspendCancellableCoroutine<Unit> { cont ->
        app.audioPlayer.play(file) { if (cont.isActive) cont.resume(Unit) }
        cont.invokeOnCancellation { app.audioPlayer.stop() }
    }
}

private fun label(labels: Map<String, String>, key: String, fallback: String): String =
    labels[key]?.takeIf { it.isNotBlank() } ?: fallback
