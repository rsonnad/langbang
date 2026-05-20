package com.sponic.langbang.ui.settings

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
import com.sponic.langbang.BuildConfig
import com.sponic.langbang.LangbangApplication
import com.sponic.langbang.domain.UsageSnapshot
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
private fun VersionHeader() {
    Text(
        "langbang v${BuildConfig.VERSION_NAME} · build ${BuildConfig.BUILD_NUMBER} · ${BuildConfig.BUILD_TIMESTAMP}",
        fontSize = 12.sp,
        fontFamily = FontFamily.Monospace,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    )
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
                        lastStatus.startsWith("OK") -> Color(0xFF1B7F3A)
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
