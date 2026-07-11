package com.sponic.langbang.shared.auth

import kotlin.random.Random
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Generates a cryptographically reasonable nonce for Google Sign-In.
 * The same logic (or equivalent) must be used on both platforms so the
 * server-side nonce check (when present) passes.
 */
@OptIn(ExperimentalEncodingApi::class)
fun generateNonce(): String {
    val bytes = ByteArray(32)
    Random.nextBytes(bytes)
    return Base64.UrlSafe.encode(bytes).trimEnd('=')
}

/**
 * The exact body shape the iOS app must eventually POST to /v1/auth/google.
 * The scaffold uses this to show what will be sent.
 */
data class GoogleSignInPayload(
    val idToken: String,
    val nonce: String,
    val instanceId: String
) {
    fun toJsonString(): String =
        """{"idToken":"$idToken","nonce":"$nonce","instanceId":"$instanceId"}"""
}

/**
 * Stub result for the vertical slice.
 */
sealed class GoogleSignInResult {
    data class Success(val sessionTokenPrefix: String, val email: String) : GoogleSignInResult()
    data class Failure(val message: String) : GoogleSignInResult()
}
