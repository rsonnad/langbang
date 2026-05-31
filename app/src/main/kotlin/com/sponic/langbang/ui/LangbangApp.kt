package com.sponic.langbang.ui

import com.sponic.langbang.ui.theme.LbColors

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
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.wrapContentWidth
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
    Numbers("7. Num"),
    Quizzes("8. Quiz"),
    Settings("Settings")
}

private val TabSections = listOf(
    Section.Pronunciation,
    Section.Verbs,
    Section.Adjectives,
    Section.Adverbs,
    Section.Nouns,
    Section.Phrases,
    Section.Numbers,
    Section.Quizzes
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
    val online by app.network.online.collectAsState()
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
        }
    }
    // Navigating away (switching tabs or opening Settings) silences whatever's
    // currently voicing — the random player and any registered quiz/play-all queue.
    // Moving on should stop the previous drill, not leave audio running underneath.
    val stopActivePlayback: () -> Unit = {
        if (randomPlayer.playing || randomPlayer.paused) randomPlayer.stop()
        PlaybackController.stop()
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
            // Two-zone body. The lesson list fills the LEFT at full height, flush to the
            // top (directly under the header — nothing stealing rows above it). The Now
            // Voicing panel renders as a band at the TOP of each lesson screen's RIGHT
            // column (above the paradigm, with the play/quiz buttons under it) — it's
            // passed in as a slot so the drill-down + config wiring stays here. The
            // top-level Phrases browser keeps the legacy full-height right-hand column.
            // Settings / Quizzes / Numbers / Pronunciation don't use Now Voicing.
            val nowVoicingSlot: @Composable () -> Unit = {
                NowVoicingPanel(
                    pinned = pinnedVoicing,
                    live = nowVoicing,
                    config = liveConfig,
                    onConfigChange = updateConfig,
                    onPlWordClick = { word ->
                        if (randomPlayer.playing) randomPlayer.stop()
                        configSeedWord = word
                        showConfigSheet = true
                    },
                    prefetch = progress
                )
            }
            val showNowVoicingColumn = section == Section.Phrases
            Row(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.weight(1.5f).fillMaxHeight()) {
                    when (section) {
                        Section.Pronunciation -> PronunciationScreen(app = app)
                        Section.Verbs -> LessonScreen(
                            app = app, prefetch = progress, nowVoicing = nowVoicingSlot
                        )
                        Section.Adjectives -> AdjectivesScreen(
                            app = app, prefetch = progress, nowVoicing = nowVoicingSlot
                        )
                        Section.Adverbs -> AdverbsScreen(
                            app = app, prefetch = progress, nowVoicing = nowVoicingSlot
                        )
                        Section.Nouns -> NounsScreen(
                            app = app, prefetch = progress, nowVoicing = nowVoicingSlot
                        )
                        Section.Phrases -> PhrasesScreen(app = app)
                        Section.Numbers -> NumbersScreen(app = app)
                        Section.Quizzes -> QuizzesScreen(app = app)
                        Section.Settings -> SettingsScreen(app = app)
                    }
                }
                if (showNowVoicingColumn) {
                    Column(
                        modifier = Modifier
                            // Fixed NARROW width (not a weight) so the phrase list on
                            // the left gets ALL remaining space and shows many rows
                            // instead of one or two (reported 2026-05-30). 340dp fits
                            // the now-voicing text + transport without dominating.
                            .width(340.dp)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState())
                    ) {
                        nowVoicingSlot()
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
    onOpenConfig: () -> Unit,
    onTitleClick: () -> Unit
) {
    Surface(
        color = LbColors.PrimaryDeep,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth()
                    .padding(start = 4.dp, end = 8.dp, top = 2.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onToggleSettings,
                    modifier = Modifier.size(36.dp),
                    colors = IconButtonDefaults.iconButtonColors(
                        contentColor = if (section == Section.Settings) LbColors.Gold
                        else LbColors.OnPrimary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = "Settings",
                        modifier = Modifier.size(20.dp)
                    )
                }
                Text(
                    "LangBang",
                    color = LbColors.OnPrimary.copy(alpha = 0.62f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    // Tap the title to check R2 for a newer build and update on the spot.
                    modifier = Modifier
                        .clickable(onClick = onTitleClick)
                        .padding(start = 4.dp, end = 16.dp)
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
                    // Idle → open the config sheet so the user can pick filters.
                    // Playing/paused → the pill IS a Stop button (orange + stop
                    // icon + label "Stop"), so actually stop playback instead of
                    // just opening the sheet. Reported 2026-05-28: tapping Stop
                    // opened the sheet and audio kept advancing, which the user
                    // perceived as "stops for 30 seconds then starts again".
                    onToggle = {
                        if (randomPlayer.playing || randomPlayer.paused) randomPlayer.stop()
                        else onOpenConfig()
                    }
                )
                // Build tag pinned to the far right, immediately after Play Phrases —
                // tiny + tightly tracked so it barely takes any width. Glanceable
                // version without opening Settings.
                Text(
                    ".${com.sponic.langbang.BuildConfig.BUILD_NUMBER.toString().takeLast(3)}",
                    color = LbColors.OnPrimary.copy(alpha = 0.55f),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    modifier = Modifier.padding(start = 4.dp)
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
                    color = LbColors.Gold,
                    trackColor = LbColors.OnPrimary.copy(alpha = 0.18f)
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
    onPlWordClick: (String) -> Unit = {},
    prefetch: PrefetchProgress
) {
    Surface(color = LbColors.Surface, modifier = Modifier.fillMaxWidth()) {
        Column {
            NowVoicingControlsStrip(
                config = config,
                onConfigChange = onConfigChange,
                showQuizDelay = live?.quizMode == true,
                prefetch = prefetch
            )
            // Body: Polish + gloss on the left, 2x2 transport pinned right. Single
            // canonical layout so the transport position never shifts between
            // sources (RandomPlayer vs VerbsTab quiz vs Adjective quiz).
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    NowVoicingContent(pinned, live, onPlWordClick)
                }
                Spacer(Modifier.width(12.dp))
                TransportGrid()
            }
        }
    }
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
    onPlWordClick: (String) -> Unit
) {
    val activeLang = live?.lang
    val plHidden = live?.plHidden == true
    val pos = pinned?.position?.let { " · $it" } ?: ""
    val tag = when (activeLang) {
        "pl-slow" -> "PL (slow)"
        "pl" -> "PL"
        "en" -> "EN"
        "pause" -> if (plHidden) "your turn → (text hidden)" else "your turn →"
        null -> "idle"
        else -> activeLang
    }
    com.sponic.langbang.ui.common.NowVoicingBody(
        pinned = pinned,
        live = live,
        statusText = "NOW VOICING$pos  ·  $tag",
        onPlWordClick = onPlWordClick
    )
}

/**
 * 2x2 transport grid pinned to the right of the NowVoicing card. Reads the active
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

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
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
        }
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
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
}

/**
 * The controls strip at the top of the now-voicing panel. Toggles the "Drill: Words /
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
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(32.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (enabled) LbColors.Primary else LbColors.TextMuted.copy(alpha = 0.45f),
            modifier = Modifier.size(20.dp)
        )
    }
}

/**
 * Single-source audio-sync indicator that lives in the AppHeader under the Play
 * Phrases pill. Used to live duplicated in each lesson tab's TopBar (VerbsTab,
 * AdjectivesScreen, AdverbsScreen); consolidating it here gives one truth.
 */
@Composable
private fun CacheBadge(prefetch: PrefetchProgress) {
    val done = prefetch.done
    val total = prefetch.total
    if (total == 0 && !prefetch.finished) return
    val complete = prefetch.finished || (total > 0 && done >= total)
    val bg = if (complete) LbColors.SuccessSoft else LbColors.WarningSoft
    val fg = if (complete) LbColors.Success else LbColors.Warning
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
                if (complete) "audio synced · $done"
                else "audio $done / $total",
                fontSize = 9.sp,
                color = fg,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

/**
 * Playback-mode toggle for the "Play Phrases" auto-drill — NOT navigation. The leading
 * "Drill:" caption plus the labels "Words / Sentences" keep it from colliding with the
 * left-hand Verbs/Phrases tab nav, which it used to mirror confusingly as "Words /
 * Phrases" (two controls both ending in "Phrases"). Words = pronoun + verb forms
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
            "Words",
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
private fun RandomPlayPill(playing: Boolean, onToggle: () -> Unit) {
    Surface(
        color = if (playing) LbColors.Accent else LbColors.OnPrimary.copy(alpha = 0.14f),
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier
            .padding(start = 4.dp)
            .clickable(onClick = onToggle)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
            Icon(
                imageVector = if (playing) Icons.Filled.Stop else Icons.Filled.Shuffle,
                contentDescription = if (playing) "Stop phrases" else "Play Phrases",
                tint = LbColors.OnPrimary,
                modifier = Modifier.size(13.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                if (playing) "Stop" else "Play Phrases",
                color = LbColors.OnPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun TabPill(label: String, selected: Boolean, onClick: () -> Unit) {
    // Number + word on ONE row ("1. Pron") so the tab strip is a single line tall —
    // it used to stack "1." above "Pron", doubling the strip height. The strip
    // horizontal-scrolls when it overflows, so one row is safe on the Tab A9+.
    // Only the word is underlined (the number stays plain) via an annotated string.
    val sep = label.indexOf(". ")
    val number = if (sep >= 0) label.substring(0, sep + 2) else ""   // "1. "
    val word = if (sep >= 0) label.substring(sep + 2) else label     // "Pron"
    val text = buildAnnotatedString {
        append(number)
        withStyle(SpanStyle(textDecoration = TextDecoration.Underline)) { append(word) }
    }
    Surface(
        color = if (selected) LbColors.OnPrimary.copy(alpha = 0.16f) else Color.Transparent,
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(
            text,
            color = if (selected) LbColors.OnPrimary else LbColors.OnPrimary.copy(alpha = 0.82f),
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
        )
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
        is com.sponic.langbang.domain.SentenceRegenService.State.Done -> {
            if (s.downloaded == 0 && s.failures == 0) return
            val msg = if (s.failures == 0) "Sentences updated — ${s.downloaded} bundles"
                      else "Sentences updated — ${s.downloaded} of ${s.total} (${s.failures} failed)"
            Surface(color = LbColors.SuccessSoft, modifier = Modifier.fillMaxWidth()) {
                Text(
                    msg,
                    fontSize = 11.sp,
                    color = LbColors.Success,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
        }
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
