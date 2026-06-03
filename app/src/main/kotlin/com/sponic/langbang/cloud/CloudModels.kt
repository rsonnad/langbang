package com.sponic.langbang.cloud

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

data class CloudSyncState(
    val bootstrap: CloudBootstrap? = null,
    val lastSyncMs: Long = 0L,
    val selectedInstanceId: String,
    val instances: List<CloudInstanceSummary> = emptyList(),
    val syncing: Boolean = false,
    val error: String? = null
)
