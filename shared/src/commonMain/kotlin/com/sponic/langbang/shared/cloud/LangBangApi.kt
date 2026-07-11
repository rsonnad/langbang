package com.sponic.langbang.shared.cloud

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * Thin Ktor-based client that talks to the real LangBangML backend.
 * This is the ONE vertical slice for Pass 1.
 */
class LangBangApi internal constructor(
    private val baseUrl: String,
    private val http: HttpClient
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    suspend fun health(): HealthResponse {
        val url = "${baseUrl.trimEnd('/')}/health"
        return http.get(url).body()
    }

    /**
     * Calls the exact contract the Android app uses.
     * For the iOS port the real idToken will come from GIDSignIn.
     * Until the iOS OAuth client exists in GCP and is added to the worker's
     * GOOGLE_WEB_CLIENT_ID allowlist, pass a placeholder.
     *
     * TODO(iOS-auth): Replace the stubbed idToken with a real one obtained via
     * GoogleSignIn on iOS (GIDSignIn). The client ID used on iOS must be registered
     * in GCP project langbang-498411 and appended to the worker env var.
     */
    suspend fun signInWithGoogleStub(
        idToken: String,   // TODO: real token from GIDSignIn
        nonce: String,
        instanceId: String = "langbangml-en-pl"
    ): Result<CloudAuthResponse> = runCatching {
        val url = "${baseUrl.trimEnd('/')}/v1/auth/google"
        val req = GoogleAuthRequest(
            idToken = idToken,
            nonce = nonce,
            instanceId = instanceId
        )
        http.post(url) {
            contentType(ContentType.Application.Json)
            setBody(req)
        }.body<CloudAuthResponse>()
    }

    /**
     * Lightweight read path used to prove end-to-end connectivity in the UI.
     * Returns a tiny summary derived from /v1/instances + first bootstrap (best effort).
     */
    suspend fun fetchBootstrapSummary(instanceId: String = "langbangml-en-pl"): Result<BootstrapSummary> = runCatching {
        // We do a cheap health + note that a real port would call /v1/instances/{id}/bootstrap
        // and parse CloudBootstrap. For Pass 1 vertical slice we keep it minimal.
        val h = health()
        BootstrapSummary(
            instanceId = instanceId,
            contentVersion = if (h.ok) "via-health" else null,
            syncedAt = "unknown"
        )
    }

    /**
     * Mirrors Android GeminiClient.postPrompt: send the optional session bearer so
     * the worker can meter paid LLM calls by signed-in user when available.
     */
    suspend fun generateWithGemini(
        prompt: String,
        sessionToken: String? = null,
        model: String = DEFAULT_GEMINI_MODEL
    ): Result<String> = runCatching {
        val url = "${baseUrl.trimEnd('/')}/v1/gemini/generate"
        val response = http.post(url) {
            accept(ContentType.Application.Json)
            contentType(ContentType.Application.Json)
            sessionToken
                ?.takeIf { it.isNotBlank() }
                ?.let { header(HttpHeaders.Authorization, "Bearer $it") }
            setBody(GeminiGenerateRequest(model = model, prompt = prompt))
        }
        val raw = response.bodyAsText()
        if (!response.status.isSuccess()) {
            error("LangBangML Gemini ${response.status.value}: $raw")
        }
        raw
    }
}

/**
 * Creates a platform-appropriate Ktor client (engine injected via actual).
 */
fun createLangBangApi(baseUrl: String): LangBangApi {
    val client = HttpClient(createPlatformHttpEngine()) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
        expectSuccess = false
    }
    return LangBangApi(baseUrl, client)
}

/**
 * Expect declaration so each platform provides the right engine.
 */
expect fun createPlatformHttpEngine(): io.ktor.client.engine.HttpClientEngine

private const val DEFAULT_GEMINI_MODEL = "gemini-3.5-flash"
