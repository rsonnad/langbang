package com.sponic.langbang.integrations

import com.sponic.langbang.BuildConfig
import com.sponic.langbang.domain.NetworkMonitor
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
            try {
                // Two slow tiers exist so cache keys don't collide when the rate is bumped:
                //   - slow50v3  → -50%  (old phrases, kept stable so existing mp3s replay)
                //   - slow60v1  → -60%  (new phrases generated after 2026-05-20)
                val slow60 = voice.endsWith(SLOW_SUFFIX_V2)
                val slow50 = !slow60 && voice.endsWith(SLOW_SUFFIX)
                val slow = slow50 || slow60
                val realVoice = when {
                    slow60 -> voice.removeSuffix(SLOW_SUFFIX_V2)
                    slow50 -> voice.removeSuffix(SLOW_SUFFIX)
                    else -> voice
                }
                val ratePct = if (slow60) "-60%" else "-50%"
                val body = if (slow)
                    "<prosody rate=\"$ratePct\">${escapeXml(text)}</prosody>"
                else
                    escapeXml(text)
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
                conn.setRequestProperty("Ocp-Apim-Subscription-Key", key)
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
        //   - slow50v3 (-50%) kept for backward compat: existing cached mp3s replay.
        //   - slow60v1 (-60%) is the current default for all newly synthesized slow audio.
        const val SLOW_SUFFIX = "|slow50v3"
        const val SLOW_SUFFIX_V2 = "|slow60v1"
        const val PL_PL_F_SLOW = "pl-PL-ZofiaNeural|slow50v3"
        const val PL_PL_F_SLOW_V2 = "pl-PL-ZofiaNeural|slow60v1"
    }
}
