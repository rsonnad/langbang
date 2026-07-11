package com.sponic.langbang.shared.cloud

import kotlinx.serialization.Serializable

@Serializable
data class HealthResponse(
    val ok: Boolean = false,
    val service: String = ""
)

@Serializable
data class CloudAuthUser(
    val id: String,
    val email: String,
    val emailVerified: Boolean = false,
    val displayName: String = "",
    val pictureUrl: String = ""
)

@Serializable
data class CloudAuthSession(
    val token: String,
    val expiresAt: String
)

@Serializable
data class CloudAuthResponse(
    val user: CloudAuthUser,
    val session: CloudAuthSession
)

@Serializable
data class GoogleAuthRequest(
    val idToken: String,
    val nonce: String,
    val instanceId: String
)

@Serializable
data class GeminiGenerateRequest(
    val model: String,
    val prompt: String
)

/**
 * Minimal shape for the one vertical slice call result surfaced in SwiftUI.
 */
data class BootstrapSummary(
    val instanceId: String,
    val contentVersion: String?,
    val syncedAt: String
)
