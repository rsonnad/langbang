package com.sponic.langbang.domain

import com.sponic.langbang.BuildConfig
import com.sponic.langbang.data.LessonRepository
import com.sponic.langbang.data.model.audioPronoun
import com.sponic.langbang.integrations.AzureTtsClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Pulls pre-generated TTS mp3s from R2 (via the langbang-pregen-audio Supabase Edge
 * Function) and stores them in AudioCache. The first launch on a new device can take
 * tens of MB of audio — pulling them from R2 is much faster than synthesising 1500+
 * mp3s on-device, and the Azure key never leaves the function.
 *
 * If the Edge Function isn't reachable (not deployed, offline, etc.) the caller can
 * fall back to PrefetchService for on-device synthesis.
 */
class R2AudioDownloader(
    private val cache: AudioCache,
    private val repo: LessonRepository,
    private val network: NetworkMonitor
) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val endpoint =
        "${BuildConfig.SUPABASE_URL}/functions/v1/langbang-pregen-audio"

    /** All (text, voice, locale) triples the app currently needs cached. */
    fun buildManifestPhrases(): List<Phrase> {
        val lesson = repo.lesson2()
        val adjLesson = repo.lesson3()
        val advLesson = repo.lesson4()
        val pron = repo.pronunciation()
        return buildList {
            fun addPl(text: String) {
                if (text.isEmpty()) return
                add(Phrase(text, AzureTtsClient.PL_PL_F, AzureTtsClient.LOCALE_PL))
                add(Phrase(text, AzureTtsClient.PL_PL_F_SLOW_V2, AzureTtsClient.LOCALE_PL))
            }
            lesson.phrases.forEach { p ->
                add(Phrase(p.en, AzureTtsClient.EN_US_F, AzureTtsClient.LOCALE_EN))
                addPl(p.pl)
            }
            lesson.verbs.forEach { v ->
                v.forms.forEach { (k, f) -> addPl("${audioPronoun(k)} $f".trim()) }
                v.past_forms?.forEach { (k, f) -> addPl("${audioPronoun(k)} $f".trim()) }
            }
            lesson.pronouns.forEach { p ->
                p.case_forms.values.forEach { addPl(it) }
            }
            adjLesson.adjectives.forEach { a ->
                a.nom.values.forEach { addPl(it) }
                a.acc.values.forEach { addPl(it) }
            }
            advLesson.adverbs.forEach { adv -> addPl(adv.lemma) }
            pron.phonemes.forEach { ph -> ph.examples.forEach { addPl(it.pl) } }
            lesson.verbs.forEach { v ->
                repo.sentencesFor(v.lemma).forEach { s ->
                    add(Phrase(s.en, AzureTtsClient.EN_US_F, AzureTtsClient.LOCALE_EN))
                    addPl(s.pl)
                }
            }
            adjLesson.adjectives.forEach { a ->
                repo.adjectiveSentencesFor(a.lemma).forEach { s ->
                    add(Phrase(s.en, AzureTtsClient.EN_US_F, AzureTtsClient.LOCALE_EN))
                    addPl(s.pl)
                }
            }
            advLesson.adverbs.forEach { adv ->
                repo.adverbSentencesFor(adv.lemma).forEach { s ->
                    add(Phrase(s.en, AzureTtsClient.EN_US_F, AzureTtsClient.LOCALE_EN))
                    addPl(s.pl)
                }
            }
        }.distinctBy { it.text + "|" + it.locale + "|" + it.voice }
    }

    /**
     * Asks the Edge Function for the R2 URLs of every phrase that isn't already on
     * disk, then downloads each missing mp3 in turn. [onProgress] fires after every
     * file with (done, total, current).
     */
    suspend fun downloadAll(onProgress: suspend (Int, Int, String) -> Unit): Result<DownloadSummary> =
        withContext(Dispatchers.IO) {
            if (!network.isOnline()) {
                return@withContext Result.failure(IOException("Offline — download skipped."))
            }
            val phrases = buildManifestPhrases()
            // Only ask the function about phrases we don't already have — saves bandwidth.
            val missing = phrases.filter { p ->
                !cache.has(cache.fileFor(p.locale, p.voice, p.text))
            }
            if (missing.isEmpty()) {
                return@withContext Result.success(
                    DownloadSummary(totalWanted = phrases.size, fetched = 0, alreadyCached = phrases.size)
                )
            }
            val manifest = try {
                fetchManifest(missing)
            } catch (t: Throwable) {
                return@withContext Result.failure(t)
            }
            var fetched = 0
            var failed = 0
            manifest.forEachIndexed { i, m ->
                onProgress(i, manifest.size, m.text)
                val file = cache.fileFor(m.locale, m.voice, m.text)
                if (cache.has(file)) return@forEachIndexed
                if (m.url.isEmpty()) { failed++; return@forEachIndexed }
                try {
                    val conn = URL(m.url).openConnection() as HttpURLConnection
                    conn.requestMethod = "GET"
                    conn.connectTimeout = 15000
                    conn.readTimeout = 30000
                    if (conn.responseCode !in 200..299) {
                        failed++
                        return@forEachIndexed
                    }
                    file.parentFile?.mkdirs()
                    conn.inputStream.use { input ->
                        file.outputStream().use { out -> input.copyTo(out) }
                    }
                    fetched++
                } catch (_: Throwable) {
                    failed++
                }
            }
            onProgress(manifest.size, manifest.size, "")
            Result.success(
                DownloadSummary(
                    totalWanted = phrases.size,
                    fetched = fetched,
                    alreadyCached = phrases.size - missing.size,
                    failed = failed
                )
            )
        }

    private fun fetchManifest(phrases: List<Phrase>): List<ManifestEntry> {
        val conn = URL(endpoint).openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.connectTimeout = 15000
        conn.readTimeout = 120000
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("Authorization", "Bearer ${BuildConfig.SUPABASE_ANON_KEY}")
        val body = json.encodeToString(
            ManifestRequest.serializer(),
            ManifestRequest(phrases)
        )
        conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        if (conn.responseCode !in 200..299) {
            val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            throw IOException("Edge function ${conn.responseCode}: ${err.take(200)}")
        }
        val raw = conn.inputStream.bufferedReader().use { it.readText() }
        val resp = json.decodeFromString(ManifestResponse.serializer(), raw)
        return resp.manifest
    }

    @Serializable
    data class Phrase(val text: String, val voice: String, val locale: String)

    @Serializable
    private data class ManifestRequest(val phrases: List<Phrase>)

    @Serializable
    data class ManifestEntry(
        val text: String,
        val voice: String,
        val locale: String,
        val sha1: String = "",
        val url: String = "",
        val uploaded: Boolean = false,
        val error: String? = null
    )

    @Serializable
    private data class ManifestResponse(val manifest: List<ManifestEntry>)

    data class DownloadSummary(
        val totalWanted: Int,
        val fetched: Int,
        val alreadyCached: Int,
        val failed: Int = 0
    )
}
