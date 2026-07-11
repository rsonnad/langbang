package com.sponic.langbang.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Separates the learner's selected cloud pack from the content that is ready to show.
 *
 * A cloud bootstrap contains only text metadata; the selected pack is not usable until
 * its R2 audio manifest has also been pulled. Keeping that checkpoint here prevents a
 * switch from briefly exposing the bundled EN→PL lessons while another language loads.
 * Audio files themselves remain in [com.sponic.langbang.domain.AudioCache], keyed by
 * locale, voice, and text, so returning to a previously selected language reuses them.
 */
class LanguagePackStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("language-packs", Context.MODE_PRIVATE)

    private val initialInstanceId = prefs.getString(KEY_SELECTED_INSTANCE_ID, null)
    private val initialReadyVersion = initialInstanceId?.let(::readyVersion)
    private val _state = MutableStateFlow(
        LanguagePackState(
            selectedInstanceId = initialInstanceId,
            selectionMade = prefs.contains(KEY_SELECTED_INSTANCE_ID),
            status = if (initialReadyVersion != null) LanguagePackStatus.READY else LanguagePackStatus.PENDING,
            contentVersionId = initialReadyVersion
        )
    )
    val state: StateFlow<LanguagePackState> = _state.asStateFlow()

    fun select(instanceId: String) {
        val readyVersion = readyVersion(instanceId)
        prefs.edit().putString(KEY_SELECTED_INSTANCE_ID, instanceId).commit()
        _state.value = LanguagePackState(
            selectedInstanceId = instanceId,
            selectionMade = true,
            // The cached bootstrap will compare this version before the UI can ungate;
            // keep a previously verified pack usable while the server revalidates it.
            status = if (readyVersion != null) LanguagePackStatus.READY else LanguagePackStatus.PENDING,
            contentVersionId = readyVersion
        )
    }

    /** Keeps existing learners on their cached/default pack when they upgrade to pack selection. */
    fun adoptLegacySelection(instanceId: String, contentVersionId: String?) {
        if (_state.value.selectionMade) return
        val version = contentVersionId.orEmpty()
        prefs.edit()
            .putString(KEY_SELECTED_INSTANCE_ID, instanceId)
            .putString(readyKey(instanceId), version)
            .commit()
        _state.value = LanguagePackState(
            selectedInstanceId = instanceId,
            selectionMade = true,
            status = LanguagePackStatus.READY,
            contentVersionId = contentVersionId
        )
    }

    /** Runs once so a bootstrap fetched by a new picker is never mistaken for an upgrade. */
    fun needsLegacyMigration(): Boolean =
        !prefs.getBoolean(KEY_LEGACY_MIGRATION_COMPLETE, false) && !_state.value.selectionMade

    fun completeLegacyMigration() {
        prefs.edit().putBoolean(KEY_LEGACY_MIGRATION_COMPLETE, true).commit()
    }

    fun beginDownload(instanceId: String, contentVersionId: String?) {
        if (_state.value.selectedInstanceId != instanceId) return
        _state.value = _state.value.copy(
            status = LanguagePackStatus.DOWNLOADING,
            contentVersionId = contentVersionId,
            downloaded = 0,
            total = 0,
            currentItem = "",
            error = null
        )
    }

    fun updateProgress(instanceId: String, downloaded: Int, total: Int, currentItem: String) {
        if (_state.value.selectedInstanceId != instanceId) return
        _state.value = _state.value.copy(
            downloaded = downloaded,
            total = total,
            currentItem = currentItem
        )
    }

    fun markReady(instanceId: String, contentVersionId: String?) {
        if (_state.value.selectedInstanceId != instanceId) return
        val version = contentVersionId.orEmpty()
        prefs.edit().putString(readyKey(instanceId), version).commit()
        _state.value = _state.value.copy(
            status = LanguagePackStatus.READY,
            contentVersionId = contentVersionId,
            downloaded = 0,
            total = 0,
            currentItem = "",
            error = null
        )
    }

    fun markFailed(instanceId: String, message: String) {
        if (_state.value.selectedInstanceId != instanceId) return
        _state.value = _state.value.copy(
            status = LanguagePackStatus.ERROR,
            error = message
        )
    }

    fun isReady(instanceId: String, contentVersionId: String?): Boolean =
        readyVersion(instanceId) == contentVersionId.orEmpty()

    private fun readyVersion(instanceId: String): String? =
        prefs.getString(readyKey(instanceId), null)

    private fun readyKey(instanceId: String) = "ready-content-version:$instanceId"

    private companion object {
        const val KEY_SELECTED_INSTANCE_ID = "selected-instance-id"
        const val KEY_LEGACY_MIGRATION_COMPLETE = "legacy-migration-complete"
    }
}

data class LanguagePackState(
    val selectedInstanceId: String? = null,
    val selectionMade: Boolean = false,
    val status: LanguagePackStatus = LanguagePackStatus.PENDING,
    val contentVersionId: String? = null,
    val downloaded: Int = 0,
    val total: Int = 0,
    val currentItem: String = "",
    val error: String? = null
)

enum class LanguagePackStatus {
    PENDING,
    DOWNLOADING,
    READY,
    ERROR
}
