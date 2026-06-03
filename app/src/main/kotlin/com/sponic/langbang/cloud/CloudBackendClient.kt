package com.sponic.langbang.cloud

import com.sponic.langbang.data.LbJson
import com.sponic.langbang.data.model.SentenceExample
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL

class CloudBackendClient(
    private val apiBase: String
) {
    private val json = LbJson.lenient

    suspend fun fetchInstances(): Result<List<CloudInstanceSummary>> = withContext(Dispatchers.IO) {
        runCatching {
            val body = get("/v1/instances")
            json.decodeFromString(CloudInstancesResponse.serializer(), body).instances
        }
    }

    suspend fun fetchBootstrap(instanceId: String): Result<CloudBootstrap> = withContext(Dispatchers.IO) {
        runCatching {
            val encoded = URLEncoder.encode(instanceId, "UTF-8")
            json.decodeFromString(CloudBootstrap.serializer(), get("/v1/instances/$encoded/bootstrap"))
        }
    }

    suspend fun completePhrase(
        sourceText: String,
        targetText: String,
        literalText: String,
        sourceLanguage: String,
        targetLanguage: String
    ): Result<SentenceExample> = withContext(Dispatchers.IO) {
        runCatching {
            if (sourceText.isBlank() && targetText.isBlank() && literalText.isBlank()) {
                error("Enter at least one phrase field.")
            }
            val request = CloudPhraseCompletionRequest(
                sourceText = sourceText,
                targetText = targetText,
                literalText = literalText,
                sourceLanguage = sourceLanguage,
                targetLanguage = targetLanguage
            )
            val body = post(
                "/v1/phrases/complete",
                json.encodeToString(CloudPhraseCompletionRequest.serializer(), request)
            )
            val response = json.decodeFromString(CloudPhraseCompletionResponse.serializer(), body)
            if (!response.consistent) {
                error(response.issue.ifBlank { "The filled phrase fields are inconsistent." })
            }
            val source = response.source.trim()
            val target = response.target.trim()
            if (source.isEmpty()) error("The server returned an empty source cue.")
            if (target.isEmpty()) error("The server returned an empty target answer.")
            SentenceExample(
                pl = target,
                en = source,
                literal = response.literal?.trim()?.takeIf { it.isNotEmpty() },
                words = response.words.takeIf { it.isNotEmpty() }
            )
        }
    }

    private fun get(path: String): String {
        val endpoint = "${apiBase.trimEnd('/')}$path"
        val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10000
            readTimeout = 20000
            setRequestProperty("Accept", "application/json")
        }
        try {
            val code = conn.responseCode
            if (code in 200..299) {
                return conn.inputStream.bufferedReader().use { it.readText() }
            }
            val err = conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            error("Cloudflare API HTTP $code: ${err.take(180)}")
        } finally {
            conn.disconnect()
        }
    }

    private fun post(path: String, body: String): String {
        val endpoint = "${apiBase.trimEnd('/')}$path"
        val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 15000
            readTimeout = 45000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/json")
        }
        try {
            conn.outputStream.use {
                it.write(body.toByteArray(Charsets.UTF_8))
            }
            val code = conn.responseCode
            if (code in 200..299) {
                return conn.inputStream.bufferedReader().use { it.readText() }
            }
            val err = conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            error("Cloudflare API HTTP $code: ${err.take(180)}")
        } finally {
            conn.disconnect()
        }
    }
}
