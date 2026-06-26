package com.sponic.langbang.cloud

import com.sponic.langbang.data.model.PhraseGroup
import com.sponic.langbang.data.model.SentenceExample
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
data class CloudAgentTokenRequest(
    val instanceId: String,
    val label: String = "Claude/Codex",
    val rotate: Boolean = false
)

@Serializable
data class CloudAgentTokenResponse(
    val ok: Boolean = false,
    val token: String = "",
    val tokenPrefix: String = "",
    val label: String = "",
    val defaultInstanceId: String = "",
    val dailyLimit: Int = 100,
    val apiBase: String = "",
    val instructionsUrl: String = "",
    val createdAt: String = ""
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

@Serializable
data class CloudUserWords(
    val verbs: List<com.sponic.langbang.data.model.VerbEntry> = emptyList(),
    val nouns: List<com.sponic.langbang.data.model.NounEntry> = emptyList(),
    val adjectives: List<com.sponic.langbang.data.model.AdjectiveEntry> = emptyList(),
    val adverbs: List<com.sponic.langbang.data.model.AdverbEntry> = emptyList()
)

@Serializable
data class CloudUserContentResponse(
    val instanceId: String,
    val groups: List<PhraseGroup> = emptyList(),
    val starredPhrases: List<String> = emptyList(),
    val words: CloudUserWords = CloudUserWords(),
    val hasRemoteContent: Boolean = false,
    val hasRemotePhrases: Boolean = false,
    val hasRemoteWords: Boolean = false,
    val syncedAt: String = ""
)

@Serializable
data class CloudAiPhraseQuota(
    val limit: Int = 50,
    val used: Int = 0,
    val remaining: Int = 50
)

@Serializable
data class CloudAiPhraseGenerateRequest(
    val instanceId: String,
    val groupId: String,
    val groupTitle: String,
    val groupSubtitle: String = "",
    val prompt: String,
    val count: Int = 5
)

@Serializable
data class CloudAiPhraseGenerateResponse(
    val ok: Boolean = false,
    val instanceId: String = "",
    val group: PhraseGroup? = null,
    val phrases: List<SentenceExample> = emptyList(),
    val quota: CloudAiPhraseQuota = CloudAiPhraseQuota()
)

@Serializable
data class CloudAiPhraseQuotaRequest(
    val instanceId: String,
    val message: String = ""
)

@Serializable
data class CloudAiPhraseQuotaResponse(
    val ok: Boolean = false,
    val sent: Boolean = false,
    val quota: CloudAiPhraseQuota = CloudAiPhraseQuota()
)

@Serializable
data class CloudPushRegisterRequest(
    val token: String,
    val platform: String = "android",
    val instanceId: String,
    val installationId: String,
    val appPackage: String,
    val appVersionCode: Int,
    val appVersionName: String,
    val buildNumber: Int,
    val locale: String
)

@Serializable
data class CloudPushRegisterResponse(
    val ok: Boolean = false,
    val enabled: Boolean = false,
    val instanceId: String = "",
    val tokenHash: String = ""
)

@Serializable
data class CloudPushUnregisterRequest(
    val token: String,
    val installationId: String
)

data class CloudSyncState(
    val bootstrap: CloudBootstrap? = null,
    val lastSyncMs: Long = 0L,
    val selectedInstanceId: String,
    val instances: List<CloudInstanceSummary> = emptyList(),
    val syncing: Boolean = false,
    val error: String? = null
)
