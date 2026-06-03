package com.sponic.langbang.ui.phrase

import com.sponic.langbang.ui.theme.LbColors

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.sponic.langbang.LangbangApplication
import com.sponic.langbang.data.model.PhraseEntry
import com.sponic.langbang.domain.sourceAudioVoice
import com.sponic.langbang.domain.sourceLanguageLabel
import com.sponic.langbang.domain.targetAudioVoice
import com.sponic.langbang.domain.targetLanguageLabel
import com.sponic.langbang.integrations.PronunciationScore
import kotlinx.coroutines.launch

@Composable
fun PhraseDetail(
    app: LangbangApplication,
    phrase: PhraseEntry,
    prefetchReady: Boolean,
    onWordTap: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val sourceLabel = app.sourceLanguageLabel()
    val targetLabel = app.targetLanguageLabel()
    val targetVoice = app.targetAudioVoice()
    var assessing by remember { mutableStateOf(false) }
    var score by remember(phrase) { mutableStateOf<PronunciationScore?>(null) }
    var error by remember(phrase) { mutableStateOf<String?>(null) }
    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED
        )
    }
    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasMicPermission = granted
        if (!granted) {
            error = "Microphone permission denied. Enable it in Android Settings → " +
                "Apps → langbang → Permissions, then tap again."
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text(phrase.en, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)

        Row(verticalAlignment = Alignment.CenterVertically) {
            PlayChip(label = "▶ $sourceLabel") {
                val source = app.sourceAudioVoice()
                val f = app.audioCache.fileFor(
                    source.locale, source.voice, phrase.en
                )
                app.audioPlayer.play(f)
            }
        }

        PolishLine(phrase.pl, onWordTap = onWordTap)

        Row(verticalAlignment = Alignment.CenterVertically) {
            PlayChip(label = "▶ $targetLabel") {
                val f = app.audioCache.fileFor(
                    targetVoice.locale, targetVoice.voice, phrase.pl
                )
                app.audioPlayer.play(f)
            }
        }

        Spacer(Modifier.height(20.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = LbColors.SurfaceRaised),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Your turn — say it in $targetLabel", fontSize = 14.sp, color = LbColors.Label)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = {
                            if (!hasMicPermission) {
                                error = null
                                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            } else {
                                assessing = true
                                error = null
                                score = null
                                scope.launch {
                                    app.pron.assessOnce(targetVoice.locale, phrase.pl)
                                        .onSuccess { score = it }
                                        .onFailure { error = it.message }
                                    assessing = false
                                }
                            }
                        },
                        enabled = !assessing,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        shape = RoundedCornerShape(28.dp)
                    ) {
                        Icon(Icons.Default.Mic, contentDescription = null, tint = Color.White)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            when {
                                assessing -> "Listening…"
                                !hasMicPermission -> "Grant mic access"
                                else -> "Tap to speak"
                            },
                            color = Color.White
                        )
                    }
                    if (assessing) {
                        Spacer(Modifier.width(12.dp))
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    }
                }
                score?.let { ScoreReadout(it) }
                error?.let { Text("Error: $it", color = Color.Red, fontSize = 12.sp) }
            }
        }

        if (!prefetchReady) {
            Text(
                "Audio still downloading — playback may use on-device TTS until cache is ready.",
                fontSize = 11.sp, color = LbColors.TextMuted
            )
        }
    }
}

@Composable
private fun PolishLine(pl: String, onWordTap: (String) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        pl.split(" ").forEach { tok ->
            val clean = tok.trim('.', ',', '?', '!')
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = LbColors.PrimarySoft,
                onClick = { onWordTap(clean) }
            ) {
                Text(
                    tok,
                    fontSize = 28.sp,
                    color = LbColors.Primary,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun PlayChip(label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) { Text(label, fontSize = 14.sp) }
}

@Composable
private fun ScoreReadout(s: PronunciationScore) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            "Heard: \"${s.transcribed}\"",
            fontSize = 13.sp, color = LbColors.TextSecondary
        )
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ScoreBadge("Accuracy", s.accuracy)
            ScoreBadge("Fluency", s.fluency)
            ScoreBadge("Complete", s.completeness)
            ScoreBadge("Overall", s.pronunciation)
        }
        if (s.words.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                s.words.forEach { w ->
                    val color = when {
                        w.accuracy >= 80 -> LbColors.Success
                        w.accuracy >= 60 -> LbColors.Warning
                        else -> LbColors.Danger
                    }
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = color.copy(alpha = 0.12f)
                    ) {
                        Text(
                            "${w.word} ${w.accuracy.toInt()}",
                            color = color, fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ScoreBadge(label: String, v: Double) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 11.sp, color = LbColors.TextMuted)
        Text(v.toInt().toString(), fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
    }
}
