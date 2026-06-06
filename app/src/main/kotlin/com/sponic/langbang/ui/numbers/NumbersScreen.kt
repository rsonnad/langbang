package com.sponic.langbang.ui.numbers

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sponic.langbang.LangbangApplication
import com.sponic.langbang.data.PracticePrefsStore
import com.sponic.langbang.data.model.TokenPair
import com.sponic.langbang.domain.NowVoicing
import com.sponic.langbang.domain.NowVoicingBus
import com.sponic.langbang.domain.PlaybackController
import com.sponic.langbang.domain.ensureCachedAudio
import com.sponic.langbang.domain.playAudioAndAwait
import com.sponic.langbang.domain.sourceAudioVoice
import com.sponic.langbang.domain.targetAudioVoice
import com.sponic.langbang.domain.targetSlowVoice
import com.sponic.langbang.integrations.AzureTtsClient
import com.sponic.langbang.ui.common.CompactLessonListDefaults
import com.sponic.langbang.ui.common.LbButton
import com.sponic.langbang.ui.common.StudyQueuePlayer
import com.sponic.langbang.ui.common.SubtleCheckbox
import com.sponic.langbang.ui.common.WordListPlaybackHeader
import com.sponic.langbang.ui.common.WordPlayLimitControl
import com.sponic.langbang.ui.theme.LbColors
import kotlinx.coroutines.launch

/**
 * Polish cardinal numbers in practical memorization groups. Playback uses the same
 * shared Now Voicing queue as the word-list screens, so Stop/Pause/Next plus the
 * Now Voicing English and Slow toggles behave consistently.
 */
@Composable
fun NumbersScreen(app: LangbangApplication) {
    val scope = rememberCoroutineScope()
    val player = remember { StudyQueuePlayer(app, scope) }
    val online by app.network.online.collectAsState()
    val cloudState by app.cloudConfig.state.collectAsState()
    val labels = cloudState.bootstrap?.labels.orEmpty()
    val targetVoice = app.targetAudioVoice()
    val targetIsEnglish = targetVoice.locale == AzureTtsClient.LOCALE_EN
    val allKeys = remember { NUMBERS.map { it.digit }.toSet() }
    var checkedKeys by remember {
        mutableStateOf(
            app.practicePrefs.checkedWordLemmas(PracticePrefsStore.CATEGORY_NUMBERS)
                .ifEmpty { allKeys }
        )
    }
    var randomOrder by remember {
        mutableStateOf(app.practicePrefs.wordPlayRandom(PracticePrefsStore.CATEGORY_NUMBERS))
    }
    var playLimitText by remember {
        mutableStateOf(app.practicePrefs.wordPlayLimit(PracticePrefsStore.CATEGORY_NUMBERS).toString())
    }
    var playingKey by remember { mutableStateOf<String?>(null) }

    val playLimit = playLimitText.toIntOrNull()
        ?.coerceIn(PracticePrefsStore.MIN_WORD_PLAY_LIMIT, PracticePrefsStore.MAX_WORD_PLAY_LIMIT)
        ?: PracticePrefsStore.DEFAULT_WORD_PLAY_LIMIT

    fun targetText(n: Num): String = if (targetIsEnglish) n.en else n.pl
    fun sourceText(n: Num): String = if (targetIsEnglish) n.pl else n.en

    fun setChecked(next: Set<String>) {
        checkedKeys = next
        app.practicePrefs.setCheckedWordLemmas(PracticePrefsStore.CATEGORY_NUMBERS, next)
    }

    fun practiceItems(): List<Num> {
        val selected = NUMBERS.filter { it.digit in checkedKeys }
        val ordered = if (randomOrder) selected.shuffled() else selected
        return buildList {
            repeat(playLimit) {
                addAll(if (randomOrder) ordered.shuffled() else ordered)
            }
        }
    }

    fun publishNumber(n: Num, lang: String, position: String? = null) {
        NowVoicingBus.publish(
            NowVoicing(
                en = n.en,
                pl = n.pl,
                literal = null,
                lang = lang,
                position = position,
                words = listOf(TokenPair(n.pl, n.en))
            )
        )
    }

    fun playOneNumber(n: Num) {
        if (player.hasQueue) PlaybackController.parkCurrent()
        scope.launch {
            val target = targetText(n)
            playingKey = n.digit
            publishNumber(n, if (targetIsEnglish) "en" else "pl")
            try {
                app.playAudioAndAwait(target, targetVoice.locale, targetVoice.voice)
            } finally {
                if (playingKey == n.digit) playingKey = null
            }
        }
    }

    fun startPractice() {
        val items = practiceItems()
        if (items.isEmpty()) return
        val speakSourceFirst = app.practicePrefs.speakEnglishFirst()
        val slowFirst = app.practicePrefs.slowFirst() && !targetIsEnglish
        val source = app.sourceAudioVoice()
        val target = app.targetAudioVoice()
        val slow = app.targetSlowVoice()
        player.start(
            total = items.size,
            publishParked = { i ->
                publishNumber(items[i], "pause", "${i + 1}/${items.size}")
            },
            prefetchItem = { i ->
                val item = items[i]
                if (speakSourceFirst) {
                    app.ensureCachedAudio(sourceText(item), source.locale, source.voice)
                }
                if (slowFirst) {
                    app.ensureCachedAudio(targetText(item), target.locale, slow)
                }
                app.ensureCachedAudio(targetText(item), target.locale, target.voice)
            }
        ) { i ->
            val item = items[i]
            val position = "${i + 1}/${items.size}"
            playingKey = item.digit
            if (speakSourceFirst) {
                publishNumber(item, if (targetIsEnglish) "pl" else "en", position)
                say(sourceText(item), source.locale, source.voice)
            }
            if (slowFirst) {
                publishNumber(item, "pl-slow", position)
                say(targetText(item), target.locale, slow)
            }
            publishNumber(item, if (targetIsEnglish) "en" else "pl", position)
            say(targetText(item), target.locale, target.voice)
            playingKey = null
            if (i < items.size - 1) reveal(250L)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            player.stop()
        }
    }

    val practiceCount = practiceItems().size

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
                            "0–1000. Ćwicz liczby pojedynczo albo jako zaznaczoną kolejkę."
                        } else {
                            "0–1000. Practice individual numbers or the checked queue."
                        }
                    ),
                    fontSize = 12.sp,
                    color = LbColors.TextSecondary
                )
            }
            if (!player.hasQueue) {
                WordPlayLimitControl(
                    limitText = playLimitText,
                    onLimitTextChange = {
                        playLimitText = it
                        it.toIntOrNull()?.let { n ->
                            app.practicePrefs.setWordPlayLimit(PracticePrefsStore.CATEGORY_NUMBERS, n)
                        }
                    },
                    leadingLabel = "with",
                    trailingLabel = "sets",
                    modifier = Modifier.padding(end = 8.dp)
                )
                LbButton.Audio(
                    label(labels, "numbers.play", if (targetIsEnglish) "Odtwórz" else "Play"),
                    onClick = { startPractice() },
                    enabled = practiceCount > 0,
                    count = practiceCount
                )
            } else {
                Text(
                    "Playing",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = LbColors.TextMuted
                )
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
            item(key = "number-master-controls") {
                WordListPlaybackHeader(
                    allChecked = checkedKeys.containsAll(allKeys),
                    onAllCheckedChange = { checked -> setChecked(if (checked) allKeys else emptySet()) },
                    random = randomOrder,
                    onRandomChange = {
                        randomOrder = it
                        app.practicePrefs.setWordPlayRandom(PracticePrefsStore.CATEGORY_NUMBERS, it)
                    },
                    enabled = !player.hasQueue
                )
            }
            NUMBER_GROUPS.forEach { group ->
                item(key = "group-${group.label}") {
                    Text(
                        group.label,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = LbColors.TextMuted,
                        modifier = Modifier.padding(top = 5.dp, bottom = 1.dp)
                    )
                }
                items(group.numbers, key = { it.digit }) { n ->
                    NumberRow(
                        number = n,
                        target = targetText(n),
                        source = sourceText(n),
                        checked = n.digit in checkedKeys,
                        enabled = !player.hasQueue,
                        isPlaying = playingKey == n.digit,
                        onCheckedChange = { checked ->
                            setChecked(if (checked) checkedKeys + n.digit else checkedKeys - n.digit)
                        },
                        onPlay = { playOneNumber(n) },
                        labels = labels
                    )
                }
            }
        }
    }
}

@Composable
private fun NumberRow(
    number: Num,
    target: String,
    source: String,
    checked: Boolean,
    enabled: Boolean,
    isPlaying: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onPlay: () -> Unit,
    labels: Map<String, String>
) {
    val rowColor = when {
        isPlaying -> LbColors.GoldSoft
        number.alternate -> CompactLessonListDefaults.AlternateItemColor
        else -> LbColors.Sheet
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(rowColor)
            .clickable(onClick = onPlay)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SubtleCheckbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(6.dp))
        Icon(
            Icons.Default.PlayArrow,
            contentDescription = label(labels, "numbers.play", "Play"),
            tint = LbColors.Primary,
            modifier = Modifier
                .size(18.dp)
                .clickable(onClick = onPlay)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            number.digit,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = LbColors.Label,
            modifier = Modifier.width(58.dp)
        )
        Text(
            target,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = LbColors.Primary,
            modifier = Modifier.weight(1f)
        )
        Text(
            source,
            fontSize = 13.sp,
            color = LbColors.TextSecondary,
            modifier = Modifier.weight(1f)
        )
    }
}

private data class Num(
    val digit: String,
    val pl: String,
    val en: String,
    val alternate: Boolean
)

private data class NumberGroup(
    val label: String,
    val numbers: List<Num>
)

private fun nums(vararg entries: Triple<String, String, String>): List<Num> =
    entries.mapIndexed { index, (digit, pl, en) -> Num(digit, pl, en, index % 2 == 1) }

private val NUMBER_GROUPS: List<NumberGroup> = listOf(
    NumberGroup(
        "1–10",
        nums(
            Triple("1", "jeden", "one"),
            Triple("2", "dwa", "two"),
            Triple("3", "trzy", "three"),
            Triple("4", "cztery", "four"),
            Triple("5", "pięć", "five"),
            Triple("6", "sześć", "six"),
            Triple("7", "siedem", "seven"),
            Triple("8", "osiem", "eight"),
            Triple("9", "dziewięć", "nine"),
            Triple("10", "dziesięć", "ten")
        )
    ),
    NumberGroup(
        "10–20",
        nums(
            Triple("11", "jedenaście", "eleven"),
            Triple("12", "dwanaście", "twelve"),
            Triple("13", "trzynaście", "thirteen"),
            Triple("14", "czternaście", "fourteen"),
            Triple("15", "piętnaście", "fifteen"),
            Triple("16", "szesnaście", "sixteen"),
            Triple("17", "siedemnaście", "seventeen"),
            Triple("18", "osiemnaście", "eighteen"),
            Triple("19", "dziewiętnaście", "nineteen"),
            Triple("20", "dwadzieścia", "twenty")
        )
    ),
    NumberGroup(
        "21–30",
        nums(
            Triple("21", "dwadzieścia jeden", "twenty-one"),
            Triple("22", "dwadzieścia dwa", "twenty-two"),
            Triple("23", "dwadzieścia trzy", "twenty-three"),
            Triple("24", "dwadzieścia cztery", "twenty-four"),
            Triple("25", "dwadzieścia pięć", "twenty-five"),
            Triple("26", "dwadzieścia sześć", "twenty-six"),
            Triple("27", "dwadzieścia siedem", "twenty-seven"),
            Triple("28", "dwadzieścia osiem", "twenty-eight"),
            Triple("29", "dwadzieścia dziewięć", "twenty-nine"),
            Triple("30", "trzydzieści", "thirty")
        )
    ),
    NumberGroup(
        "40–100",
        nums(
            Triple("40", "czterdzieści", "forty"),
            Triple("50", "pięćdziesiąt", "fifty"),
            Triple("60", "sześćdziesiąt", "sixty"),
            Triple("70", "siedemdziesiąt", "seventy"),
            Triple("80", "osiemdziesiąt", "eighty"),
            Triple("90", "dziewięćdziesiąt", "ninety"),
            Triple("100", "sto", "one hundred")
        )
    ),
    NumberGroup(
        "200–1000",
        nums(
            Triple("200", "dwieście", "two hundred"),
            Triple("300", "trzysta", "three hundred"),
            Triple("400", "czterysta", "four hundred"),
            Triple("500", "pięćset", "five hundred"),
            Triple("600", "sześćset", "six hundred"),
            Triple("700", "siedemset", "seven hundred"),
            Triple("800", "osiemset", "eight hundred"),
            Triple("900", "dziewięćset", "nine hundred"),
            Triple("1000", "tysiąc", "one thousand")
        )
    )
)

private val NUMBERS: List<Num> = NUMBER_GROUPS.flatMap { it.numbers }

private fun label(labels: Map<String, String>, key: String, fallback: String): String =
    labels[key]?.takeIf { it.isNotBlank() } ?: fallback
