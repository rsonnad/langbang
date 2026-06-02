package com.sponic.langbang.ui

import com.sponic.langbang.ui.theme.LbColors

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sponic.langbang.LangbangApplication
import com.sponic.langbang.domain.UpdateChecker
import kotlinx.coroutines.launch
import java.io.File

/**
 * Launch-time "update available" strip. Self-contained: checks R2 once on first
 * composition, and on tap downloads the new APK + fires the system installer. Renders
 * nothing when up-to-date / offline / dismissed. Lives at the top of the app under the
 * header so it's the first thing seen when a newer build exists.
 *
 * The download happens in the background; the only manual step is Android's own install
 * confirmation (unavoidable for a sideload). If the user hasn't granted "install unknown
 * apps" yet, the first install attempt routes them to that toggle.
 */
@Composable
fun UpdateBanner(
    app: LangbangApplication,
    onInstallPermissionNeeded: () -> Unit,
    onInstallReady: (File) -> Unit
) {
    val scope = rememberCoroutineScope()

    var available by remember { mutableStateOf<UpdateChecker.Available?>(null) }
    var dismissed by remember { mutableStateOf(false) }
    var downloading by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        available = app.updateChecker.check()
    }

    val update = available
    if (update == null || dismissed) return

    Surface(color = LbColors.Primary, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (downloading) "Downloading v${update.versionCode}… $progress%"
                else "Update v${update.versionCode} available",
                color = LbColors.OnPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            error?.let {
                Text(it, color = LbColors.AccentSoft, fontSize = 11.sp)
                Spacer(Modifier.width(8.dp))
            }
            if (downloading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = LbColors.OnPrimary
                )
            } else {
                TextButton(onClick = { dismissed = true }) {
                    Text("Later", color = LbColors.OnPrimary.copy(alpha = 0.8f), fontSize = 12.sp)
                }
                Spacer(Modifier.width(4.dp))
                Button(
                    onClick = {
                        // If the user hasn't allowed installs from langbang yet, send
                        // them to the branded handoff screen first; Android Settings
                        // takes over only after they tap through from there.
                        if (!app.updateChecker.canInstall()) {
                            onInstallPermissionNeeded()
                            return@Button
                        }
                        downloading = true
                        error = null
                        scope.launch {
                            val apk = app.updateChecker.download(update.url) { p -> progress = p }
                            downloading = false
                            if (apk == null) {
                                error = "Download failed"
                            } else {
                                onInstallReady(apk)
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = LbColors.Gold,
                        contentColor = LbColors.OnPrimary
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("Install", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
