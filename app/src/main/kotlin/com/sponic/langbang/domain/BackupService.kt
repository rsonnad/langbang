package com.sponic.langbang.domain

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.provider.Settings
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSch
import com.jcraft.jsch.KeyPair
import com.jcraft.jsch.Session
import com.jcraft.jsch.SftpException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Properties
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class BackupConfig(
    val host: String,
    val port: Int,
    val user: String,
    val remoteDir: String,
)

data class BackupState(
    val config: BackupConfig,
    val publicKeyOpenSsh: String,
    val lastBackupAtMs: Long,
    val lastBackupFile: String,
    val lastStatus: String,
    val inProgress: Boolean,
    val destinationLabel: String,
)

/**
 * SFTP-pushes a zip of the audio cache, user-created JSON data, and SharedPreferences to
 * ALPUCA. The first time the service runs, it generates a 2048-bit RSA SSH keypair under
 * filesDir/ssh; the public key is shown in the Settings screen for the user to paste into
 * ALPUCA's authorized_keys.
 *
 * Restore path is still manual: copy the root JSON files and audio/ into the new device's
 * filesDir, and copy shared_prefs/ into the app data shared_prefs directory. Restore-side UI
 * is deliberately out of scope for v1, but the archive now contains the custom study data
 * needed to recover phrases, words, generated examples, settings, and stars.
 */
class BackupService(
    private val context: Context,
) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val sshDir: File = File(context.filesDir, "ssh").also { it.mkdirs() }
    private val privateKeyFile = File(sshDir, "id_rsa")
    private val publicKeyFile = File(sshDir, "id_rsa.pub")

    private val _state = MutableStateFlow(read(inProgress = false))
    val state: StateFlow<BackupState> = _state.asStateFlow()

    fun updateConfig(host: String, port: Int, user: String, remoteDir: String) {
        prefs.edit()
            .putString(KEY_HOST, host.trim())
            .putInt(KEY_PORT, port.coerceIn(1, 65535))
            .putString(KEY_USER, user.trim())
            .putString(KEY_REMOTE_DIR, remoteDir.trim().trimEnd('/'))
            .apply()
        _state.value = read(inProgress = _state.value.inProgress)
    }

    /** Returns the OpenSSH public key the user must add to ALPUCA `~/.ssh/authorized_keys`. */
    fun publicKey(): String {
        ensureKeypair()
        return publicKeyFile.readText().trim()
    }

    /** Runs a single backup. Suspending — call from a coroutine, e.g. lifecycleScope.launch. */
    suspend fun runBackup(): Result<String> = withContext(Dispatchers.IO) {
        val cfg = read(inProgress = false).config
        if (cfg.user.isBlank() || cfg.host.isBlank() || cfg.remoteDir.isBlank()) {
            val msg = "Backup not configured — set host, user, and remote path in Settings."
            recordStatus(success = false, status = msg, fileName = null)
            return@withContext Result.failure(IllegalStateException(msg))
        }

        _state.value = _state.value.copy(inProgress = true, lastStatus = "Building archive…")
        try {
            ensureKeypair()
            val zip = buildZip()
            val name = zip.name
            _state.value = _state.value.copy(lastStatus = "Uploading $name…")
            uploadSftp(cfg, zip)
            zip.delete()

            recordStatus(success = true, status = "OK — uploaded $name", fileName = name)
            Result.success(name)
        } catch (t: Throwable) {
            recordStatus(success = false, status = "Failed: ${t.message ?: t.javaClass.simpleName}", fileName = null)
            Result.failure(t)
        }
    }

    // ---- internals ----

    private fun ensureKeypair() {
        if (privateKeyFile.exists() && publicKeyFile.exists()) return
        val jsch = JSch()
        val kp = KeyPair.genKeyPair(jsch, KeyPair.RSA, 2048)
        kp.writePrivateKey(privateKeyFile.absolutePath)
        kp.writePublicKey(publicKeyFile.absolutePath, "langbang@${deviceLabel()}")
        kp.dispose()
        // Tighten perms — best-effort; Android already gives us app-private mode.
        @Suppress("SetWorldReadable") privateKeyFile.setReadable(false, false)
        privateKeyFile.setReadable(true, true)
    }

    private fun buildZip(): File {
        val tsLabel = nowLabel()
        val outDir = File(context.cacheDir, "backups").also { it.mkdirs() }
        val out = File(outDir, "langbang-$tsLabel.zip")

        val sources = buildList {
            add(File(context.filesDir, "audio") to "audio")
            add(File(context.applicationInfo.dataDir, "shared_prefs") to "shared_prefs")
            userDataFileNames.forEach { name ->
                add(File(context.filesDir, name) to name)
            }
        }

        val manifest = """
            {
              "createdAt": "${Instant.now()}",
              "device": {
                "model": "${Build.MODEL}",
                "manufacturer": "${Build.MANUFACTURER}",
                "androidId": "${androidId()}"
              },
              "app": {
                "package": "${context.packageName}"
              }
            }
        """.trimIndent()

        ZipOutputStream(FileOutputStream(out)).use { zos ->
            zos.putNextEntry(ZipEntry("manifest.json"))
            zos.write(manifest.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            for ((src, prefix) in sources) {
                if (!src.exists()) continue
                if (src.isDirectory) {
                    addDirToZip(src, prefix, zos)
                } else if (src.isFile && src.canRead()) {
                    addFileToZip(src, prefix, zos)
                }
            }
        }
        return out
    }

    private fun addFileToZip(file: File, entryName: String, zos: ZipOutputStream) {
        val buf = ByteArray(64 * 1024)
        zos.putNextEntry(ZipEntry(entryName))
        FileInputStream(file).use { input ->
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                zos.write(buf, 0, n)
            }
        }
        zos.closeEntry()
    }

    private fun addDirToZip(dir: File, prefix: String, zos: ZipOutputStream) {
        val stack = ArrayDeque<Pair<File, String>>()
        stack.addLast(dir to prefix)
        val buf = ByteArray(64 * 1024)
        while (stack.isNotEmpty()) {
            val (d, p) = stack.removeLast()
            d.listFiles()?.forEach { f ->
                val entryName = "$p/${f.name}"
                if (f.isDirectory) {
                    stack.addLast(f to entryName)
                } else if (f.isFile && f.canRead()) {
                    zos.putNextEntry(ZipEntry(entryName))
                    FileInputStream(f).use { input ->
                        while (true) {
                            val n = input.read(buf)
                            if (n <= 0) break
                            zos.write(buf, 0, n)
                        }
                    }
                    zos.closeEntry()
                }
            }
        }
    }

    private fun uploadSftp(cfg: BackupConfig, zip: File) {
        val jsch = JSch()
        jsch.addIdentity(privateKeyFile.absolutePath)

        var session: Session? = null
        var sftp: ChannelSftp? = null
        try {
            session = jsch.getSession(cfg.user, cfg.host, cfg.port)
            // Tailscale provides the trust boundary; skip host-key prompts for v1.
            session.setConfig(Properties().apply {
                setProperty("StrictHostKeyChecking", "no")
                setProperty("PreferredAuthentications", "publickey")
            })
            session.connect(20_000)
            sftp = session.openChannel("sftp") as ChannelSftp
            sftp.connect(20_000)

            val deviceSubdir = sanitize(deviceLabel())
            val remoteParent = "${cfg.remoteDir}/$deviceSubdir"
            mkdirRecursive(sftp, remoteParent)
            sftp.cd(remoteParent)
            FileInputStream(zip).use { sftp.put(it, zip.name) }
        } finally {
            try { sftp?.disconnect() } catch (_: Throwable) {}
            try { session?.disconnect() } catch (_: Throwable) {}
        }
    }

    private fun mkdirRecursive(sftp: ChannelSftp, path: String) {
        // Path is relative to the SFTP session home unless it starts with '/'.
        val segments = path.split('/').filter { it.isNotEmpty() }
        val absolute = path.startsWith('/')
        val baos = StringBuilder(if (absolute) "/" else "")
        for (seg in segments) {
            if (baos.length > if (absolute) 1 else 0) baos.append('/')
            baos.append(seg)
            val cur = baos.toString()
            try {
                sftp.stat(cur)
            } catch (_: SftpException) {
                try { sftp.mkdir(cur) } catch (_: SftpException) {
                    // Race or perms issue — let the next stat surface a real error.
                }
            }
        }
    }

    private fun recordStatus(success: Boolean, status: String, fileName: String?) {
        val editor = prefs.edit().putString(KEY_LAST_STATUS, status)
        if (success) {
            editor.putLong(KEY_LAST_BACKUP_MS, System.currentTimeMillis())
            if (fileName != null) editor.putString(KEY_LAST_BACKUP_FILE, fileName)
        }
        editor.apply()
        _state.value = read(inProgress = false)
    }

    private fun read(inProgress: Boolean): BackupState {
        ensurePublicKeyText()
        val cfg = BackupConfig(
            host = prefs.getString(KEY_HOST, DEFAULT_HOST)!!,
            port = prefs.getInt(KEY_PORT, DEFAULT_PORT),
            user = prefs.getString(KEY_USER, "")!!,
            remoteDir = prefs.getString(KEY_REMOTE_DIR, DEFAULT_REMOTE_DIR)!!,
        )
        val destination = if (cfg.user.isBlank() || cfg.host.isBlank() || cfg.remoteDir.isBlank()) {
            ""
        } else {
            "${cfg.user}@${cfg.host}:${cfg.remoteDir}/${sanitize(deviceLabel())}/"
        }
        return BackupState(
            config = cfg,
            publicKeyOpenSsh = if (publicKeyFile.exists()) publicKeyFile.readText().trim() else "",
            lastBackupAtMs = prefs.getLong(KEY_LAST_BACKUP_MS, 0L),
            lastBackupFile = prefs.getString(KEY_LAST_BACKUP_FILE, "") ?: "",
            lastStatus = prefs.getString(KEY_LAST_STATUS, "") ?: "",
            inProgress = inProgress,
            destinationLabel = destination,
        )
    }

    private fun ensurePublicKeyText() {
        // Lazily materialize the keypair so the Settings screen can render the pubkey
        // immediately without forcing a backup attempt first.
        if (!publicKeyFile.exists()) ensureKeypair()
    }

    private fun deviceLabel(): String = "${Build.MANUFACTURER}-${Build.MODEL}"

    private fun androidId(): String =
        try {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"
        } catch (_: Throwable) { "unknown" }

    private fun sanitize(s: String): String =
        s.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "device" }

    private fun nowLabel(): String =
        DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
            .withZone(ZoneOffset.UTC).format(Instant.now())

    companion object {
        const val PREFS = "langbang_backup"
        private const val KEY_HOST = "host"
        private const val KEY_PORT = "port"
        private const val KEY_USER = "user"
        private const val KEY_REMOTE_DIR = "remote_dir"
        private const val KEY_LAST_BACKUP_MS = "last_backup_ms"
        private const val KEY_LAST_BACKUP_FILE = "last_backup_file"
        private const val KEY_LAST_STATUS = "last_status"

        // Defaults assume ALPUCA is reachable by its Tailscale MagicDNS short name. The
        // user can override host/user/path in the Settings screen before pressing Back Up.
        private const val DEFAULT_HOST = "alpuca"
        private const val DEFAULT_PORT = 22
        private const val DEFAULT_REMOTE_DIR = "RVAULT/backups/langbang"

        private val userDataFileNames = listOf(
            "user-verbs.json",
            "user-adjectives.json",
            "user-adverbs.json",
            "user-nouns.json",
            "user-phrases.json",
            "verb-sentences.json",
            "adjective-sentences.json",
            "adverb-sentences.json",
            "noun-sentences.json",
        )
    }
}

@Suppress("unused")
private fun ByteArrayOutputStream.toUtf8() = toString(Charsets.UTF_8.name())
