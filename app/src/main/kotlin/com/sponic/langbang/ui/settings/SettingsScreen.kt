package com.sponic.langbang.ui.settings

import com.sponic.langbang.ui.theme.LbColors

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import com.sponic.langbang.BuildConfig
import com.sponic.langbang.LangbangApplication
import com.sponic.langbang.data.PronounFilterStore
import com.sponic.langbang.data.SlowStyle
import com.sponic.langbang.domain.UsageSnapshot
import com.sponic.langbang.ui.common.GrammarVisuals
import com.sponic.langbang.ui.common.OutlinedPolishText
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun SettingsScreen(app: LangbangApplication) {
    val usage by app.usage.snapshot.collectAsState()
    val backup by app.backup.state.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        VersionHeader()
        PracticePronounsCard(app = app)
        PracticePlaybackCard(app = app)
        NounColorLegendCard()
        RegenerateSentencesCard(app = app, scope = scope, context = context)
        AudioDownloadCard(app = app, scope = scope, context = context)
        SlowAudioStyleCard(app = app)
        AzureUsageCard(usage = usage)
        BackupCard(
            host = backup.config.host,
            port = backup.config.port,
            user = backup.config.user,
            remoteDir = backup.config.remoteDir,
            publicKey = backup.publicKeyOpenSsh,
            destinationLabel = backup.destinationLabel,
            lastBackupAtMs = backup.lastBackupAtMs,
            lastBackupFile = backup.lastBackupFile,
            lastStatus = backup.lastStatus,
            inProgress = backup.inProgress,
            onSaveConfig = { h, p, u, r -> app.backup.updateConfig(h, p, u, r) },
            onBackup = {
                scope.launch {
                    val result = app.backup.runBackup()
                    val msg = result.fold(
                        onSuccess = { name -> "Backup uploaded — $name" },
                        onFailure = { t -> "Backup failed: ${t.message ?: t.javaClass.simpleName}" }
                    )
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                }
            },
            onCopyKey = { copyToClipboard(context, "SSH public key", backup.publicKeyOpenSsh) }
        )
    }
}

@Composable
private fun PracticePlaybackCard(app: LangbangApplication) {
    var slowFirst by remember { mutableStateOf(app.practicePrefs.slowFirst()) }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionHeader("Practice playback")
            Text(
                "Shared playback options for Play All and phrase drills.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            FilterChip(
                selected = slowFirst,
                onClick = {
                    slowFirst = !slowFirst
                    app.practicePrefs.setSlowFirst(slowFirst)
                },
                label = { Text("Slow Polish first", fontSize = 12.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = Color.White
                )
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NounColorLegendCard() {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionHeader("Noun colors")
            Text(
                "Only Polish noun forms are colored: fill by gender, letter outline by case.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                LegendChip("masculine", "pies", textColor = GrammarVisuals.Gender.Masculine)
                LegendChip("feminine", "kobieta", textColor = GrammarVisuals.Gender.Feminine)
                LegendChip("neuter", "dziecko", textColor = GrammarVisuals.Gender.Neuter)
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                LegendChip("Nominative", "pies", outlineColor = GrammarVisuals.Case.Nominative)
                LegendChip("Accusative", "psa", outlineColor = GrammarVisuals.Case.Accusative)
                LegendChip("Genitive", "psa", outlineColor = GrammarVisuals.Case.Genitive)
            }
        }
    }
}

@Composable
private fun LegendChip(
    label: String,
    sample: String,
    textColor: Color = MaterialTheme.colorScheme.primary,
    outlineColor: Color? = null
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            modifier = Modifier.padding(end = 6.dp)
        )
        OutlinedPolishText(
            text = sample,
            fillColor = textColor,
            outlineColor = outlineColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            outlineWidth = GrammarVisuals.NounForm.LegendOutlineWidth,
            backingOutlineExtraWidth = GrammarVisuals.NounForm.LegendBackingExtraWidth,
            glyphGap = GrammarVisuals.NounForm.LegendGlyphGap,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        )
    }
}

/**
 * "Download all audio" card. One tap pulls every cached phrase's mp3 from R2 via the
 * langbang-pregen-audio Edge Function — much faster than letting the app synth on
 * cache miss, and the Azure key stays server-side. Falls back to direct on-device
 * synth via PrefetchWorker if the function isn't reachable.
 */
@Composable
private fun AudioDownloadCard(
    app: LangbangApplication,
    scope: kotlinx.coroutines.CoroutineScope,
    context: Context
) {
    val online by app.network.online.collectAsState()
    var busy by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(0) }
    var total by remember { mutableStateOf(0) }
    var current by remember { mutableStateOf("") }
    var lastSummary by remember { mutableStateOf<String?>(null) }
    var lastError by remember { mutableStateOf<String?>(null) }
    // On-disk truth — count every mp3 in the cache dir, refresh on screen open
    // and whenever a download completes. This is the one number that's always
    // honest about what's actually there, regardless of which path put it there.
    //
    // CRITICAL: stats() does ~8000 file stat() syscalls on the tablet's flash.
    // Running it synchronously inside composition froze the Settings screen for
    // ~4s on open (reported 2026-05-30). Start null, compute on Dispatchers.IO,
    // and show a placeholder until it lands so the screen paints instantly.
    var cacheStats by remember {
        mutableStateOf<com.sponic.langbang.domain.AudioCache.Stats?>(null)
    }
    androidx.compose.runtime.LaunchedEffect(busy) {
        if (!busy) {
            cacheStats = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                app.audioCache.stats()
            }
        }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(20.dp)) {
            SectionHeader("Audio cache")
            Spacer(Modifier.height(4.dp))
            Text(
                "Pulls every phrase's pre-generated mp3 from R2 so playback is instant " +
                    "offline. First run downloads ~60-80 MB; later runs only fetch what's " +
                    "new. Safe to tap any time — already-cached files are skipped.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Spacer(Modifier.height(8.dp))
            // Persistent on-disk truth — N files / X MB. This is independent of
            // which download path put each file there, so it doesn't lie the way
            // "already cached" relative to the R2 manifest did.
            Text(
                cacheStats?.let {
                    "On disk: ${"%,d".format(Locale.US, it.count)} files · " +
                        "${"%.1f".format(it.megabytes)} MB"
                } ?: "On disk: counting…",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = {
                        if (busy) return@Button
                        busy = true; lastSummary = null; lastError = null
                        done = 0; total = 0; current = ""
                        scope.launch {
                            app.r2Audio.downloadAll { d, t, c ->
                                done = d; total = t; current = c
                            }.fold(
                                onSuccess = { s ->
                                    // R2 only ships a subset of the audio variants
                                    // (it doesn't have the legacy -50% slow). The
                                    // wording is now explicit about that — "wanted
                                    // X from R2" not "X total" — so the user doesn't
                                    // think the R2-manifest count is the whole cache.
                                    lastSummary = "R2 sync: fetched ${s.fetched} new of " +
                                        "${s.totalWanted} R2-pregen files" +
                                        if (s.failed > 0) " · ${s.failed} failed" else ""
                                    Toast.makeText(
                                        context,
                                        "Audio download complete",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                },
                                onFailure = { t ->
                                    lastError = t.message ?: t.javaClass.simpleName
                                }
                            )
                            busy = false
                        }
                    },
                    enabled = online && !busy
                ) {
                    Text(if (busy) "Syncing audio…" else "Sync audio from R2")
                }
                if (busy) {
                    Spacer(Modifier.width(12.dp))
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp), strokeWidth = 2.dp
                    )
                }
            }
            if (busy && total > 0) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "$done / $total · ${current.take(40)}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            lastSummary?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, fontSize = 11.sp, color = LbColors.Success)
            }
            lastError?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Error: $it (function may not be deployed — playback still works via " +
                        "on-device synth on cache miss)",
                    fontSize = 11.sp,
                    color = LbColors.Danger
                )
            }
            if (!online) {
                Spacer(Modifier.height(8.dp))
                Text("Offline — connect to wifi to download.",
                    fontSize = 11.sp, color = LbColors.Danger)
            }
        }
    }
}

@Composable
private fun VersionHeader() {
    Text(
        "langbang v${BuildConfig.VERSION_NAME} · build ${BuildConfig.BUILD_NUMBER} · ${BuildConfig.BUILD_TIMESTAMP}",
        fontSize = 12.sp,
        fontFamily = FontFamily.Monospace,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    )
}

/**
 * Picks which Azure SSML strategy produces the slow Polish audio in every playback
 * loop. Stretch = `<prosody rate="-60%">` (default, what we shipped first). Articulate
 * = `<prosody rate="-10%"><break time="250ms"/>` between words — Azure re-articulates
 * each word in isolation. Cache keys differ, so flipping pulls a different mp3 from R2
 * rather than re-synthesizing on the fly.
 */
@Composable
private fun SlowAudioStyleCard(app: LangbangApplication) {
    val current by app.audioPrefs.slowStyle.collectAsState()
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionHeader("Slow audio style")
            Text(
                when (current) {
                    SlowStyle.STRETCH -> "Stretch — slowed normal speech (-60% rate). Faster to follow when you already know the words."
                    SlowStyle.ARTICULATE -> "Articulate — each word re-pronounced with a 250 ms pause between. Clearer for new vocabulary."
                },
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = current == SlowStyle.STRETCH,
                    onClick = { app.audioPrefs.setSlowStyle(SlowStyle.STRETCH) },
                    label = { Text("Stretch") }
                )
                FilterChip(
                    selected = current == SlowStyle.ARTICULATE,
                    onClick = { app.audioPrefs.setSlowStyle(SlowStyle.ARTICULATE) },
                    label = { Text("Articulate") }
                )
            }
            Text(
                "Tap “Download all audio” above after switching to pre-cache mp3s in the new style.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
private fun AzureUsageCard(usage: UsageSnapshot) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(20.dp)) {
            SectionHeader("API spend (estimated)")
            Spacer(Modifier.height(4.dp))
            Text(
                "Counted locally on every Azure or Gemini call we make. Cached audio replays " +
                    "are free and don't add here. Not the real invoice — close enough to catch " +
                    "a runaway.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Spacer(Modifier.height(16.dp))

            UsageRow(
                label = "This month (${usage.monthLabel})",
                amount = usage.totalThisMonthUsd
            )
            Spacer(Modifier.height(6.dp))
            UsageRow(label = "All time", amount = usage.totalAllTimeUsd)

            Spacer(Modifier.height(16.dp))
            Text("Azure — month-to-date",
                fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Spacer(Modifier.height(4.dp))
            DetailLine(
                "Neural TTS",
                "${formatThousands(usage.ttsCharsThisMonth)} chars",
                usage.ttsCostThisMonthUsd
            )
            DetailLine(
                "Pronunciation Assessment",
                "${usage.pronSecondsThisMonth}s of audio",
                usage.pronCostThisMonthUsd
            )

            Spacer(Modifier.height(12.dp))
            Text("Gemini — month-to-date",
                fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Spacer(Modifier.height(4.dp))
            DetailLine(
                "Input tokens",
                formatThousands(usage.geminiInputTokensThisMonth),
                usage.geminiInputTokensThisMonth.toDouble() / 1_000_000.0 *
                    usage.rateGeminiInputPerMillionTokensUsd
            )
            DetailLine(
                "Output tokens",
                formatThousands(usage.geminiOutputTokensThisMonth),
                usage.geminiOutputTokensThisMonth.toDouble() / 1_000_000.0 *
                    usage.rateGeminiOutputPerMillionTokensUsd
            )

            Spacer(Modifier.height(12.dp))
            Text(
                "Rates: TTS \$${"%.2f".format(usage.rateTtsPerMillionCharsUsd)}/1M chars · " +
                    "Pron \$${"%.2f".format(usage.ratePronPerAudioHourUsd)}/audio-hour · " +
                    "Gemini \$${"%.2f".format(usage.rateGeminiInputPerMillionTokensUsd)} in / " +
                    "\$${"%.2f".format(usage.rateGeminiOutputPerMillionTokensUsd)} out per 1M tokens",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
            )
        }
    }
}

@Composable
private fun UsageRow(label: String, amount: Double) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 14.sp)
        Text(formatUsd(amount),
            fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
    }
}

// Show sub-cent precision while spend is small so pronunciation attempts visibly
// tick the counter; collapse to 2 decimals once we're past a dollar.
private fun formatUsd(amount: Double): String =
    if (amount < 1.0) "\$${"%.4f".format(amount)}" else "\$${"%.2f".format(amount)}"

@Composable
private fun DetailLine(name: String, qty: String, cost: Double) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text("$name — $qty", fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f))
        Text("\$${"%.4f".format(cost)}", fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f))
    }
}

@Composable
private fun BackupCard(
    host: String,
    port: Int,
    user: String,
    remoteDir: String,
    publicKey: String,
    destinationLabel: String,
    lastBackupAtMs: Long,
    lastBackupFile: String,
    lastStatus: String,
    inProgress: Boolean,
    onSaveConfig: (String, Int, String, String) -> Unit,
    onBackup: () -> Unit,
    onCopyKey: () -> Unit
) {
    var hostF by remember(host) { mutableStateOf(host) }
    var portF by remember(port) { mutableStateOf(port.toString()) }
    var userF by remember(user) { mutableStateOf(user) }
    var pathF by remember(remoteDir) { mutableStateOf(remoteDir) }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(20.dp)) {
            SectionHeader("Backup to ALPUCA")
            Spacer(Modifier.height(4.dp))
            Text(
                "Pushes cached audio + app settings to ALPUCA over SFTP so you can restore " +
                    "on a new tablet. First-time setup: add the public key below to " +
                    "ALPUCA's ~/.ssh/authorized_keys.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )

            Spacer(Modifier.height(16.dp))
            Text("Destination", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text(
                if (destinationLabel.isNotBlank()) destinationLabel else "(not configured)",
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
            )

            Spacer(Modifier.height(12.dp))
            Text("Last backup", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text(formatLastBackup(lastBackupAtMs),
                fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            if (lastBackupFile.isNotBlank()) {
                Text(
                    lastBackupFile,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                )
            }

            if (inProgress || lastStatus.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (inProgress) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    val statusColor = when {
                        inProgress -> MaterialTheme.colorScheme.onSurface
                        lastStatus.startsWith("OK") -> LbColors.Success
                        lastStatus.startsWith("Failed") -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    }
                    Text(lastStatus, fontSize = 13.sp, color = statusColor)
                }
            }

            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = hostF, onValueChange = { hostF = it },
                    label = { Text("Host") },
                    singleLine = true,
                    modifier = Modifier.weight(2f)
                )
                OutlinedTextField(
                    value = portF, onValueChange = { portF = it.filter(Char::isDigit) },
                    label = { Text("Port") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = userF, onValueChange = { userF = it },
                label = { Text("SSH user (e.g. rahul)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = pathF, onValueChange = { pathF = it },
                label = { Text("Remote path") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        onSaveConfig(hostF, portF.toIntOrNull() ?: 22, userF, pathF)
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("Save config") }
                Button(
                    enabled = !inProgress,
                    onClick = {
                        onSaveConfig(hostF, portF.toIntOrNull() ?: 22, userF, pathF)
                        onBackup()
                    },
                    modifier = Modifier.weight(1f)
                ) { Text(if (inProgress) "Backing up…" else "Back up now") }
            }

            Spacer(Modifier.height(20.dp))
            Text("SSH public key (paste into ALPUCA ~/.ssh/authorized_keys):",
                fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            SelectionContainer {
                Text(
                    text = if (publicKey.isBlank()) "Generating…" else publicKey,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                )
            }
            OutlinedButton(onClick = onCopyKey, enabled = publicKey.isNotBlank()) {
                Text("Copy public key")
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
}

private fun copyToClipboard(context: Context, label: String, value: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    cm.setPrimaryClip(ClipData.newPlainText(label, value))
}

private fun formatLastBackup(ms: Long): String {
    if (ms <= 0L) return "never"
    val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        .withLocale(Locale.US).withZone(ZoneId.systemDefault())
    return fmt.format(Instant.ofEpochMilli(ms))
}

private fun formatThousands(n: Long): String =
    "%,d".format(Locale.US, n)

/**
 * Global "which pronouns do you want to practice" picker. Tapping a chip flips that
 * person key for BOTH present and past tense in PronounFilterStore — gives the user a
 * fast way to narrow drills to just "ja" or "ja + ty + on" without hunting through
 * each verb's checkboxes. Per-verb checkboxes still override locally; this is the
 * global default that every Play All / Quiz / Random play respects.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PracticePronounsCard(app: LangbangApplication) {
    // Local recomposition trigger — when we flip a key in PronounFilterStore we want
    // the chips to repaint immediately even though SharedPreferences won't notify us.
    var bump by remember { mutableStateOf(0) }
    val order = listOf("1sg", "2sg", "3sg", "1pl", "2pl", "3pl")
    val included = remember(bump) {
        order.filter { app.pronounFilter.isIncluded(it, PronounFilterStore.TENSE_PRESENT) }
            .toSet()
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionHeader("Practice pronouns")
            Text(
                "Limit which subject pronouns appear in drills. Toggle off the ones " +
                    "you don't want to practice (e.g. focus on ja / ty / on only). " +
                    "Applies to Play All, Quiz, and Play Phrases across all lessons.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                order.forEach { k ->
                    val selected = k in included
                    FilterChip(
                        selected = selected,
                        onClick = {
                            val now = !selected
                            // Toggle the same key for present AND past so the user
                            // doesn't have to flip twice.
                            app.pronounFilter.setIncluded(k, now, PronounFilterStore.TENSE_PRESENT)
                            app.pronounFilter.setIncluded(k, now, PronounFilterStore.TENSE_PAST)
                            bump++
                        },
                        label = { Text(pronounChipLabel(k), fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }
            if (included.isEmpty()) {
                Text(
                    "All pronouns off — drills will be empty. Toggle at least one back on.",
                    fontSize = 11.sp,
                    color = LbColors.Danger
                )
            }
        }
    }
}

private fun pronounChipLabel(k: String): String = when (k) {
    "1sg" -> "ja (I)"
    "2sg" -> "ty (you)"
    "3sg" -> "on / ona"
    "1pl" -> "my (we)"
    "2pl" -> "wy (y'all)"
    "3pl" -> "oni / one"
    else -> k
}

/**
 * Mirror of the always-visible top-bar SentenceRegen banner inside Settings. Buttons
 * trigger a *download* from R2 (pre-generated bundles from the
 * `langbang-pregen-sentences` Edge Function) — never an on-device Gemini call for
 * canonical content. The old multi-minute client-side Gemini regen path is gone:
 * R2 sync usually finishes in <30s and runs automatically on launch via
 * `LangbangApplication.onCreate`. This card is only useful when the user wants to
 * manually re-check R2 for newer bundles after the prompt has been bumped.
 *
 * No-op on a "Re-sync" tap when local cache already matches the manifest — the
 * underlying service returns Done(downloaded=0) and the row reads "All sentences
 * up to date". The button stays available for force-refresh.
 */
@Composable
private fun RegenerateSentencesCard(
    app: LangbangApplication,
    @Suppress("UNUSED_PARAMETER") scope: kotlinx.coroutines.CoroutineScope,
    @Suppress("UNUSED_PARAMETER") context: Context
) {
    val online by app.network.online.collectAsState()
    val state by app.sentenceRegen.state.collectAsState()
    val downloading = state is com.sponic.langbang.domain.SentenceRegenService.State.Downloading

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(20.dp)) {
            SectionHeader("Sentence library")
            Spacer(Modifier.height(4.dp))
            Text(
                "Example sentences for verbs, adjectives, adverbs, and nouns are " +
                    "pre-generated server-side and stored in R2; the app pulls missing " +
                    "bundles automatically on launch. Tap below to force a re-check " +
                    "(only useful right after a prompt update).",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(
                    enabled = online && !downloading,
                    onClick = { app.sentenceRegen.startIfNeeded(force = true) }
                ) {
                    Text(if (downloading) "Re-syncing…" else "Re-sync sentences from R2")
                }
                if (downloading) {
                    Spacer(Modifier.width(12.dp))
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp), strokeWidth = 2.dp
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            // Status line — mirrors the always-visible top-bar banner so the Settings
            // card and the banner agree on what's happening.
            when (val s = state) {
                is com.sponic.langbang.domain.SentenceRegenService.State.Idle ->
                    Text("Ready.", fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                is com.sponic.langbang.domain.SentenceRegenService.State.Downloading -> {
                    val label = if (s.total == 0) "Fetching manifest…"
                                else "Downloading ${s.done}/${s.total} · ${s.currentKey}"
                    Text(label, fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                }
                is com.sponic.langbang.domain.SentenceRegenService.State.Done -> {
                    val msg = if (s.downloaded == 0 && s.failures == 0)
                        "All sentences up to date — ${s.total} bundles indexed."
                    else if (s.failures == 0)
                        "Updated — ${s.downloaded} of ${s.total} bundles downloaded."
                    else
                        "Updated — ${s.downloaded} of ${s.total} (${s.failures} failed, tap to retry)."
                    Text(msg, fontSize = 11.sp,
                        color = if (s.failures == 0) LbColors.Success else LbColors.Danger)
                }
                is com.sponic.langbang.domain.SentenceRegenService.State.Failed ->
                    Text("Sync failed — ${s.message}", fontSize = 11.sp, color = LbColors.Danger)
            }
            if (!online) {
                Spacer(Modifier.height(4.dp))
                Text("Offline — connect to wifi to re-sync.",
                    fontSize = 11.sp, color = LbColors.Danger)
            }
        }
    }
}
