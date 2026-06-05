package com.sponic.langbang.ui

import com.sponic.langbang.ui.theme.LbColors

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.displayCutoutPadding
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.sponic.langbang.R
import com.sponic.langbang.LangbangApplication
import com.sponic.langbang.BuildConfig
import com.sponic.langbang.data.IncludeMode
import com.sponic.langbang.data.PlayMode
import com.sponic.langbang.data.RandomConfig
import com.sponic.langbang.domain.AudioActivityBus
import com.sponic.langbang.domain.NowVoicing
import com.sponic.langbang.domain.NowVoicingBus
import com.sponic.langbang.domain.PlaybackController
import com.sponic.langbang.domain.PrefetchProgress
import com.sponic.langbang.domain.PrefetchWorker
import com.sponic.langbang.ui.common.NowVoicingPanel
import com.sponic.langbang.ui.lessons.AdjectivesScreen
import com.sponic.langbang.ui.lessons.AdverbsScreen
import com.sponic.langbang.ui.lessons.LessonScreen
import com.sponic.langbang.ui.lessons.NounsScreen
import com.sponic.langbang.ui.phrases.PhrasesScreen
import com.sponic.langbang.ui.numbers.NumbersScreen
import com.sponic.langbang.ui.pronunciation.PronunciationScreen
import com.sponic.langbang.ui.quizzes.QuizzesScreen
import com.sponic.langbang.ui.settings.SettingsScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

private enum class Section(val labelKey: String, val fallbackLabel: String) {
    Pronunciation("tabs.pronunciation", "Pron"),
    Verbs("tabs.verbs", "Verbs"),
    Adjectives("tabs.adjectives", "Adj"),
    Adverbs("tabs.adverbs", "Adv"),
    Nouns("tabs.nouns", "Nouns"),
    Phrases("tabs.phrases", "Phrases"),
    Quizzes("tabs.quizzes", "Quiz"),
    Numbers("tabs.numbers", "Num"),
    Settings("tabs.settings", "Settings")
}

private val TabSections = listOf(
    Section.Pronunciation,
    Section.Numbers,
    Section.Verbs,
    Section.Adjectives,
    Section.Adverbs,
    Section.Nouns,
    Section.Phrases,
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
    var quizResetToken by remember { mutableIntStateOf(0) }
    val nowVoicing by NowVoicingBus.state.collectAsState()
    val cloudState by app.cloudConfig.state.collectAsState()
    val cloudLabels = cloudState.bootstrap?.labels.orEmpty()
    val audioActive by AudioActivityBus.active.collectAsState()
    val playbackTransport by PlaybackController.transport.collectAsState()
    val online by app.network.online.collectAsState()
    val scope = rememberCoroutineScope()
    val randomPlayer = remember { RandomPlayerState(app, scope) }
    // Starred phrases — lets the user add whatever is currently voicing to their
    // personal quiz deck straight from the sticky Now Voicing panel.
    val starredPhrases by app.starredPhrases.starred.collectAsState()
    // The panel keeps the latest phrase while audio controls are active, but it should
    // disappear when playback is cleared so old quiz answers cannot sit above a new card.
    var pinnedVoicing by remember { mutableStateOf<NowVoicing?>(null) }
    LaunchedEffect(nowVoicing, playbackTransport, randomPlayer.playing, randomPlayer.paused) {
        if (nowVoicing != null) {
            pinnedVoicing = nowVoicing
        } else if (playbackTransport == null && !randomPlayer.playing && !randomPlayer.paused) {
            pinnedVoicing = null
        }
    }
    var showConfigSheet by remember { mutableStateOf(false) }
    // Guards the tap-title-to-update flow against double-taps while a check/download runs.
    var checkingUpdate by remember { mutableStateOf(false) }
    var installingUpdate by remember { mutableStateOf(false) }
    var installerOverlayText by remember { mutableStateOf(label(cloudLabels, "install.preparing", "Preparing update…")) }
    // Live config — drives the in-panel controls strip + the Play Phrases sheet
    // initial values. Updates persist immediately so other readers (VerbsTab quiz
    // delay, RandomPlayer collect) see the latest values.
    var liveConfig by remember { mutableStateOf(app.randomConfig.load()) }
    val saveConfig: (RandomConfig) -> Unit = remember {
        { c ->
            liveConfig = c
            app.randomConfig.save(c)
        }
    }
    val updateConfig: (RandomConfig) -> Unit = remember {
        { c ->
            saveConfig(c)
            randomPlayer.reconfigure(c)
        }
    }
    // Navigating away (switching tabs or opening Settings) silences whatever's
    // currently voicing — the random player and any registered quiz/play-all queue.
    // Moving on should stop the previous drill, not leave audio running underneath.
    val stopActivePlayback: () -> Unit = {
        if (randomPlayer.playing || randomPlayer.paused) randomPlayer.stop()
        PlaybackController.stop()
        NowVoicingBus.clear()
        pinnedVoicing = null
    }
    val showInstallerThenLaunch: (java.io.File) -> Unit = { apk ->
        installingUpdate = true
        installerOverlayText = label(cloudLabels, "install.opening", "Opening Android installer…")
        stopActivePlayback()
        scope.launch {
            // Android Settings / Package Installer takes over once launched, so
            // keep the branded screen visible long enough to register first.
            delay(2200)
            app.updateChecker.install(apk)
        }
    }
    val showInstallPermissionMissing: () -> Unit = {
        installingUpdate = true
        installerOverlayText = label(cloudLabels, "status.install_permission_missing", "Install permission is not enabled.")
        stopActivePlayback()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .displayCutoutPadding()
            .background(LbColors.Canvas)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            AppHeader(
                section = section,
                onSelect = {
                    if (it == section && it == Section.Quizzes) {
                        quizResetToken += 1
                    } else {
                        if (it != section) stopActivePlayback()
                        section = it
                        if (it != Section.Settings) lastTabSection = it
                    }
                },
                onToggleSettings = {
                    stopActivePlayback()
                    section = if (section == Section.Settings) lastTabSection
                    else Section.Settings
                },
                prefetch = progress,
                labels = cloudLabels,
                randomPlayer = randomPlayer,
                audioActive = audioActive,
                liveConfig = liveConfig,
                onOpenConfig = { showConfigSheet = true },
                onTitleClick = {
                    // Tap the title → check R2 for a newer build and, if found,
                    // download + launch the installer right away.
                    if (!checkingUpdate) {
                        checkingUpdate = true
                        Toast.makeText(
                            context,
                            label(cloudLabels, "status.checking_updates", "Checking for updates…"),
                            Toast.LENGTH_SHORT
                        ).show()
                        scope.launch {
                            val avail = app.updateChecker.check()
                            when {
                                avail == null -> {
                                    Toast.makeText(
                                        context,
                                        "${label(cloudLabels, "status.up_to_date", "LangBangML is up to date")} (v${BuildConfig.BUILD_NUMBER})",
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
                                    showInstallPermissionMissing()
                                    checkingUpdate = false
                                }
                                else -> {
                                    Toast.makeText(
                                        context,
                                        "${label(cloudLabels, "status.downloading", "Downloading")} v${avail.versionCode}…",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    val apk = app.updateChecker.download(avail.url)
                                    checkingUpdate = false
                                    if (apk == null) {
                                        Toast.makeText(
                                            context,
                                            label(cloudLabels, "status.update_download_failed", "Update download failed"),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    } else {
                                        showInstallerThenLaunch(apk)
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
                        label(
                            cloudLabels,
                            "status.offline",
                            "Offline — cached content only. Generation + audio synth disabled."
                        ),
                        fontSize = 11.sp,
                        color = LbColors.Danger,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
            }
            UpdateBanner(
                app = app,
                onInstallPermissionNeeded = showInstallPermissionMissing,
                onInstallReady = showInstallerThenLaunch
            )
            SentenceRegenBanner(app = app)
            // App-wide Now Voicing surface. Audio can start from any tab (for example,
            // the global Play Phrases button while Pronunciation is open), so the
            // canonical panel lives in the shell instead of inside individual lesson
            // screens.
            val nowVoicingSlot: @Composable () -> Unit = {
                NowVoicingPanel(
                    app = app,
                    pinned = pinnedVoicing,
                    live = nowVoicing,
                    isStarred = pinnedVoicing?.pl?.let { it in starredPhrases } == true,
                    onToggleStar = { pinnedVoicing?.pl?.let { app.starredPhrases.toggle(it) } },
                    syllableShading = liveConfig.syllableShading
                )
            }
            if (nowVoicing != null || playbackTransport != null || randomPlayer.playing || randomPlayer.paused) {
                nowVoicingSlot()
            }
            val noNowVoicingSlot: @Composable () -> Unit = {}
            Row(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.weight(1.5f).fillMaxHeight()) {
                    key(
                        cloudState.selectedInstanceId,
                        cloudState.bootstrap?.content?.versionId,
                        cloudState.bootstrap?.syncedAt
                    ) {
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
                            Section.Quizzes -> QuizzesScreen(
                                app = app,
                                nowVoicing = noNowVoicingSlot,
                                resetToken = quizResetToken
                            )
                            Section.Settings -> SettingsScreen(app = app)
                        }
                    }
                }
            }
        }
        if (showConfigSheet) {
            RandomConfigSheet(
                app = app,
                initial = liveConfig,
                initialMustContain = liveConfig.mustContainWord,
                randomPlayer = randomPlayer,
                onCancel = {
                    showConfigSheet = false
                },
                onSyllableShadingChange = { enabled ->
                    saveConfig(liveConfig.copy(syllableShading = enabled))
                },
                onPlay = { config ->
                    updateConfig(config)
                    randomPlayer.start(config)
                    // Keep sheet open so the user sees Now Playing controls + can
                    // tweak config without re-tapping the pill.
                }
            )
        }
        if (installingUpdate) {
            InstallerOverlay(installerOverlayText)
        }
    }
}

@Composable
private fun InstallerOverlay(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(R.drawable.installer_langbang),
            contentDescription = "Installing LangBang",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        Surface(
            color = Color.Black.copy(alpha = 0.58f),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 36.dp)
        ) {
            Text(
                message,
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp)
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
    labels: Map<String, String>,
    randomPlayer: RandomPlayerState,
    audioActive: Boolean,
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
                    .padding(start = 10.dp, end = 12.dp, top = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clickable(onClick = onTitleClick)
                        .padding(start = 2.dp, end = 13.dp)
                ) {
                    Image(
                        painter = painterResource(R.drawable.langbang_logo_square),
                        contentDescription = label(labels, "app.title", "LangBangML"),
                        modifier = Modifier.size(31.dp),
                        contentScale = ContentScale.Fit
                    )
                    Image(
                        painter = painterResource(R.drawable.langbang_logo_wordmark),
                        contentDescription = null,
                        modifier = Modifier
                            .padding(start = 7.dp)
                            .width(108.dp)
                            .height(32.dp),
                        contentScale = ContentScale.Fit
                    )
                    Surface(
                        color = LbColors.Primary.copy(alpha = 0.18f),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, LbColors.Primary.copy(alpha = 0.5f)),
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Text(
                            languagePairBadge(),
                            color = LbColors.AudioBright,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Text(
                        " v${BuildConfig.BUILD_NUMBER}",
                        color = LbColors.OnDark2,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 5.dp, top = 1.dp)
                    )
                }
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.Bottom
                ) {
                    TabStrip(
                        section = section,
                        labels = labels,
                        onSelect = onSelect,
                        modifier = Modifier.height(50.dp)
                    )
                }
                IconButton(
                    onClick = onToggleSettings,
                    modifier = Modifier.size(34.dp),
                    colors = IconButtonDefaults.iconButtonColors(
                        contentColor = if (section == Section.Settings) LbColors.AudioBright
                        else LbColors.OnDark
                    )
                ) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = label(labels, "tabs.settings", "Settings"),
                        modifier = Modifier.size(20.dp)
                    )
                }
                // Top bar stays single-row tall. The CacheBadge ("audio 9667/10054")
                // used to sit stacked under the pill here — moved to the chip strip
                // below where there's idle horizontal space, so the header collapses
                // to one row.
                RandomPlayPill(
                    voicing = audioActive,
                    labels = labels,
                    canStopVoicing = randomPlayer.playing || randomPlayer.paused,
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

private fun languagePairBadge(): String = when (BuildConfig.LANGBANGML_INSTANCE_ID) {
    "langbangml-pl-en" -> "PL/EN"
    else -> "EN/PL"
}

@Composable
private fun RandomPlayPill(
    voicing: Boolean,
    labels: Map<String, String>,
    canStopVoicing: Boolean,
    onToggle: () -> Unit,
    onConfigure: () -> Unit
) {
    Surface(
        modifier = Modifier.padding(start = 4.dp),
        color = LbColors.Audio.copy(alpha = 0.16f),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(
            1.dp,
            LbColors.AudioBright.copy(alpha = if (voicing) 0.55f else 0.4f)
        )
    ) {
        Row(
            modifier = Modifier.padding(3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            if (voicing) {
                Surface(
                    color = LbColors.Audio,
                    shape = RoundedCornerShape(7.dp),
                    modifier = Modifier.clickable(
                        enabled = canStopVoicing,
                        onClick = onToggle
                    )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            label(labels, "actions.voicing", "Voicing"),
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            if (!voicing) {
                Surface(
                    color = Color.White.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(7.dp),
                    border = BorderStroke(1.dp, LbColors.AudioBright.copy(alpha = 0.35f)),
                    modifier = Modifier.clickable(onClick = onConfigure)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Shuffle,
                        contentDescription = label(labels, "actions.configure_listening", "Configure listening mix"),
                        tint = LbColors.AudioBright,
                        modifier = Modifier.padding(5.dp).size(14.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun TabStrip(
    section: Section,
    labels: Map<String, String>,
    onSelect: (Section) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.Bottom
    ) {
        TabSections.forEachIndexed { index, tab ->
            if (index > 0) {
                Box(
                    modifier = Modifier
                        .height(28.dp)
                        .width(1.dp)
                        .background(LbColors.OnDark2.copy(alpha = 0.2f))
                )
            }
            TabPill(
                label = label(labels, tab.labelKey, tab.fallbackLabel),
                selected = section == tab,
                onClick = { onSelect(tab) }
            )
        }
    }
}

@Composable
private fun TabPill(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        color = if (selected) Color.White else Color.Transparent,
        shape = RoundedCornerShape(
            topStart = 7.dp,
            topEnd = 7.dp,
            bottomStart = 0.dp,
            bottomEnd = 0.dp
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) LbColors.Primary.copy(alpha = 0.65f) else Color.Transparent
        ),
        modifier = Modifier
            .height(50.dp)
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Bottom
        ) {
            Text(
                label,
                color = if (selected) LbColors.Primary else LbColors.OnDark,
                fontSize = 11.sp,
                fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(if (selected) LbColors.Primary else Color.Transparent)
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

private fun label(labels: Map<String, String>, key: String, fallback: String): String =
    labels[key]?.takeIf { it.isNotBlank() } ?: fallback

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
            Surface(
                color = LbColors.DangerSoft,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { app.sentenceRegen.startIfNeeded(force = true) }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Sentence sync unavailable. Tap to retry.",
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.SemiBold,
                        color = LbColors.Danger,
                        modifier = Modifier.weight(1f)
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
