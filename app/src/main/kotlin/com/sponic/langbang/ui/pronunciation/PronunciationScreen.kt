package com.sponic.langbang.ui.pronunciation

import com.sponic.langbang.ui.theme.LbColors

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sponic.langbang.LangbangApplication
import com.sponic.langbang.data.PracticePrefsStore
import com.sponic.langbang.data.model.ExampleWord
import com.sponic.langbang.data.model.PhonemeEntry
import com.sponic.langbang.data.model.TokenPair
import com.sponic.langbang.domain.NowVoicing
import com.sponic.langbang.domain.NowVoicingBus
import com.sponic.langbang.domain.ensureCachedAudio
import com.sponic.langbang.domain.sourceAudioVoice
import com.sponic.langbang.domain.targetAudioVoice
import com.sponic.langbang.domain.targetSlowVoice
import com.sponic.langbang.ui.common.CompactLessonListCard
import com.sponic.langbang.ui.common.CompactLessonListDefaults
import com.sponic.langbang.ui.common.SelectionNavButtons
import com.sponic.langbang.ui.common.StudyQueuePlayer
import com.sponic.langbang.ui.common.SubtleCheckbox
import com.sponic.langbang.ui.common.WordListPlaybackHeader
import com.sponic.langbang.ui.common.WordPlayLimitControl
import com.sponic.langbang.integrations.PronunciationScore
import kotlinx.coroutines.launch

@Composable
fun PronunciationScreen(app: LangbangApplication) {
    val cloudState by app.cloudConfig.state.collectAsState()
    val labels = cloudState.bootstrap?.labels.orEmpty()
    val data = remember { app.lessonRepo.pronunciation() }
    var selected by remember { mutableStateOf(data.phonemes.firstOrNull()) }
    val online by app.network.online.collectAsState()
    val scope = rememberCoroutineScope()
    val player = remember { StudyQueuePlayer(app, scope) }
    val allKeys = remember(data.phonemes) { data.phonemes.map { it.letter } }
    var checkedKeys by remember {
        mutableStateOf(
            app.practicePrefs.checkedWordLemmas(PracticePrefsStore.CATEGORY_PRONUNCIATION)
                .ifEmpty { allKeys.toSet() }
        )
    }
    var randomOrder by remember {
        mutableStateOf(app.practicePrefs.wordPlayRandom(PracticePrefsStore.CATEGORY_PRONUNCIATION))
    }
    var playLimitText by remember {
        mutableStateOf(app.practicePrefs.wordPlayLimit(PracticePrefsStore.CATEGORY_PRONUNCIATION).toString())
    }
    var playingWord by remember { mutableStateOf<String?>(null) }
    val playLimit = playLimitText.toIntOrNull()
        ?.coerceIn(PracticePrefsStore.MIN_WORD_PLAY_LIMIT, PracticePrefsStore.MAX_WORD_PLAY_LIMIT)
        ?: PracticePrefsStore.DEFAULT_WORD_PLAY_LIMIT

    fun setChecked(next: Set<String>) {
        checkedKeys = next
        app.practicePrefs.setCheckedWordLemmas(PracticePrefsStore.CATEGORY_PRONUNCIATION, next)
    }

    fun practiceItems(): List<PronunciationPracticeItem> {
        val pool = data.phonemes.filter { it.letter in checkedKeys }
            .flatMap { ph ->
                val examples = if (randomOrder) ph.examples.shuffled() else ph.examples
                examples.take(playLimit).map { ex -> PronunciationPracticeItem(ph, ex) }
            }
        return if (randomOrder) pool.shuffled() else pool
    }

    fun publishItem(item: PronunciationPracticeItem, lang: String, position: String) {
        NowVoicingBus.publish(
            NowVoicing(
                en = item.example.en,
                pl = item.example.pl,
                literal = null,
                lang = lang,
                position = position,
                words = listOf(TokenPair(item.example.pl, item.example.en))
            )
        )
    }

    fun startPractice() {
        val items = practiceItems()
        if (items.isEmpty()) return
        val speakEnglish = app.practicePrefs.speakEnglishFirst()
        val slowFirst = app.practicePrefs.slowFirst()
        val source = app.sourceAudioVoice()
        val target = app.targetAudioVoice()
        val slow = app.targetSlowVoice()
        player.start(
            total = items.size,
            publishParked = { i -> publishItem(items[i], "pause", "${i + 1}/${items.size}") },
            prefetchItem = { i ->
                val item = items[i]
                if (speakEnglish) {
                    app.ensureCachedAudio(item.example.en, source.locale, source.voice)
                }
                if (slowFirst) app.ensureCachedAudio(item.example.pl, target.locale, slow)
                app.ensureCachedAudio(item.example.pl, target.locale, target.voice)
            }
        ) { i ->
            val item = items[i]
            val position = "${i + 1}/${items.size}"
            selected = item.phoneme
            playingWord = item.example.pl
            if (speakEnglish) {
                publishItem(item, "en", position)
                say(item.example.en, source.locale, source.voice)
            }
            if (slowFirst) {
                publishItem(item, "pl-slow", position)
                say(item.example.pl, target.locale, slow)
            }
            publishItem(item, "pl", position)
            say(item.example.pl, target.locale, target.voice)
            playingWord = null
            if (i < items.size - 1) reveal(300L)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxSize()) {
            PhonemeList(
                phonemes = data.phonemes,
                selected = selected,
                onSelect = { selected = it },
                checkedKeys = checkedKeys,
                onTogglePhoneme = { key, checked ->
                    setChecked(if (checked) checkedKeys + key else checkedKeys - key)
                },
                onToggleAll = { checked -> setChecked(if (checked) allKeys.toSet() else emptySet()) },
                randomOrder = randomOrder,
                onRandomOrderChange = {
                    randomOrder = it
                    app.practicePrefs.setWordPlayRandom(PracticePrefsStore.CATEGORY_PRONUNCIATION, it)
                },
                enabled = !player.hasQueue,
                modifier = Modifier
                    .width(360.dp)
                    .fillMaxHeight()
                    .background(LbColors.Canvas)
            )
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                selected?.let { ph ->
                    PhonemeDetail(
                        app = app,
                        phoneme = ph,
                        phonemes = data.phonemes,
                        labels = labels,
                        online = online,
                        onSelectPhoneme = { selected = it },
                        playingWord = playingWord,
                        practiceCount = practiceItems().size,
                        practiceActive = player.hasQueue,
                        playLimitText = playLimitText,
                        onPlayLimitTextChange = {
                            playLimitText = it
                            it.toIntOrNull()?.let { n ->
                                app.practicePrefs.setWordPlayLimit(
                                    PracticePrefsStore.CATEGORY_PRONUNCIATION,
                                    n
                                )
                            }
                        },
                        onStartPractice = { startPractice() }
                    )
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            player.stop()
        }
    }
}

private data class PronunciationPracticeItem(
    val phoneme: PhonemeEntry,
    val example: ExampleWord
)

@Composable
private fun PhonemeList(
    phonemes: List<PhonemeEntry>,
    selected: PhonemeEntry?,
    onSelect: (PhonemeEntry) -> Unit,
    checkedKeys: Set<String>,
    onTogglePhoneme: (String, Boolean) -> Unit,
    onToggleAll: (Boolean) -> Unit,
    randomOrder: Boolean,
    onRandomOrderChange: (Boolean) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    // Flashcard-quiz button moved out of here to the detail header (left of "Play
    // all"), so the list runs flush to the top and shows more rows.
    LazyColumn(
        modifier = modifier,
        contentPadding = CompactLessonListDefaults.ContentPadding,
        verticalArrangement = Arrangement.spacedBy(CompactLessonListDefaults.ItemGap)
    ) {
        item(key = "all-phonemes-master") {
            val allKeys = phonemes.map { it.letter }
            WordListPlaybackHeader(
                allChecked = allKeys.isNotEmpty() && checkedKeys.containsAll(allKeys),
                onAllCheckedChange = onToggleAll,
                random = randomOrder,
                onRandomChange = onRandomOrderChange,
                enabled = enabled
            )
        }
        itemsIndexed(phonemes) { index, ph ->
            val isSelected = ph == selected
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                SubtleCheckbox(
                    checked = ph.letter in checkedKeys,
                    onCheckedChange = { onTogglePhoneme(ph.letter, it) },
                    enabled = enabled,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                CompactLessonListCard(
                    selected = isSelected,
                    onClick = { onSelect(ph) },
                    modifier = Modifier.weight(1f),
                    alternate = index % 2 == 1,
                    contentPadding = CompactLessonListDefaults.MultiLineItemPadding
                ) {
                    val glyphColor = if (isSelected) Color.White else LbColors.Primary
                    val ipaColor = if (isSelected) Color.White.copy(alpha = 0.9f)
                    else LbColors.TextSecondary
                    val hintColor = if (isSelected) Color.White.copy(alpha = 0.85f)
                    else LbColors.TextMuted
                    Text(
                        text = buildAnnotatedString {
                            withStyle(
                                SpanStyle(
                                    color = glyphColor,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            ) {
                                append(ph.letter)
                            }
                            append("  ")
                            withStyle(
                                SpanStyle(
                                    color = ipaColor,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            ) {
                                append(ph.ipa)
                            }
                            append("  ")
                            withStyle(SpanStyle(color = hintColor)) {
                                append(ph.englishApproximation)
                            }
                        },
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        color = hintColor,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun PhonemeDetail(
    app: LangbangApplication,
    phoneme: PhonemeEntry,
    phonemes: List<PhonemeEntry>,
    labels: Map<String, String>,
    online: Boolean,
    onSelectPhoneme: (PhonemeEntry) -> Unit,
    playingWord: String?,
    practiceCount: Int,
    practiceActive: Boolean,
    playLimitText: String,
    onPlayLimitTextChange: (String) -> Unit,
    onStartPractice: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val targetVoice = app.targetAudioVoice()
    // Word currently being scored (only one at a time).
    var assessing by remember(phoneme) { mutableStateOf<String?>(null) }
    // Mic capture confirmed by SDK sessionStarted event. Drives "Listening…" badge.
    var micActive by remember(phoneme) { mutableStateOf(false) }
    // Live partial transcript from Azure's `recognizing` event stream.
    var partial by remember(phoneme) { mutableStateOf("") }
    // Latest score by Polish word, cleared when the user switches phonemes.
    val scores = remember(phoneme) { mutableStateOf(mapOf<String, PronunciationScore>()) }
    var error by remember(phoneme) { mutableStateOf<String?>(null) }
    fun publishExample(ex: ExampleWord) {
        NowVoicingBus.publish(
            NowVoicing(
                en = ex.en,
                pl = ex.pl,
                literal = null,
                lang = "pl",
                words = listOf(TokenPair(ex.pl, ex.en))
            )
        )
    }

    fun playSingleExample(ex: ExampleWord) {
        scope.launch {
            publishExample(ex)
            val f = app.ensureCachedAudio(ex.pl, targetVoice.locale, targetVoice.voice) ?: return@launch
            app.audioPlayer.play(f) {}
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                phoneme.letter,
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
                color = LbColors.Primary
            )
            Spacer(Modifier.width(16.dp))
            Column {
                Text(phoneme.name, fontSize = 12.sp, color = LbColors.TextMuted)
                Text(
                    phoneme.ipa,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = LbColors.TextPrimary
                )
            }
            Spacer(Modifier.weight(1f))
            if (!practiceActive) {
                CompactPronHeaderButton(
                    label = "${label(labels, "pronunciation.play", "Play")} $practiceCount",
                    onClick = onStartPractice,
                    icon = Icons.Default.PlayArrow,
                    contentDescription = "${label(labels, "pronunciation.play", "Play")} $practiceCount",
                    containerColor = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(6.dp))
                WordPlayLimitControl(
                    limitText = playLimitText,
                    onLimitTextChange = onPlayLimitTextChange,
                    leadingLabel = "with",
                    trailingLabel = "vars"
                )
            } else {
                Text("Playing", fontSize = 12.sp, color = LbColors.TextMuted)
            }
            Spacer(Modifier.width(8.dp))
            SelectionNavButtons(
                items = phonemes,
                selected = phoneme,
                onSelect = onSelectPhoneme,
                previousContentDescription = label(labels, "pronunciation.previous", "Previous phoneme"),
                nextContentDescription = label(labels, "pronunciation.next", "Next phoneme")
            )
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = LbColors.SurfaceRaised),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)) {
                // "Sounds like" label dropped — the approximation already reads as a
                // sounds-like hint, and at full width approximation + description fit in
                // ~2 lines instead of the 3 the narrow column forced (reported 2026-05-30).
                Text(
                    phoneme.englishApproximation,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = LbColors.TextPrimary
                )
                Text(phoneme.description, fontSize = 12.sp, color = LbColors.TextSecondary)
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                label(labels, "pronunciation.common_words", "Common words"),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = LbColors.Primary,
                modifier = Modifier.weight(1f)
            )
            if (online) {
                Text(
                    label(labels, "pronunciation.tap_mic", "Tap mic to score yourself"),
                    fontSize = 11.sp,
                    color = LbColors.TextMuted
                )
            } else {
                Text(
                    label(labels, "pronunciation.offline_mic", "Offline — mic scoring unavailable"),
                    fontSize = 11.sp,
                    color = LbColors.Danger
                )
            }
        }

        // Inline "Listening…" / partial-transcript banner. Visible whenever a row is
        // being scored — bigger and louder than the per-row spinner so the user has
        // unambiguous feedback that the mic is open and what Azure thinks it's hearing.
        if (assessing != null) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (micActive) LbColors.SuccessSoft else LbColors.WarningSoft
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = if (micActive) LbColors.Success else LbColors.Label
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (micActive) {
                                "${label(labels, "pronunciation.listening_say", "Listening — say")} \"${assessing}\""
                            } else {
                                label(labels, "pronunciation.opening_mic", "Opening mic…")
                            },
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (micActive) LbColors.Success else LbColors.Label
                        )
                        if (partial.isNotBlank()) {
                            Text(
                                "${label(labels, "pronunciation.heard", "Heard")}: $partial",
                                fontSize = 12.sp,
                                color = LbColors.TextSecondary
                            )
                        }
                    }
                }
            }
        }
        error?.let {
            Text("Error: $it", color = Color.Red, fontSize = 12.sp)
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            phoneme.examples.forEach { ex ->
                ExampleRow(
                    ex = ex,
                    online = online,
                    isAssessing = assessing == ex.pl,
                    isCurrent = playingWord == ex.pl,
                    score = scores.value[ex.pl],
                    onPlay = {
                        playSingleExample(ex)
                    },
                    onMic = {
                        if (assessing != null) return@ExampleRow
                        assessing = ex.pl
                        micActive = false
                        partial = ""
                        error = null
                        scope.launch {
                            app.pron.assessOnce(
                                locale = targetVoice.locale,
                                referenceText = ex.pl,
                                onListening = { micActive = true },
                                onPartial = { partial = it }
                            )
                                .onSuccess { s ->
                                    scores.value = scores.value + (ex.pl to s)
                                }
                                .onFailure { error = it.message }
                            assessing = null
                            micActive = false
                            partial = ""
                        }
                    }
                )
            }
        }
    }
}

private fun label(labels: Map<String, String>, key: String, fallback: String): String =
    labels[key]?.takeIf { it.isNotBlank() } ?: fallback

@Composable
private fun CompactPronHeaderButton(
    label: String,
    onClick: () -> Unit,
    icon: ImageVector,
    containerColor: Color,
    contentDescription: String? = label
) {
    Surface(
        color = containerColor,
        contentColor = Color.White,
        shape = RoundedCornerShape(7.dp),
        border = BorderStroke(1.dp, containerColor),
        modifier = Modifier
            .height(30.dp)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = Color.White,
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(5.dp))
            Text(
                label,
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ExampleRow(
    ex: ExampleWord,
    online: Boolean,
    isAssessing: Boolean,
    isCurrent: Boolean = false,
    score: PronunciationScore?,
    onPlay: () -> Unit,
    onMic: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrent) LbColors.PrimarySoft else LbColors.PrimarySoft
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier
                .padding(horizontal = 12.dp, vertical = 4.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.PlayArrow,
                contentDescription = "Play",
                tint = LbColors.Primary,
                modifier = Modifier
                    .size(18.dp)
                    .clickable(onClick = onPlay)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                ex.pl,
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
                color = LbColors.Primary,
                modifier = Modifier.width(150.dp)
            )
            Text(
                ex.en,
                fontSize = 13.sp,
                color = LbColors.TextSecondary,
                modifier = Modifier.weight(1f)
            )
            score?.let {
                ScoreChip(it.accuracy.toInt())
                Spacer(Modifier.width(8.dp))
            }
            if (online) {
                if (isAssessing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    IconButton(onClick = onMic, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Default.Mic,
                            contentDescription = "Score",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ScoreChip(accuracy: Int) {
    val color = when {
        accuracy >= 80 -> LbColors.Success
        accuracy >= 60 -> LbColors.Warning
        else -> LbColors.Danger
    }
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = color.copy(alpha = 0.12f)
    ) {
        Text(
            "$accuracy",
            color = color,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}
