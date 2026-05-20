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

    private fun sha1(s: String): String {
        val md = MessageDigest.getInstance("SHA-1")
        val bytes = md.digest(s.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
