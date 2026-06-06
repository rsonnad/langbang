package com.sponic.langbang.cloud

import com.sponic.langbang.data.LessonRepository
import com.sponic.langbang.data.StarredPhrasesStore

class PhraseSyncService(
    private val backend: CloudBackendClient,
    private val authStore: AuthStore,
    private val lessonRepo: LessonRepository,
    private val starredPhrases: StarredPhrasesStore,
    private val cloudConfig: CloudConfigStore
) {
    suspend fun syncNow(): Result<CloudUserContentResponse> {
        val auth = authStore.state.value
        if (!auth.signedIn) {
            return Result.failure(IllegalStateException("Sign in first."))
        }
        val token = auth.sessionToken
        val instanceId = cloudConfig.state.value.selectedInstanceId
        authStore.markPhraseSyncing()
        return runCatching {
            val remote = backend.fetchUserContent(token, instanceId).getOrThrow()
            if (remote.hasRemotePhrases) {
                lessonRepo.replaceUserPhraseGroups(remote.groups)
                starredPhrases.replaceAll(remote.starredPhrases.toSet())
            } else if (lessonRepo.userPhraseGroups().isNotEmpty() || starredPhrases.starred.value.isNotEmpty()) {
                backend.syncUserPhrases(
                    sessionToken = token,
                    instanceId = instanceId,
                    groups = lessonRepo.userPhraseGroups(),
                    starredPhrases = starredPhrases.starred.value.toList(),
                    replace = true
                ).getOrThrow()
            }
            if (remote.hasRemoteWords) {
                lessonRepo.replaceUserWords(remote.words)
            }
            remote.copy(
                groups = lessonRepo.userPhraseGroups(),
                starredPhrases = starredPhrases.starred.value.toList(),
                words = lessonRepo.userWords()
            )
        }.onSuccess {
            authStore.savePhraseSyncSuccess()
        }.onFailure { t ->
            authStore.saveError(t.message ?: t.javaClass.simpleName)
        }
    }

    suspend fun pushLocalAfterEdit(): Result<CloudUserPhrasesResponse> {
        val auth = authStore.state.value
        if (!auth.signedIn) return Result.success(
            CloudUserPhrasesResponse(
                instanceId = cloudConfig.state.value.selectedInstanceId,
                groups = lessonRepo.userPhraseGroups(),
                starredPhrases = starredPhrases.starred.value.toList()
            )
        )
        val token = auth.sessionToken
        val instanceId = cloudConfig.state.value.selectedInstanceId
        val groups = lessonRepo.userPhraseGroups()
        val stars = starredPhrases.starred.value.filter { it.isNotBlank() }.distinct()
        authStore.markPhraseSyncing()
        return backend.syncUserPhrases(
            sessionToken = token,
            instanceId = instanceId,
            groups = groups,
            starredPhrases = stars,
            replace = true
        ).onSuccess {
            authStore.savePhraseSyncSuccess()
        }.onFailure { t ->
            authStore.saveError(t.message ?: t.javaClass.simpleName)
        }
    }
}
