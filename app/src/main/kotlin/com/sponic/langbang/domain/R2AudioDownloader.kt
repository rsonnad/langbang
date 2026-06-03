package com.sponic.langbang.domain

import com.sponic.langbang.BuildConfig
import com.sponic.langbang.data.LbJson
import com.sponic.langbang.data.LessonRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Pulls pre-generated TTS mp3s from R2 (via the LangBangML Cloudflare Worker)
 * and stores them in AudioCache. The first launch on a new device can take
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

    private val json = LbJson.lenient
    private val endpoint =
        "${BuildConfig.LANGBANGML_API_BASE.trimEnd('/')}/v1/audio/manifest"

    /** All (text, voice, locale) triples the app currently needs cached — see [audioManifest]. */
    fun buildManifestPhrases(): List<Phrase> =
        audioManifest(repo).map { Phrase(it.text, it.voice, it.locale) }

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
            downloadMissing(phrases, onProgress)
        }

    suspend fun downloadPhrases(
        phrases: List<Phrase>,
        onProgress: suspend (Int, Int, String) -> Unit
    ): Result<DownloadSummary> =
        withContext(Dispatchers.IO) {
            if (!network.isOnline()) {
                return@withContext Result.failure(IOException("Offline — download skipped."))
            }
            downloadMissing(phrases.distinctBy { "${it.locale}|${it.voice}|${it.text}" }, onProgress)
        }

    private fun fetchManifest(phrases: List<Phrase>): List<ManifestEntry> {
        val conn = URL(endpoint).openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.connectTimeout = 15000
        // Matches the Edge Function's ~150s wall-clock ceiling. A fully-cold batch
        // synthesizes server-side at ~2s/phrase, so the read must outlast the slowest
        // batch we send (see BATCH_SIZE) or the POST times out, the batch is dropped as
        // "failed", and its phrases are never downloaded — leaving the progress bar
        // parked on a batch boundary while the spinner keeps going.
        conn.readTimeout = 150000
        conn.setRequestProperty("Content-Type", "application/json")
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

    private suspend fun downloadMissing(
        phrases: List<Phrase>,
        onProgress: suspend (Int, Int, String) -> Unit
    ): Result<DownloadSummary> {
        val missing = phrases.filter { p ->
            !cache.has(cache.fileFor(p.locale, p.voice, p.text))
        }
        if (missing.isEmpty()) {
            return Result.success(
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
        return Result.success(
            DownloadSummary(
                totalWanted = phrases.size,
                fetched = fetched,
                alreadyCached = phrases.size - missing.size,
                failed = failed
            )
        )
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
        // Phrases per Edge Function call. Cold synth measures ~2s/phrase server-side
        // (sequential), so a fully-uncached batch of 80 took ~160s — past both the
        // function's ~150s ceiling and the old 120s client read timeout, which dropped
        // the whole batch as "failed" and froze the progress bar on the boundary. 40
        // keeps a worst-case cold batch near ~85s, comfortably inside the 150s read
        // timeout. Cached batches still return near-instantly, so this only adds round
        // trips on first fill / newly-added content.
        private const val BATCH_SIZE = 40

        private val sharedJson = LbJson.lenient
        private val sharedEndpoint =
            "${BuildConfig.LANGBANGML_API_BASE.trimEnd('/')}/v1/audio/manifest"

        /**
         * Fetch a single phrase's mp3 from the shared R2 cache, asking the
         * langbang-pregen-audio Edge Function to synthesize-and-upload it server-side if
         * it isn't there yet, then download it into [outFile]. Returns true on success.
         *
         * This is the "synthesize once, reuse on every device" path: on-demand playback
         * (see [AzureTtsClient.synthesize]) calls this before spending Azure locally, so a
         * cache miss on one device no longer forces every other device to re-synthesize the
         * same audio — the function caches the result to R2 and all devices download it
         * thereafter. Mirrors the per-phrase POST + download that [downloadAll] does in bulk.
         *
         * Self-contained (no instance state) so the TTS client can call it without wiring,
         * and best-effort: any network / function / IO error returns false so the caller
         * falls back to on-device synthesis. Callers are expected to gate on connectivity
         * first (the 15s connect timeout would otherwise stall an offline cache miss).
         */
        suspend fun fetchOne(
            text: String,
            voice: String,
            locale: String,
            outFile: File
        ): Boolean = withContext(Dispatchers.IO) {
            if (text.isBlank()) return@withContext false
            try {
                val req = (URL(sharedEndpoint).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    connectTimeout = 15000
                    readTimeout = 30000
                    setRequestProperty("Content-Type", "application/json")
                }
                val payload = sharedJson.encodeToString(
                    ManifestRequest.serializer(),
                    ManifestRequest(listOf(Phrase(text, voice, locale)))
                )
                req.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
                if (req.responseCode !in 200..299) return@withContext false
                val raw = req.inputStream.bufferedReader().use { it.readText() }
                val entry = sharedJson
                    .decodeFromString(ManifestResponse.serializer(), raw)
                    .manifest.firstOrNull()
                if (entry == null || entry.url.isEmpty()) return@withContext false

                val audio = (URL(entry.url).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 15000
                    readTimeout = 30000
                }
                if (audio.responseCode !in 200..299) return@withContext false
                outFile.parentFile?.mkdirs()
                audio.inputStream.use { input ->
                    outFile.outputStream().use { out -> input.copyTo(out) }
                }
                true
            } catch (_: Throwable) {
                false
            }
        }
    }
}
