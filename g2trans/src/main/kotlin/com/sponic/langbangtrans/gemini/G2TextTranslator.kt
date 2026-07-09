package com.sponic.langbangtrans.gemini

import com.sponic.langbangtrans.GeminiConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class G2TextTranslation(
    val english: String,
    val polish: String,
    val phonetics: String,
    val displayText: String,
)

class G2TextTranslator(
    private val config: GeminiConfig,
) {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun translate(english: String): G2TextTranslation = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("english", english)
            .toString()
            .toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder()
            .url(config.textEndpoint)
            .header("Authorization", "Bearer ${config.proxyToken}")
            .post(body)
            .build()
        httpClient.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException("G2 text translate failed HTTP ${response.code}: ${raw.take(180)}")
            }
            val json = JSONObject(raw)
            G2TextTranslation(
                english = json.getString("english"),
                polish = json.getString("polish"),
                phonetics = json.getString("phonetics"),
                displayText = json.optString("displayText").ifBlank {
                    "G2Trans text\nEN: ${json.getString("english")}\nPL: ${json.getString("polish")}\nPH: ${json.getString("phonetics")}"
                },
            )
        }
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
