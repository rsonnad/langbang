package com.sponic.langbang.integrations

import com.sponic.langbang.BuildConfig
import com.sponic.langbang.domain.NetworkMonitor
import com.sponic.langbang.domain.R2AudioDownloader
import com.sponic.langbang.domain.UsageTracker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Calls Azure Speech TTS REST API directly to produce an MP3 for the given text+voice.
 *
 * Uses the cognitiveservices/v1 endpoint with SSML. We avoid the native Speech SDK for
 * TTS because (a) it's a heavy AAR and (b) REST + MP3 fits our "cache to disk and replay
 * later" model perfectly with no SDK lifecycle.
 *
 * The Speech SDK is still pulled in for STT + Pronunciation Assessment, which needs the
 * streaming recognizer.
 */
class AzureTtsClient(
    private val usage: UsageTracker? = null,
    private val network: NetworkMonitor? = null
) {

    private val region = BuildConfig.AZURE_SPEECH_REGION
    private val key = BuildConfig.AZURE_SPEECH_KEY
    private val endpoint = "https://$region.tts.speech.microsoft.com/cognitiveservices/v1"

    suspend fun synthesize(text: String, voice: String, locale: String, outFile: File): Result<File> =
        withContext(Dispatchers.IO) {
            if (network?.isOnline() == false) {
                return@withContext Result.failure(IOException("Offline — TTS skipped."))
            }
            // Prefer the shared R2 cache: ask the Edge Function (which synthesizes once,
            // server-side, and uploads to R2) before spending Azure here. A cache miss on
            // one device no longer makes every other device re-synthesize the same phrase —
            // the function caches its single result to R2 and every device downloads it.
            // Falls through to the on-device synth below only when R2 / the function can't
            // serve it (e.g. a brand-new phrase the function fails to generate).
            if (R2AudioDownloader.fetchOne(text, voice, locale, outFile)) {
                return@withContext Result.success(outFile)
            }
            try {
                // Three slow tiers exist so cache keys don't collide when the rate is bumped:
                //   - slow50v3 → -50% time-stretch (legacy, kept so existing mp3s replay)
                //   - slow60v1 → -60% time-stretch ("stretch" style — slowed normal speech)
                //   - slowart1 → -10% rate + 250ms <break/> between words ("articulate"
                //                style — each word re-articulated, like a teacher repeating
                //                them one at a time. Avoids the muddied feel of time-stretch.)
                val slowArt = voice.endsWith(SLOW_SUFFIX_V3)
                val slow60 = !slowArt && voice.endsWith(SLOW_SUFFIX_V2)
                val slow50 = !slowArt && !slow60 && voice.endsWith(SLOW_SUFFIX)
                val realVoice = when {
                    slowArt -> voice.removeSuffix(SLOW_SUFFIX_V3)
                    slow60 -> voice.removeSuffix(SLOW_SUFFIX_V2)
                    slow50 -> voice.removeSuffix(SLOW_SUFFIX)
                    else -> voice
                }
                val body = when {
                    slowArt -> {
                        val tokens = text.split(Regex("\\s+")).filter { it.isNotEmpty() }
                        val joined = tokens.joinToString("<break time=\"250ms\"/>") { escapeXml(it) }
                        "<prosody rate=\"-10%\">$joined</prosody>"
                    }
                    slow60 -> "<prosody rate=\"-60%\">${escapeXml(text)}</prosody>"
                    slow50 -> "<prosody rate=\"-50%\">${escapeXml(text)}</prosody>"
                    else -> escapeXml(text)
                }
                // Azure Neural voices require the SSML namespace on <speak>, otherwise
                // <prosody> is silently dropped and the result plays at normal rate.
                val ssml = """
                    <speak version="1.0" xmlns="http://www.w3.org/2001/10/synthesis" xml:lang="$locale">
                      <voice name="$realVoice">$body</voice>
                    </speak>
                """.trimIndent()

                val conn = URL(endpoint).openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.connectTimeout = 15000
                conn.readTimeout = 30000
                // Auth: prefer a short-lived token (no key in release builds); fall
                // back to the embedded subscription key only when present (debug).
                val azureToken = AzureSpeechAuth.getToken()?.first
                when {
                    azureToken != null -> conn.setRequestProperty("Authorization", "Bearer $azureToken")
                    key.isNotBlank() -> conn.setRequestProperty("Ocp-Apim-Subscription-Key", key)
                    else -> return@withContext Result.failure(IOException("Azure TTS auth unavailable"))
                }
                conn.setRequestProperty("Content-Type", "application/ssml+xml")
                conn.setRequestProperty("X-Microsoft-OutputFormat", "audio-24khz-48kbitrate-mono-mp3")
                conn.setRequestProperty("User-Agent", "langbang/0.1")

                conn.outputStream.use { it.write(ssml.toByteArray(Charsets.UTF_8)) }

                if (conn.responseCode !in 200..299) {
                    val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                    return@withContext Result.failure(RuntimeException("TTS ${conn.responseCode}: $err"))
                }

                outFile.parentFile?.mkdirs()
                conn.inputStream.use { input ->
                    outFile.outputStream().use { out -> input.copyTo(out) }
                }
                usage?.recordTtsChars(text.length)
                Result.success(outFile)
            } catch (t: Throwable) {
                Result.failure(t)
            }
        }

    private fun escapeXml(s: String) =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            .replace("\"", "&quot;").replace("'", "&apos;")

    companion object Voices {
        const val EN_US_F = "en-US-JennyNeural"
        const val EN_US_M = "en-US-GuyNeural"
        const val PL_PL_F = "pl-PL-ZofiaNeural"
        const val PL_PL_M = "pl-PL-MarekNeural"
        const val LOCALE_EN = "en-US"
        const val LOCALE_PL = "pl-PL"
        // Rate suffix is baked into the cache key — bumping the version forces fresh
        // synthesis so a rate change isn't masked by stale mp3s in the AudioCache.
        //   - slow50v3 (-50% stretch)  kept for backward compat: existing cached mp3s replay.
        //   - slow60v1 (-60% stretch)  the "stretch" style (default).
        //   - slowart1 (rearticulated) the "articulate" style: -10% rate + 250ms breaks
        //                              between words. User picks between V2 and ART in
        //                              Settings → Slow audio style.
        const val SLOW_SUFFIX = "|slow50v3"
        const val SLOW_SUFFIX_V2 = "|slow60v1"
        const val SLOW_SUFFIX_V3 = "|slowart1"
        const val PL_PL_F_SLOW = "pl-PL-ZofiaNeural|slow50v3"
        const val PL_PL_F_SLOW_V2 = "pl-PL-ZofiaNeural|slow60v1"
        const val PL_PL_F_SLOW_ART = "pl-PL-ZofiaNeural|slowart1"
    }
}
