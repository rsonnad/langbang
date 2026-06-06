package com.sponic.langbang.cloud

import com.sponic.langbang.data.LbJson
import com.sponic.langbang.data.model.PhraseGroup
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

    suspend fun signInWithGoogle(
        idToken: String,
        nonce: String,
        instanceId: String
    ): Result<CloudAuthResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val request = CloudGoogleAuthRequest(
                idToken = idToken,
                nonce = nonce,
                instanceId = instanceId
            )
            val body = post(
                "/v1/auth/google",
                json.encodeToString(CloudGoogleAuthRequest.serializer(), request)
            )
            json.decodeFromString(CloudAuthResponse.serializer(), body)
        }
    }

    suspend fun startEmailSignIn(email: String): Result<CloudEmailStartResponse> =
        withContext(Dispatchers.IO) {
            runCatching {
                val request = CloudEmailStartRequest(email = email.trim())
                val body = post(
                    "/v1/auth/email/start",
                    json.encodeToString(CloudEmailStartRequest.serializer(), request)
                )
                json.decodeFromString(CloudEmailStartResponse.serializer(), body)
            }
        }

    suspend fun verifyEmailSignIn(
        email: String,
        code: String,
        instanceId: String
    ): Result<CloudAuthResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val request = CloudEmailVerifyRequest(
                email = email.trim(),
                code = code.trim(),
                instanceId = instanceId
            )
            val body = post(
                "/v1/auth/email/verify",
                json.encodeToString(CloudEmailVerifyRequest.serializer(), request)
            )
            json.decodeFromString(CloudAuthResponse.serializer(), body)
        }
    }

    suspend fun createAgentToken(
        sessionToken: String,
        instanceId: String,
        rotate: Boolean = false
    ): Result<CloudAgentTokenResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val request = CloudAgentTokenRequest(
                instanceId = instanceId,
                rotate = rotate
            )
            val body = post(
                "/v1/me/agent-token",
                json.encodeToString(CloudAgentTokenRequest.serializer(), request),
                bearerToken = sessionToken
            )
            json.decodeFromString(CloudAgentTokenResponse.serializer(), body)
        }
    }

    suspend fun fetchUserContent(
        sessionToken: String,
        instanceId: String
    ): Result<CloudUserContentResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val encoded = URLEncoder.encode(instanceId, "UTF-8")
            val body = get("/v1/me/content?instanceId=$encoded", bearerToken = sessionToken)
            json.decodeFromString(CloudUserContentResponse.serializer(), body)
        }
    }

    suspend fun fetchUserPhrases(
        sessionToken: String,
        instanceId: String
    ): Result<CloudUserPhrasesResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val encoded = URLEncoder.encode(instanceId, "UTF-8")
            val body = get("/v1/me/phrases?instanceId=$encoded", bearerToken = sessionToken)
            json.decodeFromString(CloudUserPhrasesResponse.serializer(), body)
        }
    }

    suspend fun syncUserPhrases(
        sessionToken: String,
        instanceId: String,
        groups: List<PhraseGroup>,
        starredPhrases: List<String>,
        replace: Boolean = true
    ): Result<CloudUserPhrasesResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val request = CloudUserPhrasesRequest(
                instanceId = instanceId,
                groups = groups,
                starredPhrases = starredPhrases,
                replace = replace
            )
            val body = put(
                "/v1/me/phrases",
                json.encodeToString(CloudUserPhrasesRequest.serializer(), request),
                bearerToken = sessionToken
            )
            json.decodeFromString(CloudUserPhrasesResponse.serializer(), body)
        }
    }

    suspend fun generateAiPhrases(
        sessionToken: String,
        instanceId: String,
        groupId: String,
        groupTitle: String,
        groupSubtitle: String,
        prompt: String,
        count: Int
    ): Result<CloudAiPhraseGenerateResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val request = CloudAiPhraseGenerateRequest(
                instanceId = instanceId,
                groupId = groupId,
                groupTitle = groupTitle,
                groupSubtitle = groupSubtitle,
                prompt = prompt,
                count = count
            )
            val body = post(
                "/v1/me/phrases/ai-generate",
                json.encodeToString(CloudAiPhraseGenerateRequest.serializer(), request),
                bearerToken = sessionToken
            )
            json.decodeFromString(CloudAiPhraseGenerateResponse.serializer(), body)
        }
    }

    suspend fun requestAiPhraseQuota(
        sessionToken: String,
        instanceId: String,
        message: String
    ): Result<CloudAiPhraseQuotaResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val request = CloudAiPhraseQuotaRequest(
                instanceId = instanceId,
                message = message
            )
            val body = post(
                "/v1/me/phrases/ai-quota-request",
                json.encodeToString(CloudAiPhraseQuotaRequest.serializer(), request),
                bearerToken = sessionToken
            )
            json.decodeFromString(CloudAiPhraseQuotaResponse.serializer(), body)
        }
    }

    suspend fun signOut(sessionToken: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            post("/v1/auth/sign-out", "{}", bearerToken = sessionToken)
            Unit
        }
    }

    private fun get(path: String, bearerToken: String? = null): String {
        val endpoint = "${apiBase.trimEnd('/')}$path"
        val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10000
            readTimeout = 20000
            setRequestProperty("Accept", "application/json")
            bearerToken?.takeIf { it.isNotBlank() }?.let {
                setRequestProperty("Authorization", "Bearer $it")
            }
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

    private fun post(path: String, body: String, bearerToken: String? = null): String =
        write("POST", path, body, bearerToken)

    private fun put(path: String, body: String, bearerToken: String? = null): String =
        write("PUT", path, body, bearerToken)

    private fun write(method: String, path: String, body: String, bearerToken: String? = null): String {
        val endpoint = "${apiBase.trimEnd('/')}$path"
        val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            doOutput = true
            connectTimeout = 15000
            readTimeout = 45000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/json")
            bearerToken?.takeIf { it.isNotBlank() }?.let {
                setRequestProperty("Authorization", "Bearer $it")
            }
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
