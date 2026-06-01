package com.sponic.langbang.ui

import com.sponic.langbang.ui.theme.LbColors

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.sponic.langbang.LangbangApplication
import com.sponic.langbang.BuildConfig
import com.sponic.langbang.data.IncludeMode
import com.sponic.langbang.data.PlayMode
import com.sponic.langbang.data.RandomConfig
import com.sponic.langbang.domain.NowVoicing
import com.sponic.langbang.domain.NowVoicingBus
import com.sponic.langbang.domain.PlaybackController
import com.sponic.langbang.domain.PrefetchProgress
import com.sponic.langbang.domain.PrefetchWorker
import com.sponic.langbang.ui.common.GrammarVisuals
import com.sponic.langbang.ui.common.FilterGroup
import com.sponic.langbang.ui.common.LbChip
import com.sponic.langbang.ui.lessons.AdjectivesScreen
import com.sponic.langbang.ui.lessons.AdverbsScreen
import com.sponic.langbang.ui.lessons.LessonScreen
import com.sponic.langbang.ui.lessons.NounsScreen
import com.sponic.langbang.ui.phrases.PhrasesScreen
import com.sponic.langbang.ui.numbers.NumbersScreen
import com.sponic.langbang.ui.pronunciation.PronunciationScreen
import com.sponic.langbang.ui.quizzes.QuizzesScreen
import com.sponic.langbang.ui.settings.SettingsScreen
import kotlinx.coroutines.launch
import java.util.Locale

private enum class Section(val tabLabel: String) {
    // Short labels — original 6-tab strip + the "Play Phrases" pill overflowed
    // the 1920px landscape width on the Tab A9+ and Quizzes wasn't reachable
    // without a tab-strip swipe nobody discovered.
    Pronunciation("1. Pron"),
    Verbs("2. Verbs"),
    Adjectives("3. Adj"),
    Adverbs("4. Adv"),
    Nouns("5. Nouns"),
    Phrases("6. Phrases"),
    Quizzes("7. Quiz"),
    Numbers("8. Num"),
    Settings("Settings")
}

private val TabSections = listOf(
    Section.Pronunciation,
    Section.Verbs,
    Section.Adjectives,
    Section.Adverbs,
    Section.Nouns,
    Section.Phrases,
    Section.Quizzes,
    Section.Numbers
)

@Composable
fun LangbangApp(app: LangbangApplication) {
    val context = LocalContext.current
    val workFlow = remember {
        WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(PrefetchWorker.UNIQUE_NAME)
    }
    val infos by workFlow.collectAsState(initial = emptyList())
    val progress = infos.toPrefetchProgress()

    var section by remember { mutableStateOf(Section.Pronunciation) }
    var lastTabSection by remember { mutableStateOf(Section.Pronunciation) }
    val nowVoicing by NowVoicingBus.state.collectAsState()
    val playbackTransport by PlaybackController.transport.collectAsState()
    val online by app.network.online.collectAsState()
    // Starred phrases — lets the user add whatever is currently voicing to their
    // personal quiz deck straight from the sticky Now Voicing panel.
    val starredPhrases by app.starredPhrases.starred.collectAsState()
    // The sticky panel needs to keep showing the last phrase even after playback ends.
    // nowVoicing flips to null on stop; pinnedVoicing holds the last non-null value so
    // the user can still read it (and click words for drill-down).
    var pinnedVoicing by remember { mutableStateOf<NowVoicing?>(null) }
    LaunchedEffect(nowVoicing) {
        nowVoicing?.let { pinnedVoicing = it }
    }
    val scope = rememberCoroutineScope()
    val randomPlayer = remember { RandomPlayerState(app, scope) }
    var showConfigSheet by remember { mutableStateOf(false) }
    // Carries a Polish word from a word-click into the sheet's must-contain box.
    // Reset to null after the sheet consumes it so reopening starts clean.
    var configSeedWord by remember { mutableStateOf<String?>(null) }
    // Guards the tap-title-to-update flow against double-taps while a check/download runs.
    var checkingUpdate by remember { mutableStateOf(false) }
    // Live config — drives the in-panel controls strip + the Play Phrases sheet
    // initial values. Updates persist immediately so other readers (VerbsTab quiz
    // delay, RandomPlayer collect) see the latest values.
    var liveConfig by remember { mutableStateOf(app.randomConfig.load()) }
    val updateConfig: (RandomConfig) -> Unit = remember {
        { c ->
            liveConfig = c
            app.randomConfig.save(c)
            randomPlayer.reconfigure(c)
        }
    }
    // Navigating away (switching tabs or opening Settings) silences whatever's
    // currently voicing — the random player and any registered quiz/play-all queue.
    // Moving on should stop the previous drill, not leave audio running underneath.
    val stopActivePlayback: () -> Unit = {
        if (randomPlayer.playing || randomPlayer.paused) randomPlayer.stop()
        PlaybackController.stop()
        pinnedVoicing = null
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .background(LbColors.Canvas)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            AppHeader(
                section = section,
                onSelect = {
                    if (it != section) stopActivePlayback()
                    section = it
                    if (it != Section.Settings) lastTabSection = it
                },
                onToggleSettings = {
                    stopActivePlayback()
                    section = if (section == Section.Settings) lastTabSection
                    else Section.Settings
                },
                prefetch = progress,
                randomPlayer = randomPlayer,
                liveConfig = liveConfig,
                onOpenConfig = { showConfigSheet = true },
                onTitleClick = {
                    // Tap the title → check R2 for a newer build and, if found,
                    // download + launch the installer right away.
                    if (!checkingUpdate) {
                        checkingUpdate = true
                        Toast.makeText(context, "Checking for updates…", Toast.LENGTH_SHORT).show()
                        scope.launch {
                            val avail = app.updateChecker.check()
                            when {
                                avail == null -> {
                                    Toast.makeText(
                                        context,
                                        "LangBang is up to date (${BuildConfig.VERSION_NAME})",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    checkingUpdate = false
                                }
                                !app.updateChecker.canInstall() -> {
                                    Toast.makeText(
                                        context,
                                        "Allow installs for LangBang, then tap the title again",
                                        Toast.LENGTH_LONG
                                    ).show()
                                    app.updateChecker.openInstallPermissionSettings()
                                    checkingUpdate = false
                                }
                                else -> {
                                    Toast.makeText(
                                        context,
                                        "Downloading ${avail.versionName}…",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    val apk = app.updateChecker.download(avail.url)
                                    checkingUpdate = false
                                    if (apk == null) {
                                        Toast.makeText(
                                            context, "Update download failed", Toast.LENGTH_SHORT
                                        ).show()
                                    } else {
                                        app.updateChecker.install(apk)
                                    }
                                }
                            }
                        }
                    }
                }
            )
            if (!online) {
                Surface(color = LbColors.DangerSoft, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "Offline — cached content only. Generation + audio synth disabled.",
                        fontSize = 11.sp,
                        color = LbColors.Danger,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
            }
            UpdateBanner(app = app)
            SentenceRegenBanner(app = app)
            // App-wide Now Voicing surface. Audio can start from any tab (for example,
            // the global Play Phrases button while Pronunciation is open), so the
            // canonical panel lives in the shell instead of inside individual lesson
            // screens.
            val nowVoicingSlot: @Composable () -> Unit = {
                NowVoicingPanel(
                    pinned = pinnedVoicing,
                    live = nowVoicing,
                    config = liveConfig,
                    onConfigChange = updateConfig,
                    isStarred = pinnedVoicing?.pl?.let { it in starredPhrases } == true,
                    onToggleStar = { pinnedVoicing?.pl?.let { app.starredPhrases.toggle(it) } },
                    onPlWordClick = { word ->
                        if (randomPlayer.playing) randomPlayer.stop()
                        configSeedWord = word
                        showConfigSheet = true
                    },
                    prefetch = progress
                )
            }
            if (pinnedVoicing != null || nowVoicing != null || playbackTransport != null ||
                randomPlayer.playing || randomPlayer.paused
            ) {
                nowVoicingSlot()
            }
            val noNowVoicingSlot: @Composable () -> Unit = {}
            Row(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.weight(1.5f).fillMaxHeight()) {
                    when (section) {
                        Section.Pronunciation -> PronunciationScreen(app = app)
                        Section.Verbs -> LessonScreen(
                            app = app, prefetch = progress, nowVoicing = noNowVoicingSlot
                        )
                        Section.Adjectives -> AdjectivesScreen(
                            app = app, prefetch = progress, nowVoicing = noNowVoicingSlot
                        )
                        Section.Adverbs -> AdverbsScreen(
                            app = app, prefetch = progress, nowVoicing = noNowVoicingSlot
                        )
                        Section.Nouns -> NounsScreen(
                            app = app, prefetch = progress, nowVoicing = noNowVoicingSlot
                        )
                        Section.Phrases -> PhrasesScreen(app = app, nowVoicing = noNowVoicingSlot)
                        Section.Numbers -> NumbersScreen(app = app)
                        Section.Quizzes -> QuizzesScreen(app = app, nowVoicing = noNowVoicingSlot)
                        Section.Settings -> SettingsScreen(app = app)
                    }
                }
            }
        }
        if (showConfigSheet) {
            RandomConfigSheet(
                app = app,
                initial = liveConfig,
                initialMustContain = configSeedWord ?: liveConfig.mustContainWord,
                randomPlayer = randomPlayer,
                onCancel = {
                    showConfigSheet = false
                    configSeedWord = null
                },
                onPlay = { config ->
                    updateConfig(config)
                    configSeedWord = null
                    randomPlayer.start(config)
                    // Keep sheet open so the user sees Now Playing controls + can
                    // tweak config without re-tapping the pill.
                }
            )
        }
    }
}

@Composable
private fun AppHeader(
    section: Section,
    onSelect: (Section) -> Unit,
    onToggleSettings: () -> Unit,
    prefetch: PrefetchProgress,
    randomPlayer: RandomPlayerState,
    liveConfig: RandomConfig,
    onOpenConfig: () -> Unit,
    onTitleClick: () -> Unit
) {
    Surface(
        color = LbColors.Bar,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth()
                    .border(0.dp, Color.Transparent)
                    .padding(start = 10.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onToggleSettings,
                    modifier = Modifier.size(34.dp),
                    colors = IconButtonDefaults.iconButtonColors(
                        contentColor = if (section == Section.Settings) LbColors.Primary
                        else LbColors.TextPrimary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Filled.Tune,
                        contentDescription = "Settings",
                        modifier = Modifier.size(20.dp)
                    )
                }
                Text(
                    "LangBang",
                    color = LbColors.TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    // Tap the title to check R2 for a newer build and update on the spot.
                    modifier = Modifier
                        .clickable(onClick = onTitleClick)
                        .padding(start = 6.dp, end = 16.dp)
                )
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TabSections.forEach { s ->
                        TabPill(
                            label = s.tabLabel,
                            selected = section == s,
                            onClick = { onSelect(s) }
                        )
                    }
                }
                // Top bar stays single-row tall. The CacheBadge ("audio 9667/10054")
                // used to sit stacked under the pill here — moved to the chip strip
                // below where there's idle horizontal space, so the header collapses
                // to one row.
                RandomPlayPill(
                    playing = randomPlayer.playing || randomPlayer.paused,
                    onToggle = {
                        if (randomPlayer.playing || randomPlayer.paused) randomPlayer.stop()
                        else randomPlayer.start(liveConfig)
                    },
                    onConfigure = onOpenConfig
                )
            }
            if (!prefetch.finished && prefetch.total > 0) {
                // Thin progress bar only — the numeric counter lives in CacheBadge
                // under the Play Phrases pill so we don't duplicate the same data.
                LinearProgressIndicator(
                    progress = { prefetch.ratio },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = LbColors.Audio,
                    trackColor = LbColors.Line
                )
            }
            // NowVoicingPanel used to live here as a full-width band below the tab
            // row — it ate a horizontal strip across the top and pushed the lesson
            // list down so only a few items were visible (reported 2026-05-30). It
            // now lives in a right-hand column next to the lesson content (see the
            // body Row in LangbangApp) so the left panel gets full height.
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NowVoicingPanel(
    pinned: NowVoicing?,
    live: NowVoicing?,
    config: RandomConfig,
    onConfigChange: (RandomConfig) -> Unit,
    isStarred: Boolean = false,
    onToggleStar: () -> Unit = {},
    onPlWordClick: (String) -> Unit = {},
    prefetch: PrefetchProgress
) {
    Surface(
        color = GrammarVisuals.NowVoicingPanel.Background,
        shape = RoundedCornerShape(0.dp),
        border = BorderStroke(
            GrammarVisuals.NowVoicingPanel.BorderWidth,
            GrammarVisuals.NowVoicingPanel.Border
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 0.dp, vertical = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TransportGrid()
            Box(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .width(1.dp)
                    .height(86.dp)
                    .background(LbColors.OnDark2.copy(alpha = 0.22f))
            )
            Box(modifier = Modifier.weight(1f).padding(end = 18.dp)) {
                NowVoicingContent(pinned, live, onPlWordClick, showStatus = false)
            }
            Column(
                modifier = Modifier.width(340.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (pinned != null) {
                        IconButton(onClick = onToggleStar, modifier = Modifier.size(34.dp)) {
                            Icon(
                                if (isStarred) Icons.Default.Star else Icons.Default.StarBorder,
                                contentDescription = if (isStarred) "Unstar phrase" else "Star phrase",
                                tint = if (isStarred) LbColors.Star else LbColors.OnDark2,
                                modifier = Modifier.size(21.dp)
                            )
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            nowVoicingStatus(pinned, live).uppercase(),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = LbColors.OnDark2,
                            maxLines = 1
                        )
                        LinearProgressIndicator(
                            progress = { nowVoicingProgress(pinned) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = LbColors.AudioBright,
                            trackColor = LbColors.OnDark2.copy(alpha = 0.22f)
                        )
                    }
                }
                NowVoicingFilterChecks(
                    config = config,
                    onConfigChange = onConfigChange,
                    showQuizDelay = live?.quizMode == true,
                    prefetch = prefetch
                )
            }
        }
    }
}

private fun nowVoicingStatus(pinned: NowVoicing?, live: NowVoicing?): String {
    val activeLang = live?.lang
    val pos = pinned?.position?.let { " · $it" } ?: ""
    val tag = when (activeLang) {
        "pl-slow" -> "PL (slow)"
        "pl" -> "PL"
        "en" -> "EN"
        "pause" -> ""
        null -> "idle"
        else -> activeLang
    }
    return if (tag.isEmpty()) "NOW VOICING$pos" else "NOW VOICING$pos  ·  $tag"
}

private fun nowVoicingProgress(pinned: NowVoicing?): Float {
    val raw = pinned?.position ?: return 0f
    val m = Regex("(\\d+)\\s*/\\s*(\\d+)").find(raw) ?: return 0f
    val idx = m.groupValues[1].toFloatOrNull() ?: return 0f
    val total = m.groupValues[2].toFloatOrNull()?.takeIf { it > 0f } ?: return 0f
    return (idx / total).coerceIn(0f, 1f)
}

/**
 * Sticky-header Now Voicing body. Delegates to the shared
 * [com.sponic.langbang.ui.common.NowVoicingBody] so the in-sheet panel
 * (RandomConfigSheet.NowPlayingPanel) and this one render identically — any visual
 * tweak made in NowVoicingBody.kt propagates to both surfaces automatically.
 */
@Composable
private fun NowVoicingContent(
    pinned: NowVoicing?,
    live: NowVoicing?,
    onPlWordClick: (String) -> Unit,
    showStatus: Boolean = true
) {
    com.sponic.langbang.ui.common.NowVoicingBody(
        pinned = pinned,
        live = live,
        statusText = if (showStatus) nowVoicingStatus(pinned, live) else "",
        onPlWordClick = onPlWordClick,
        idlePlaceholder = "Playback is starting...",
        dark = true
    )
}

/**
 * Transport row pinned to the right of the NowVoicing header. Reads the active
 * [PlaybackTransport] from [PlaybackController] so the same buttons appear regardless
 * of which source registered (RandomPlayer, VerbsTab quiz, Adjective quiz, …). Buttons
 * the source doesn't support stay visible but disabled — so layout doesn't shift.
 */
@Composable
private fun TransportGrid() {
    val transport by PlaybackController.transport.collectAsState()
    val anyPlaying by PlaybackController.playing.collectAsState()
    val isPaused = transport?.isPaused?.invoke() == true
    val playPauseIcon = if (isPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause
    val playPauseLabel = if (isPaused) "Resume" else "Pause"

    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .border(1.dp, LbColors.OnDark2.copy(alpha = 0.22f), RoundedCornerShape(14.dp))
            .padding(6.dp)
    ) {
        TransportIcon(
            icon = Icons.Filled.Replay,
            label = "Restart",
            enabled = transport?.restart != null,
            onClick = { PlaybackController.restart() }
        )
        TransportIcon(
            icon = Icons.Filled.SkipPrevious,
            label = "Rewind",
            enabled = transport?.rewind != null,
            onClick = { PlaybackController.rewind() }
        )
        TransportIcon(
            icon = playPauseIcon,
            label = playPauseLabel,
            enabled = transport?.pauseResume != null,
            onClick = { PlaybackController.pauseResume() }
        )
        TransportIcon(
            icon = Icons.Filled.Stop,
            label = "Stop",
            enabled = anyPlaying,
            onClick = { PlaybackController.stop() }
        )
    }
}

@Composable
private fun NowVoicingFilterChecks(
    config: RandomConfig,
    onConfigChange: (RandomConfig) -> Unit,
    showQuizDelay: Boolean,
    prefetch: PrefetchProgress
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FilterGroup(label = "Voice", dark = true) {
            LbChip(
                "Sentence",
                selected = config.playMode == PlayMode.PHRASES,
                selectedColor = LbColors.DarkScope,
                dark = true,
                showCheck = false,
                onClick = { onConfigChange(config.copy(playMode = PlayMode.PHRASES)) }
            )
            LbChip(
                "Verb",
                selected = config.playMode == PlayMode.VERBS,
                selectedColor = LbColors.DarkScope,
                dark = true,
                showCheck = false,
                onClick = { onConfigChange(config.copy(playMode = PlayMode.VERBS)) }
            )
            if (config.playMode == PlayMode.PHRASES) {
                LbChip(
                    "Adjective",
                    selected = config.adjectiveMode != IncludeMode.OFF,
                    selectedColor = LbColors.DarkScope,
                    dark = true,
                    showCheck = false,
                    onClick = {
                        val now = if (config.adjectiveMode == IncludeMode.OFF) IncludeMode.YES
                                  else IncludeMode.OFF
                        onConfigChange(config.copy(adjectiveMode = now))
                    }
                )
                LbChip(
                    "Adverb",
                    selected = config.adverbMode != IncludeMode.OFF,
                    selectedColor = LbColors.DarkScope,
                    dark = true,
                    showCheck = false,
                    onClick = {
                        val now = if (config.adverbMode == IncludeMode.OFF) IncludeMode.YES
                                  else IncludeMode.OFF
                        onConfigChange(config.copy(adverbMode = now))
                    }
                )
            }
        }
        FilterGroup(label = "Tense", dark = true) {
            LbChip(
                "Present",
                selected = "present" in config.tenses,
                selectedColor = LbColors.DarkScope,
                dark = true,
                showCheck = false,
                onClick = {
                    val now = if ("present" in config.tenses) config.tenses - "present"
                              else config.tenses + "present"
                    onConfigChange(config.copy(tenses = now))
                }
            )
            LbChip(
                "Past",
                selected = "past" in config.tenses,
                selectedColor = LbColors.DarkScope,
                dark = true,
                showCheck = false,
                onClick = {
                    val now = if ("past" in config.tenses) config.tenses - "past"
                              else config.tenses + "past"
                    onConfigChange(config.copy(tenses = now))
                }
            )
            if (showQuizDelay) {
                QuizDelayControl(
                    seconds = config.quizDelaySeconds,
                    onChange = { onConfigChange(config.copy(quizDelaySeconds = it)) }
                )
            }
        }
        CacheBadge(prefetch, dark = true, onlyWhileRunning = true)
    }
}

@Composable
private fun FilterCheck(
    label: String,
    selected: Boolean,
    labelColor: Color,
    onToggle: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .clickable(onClick = onToggle)
            .padding(horizontal = 2.dp, vertical = 1.dp)
    ) {
        Box(
            modifier = Modifier
                .size(14.dp)
                .border(
                    width = 1.5.dp,
                    color = if (selected) LbColors.Primary else LbColors.TextMuted,
                    shape = RoundedCornerShape(2.dp)
                )
                .background(
                    color = if (selected) LbColors.Primary else Color.Transparent,
                    shape = RoundedCornerShape(2.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(12.dp)
                )
            }
        }
        Spacer(Modifier.width(3.dp))
        Text(
            label,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = labelColor,
            maxLines = 1
        )
    }
}

/**
 * The controls strip at the top of the now-voicing panel. Toggles the "Drill: Verbs /
 * Sentences" play-mode, present/past tense, and (in Sentences mode) adj/adv inclusion.
 * When [showQuizDelay]
 * is true the right edge gets a delay slider used by the verb quiz between English
 * and reveal/speak. Persists every change immediately.
 *
 * Transport buttons are NOT here — they live in [TransportGrid] which is pinned to
 * the right of the NowVoicing content area so it's in the same spot every time.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NowVoicingControlsStrip(
    config: RandomConfig,
    onConfigChange: (RandomConfig) -> Unit,
    showQuizDelay: Boolean,
    prefetch: PrefetchProgress
) {
    Surface(
        color = LbColors.Canvas,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Chips on the left in a horizontally-scrollable strip; CacheBadge pinned
            // right (NOT inside the scroll) so it never goes out of view.
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                PlayModeSlider(
                    mode = config.playMode,
                    onChange = { onConfigChange(config.copy(playMode = it)) }
                )
                Spacer(Modifier.width(4.dp))
                TenseChip(
                    label = "Present",
                    selected = "present" in config.tenses,
                    onToggle = {
                        val now = if ("present" in config.tenses) config.tenses - "present"
                                  else config.tenses + "present"
                        onConfigChange(config.copy(tenses = now))
                    }
                )
                TenseChip(
                    label = "Past",
                    selected = "past" in config.tenses,
                    onToggle = {
                        val now = if ("past" in config.tenses) config.tenses - "past"
                                  else config.tenses + "past"
                        onConfigChange(config.copy(tenses = now))
                    }
                )
                if (config.playMode == PlayMode.PHRASES) {
                    Spacer(Modifier.width(4.dp))
                    TenseChip(
                        label = "Adj",
                        selected = config.adjectiveMode != IncludeMode.OFF,
                        onToggle = {
                            val now = if (config.adjectiveMode == IncludeMode.OFF)
                                          IncludeMode.YES else IncludeMode.OFF
                            onConfigChange(config.copy(adjectiveMode = now))
                        }
                    )
                    TenseChip(
                        label = "Adv",
                        selected = config.adverbMode != IncludeMode.OFF,
                        onToggle = {
                            val now = if (config.adverbMode == IncludeMode.OFF)
                                          IncludeMode.YES else IncludeMode.OFF
                            onConfigChange(config.copy(adverbMode = now))
                        }
                    )
                }
                if (showQuizDelay) {
                    Spacer(Modifier.width(6.dp))
                    QuizDelayControl(
                        seconds = config.quizDelaySeconds,
                        onChange = { onConfigChange(config.copy(quizDelaySeconds = it)) }
                    )
                }
            }
            // Audio cache badge: lives here (right side of the chip strip) instead of
            // under the Play Phrases pill in the top bar — saves a row of header
            // height when there's plenty of slack right here.
            Spacer(Modifier.width(8.dp))
            CacheBadge(prefetch)
        }
    }
}

@Composable
private fun TransportIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val bg = when {
        label == "Stop" -> LbColors.Stop
        label == "Pause" || label == "Resume" -> LbColors.AudioBright
        else -> LbColors.OnDark2.copy(alpha = 0.12f)
    }
    val fg = when {
        label == "Stop" || label == "Pause" || label == "Resume" -> Color.White
        else -> LbColors.OnDark
    }
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(if (label == "Pause" || label == "Resume") 54.dp else 42.dp)
            .background(bg.copy(alpha = if (enabled) 1f else 0.35f), RoundedCornerShape(12.dp))
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (enabled) fg else LbColors.OnDark2.copy(alpha = 0.45f),
            modifier = Modifier.size(if (label == "Pause" || label == "Resume") 24.dp else 20.dp)
        )
    }
}

/**
 * Single-source audio-sync indicator that lives in the AppHeader under the Play
 * Phrases pill. Used to live duplicated in each lesson tab's TopBar (VerbsTab,
 * AdjectivesScreen, AdverbsScreen); consolidating it here gives one truth.
 */
@Composable
private fun CacheBadge(
    prefetch: PrefetchProgress,
    dark: Boolean = false,
    onlyWhileRunning: Boolean = false
) {
    val done = prefetch.done
    val total = prefetch.total
    if (total == 0 && !prefetch.finished) return
    val complete = prefetch.finished || (total > 0 && done >= total)
    if (onlyWhileRunning && complete) return
    val bg = when {
        dark -> LbColors.Star.copy(alpha = 0.18f)
        complete -> LbColors.SuccessSoft
        else -> LbColors.WarningSoft
    }
    val fg = when {
        dark -> LbColors.Star
        complete -> LbColors.Success
        else -> LbColors.Warning
    }
    Surface(color = bg, shape = RoundedCornerShape(10.dp)) {
        Row(
            Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            if (!complete) {
                androidx.compose.material3.CircularProgressIndicator(
                    modifier = Modifier.size(8.dp),
                    strokeWidth = 1.dp,
                    color = fg
                )
            }
            Text(
                if (complete) "Audio synced · $done"
                else "Caching audio · $done / $total",
                fontSize = if (dark) 10.sp else 9.sp,
                color = fg,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

/**
 * Playback-mode toggle for the "Play Phrases" auto-drill — NOT navigation. The leading
 * "Drill:" caption plus the labels "Verbs / Sentences" keep it from colliding with the
 * left-hand Verbs/Phrases tab nav, which it used to mirror confusingly as "Words /
 * Phrases" (two controls both ending in "Phrases"). Verbs = pronoun + verb forms
 * ("ja jestem"); Sentences = full example sentences. Tap either label to jump, or
 * drag the switch knob. (Backed by PlayMode.VERBS / PlayMode.PHRASES — the enum keeps
 * its historical names; only the user-facing labels changed.)
 */
@Composable
private fun PlayModeSlider(mode: PlayMode, onChange: (PlayMode) -> Unit) {
    val isPhrases = mode == PlayMode.PHRASES
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "Drill:",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = LbColors.Label,
            modifier = Modifier.padding(end = 6.dp)
        )
        Text(
            "Verbs",
            fontSize = 11.sp,
            fontWeight = if (!isPhrases) FontWeight.SemiBold else FontWeight.Normal,
            color = if (!isPhrases) LbColors.Primary else LbColors.TextMuted,
            modifier = Modifier.clickable { onChange(PlayMode.VERBS) }
        )
        Spacer(Modifier.width(4.dp))
        androidx.compose.material3.Switch(
            checked = isPhrases,
            onCheckedChange = { onChange(if (it) PlayMode.PHRASES else PlayMode.VERBS) },
            modifier = Modifier.scale(0.65f)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            "Sentences",
            fontSize = 11.sp,
            fontWeight = if (isPhrases) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isPhrases) LbColors.Primary else LbColors.TextMuted,
            modifier = Modifier.clickable { onChange(PlayMode.PHRASES) }
        )
    }
}

@Composable
private fun TenseChip(label: String, selected: Boolean, onToggle: () -> Unit) {
    StickyChip(label = label, selected = selected, onClick = onToggle, emphasized = false)
}

@Composable
private fun StickyChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    emphasized: Boolean
) {
    // Selected = dark navy fill, white text. Unselected = pale tan fill, muted text.
    // Emphasized (mode chips) gets a thicker visual weight when selected.
    val bg = when {
        selected -> LbColors.Primary
        else -> LbColors.ChipIdle
    }
    val border = when {
        selected -> LbColors.Primary
        else -> LbColors.ChipBorder
    }
    val textColor = if (selected) Color.White else LbColors.Label
    Surface(
        color = bg,
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, border),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
        ) {
            // Leading indicator: filled circle when selected, ring outline otherwise.
            // Gives a second visual cue beyond color contrast (helps colorblind users).
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(
                        if (selected) Color.White else Color.Transparent
                    )
                    .then(
                        if (!selected) Modifier.border(
                            1.dp, LbColors.ChipRing,
                            androidx.compose.foundation.shape.CircleShape
                        ) else Modifier
                    )
            )
            Spacer(Modifier.width(6.dp))
            Text(
                label,
                fontSize = 11.sp,
                fontWeight = if (selected || emphasized) FontWeight.SemiBold
                else FontWeight.Medium,
                color = textColor
            )
        }
    }
}

/** Stepper for the quiz reveal delay. ±0.5s increments, clamped to [0.5, 5.0]. */
@Composable
private fun QuizDelayControl(seconds: Float, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "Delay",
            fontSize = 10.sp,
            color = LbColors.Label,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.width(4.dp))
        Surface(
            color = Color.White,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.clickable {
                onChange((seconds - 0.5f).coerceAtLeast(0.5f))
            }
        ) {
            Text("−", fontSize = 13.sp, color = LbColors.TextSecondary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 1.dp))
        }
        Text(
            String.format(Locale.US, "%.1fs", seconds),
            fontSize = 11.sp,
            color = LbColors.TextPrimary,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        Surface(
            color = Color.White,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.clickable {
                onChange((seconds + 0.5f).coerceAtMost(5.0f))
            }
        ) {
            Text("+", fontSize = 13.sp, color = LbColors.TextSecondary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 1.dp))
        }
    }
}

@Composable
private fun RandomPlayPill(
    playing: Boolean,
    onToggle: () -> Unit,
    onConfigure: () -> Unit
) {
    Row(
        modifier = Modifier.padding(start = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Surface(
            color = if (playing) LbColors.Stop else LbColors.Audio,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.clickable(onClick = onToggle)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp)
            ) {
                Icon(
                    imageVector = if (playing) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                    contentDescription = if (playing) "Stop phrases" else "Play Phrases",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    if (playing) "Stop" else "Play Phrases",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        if (!playing) {
            Surface(
                color = Color.White,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, LbColors.Line),
                modifier = Modifier.clickable(onClick = onConfigure)
            ) {
                Icon(
                    imageVector = Icons.Filled.Tune,
                    contentDescription = "Configure Play Phrases",
                    tint = LbColors.TextPrimary,
                    modifier = Modifier.padding(9.dp).size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun TabPill(label: String, selected: Boolean, onClick: () -> Unit) {
    val sep = label.indexOf(". ")
    val number = if (sep >= 0) label.substring(0, sep + 1) else ""
    val word = if (sep >= 0) label.substring(sep + 2) else label
    Surface(
        color = if (selected) Color.White else Color.Transparent,
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(1.dp, if (selected) Color.White else LbColors.Line.copy(alpha = 0.65f)),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (number.isNotBlank()) {
                Text(
                    number,
                    color = LbColors.Primary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1
                )
                Spacer(Modifier.width(4.dp))
            }
            Text(
                word,
                color = LbColors.TextPrimary,
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
                maxLines = 1
            )
        }
    }
}

private fun List<WorkInfo>.toPrefetchProgress(): PrefetchProgress {
    val info = firstOrNull() ?: return PrefetchProgress()
    return when (info.state) {
        // outputData carries final N from the worker's Result.success(...) so the
        // badge can read "audio 1572/1572 ✓" instead of "0/0 ✓".
        WorkInfo.State.SUCCEEDED -> {
            val n = info.outputData.getInt(PrefetchWorker.KEY_TOTAL, 0)
            PrefetchProgress(total = n, done = n, finished = true)
        }
        WorkInfo.State.RUNNING, WorkInfo.State.ENQUEUED -> PrefetchWorker.progressFrom(info.progress)
        else -> PrefetchWorker.progressFrom(info.progress)
    }
}

/**
 * Top-of-app banner that shows whenever [com.sponic.langbang.domain.SentenceRegenService]
 * is downloading sentence bundles from R2 (or failed and offers a Retry). Visible on
 * every tab so the user always knows when content is loading — replaces the small
 * gray progress text buried inside the Settings → Regenerate card.
 */
@Composable
private fun SentenceRegenBanner(app: LangbangApplication) {
    val state by app.sentenceRegen.state.collectAsState()
    when (val s = state) {
        is com.sponic.langbang.domain.SentenceRegenService.State.Idle -> Unit
        is com.sponic.langbang.domain.SentenceRegenService.State.Done -> Unit
        is com.sponic.langbang.domain.SentenceRegenService.State.Failed -> {
            Surface(color = LbColors.DangerSoft, modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Sentence sync failed — ${s.message}. Tap to retry.",
                        fontSize = 11.sp,
                        color = LbColors.Danger,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { app.sentenceRegen.startIfNeeded(force = true) }
                    )
                }
            }
        }
        is com.sponic.langbang.domain.SentenceRegenService.State.Downloading -> {
            Surface(color = LbColors.WarningSoft, modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    val label = if (s.total == 0)
                        "Fetching sentence manifest…"
                    else
                        "Downloading sentences ${s.done}/${s.total} · ${s.currentKey}"
                    Text(
                        label,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = LbColors.Label
                    )
                    val progress = if (s.total > 0) s.done.toFloat() / s.total else 0f
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = LbColors.SurfaceTint
                    )
                }
            }
        }
    }
}
