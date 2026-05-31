package com.sponic.langbang.domain

import com.sponic.langbang.BuildConfig
import com.sponic.langbang.data.LessonRepository
import com.sponic.langbang.data.VerbSentenceStore
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
        val nounLesson = repo.lesson6()
        val pron = repo.pronunciation()
        return buildList {
            fun addPl(text: String) {
                if (text.isEmpty()) return
                add(Phrase(text, AzureTtsClient.PL_PL_F, AzureTtsClient.LOCALE_PL))
                // Both slow styles so the Settings → Slow audio style toggle is instant
                // — flipping it picks the already-cached mp3, no re-download needed.
                add(Phrase(text, AzureTtsClient.PL_PL_F_SLOW_V2, AzureTtsClient.LOCALE_PL))
                add(Phrase(text, AzureTtsClient.PL_PL_F_SLOW_ART, AzureTtsClient.LOCALE_PL))
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
            nounLesson.nouns.forEach { n ->
                n.nom.values.forEach { addPl(it) }
                n.acc.values.forEach { addPl(it) }
                n.gen.values.forEach { addPl(it) }
            }
            pron.phonemes.forEach { ph -> ph.examples.forEach { addPl(it.pl) } }
            lesson.verbs.forEach { v ->
                repo.sentencesFor(v.lemma, VerbSentenceStore.TENSE_PRESENT).forEach { s ->
                    add(Phrase(s.en, AzureTtsClient.EN_US_F, AzureTtsClient.LOCALE_EN))
                    addPl(s.pl)
                }
                repo.sentencesFor(v.lemma, VerbSentenceStore.TENSE_PAST).forEach { s ->
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
            nounLesson.nouns.forEach { n ->
                repo.nounSentencesFor(n.lemma).forEach { s ->
                    add(Phrase(s.en, AzureTtsClient.EN_US_F, AzureTtsClient.LOCALE_EN))
                    addPl(s.pl)
                }
            }
        }.distinctBy { it.text + "|" + it.locale + "|" + it.voice }
    }

    /**
     * Pulls every not-yet-cached phrase's mp3 from R2, asking the Edge Function for URLs
     * in BATCHES so a fresh device (where ~all phrases are missing) doesn't blow past the
     * Edge Function's ~150s wall-clock limit. Each batch: POST up to [BATCH_SIZE] phrases
     * → get URLs → download that batch's files → next batch. [onProgress] fires after
     * every file with (doneAcrossAllBatches, totalMissing, current).
     *
     * Best-effort: if a batch's manifest call fails (function cold-recycle, network blip),
     * that batch is skipped and counted as failed; the next batch still runs. Re-running
     * later picks up whatever's still missing (idempotent — cache.has skips done files).
     */
    suspend fun downloadAll(onProgress: suspend (Int, Int, String) -> Unit): Result<DownloadSummary> =
        withContext(Dispatchers.IO) {
            if (!network.isOnline()) {
                return@withContext Result.failure(IOException("Offline — download skipped."))
            }
            val phrases = buildManifestPhrases()
            val missing = phrases.filter { p ->
                !cache.has(cache.fileFor(p.locale, p.voice, p.text))
            }
            if (missing.isEmpty()) {
                return@withContext Result.success(
                    DownloadSummary(totalWanted = phrases.size, fetched = 0, alreadyCached = phrases.size)
                )
            }
            var fetched = 0
            var failed = 0
            var done = 0
            val total = missing.size
            for (batch in missing.chunked(BATCH_SIZE)) {
                val manifest = try {
                    fetchManifest(batch)
                } catch (_: Throwable) {
                    // Whole batch unreachable — count its phrases as failed, keep going.
                    failed += batch.size
                    done += batch.size
                    onProgress(done, total, "")
                    continue
                }
                for (m in manifest) {
                    done++
                    onProgress(done, total, m.text)
                    val file = cache.fileFor(m.locale, m.voice, m.text)
                    if (cache.has(file)) { fetched++; continue }
                    if (m.url.isEmpty()) { failed++; continue }
                    try {
                        val conn = URL(m.url).openConnection() as HttpURLConnection
                        conn.requestMethod = "GET"
                        conn.connectTimeout = 15000
                        conn.readTimeout = 30000
                        if (conn.responseCode !in 200..299) { failed++; continue }
                        file.parentFile?.mkdirs()
                        conn.inputStream.use { input ->
                            file.outputStream().use { out -> input.copyTo(out) }
                        }
                        fetched++
                    } catch (_: Throwable) {
                        failed++
                    }
                }
            }
            onProgress(total, total, "")
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

    companion object {
        // Phrases per Edge Function call. At ~1s/phrase synth (cold), 80 keeps each
        // request comfortably under the ~150s function ceiling even when nothing is
        // cached server-side yet. Cached batches return near-instantly.
        private const val BATCH_SIZE = 80
    }
}
