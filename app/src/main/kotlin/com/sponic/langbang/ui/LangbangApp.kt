package com.sponic.langbang.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shuffle
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.sponic.langbang.LangbangApplication
import com.sponic.langbang.domain.NowVoicing
import com.sponic.langbang.domain.NowVoicingBus
import com.sponic.langbang.domain.PrefetchProgress
import com.sponic.langbang.domain.PrefetchWorker
import com.sponic.langbang.ui.lessons.AdjectivesScreen
import com.sponic.langbang.ui.lessons.LessonScreen
import com.sponic.langbang.ui.pronunciation.PronunciationScreen
import com.sponic.langbang.ui.settings.SettingsScreen

private enum class Section(val tabLabel: String) {
    Pronunciation("1. Pronunciation"),
    Verbs("2. Core verbs"),
    Adjectives("3. Adjectives"),
    Settings("Settings")
}

private val TabSections = listOf(Section.Pronunciation, Section.Verbs, Section.Adjectives)

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
    val scope = rememberCoroutineScope()
    val randomPlayer = remember { RandomPlayerState(app, scope) }

    Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
        AppHeader(
            section = section,
            onSelect = {
                section = it
                if (it != Section.Settings) lastTabSection = it
            },
            onToggleSettings = {
                section = if (section == Section.Settings) lastTabSection
                else Section.Settings
            },
            prefetch = progress,
            nowVoicing = nowVoicing,
            randomPlayer = randomPlayer
        )
        Box(modifier = Modifier.fillMaxSize()) {
            when (section) {
                Section.Pronunciation -> PronunciationScreen(app = app)
                Section.Verbs -> LessonScreen(app = app, prefetch = progress)
                Section.Adjectives -> AdjectivesScreen(app = app, prefetch = progress)
                Section.Settings -> SettingsScreen(app = app)
            }
        }
    }
}

@Composable
private fun AppHeader(
    section: Section,
    onSelect: (Section) -> Unit,
    onToggleSettings: () -> Unit,
    prefetch: PrefetchProgress,
    nowVoicing: NowVoicing?,
    randomPlayer: RandomPlayerState
) {
    Surface(
        color = MaterialTheme.colorScheme.primary,
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
                        contentColor = if (section == Section.Settings) Color(0xFFE8A33D)
                        else Color.White
                    )
                ) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = "Settings",
                        modifier = Modifier.size(20.dp)
                    )
                }
                Text(
                    "LangBang: Polish",
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 4.dp, end = 16.dp)
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
                RandomPlayPill(
                    playing = randomPlayer.playing,
                    onToggle = { randomPlayer.toggle() }
                )
            }
            if (!prefetch.finished && prefetch.total > 0) {
                LinearProgressIndicator(
                    progress = prefetch.ratio,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = Color(0xFFE8A33D),
                    trackColor = Color.White.copy(alpha = 0.2f)
                )
                Text(
                    "Downloading audio… ${prefetch.done}/${prefetch.total}  •  ${prefetch.current}",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 10.sp,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 1.dp)
                )
            }
            NowVoicingPanel(nowVoicing)
        }
    }
}

@Composable
private fun NowVoicingPanel(v: NowVoicing?) {
    if (v == null) return
    val plActive = v.lang == "pl"
    val slowActive = v.lang == "pl-slow"
    val enActive = v.lang == "en"
    val pausing = v.lang == "pause"
    Surface(
        color = Color.White,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val tag = when (v.lang) {
                "pl-slow" -> "PL (slow)"
                "pl" -> "PL"
                "en" -> "EN"
                "pause" -> "your turn →"
                else -> v.lang
            }
            val pos = v.position?.let { " · $it" } ?: ""
            Text(
                "NOW VOICING$pos  ·  $tag",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF7A5A1F)
            )
            Text(
                v.en,
                fontSize = 22.sp,
                fontWeight = if (enActive) FontWeight.Bold else FontWeight.Normal,
                color = if (enActive) Color.Black else Color(0xFF666666)
            )
            Text(
                v.pl,
                fontSize = 26.sp,
                fontWeight = if (plActive || slowActive) FontWeight.Bold
                else FontWeight.Medium,
                color = when {
                    plActive || slowActive -> Color.Black
                    pausing -> Color(0xFFBBBBBB)
                    else -> Color(0xFF666666)
                }
            )
            if (!v.literal.isNullOrEmpty()) {
                Text(
                    v.literal,
                    fontSize = 15.sp,
                    fontStyle = FontStyle.Italic,
                    color = Color(0xFF888888)
                )
            }
        }
    }
}

@Composable
private fun RandomPlayPill(playing: Boolean, onToggle: () -> Unit) {
    Surface(
        color = if (playing) Color(0xFFE8A33D) else Color.White.copy(alpha = 0.18f),
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier
            .padding(start = 4.dp)
            .clickable(onClick = onToggle)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            Icon(
                imageVector = if (playing) Icons.Filled.Stop else Icons.Filled.Shuffle,
                contentDescription = if (playing) "Stop random" else "Play random",
                tint = Color.White,
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                if (playing) "Stop" else "Play random",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun TabPill(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        color = if (selected) Color.White.copy(alpha = 0.18f) else Color.Transparent,
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(
            label,
            color = if (selected) Color.White else Color.White.copy(alpha = 0.85f),
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
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
