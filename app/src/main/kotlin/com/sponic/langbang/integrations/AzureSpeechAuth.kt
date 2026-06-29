package com.sponic.langbang.integrations

import com.sponic.langbang.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL

/**
 * Fetches and caches a short-lived Azure Speech authorization token from the
 * LangBang Worker (POST /v1/azure/speech-token), so the app authenticates with a
 * ~10-minute token instead of shipping the raw AZURE_SPEECH_KEY in the APK
 * (plan BE-4 / AND-4).
 *
 * Release builds carry no embedded key and rely entirely on this token. Debug
 * builds keep BuildConfig.AZURE_SPEECH_KEY as a fallback (see AzureTtsClient and
 * AzurePronunciationClient) so on-device/tablet testing stays resilient if the
 * token endpoint is briefly unavailable.
 */
object AzureSpeechAuth {
    private val mutex = Mutex()
    private const val TTL_MS = 8 * 60 * 1000L // refresh ahead of the ~10-min expiry

    @Volatile private var token: String? = null
    @Volatile private var region: String = BuildConfig.AZURE_SPEECH_REGION
    @Volatile private var fetchedAtMs: Long = 0L

    /** Returns (token, region), or null if the token endpoint is unavailable. */
    suspend fun getToken(): Pair<String, String>? = withContext(Dispatchers.IO) {
        val cached = token
        if (cached != null && System.currentTimeMillis() - fetchedAtMs < TTL_MS) {
            return@withContext cached to region
        }
        mutex.withLock {
            val again = token
            if (again != null && System.currentTimeMillis() - fetchedAtMs < TTL_MS) {
                return@withLock again to region
            }
            try {
                val url = URL("${BuildConfig.LANGBANGML_API_BASE.trimEnd('/')}/v1/azure/speech-token")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    connectTimeout = 10000
                    readTimeout = 10000
                    setRequestProperty("Content-Type", "application/json")
                }
                conn.outputStream.use { it.write("{}".toByteArray(Charsets.UTF_8)) }
                if (conn.responseCode !in 200..299) return@withLock null
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val obj = Json.parseToJsonElement(body).jsonObject
                val t = obj["token"]?.jsonPrimitive?.content ?: return@withLock null
                val r = obj["region"]?.jsonPrimitive?.content ?: BuildConfig.AZURE_SPEECH_REGION
                token = t
                region = r
                fetchedAtMs = System.currentTimeMillis()
                t to r
            } catch (_: Throwable) {
                null
            }
        }
    }
}
