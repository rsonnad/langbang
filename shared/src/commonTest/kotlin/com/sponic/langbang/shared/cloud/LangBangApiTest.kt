package com.sponic.langbang.shared.cloud

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LangBangApiTest {
    @Test
    fun generateWithGemini_attachesSessionBearer() = runTest {
        var authHeader: String? = null
        val engine = MockEngine { request ->
            authHeader = request.headers[HttpHeaders.Authorization]
            respond(
                content = """{"candidates":[]}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }
        val api = LangBangApi("https://example.test", testClient(engine))

        val result = api.generateWithGemini(
            prompt = "Generate one beginner Polish sentence.",
            sessionToken = "lb_session_token"
        )

        assertTrue(result.isSuccess)
        assertEquals("Bearer lb_session_token", authHeader)
    }

    @Test
    fun generateWithGemini_omitsBlankSessionBearer() = runTest {
        var authHeader: String? = "not observed"
        val engine = MockEngine { request ->
            authHeader = request.headers[HttpHeaders.Authorization]
            respond(
                content = """{"candidates":[]}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }
        val api = LangBangApi("https://example.test", testClient(engine))

        val result = api.generateWithGemini(
            prompt = "Generate one beginner Polish sentence.",
            sessionToken = " "
        )

        assertTrue(result.isSuccess)
        assertEquals(null, authHeader)
    }

    private fun testClient(engine: MockEngine): HttpClient =
        HttpClient(engine) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                })
            }
            expectSuccess = false
        }
}
