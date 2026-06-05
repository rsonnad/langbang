package com.sponic.langbang.cloud

import com.sponic.langbang.data.model.PhraseGroup
import com.sponic.langbang.data.model.TokenPair
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class CloudBootstrap(
    val instance: CloudInstance,
    val languagePair: CloudLanguagePair,
    val content: CloudContent,
    val labels: Map<String, String> = emptyMap(),
    val audio: CloudAudioConfig,
    val syncedAt: String
)

@Serializable
data class CloudInstancesResponse(
    val instances: List<CloudInstanceSummary> = emptyList()
)

@Serializable
data class CloudInstanceSummary(
    val id: String,
    val displayName: String,
    val uiLocale: String,
    val contentVersionId: String,
    val languagePair: CloudLanguagePairSummary
)

@Serializable
data class CloudLanguagePairSummary(
    val id: String,
    val sourceLanguage: String,
    val targetLanguage: String,
    val sourceLocale: String,
    val targetLocale: String
)

@Serializable
data class CloudInstance(
    val id: String,
    val displayName: String,
    val uiLocale: String,
    val settings: JsonObject = JsonObject(emptyMap()),
    val updatedAt: String = ""
)

@Serializable
data class CloudLanguagePair(
    val id: String,
    val sourceLanguage: String,
    val targetLanguage: String,
    val sourceLocale: String,
    val targetLocale: String,
    val sourceVoice: String,
    val targetVoice: String,
    val targetSlowVoices: List<String> = emptyList(),
    val description: String = ""
)

@Serializable
data class CloudContent(
    val versionId: String? = null,
    val lessons: List<CloudLesson> = emptyList()
)

@Serializable
data class CloudLesson(
    val id: String,
    val type: String,
    val sortOrder: Int,
    val title: String,
    val summary: String = "",
    val payload: JsonObject = JsonObject(emptyMap()),
    val updatedAt: String = ""
)

@Serializable
data class CloudAudioConfig(
    val manifestEndpoint: String,
    val publicR2Base: String,
    val audioPrefix: String
)

@Serializable
data class CloudPhraseCompletionRequest(
    val sourceText: String = "",
    val targetText: String = "",
    val literalText: String = "",
    val sourceLanguage: String,
    val targetLanguage: String
)

@Serializable
data class CloudPhraseCompletionResponse(
    val consistent: Boolean = true,
    val issue: String = "",
    val source: String = "",
    val target: String = "",
    val literal: String? = null,
    val words: List<TokenPair> = emptyList()
)

@Serializable
data class CloudGoogleAuthRequest(
    val idToken: String,
    val nonce: String,
    val instanceId: String
)

@Serializable
data class CloudEmailStartRequest(
    val email: String
)

@Serializable
data class CloudEmailStartResponse(
    val ok: Boolean = false,
    val email: String = "",
    val sent: Boolean = false,
    val expiresInMinutes: Int = 0
)

@Serializable
data class CloudEmailVerifyRequest(
    val email: String,
    val code: String,
    val instanceId: String
)

@Serializable
data class CloudAuthResponse(
    val user: CloudAuthUser,
    val session: CloudAuthSession
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
data class CloudUserPhrasesRequest(
    val instanceId: String,
    val groups: List<PhraseGroup>,
    val starredPhrases: List<String> = emptyList(),
    val replace: Boolean = true
)

@Serializable
data class CloudUserPhrasesResponse(
    val instanceId: String,
    val groups: List<PhraseGroup> = emptyList(),
    val starredPhrases: List<String> = emptyList(),
    val syncedAt: String = ""
)

data class CloudSyncState(
    val bootstrap: CloudBootstrap? = null,
    val lastSyncMs: Long = 0L,
    val selectedInstanceId: String,
    val instances: List<CloudInstanceSummary> = emptyList(),
    val syncing: Boolean = false,
    val error: String? = null
)
