package com.sponic.langbang.domain

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import com.sponic.langbang.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.sponic.langbang.data.LbJson
import kotlinx.serialization.Serializable
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * In-app APK self-update for this sideloaded build. On launch we fetch a tiny JSON
 * manifest from R2 ([MANIFEST_URL]) describing the newest published build; if its
 * versionCode is higher than the running [BuildConfig.BUILD_NUMBER] we surface an
 * "update available" banner. Tapping it downloads langbang-latest.apk to cacheDir and
 * fires the system package installer.
 *
 * Because the debug signing key is pinned (committed keystore), the downloaded APK has
 * the same signature as what's installed, so Android performs a true in-place upgrade —
 * no uninstall, the audio cache survives.
 *
 * Stock Android always shows its own install-confirmation dialog (you can't install
 * silently without device-owner/MDM), so "auto-update" means: detect + download in the
 * background, then one tap on the system prompt. That's the best achievable for a
 * non-Play sideload.
 */
class UpdateChecker(
    private val context: Context,
    private val network: NetworkMonitor
) {
    private val json = LbJson.lenient

    @Serializable
    private data class LatestManifest(
        val versionCode: Int = 0,
        val versionName: String = "",
        val url: String = "",
        val notes: String = ""
    )

    data class Available(
        val versionCode: Int,
        val versionName: String,
        val url: String,
        val notes: String
    )

    /** Returns the newer build if one exists, else null (up-to-date, offline, or error). */
    suspend fun check(): Available? = withContext(Dispatchers.IO) {
        if (!network.isOnline()) return@withContext null
        val manifest = try {
            val conn = (URL(MANIFEST_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 8000
                // R2 caches aggressively; ask for a fresh copy so a just-published
                // build is seen promptly.
                setRequestProperty("Cache-Control", "no-cache")
            }
            if (conn.responseCode !in 200..299) return@withContext null
            val raw = conn.inputStream.bufferedReader().use { it.readText() }
            json.decodeFromString(LatestManifest.serializer(), raw)
        } catch (_: Throwable) {
            return@withContext null
        }
        if (manifest.versionCode > BuildConfig.BUILD_NUMBER && manifest.url.isNotEmpty()) {
            Available(manifest.versionCode, manifest.versionName, manifest.url, manifest.notes)
        } else null
    }

    /** Downloads the APK to cacheDir/update.apk. [onProgress] fires 0..100. */
    suspend fun download(url: String, onProgress: (Int) -> Unit = {}): File? =
        withContext(Dispatchers.IO) {
            try {
                val out = File(context.cacheDir, "update.apk")
                if (out.exists()) out.delete()
                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15000
                    readTimeout = 60000
                }
                if (conn.responseCode !in 200..299) return@withContext null
                val len = conn.contentLength
                conn.inputStream.use { input ->
                    out.outputStream().use { o ->
                        val buf = ByteArray(64 * 1024)
                        var total = 0L
                        var r: Int
                        while (input.read(buf).also { r = it } >= 0) {
                            o.write(buf, 0, r)
                            total += r
                            if (len > 0) onProgress((total * 100 / len).toInt())
                        }
                    }
                }
                out
            } catch (_: Throwable) {
                null
            }
        }

    /** True if the user has already granted "install unknown apps" to langbang. */
    fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    /** Opens the system "install unknown apps" toggle for this app. */
    fun openInstallPermissionSettings() {
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    /** Fires the system package installer for a downloaded APK. */
    fun install(apk: File) {
        val uri = FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", apk
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { context.startActivity(intent) }
    }

    companion object {
        // Public R2 dev URL — no auth, no secret. Written by scripts/publish-r2.sh on
        // every publish so it always names the newest build.
        private const val MANIFEST_URL =
            "https://pub-5a7344c4dab2467eb917ff4b897e066d.r2.dev/langbang/langbang-latest.json"
    }
}
