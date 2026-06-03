package com.sponic.langbang.ui.phrases

import com.sponic.langbang.ui.theme.LbColors

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sponic.langbang.LangbangApplication
import com.sponic.langbang.data.model.PhraseGroup
import com.sponic.langbang.data.model.SentenceExample
import com.sponic.langbang.domain.NowVoicing
import com.sponic.langbang.domain.NowVoicingBus
import com.sponic.langbang.domain.PlaybackController
import com.sponic.langbang.ui.common.StudyQueuePlayer
import com.sponic.langbang.domain.PlaybackTransport
import com.sponic.langbang.domain.ensureCachedAudio
import com.sponic.langbang.domain.playAudioAndAwait
import com.sponic.langbang.integrations.AzureTtsClient
import com.sponic.langbang.ui.common.CompactLessonListCard
import com.sponic.langbang.ui.common.CompactLessonListDefaults
import com.sponic.langbang.ui.common.DelayedEnglishTranslation
import com.sponic.langbang.ui.common.LbButton
import com.sponic.langbang.ui.common.LbChip
import com.sponic.langbang.ui.common.SelectionNavButtons
import com.sponic.langbang.ui.common.SectionLabel
import com.sponic.langbang.ui.common.StarToggle
import com.sponic.langbang.ui.common.SubtleCheckbox
import com.sponic.langbang.ui.common.WordAlignedPolish
import com.sponic.langbang.ui.theme.LbShapes
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Phrases tab — multi-sentence real-world utterances (introductions, small-talk, etc.).
 * Two-pane: left list of groups, right detail with each sentence playable individually
 * plus a "Play" queue that plays the whole group EN → PL-slow → PL per sentence.
 *
 * Custom user-added groups merge in from `UserPhraseStore` ahead of the bundled ones
 * (so personal phrases are immediately visible at the top). Editing flow is deferred
 * to a later turn — for now the bundled "Introduction — Rahul" is enough to dogfood.
 */
@Composable
fun PhrasesScreen(
    app: LangbangApplication,
    nowVoicing: @Composable () -> Unit = {}
) {
    val data = remember { app.lessonRepo.lesson5() }
    var selected by remember { mutableStateOf(data.groups.firstOrNull()) }
    val starred by app.starredPhrases.starred.collectAsState()

    Row(modifier = Modifier.fillMaxSize()) {
        PhraseGroupList(
            groups = data.groups,
            selected = selected,
            starred = starred,
            onSelect = { selected = it },
            modifier = Modifier
                .width(360.dp)
                .fillMaxHeight()
                .background(LbColors.Surface)
        )
        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
            nowVoicing()
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                selected?.let { group ->
                    PhraseDetail(
                        app = app,
                        group = group,
                        groups = data.groups,
                        onSelectGroup = { selected = it }
                    )
                } ?: Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "No phrases yet.",
                        fontSize = 13.sp, color = LbColors.TextMuted
                    )
                }
            }
        }
    }
}

@Composable
private fun PhraseGroupList(
    groups: List<PhraseGroup>,
    selected: PhraseGroup?,
    starred: Set<String>,
    onSelect: (PhraseGroup) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(key = "phrase-rail-head") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionLabel("Phrase groups", modifier = Modifier.weight(1f))
                    LbChip(
                        label = groups.size.toString(),
                        selected = false,
                        onClick = {},
                        enabled = false,
                        showCheck = false
                    )
                }
                Surface(
                    color = LbColors.SurfaceTint,
                    shape = LbShapes.Inset,
                    border = BorderStroke(1.dp, LbColors.Line),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Search, contentDescription = null, tint = LbColors.TextMuted, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Search groups...", fontSize = 13.sp, color = LbColors.TextMuted)
                    }
                }
            }
        }
        itemsIndexed(groups, key = { _, g -> "g-${g.id}" }) { index, g ->
            val isSel = g == selected
            val starCount = g.sentences.count { it.pl in starred }
            Surface(
                color = Color.White,
                shape = LbShapes.Card,
                border = BorderStroke(1.dp, if (isSel) LbColors.Primary.copy(alpha = 0.25f) else LbColors.Line),
                shadowElevation = if (isSel) 3.dp else 0.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(LbShapes.Card)
                    .clickable { onSelect(g) }
            ) {
                Row {
                    Box(
                        Modifier
                            .width(4.dp)
                            .fillMaxHeight()
                            .background(if (isSel) LbColors.Primary else Color.Transparent)
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 12.dp, vertical = 11.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            g.title,
                            color = if (isSel) LbColors.Primary else LbColors.TextPrimary,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp,
                            maxLines = 1
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            MetaChip("${g.sentences.size} sentences")
                            if (starCount > 0) MetaChip("$starCount", star = true)
                        }
                    }
                    Text(
                        phraseGroupTag(g),
                        color = LbColors.TextMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(top = 12.dp, end = 12.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PhraseDetail(
    app: LangbangApplication,
    group: PhraseGroup,
    groups: List<PhraseGroup>,
    onSelectGroup: (PhraseGroup) -> Unit
) {
    val scope = rememberCoroutineScope()
    val player = remember(group) { StudyQueuePlayer(app, scope) }
    val playingIndex = player.playingIndex
    val playing = player.hasQueue
    val starred by app.starredPhrases.starred.collectAsState()
    // "Starred only" scopes the quiz to the learner's personal deck (across ALL groups),
    // not just the current group. Sticky within this composition.
    var starredOnly by remember { mutableStateOf(false) }
    var slowFirst by remember { mutableStateOf(app.practicePrefs.slowFirst()) }
    val setSlowFirst: (Boolean) -> Unit = { enabled ->
        slowFirst = enabled
        app.practicePrefs.setSlowFirst(enabled)
    }
    // Stop playback when this group's detail leaves composition.
    DisposableEffect(group) {
        onDispose { player.stop() }
    }

    // Warm each tappable word's (normal-voice) audio in the background the moment the
    // group opens, so tapping a word in the list voices instantly instead of waiting on
    // an on-demand synth. Cancelled + restarted automatically when the group changes.
    LaunchedEffect(group) {
        group.sentences
            .flatMap { it.pl.split(PHRASE_WHITESPACE) }
            .map { it.polishWordForPhraseAudio() }
            .filter { it.isNotEmpty() }
            .distinct()
            .forEach { w -> app.ensureCachedAudio(w, AzureTtsClient.LOCALE_PL, AzureTtsClient.PL_PL_F) }
    }

    fun startPlayback(items: List<SentenceExample>, quiz: Boolean) {
        if (items.isEmpty()) return
        player.stop()
        val slowPlVoice = app.audioPrefs.slowPlVoice()
        val slow = slowFirst
        player.start(
            total = items.size,
            publishParked = { i ->
                publishNV(items[i], "pause", "${i + 1}/${items.size}", plHidden = quiz, quiz = quiz)
            },
            prefetchItem = { i ->
                val s = items[i]
                app.ensureCachedAudio(s.en, AzureTtsClient.LOCALE_EN, AzureTtsClient.EN_US_F)
                app.ensureCachedAudio(s.pl, AzureTtsClient.LOCALE_PL, AzureTtsClient.PL_PL_F)
                if (slow) app.ensureCachedAudio(s.pl, AzureTtsClient.LOCALE_PL, slowPlVoice)
            },
        ) { i ->
            val s = items[i]
            val pos = "${i + 1}/${items.size}"
            if (quiz) {
                publishNV(s, "en", pos, plHidden = true, quiz = true)
                say(s.en, AzureTtsClient.LOCALE_EN, AzureTtsClient.EN_US_F)
                publishNV(s, "pause", pos, plHidden = true, quiz = true)
                reveal(1500L)
                if (slow) {
                    publishNV(s, "pl-slow", pos, plHidden = false, quiz = true)
                    say(s.pl, AzureTtsClient.LOCALE_PL, slowPlVoice)
                }
                publishNV(s, "pl", pos, plHidden = false, quiz = true)
                say(s.pl, AzureTtsClient.LOCALE_PL, AzureTtsClient.PL_PL_F)
            } else {
                publishNV(s, "en", pos)
                say(s.en, AzureTtsClient.LOCALE_EN, AzureTtsClient.EN_US_F)
                if (slow) {
                    publishNV(s, "pl-slow", pos)
                    say(s.pl, AzureTtsClient.LOCALE_PL, slowPlVoice)
                }
                publishNV(s, "pl", pos)
                say(s.pl, AzureTtsClient.LOCALE_PL, AzureTtsClient.PL_PL_F)
            }
        }
    }

    fun playSingleWord(raw: String) {
        val word = raw.polishWordForPhraseAudio()
        if (word.isEmpty()) return
        player.stop()
        scope.launch {
            // A list word-tap is a quick lookup: one normal-rate pronunciation, played
            // straight from cache once the group warm-up above has reached it. (The
            // deliberate slow→normal study pass still lives in the Now Voicing word taps.)
            app.ensureCachedAudio(word, AzureTtsClient.LOCALE_PL, AzureTtsClient.PL_PL_F)
            playAndAwait(app, word, AzureTtsClient.LOCALE_PL, AzureTtsClient.PL_PL_F)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SectionLabel(phraseGroupTag(group))
                    Text(
                        group.title,
                        fontSize = 23.sp,
                        lineHeight = 27.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = LbColors.TextPrimary
                    )
                    if (group.subtitle.isNotBlank()) {
                        Text(
                            group.subtitle,
                            fontSize = 14.sp,
                            color = LbColors.TextSecondary
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${group.sentences.size} sentences", fontSize = 12.sp, color = LbColors.TextMuted)
                        Text("•", fontSize = 12.sp, color = LbColors.TextMuted)
                        Text("${group.sentences.count { it.pl in starred }} starred", fontSize = 12.sp, color = LbColors.TextMuted)
                    }
                }
                SelectionNavButtons(
                    items = groups,
                    selected = group,
                    onSelect = onSelectGroup,
                    previousContentDescription = "Previous phrase group",
                    nextContentDescription = "Next phrase group"
                )
            }
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (playing) {
                    LbButton.Stop(
                        label = "Stop",
                        icon = Icons.Default.Stop,
                        onClick = { player.stop() }
                    )
                } else {
                    LbButton.Audio(
                        label = "Play",
                        count = group.sentences.size,
                        onClick = {
                            startPlayback(group.sentences, quiz = false)
                        }
                    )
                }
                if (!playing) {
                    LbButton.Ghost(
                        label = "Quiz",
                        icon = Icons.Default.Quiz,
                        onClick = {
                            val pool: List<SentenceExample> =
                                if (starredOnly) {
                                    app.lessonRepo.lesson5().groups
                                        .flatMap { it.sentences }
                                        .filter { it.pl in starred }
                                } else group.sentences
                            if (pool.isNotEmpty()) {
                                startPlayback(pool, quiz = true)
                            }
                        }
                    )
                }
                PhraseToggle(
                    label = "Slow first",
                    checked = slowFirst,
                    enabled = !playing,
                    onCheckedChange = setSlowFirst
                )
                Surface(
                    color = if (starredOnly) LbColors.PrimarySoft else Color.White,
                    shape = LbShapes.Button,
                    border = BorderStroke(1.dp, if (starredOnly) LbColors.Primary else LbColors.Line),
                    modifier = Modifier
                        .height(30.dp)
                        .clip(LbShapes.Button)
                        .clickable { starredOnly = !starredOnly }
                ) {
                    Row(
                        Modifier.padding(horizontal = 11.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (starredOnly) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = null,
                            tint = if (starredOnly) LbColors.Primary else LbColors.TextMuted,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            "Starred only",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = LbColors.TextPrimary
                        )
                    }
                }
            }
        }

        // Sentence list
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            group.sentences.forEachIndexed { i, s ->
                SentenceRow(
                    sentence = s,
                    isCurrent = i == playingIndex,
                    isStarred = s.pl in starred,
                    onToggleStar = { app.starredPhrases.toggle(s.pl) },
                    onWordClick = ::playSingleWord,
                    onPlay = {
                        // Inline one-off playback stays local; queue playback drives
                        // Now Voicing so tapping a row doesn't jump the user around.
                        scope.launch {
                            val slowPlVoice = app.audioPrefs.slowPlVoice()
                            if (slowFirst) {
                                app.ensureCachedAudio(s.pl, AzureTtsClient.LOCALE_PL,
                                    slowPlVoice)
                                playAndAwait(app, s.pl, AzureTtsClient.LOCALE_PL,
                                    slowPlVoice)
                            }
                            playAndAwait(app, s.pl, AzureTtsClient.LOCALE_PL,
                                AzureTtsClient.PL_PL_F)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun PhraseToggle(
    label: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        color = if (checked) LbColors.PrimarySoft else Color.White,
        shape = LbShapes.Button,
        border = BorderStroke(1.dp, if (checked) LbColors.Primary else LbColors.Line),
        modifier = Modifier
            .height(30.dp)
            .clip(LbShapes.Button)
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
    ) {
        Row(
            Modifier.padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SubtleCheckbox(
                checked = checked,
                onCheckedChange = if (enabled) onCheckedChange else null,
                enabled = enabled,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                label,
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
                color = if (enabled) LbColors.TextPrimary else LbColors.TextMuted
            )
        }
    }
}

@Composable
private fun SentenceRow(
    sentence: SentenceExample,
    isCurrent: Boolean,
    isStarred: Boolean,
    onToggleStar: () -> Unit,
    onWordClick: (String) -> Unit,
    onPlay: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrent) LbColors.SuccessSoft else LbColors.SurfaceRaised
        ),
        border = BorderStroke(1.dp, if (isCurrent) LbColors.Audio.copy(alpha = 0.45f) else LbColors.Line),
        shape = LbShapes.Card,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                Icons.Default.PlayArrow,
                contentDescription = "Play",
                tint = LbColors.Primary,
                modifier = Modifier
                    .align(Alignment.CenterVertically)
                    .size(18.dp)
                    .clickable(onClick = onPlay)
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                DelayedEnglishTranslation(
                    text = sentence.en,
                    fontSize = 13.sp,
                    color = LbColors.TextMuted
                )
                WordAlignedPolish(
                    sentence = sentence,
                    plFontSize = 20.sp,
                    plFontWeight = FontWeight.Bold,
                    glossFontSize = 11.sp,
                    onPlWordClick = onWordClick
                )
            }
            StarToggle(selected = isStarred, onClick = onToggleStar)
        }
    }
}

@Composable
private fun MetaChip(text: String, star: Boolean = false) {
    Surface(
        color = if (star) LbColors.GoldSoft else LbColors.SurfaceTint,
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(1.dp, if (star) LbColors.Star.copy(alpha = 0.25f) else LbColors.Line)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (star) {
                Icon(Icons.Filled.Star, contentDescription = null, tint = LbColors.Star, modifier = Modifier.size(11.dp))
                Spacer(Modifier.width(3.dp))
            }
            Text(text, fontSize = 11.sp, color = if (star) LbColors.Star else LbColors.TextMuted, fontWeight = FontWeight.Bold)
        }
    }
}

private fun phraseGroupTag(group: PhraseGroup): String {
    val text = "${group.title} ${group.subtitle}".lowercase()
    return when {
        "intro" in text || "meet" in text -> "Conversation"
        "home" in text || "daily" in text || "food" in text -> "Daily life"
        "travel" in text || "city" in text || "shop" in text -> "Around town"
        else -> "Practice"
    }
}

private const val PHRASE_WORD_EDGE_PUNCTUATION = ".,;:!?()[]{}\"'"
private val PHRASE_WHITESPACE = Regex("\\s+")

private fun String.polishWordForPhraseAudio(): String =
    trim().trim { PHRASE_WORD_EDGE_PUNCTUATION.contains(it) }

private fun publishNV(
    s: SentenceExample,
    lang: String,
    position: String,
    plHidden: Boolean = false,
    quiz: Boolean = false
) {
    NowVoicingBus.publish(
        NowVoicing(
            en = s.en, pl = s.pl, literal = s.literal,
            lang = lang, position = position, words = s.words,
            plHidden = plHidden, quizMode = quiz
        )
    )
}

private suspend fun playAndAwait(
    app: LangbangApplication,
    text: String,
    locale: String,
    voice: String
) {
    app.playAudioAndAwait(text, locale, voice)
}
