package com.sponic.langbang.cloud

import com.sponic.langbang.data.LessonRepository
import com.sponic.langbang.data.StarredPhrasesStore
import com.sponic.langbang.data.model.PhraseGroup
import com.sponic.langbang.data.model.SentenceExample

class PhraseSyncService(
    private val backend: CloudBackendClient,
    private val authStore: AuthStore,
    private val lessonRepo: LessonRepository,
    private val starredPhrases: StarredPhrasesStore,
    private val cloudConfig: CloudConfigStore
) {
    suspend fun syncNow(): Result<CloudUserPhrasesResponse> {
        val auth = authStore.state.value
        if (!auth.signedIn) {
            return Result.failure(IllegalStateException("Sign in first."))
        }
        val token = auth.sessionToken
        val instanceId = cloudConfig.state.value.selectedInstanceId
        authStore.markPhraseSyncing()
        return runCatching {
            val remote = backend.fetchUserPhrases(token, instanceId).getOrThrow()
            val mergedGroups = mergeGroups(remote.groups, lessonRepo.userPhraseGroups())
            val mergedStars = (remote.starredPhrases + starredPhrases.starred.value)
                .filter { it.isNotBlank() }
                .distinct()

            lessonRepo.replaceUserPhraseGroups(mergedGroups)
            starredPhrases.replaceAll(mergedStars.toSet())

            backend.syncUserPhrases(
                sessionToken = token,
                instanceId = instanceId,
                groups = mergedGroups,
                starredPhrases = mergedStars,
                replace = true
            ).getOrThrow()
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

    private fun mergeGroups(remote: List<PhraseGroup>, local: List<PhraseGroup>): List<PhraseGroup> {
        val byId = LinkedHashMap<String, PhraseGroup>()
        remote.forEach { group -> byId[group.id.lowercase()] = group }
        local.forEach { group ->
            val key = group.id.lowercase()
            val existing = byId[key]
            byId[key] = if (existing == null) {
                group
            } else {
                group.copy(sentences = mergeSentences(existing.sentences, group.sentences))
            }
        }
        return byId.values.toList()
    }

    private fun mergeSentences(
        remote: List<SentenceExample>,
        local: List<SentenceExample>
    ): List<SentenceExample> {
        val byKey = LinkedHashMap<String, SentenceExample>()
        (remote + local).forEach { sentence ->
            val key = "${sentence.pl.trim().lowercase()}|${sentence.en.trim().lowercase()}"
            if (key.isNotBlank()) byKey[key] = sentence
        }
        return byKey.values.toList()
    }
}
