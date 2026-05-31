package com.sponic.langbang.domain

import android.content.Context
import java.io.File
import java.security.MessageDigest

class AudioCache(context: Context) {

    private val root: File = File(context.filesDir, "audio").also { it.mkdirs() }

    fun fileFor(language: String, voice: String, text: String): File {
        val key = sha1("$language|$voice|$text").take(20)
        return File(root, "$language-$key.mp3")
    }

    fun has(file: File): Boolean = file.exists() && file.length() > 0

    /**
     * Walks the cache directory once and returns (file count, total bytes). Used by
     * the Settings Audio-cache card to show actual on-disk state — independent of
     * whichever code path (PrefetchWorker, R2 downloader) put each file there.
     * O(N) but N is bounded (~10k files) so the cost is negligible on screen open.
     */
    fun stats(): Stats {
        val files = root.listFiles() ?: return Stats(0, 0L)
        var count = 0
        var bytes = 0L
        files.forEach { f ->
            if (f.isFile && f.length() > 0) {
                count++
                bytes += f.length()
            }
        }
        return Stats(count, bytes)
    }

    data class Stats(val count: Int, val bytes: Long) {
        val megabytes: Double get() = bytes / 1_000_000.0
    }

    private fun sha1(s: String): String {
        val md = MessageDigest.getInstance("SHA-1")
        val bytes = md.digest(s.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
