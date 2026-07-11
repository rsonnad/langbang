package com.sponic.langbang.cloud

import android.content.Context
import com.sponic.langbang.data.LbJson
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class CloudConfigStore(
    context: Context,
    defaultInstanceId: String
) {
    private val prefs = context.applicationContext.getSharedPreferences("cloud-config", Context.MODE_PRIVATE)
    private val json = LbJson.pretty
    private val instanceListSerializer = ListSerializer(CloudInstanceSummary.serializer())
    private val legacyBootstrap = loadLegacyBootstrap()
    private val initialSelectedInstanceId = prefs.getString(KEY_SELECTED_INSTANCE_ID, null)
        ?: legacyBootstrap?.instance?.id
        ?: defaultInstanceId
    private val cachedBootstrap = loadBootstrap(initialSelectedInstanceId)
        ?: legacyBootstrap?.takeIf { it.instance.id == initialSelectedInstanceId }

    private val _state = MutableStateFlow(
        CloudSyncState(
            bootstrap = cachedBootstrap,
            lastSyncMs = prefs.getLong(KEY_LAST_SYNC_MS, 0L),
            selectedInstanceId = initialSelectedInstanceId,
            instances = loadInstances(),
            error = prefs.getString(KEY_LAST_ERROR, null)
        )
    )
    val state: StateFlow<CloudSyncState> = _state.asStateFlow()

    fun label(key: String, fallback: String): String =
        _state.value.bootstrap?.labels?.get(key)?.takeIf { it.isNotBlank() } ?: fallback

    /**
     * True only for the old single-bootstrap layout. New picker installs persist a
     * per-instance key as soon as they fetch, so they are never auto-migrated as users.
     */
    fun hasLegacyBootstrapForLanguagePackMigration(): Boolean =
        legacyBootstrap != null && prefs.getString(bootstrapKey(legacyBootstrap.instance.id), null) == null

    fun markSyncing() {
        _state.value = _state.value.copy(syncing = true, error = null)
    }

    /** Keeps first-launch labels available without persisting a default as the learner's choice. */
    fun previewBootstrap(bootstrap: CloudBootstrap) {
        _state.value = _state.value.copy(
            bootstrap = bootstrap,
            syncing = false,
            error = null
        )
    }

    fun saveInstances(instances: List<CloudInstanceSummary>) {
        prefs.edit()
            .putString(KEY_INSTANCES_JSON, json.encodeToString(instanceListSerializer, instances))
            .apply()
        _state.value = _state.value.copy(instances = instances)
    }

    fun setSelectedInstance(instanceId: String) {
        prefs.edit()
            .putString(KEY_SELECTED_INSTANCE_ID, instanceId)
            .apply()
        val current = _state.value
        _state.value = current.copy(
            selectedInstanceId = instanceId,
            bootstrap = loadBootstrap(instanceId)
                ?: current.bootstrap?.takeIf { it.instance.id == instanceId },
            error = null
        )
    }

    fun saveBootstrap(bootstrap: CloudBootstrap) {
        val now = System.currentTimeMillis()
        prefs.edit()
            .putString(KEY_BOOTSTRAP_JSON, json.encodeToString(CloudBootstrap.serializer(), bootstrap))
            .putString(bootstrapKey(bootstrap.instance.id), json.encodeToString(CloudBootstrap.serializer(), bootstrap))
            .putLong(KEY_LAST_SYNC_MS, now)
            .putString(KEY_SELECTED_INSTANCE_ID, bootstrap.instance.id)
            .remove(KEY_LAST_ERROR)
            .apply()
        _state.value = _state.value.copy(
            bootstrap = bootstrap,
            lastSyncMs = now,
            selectedInstanceId = bootstrap.instance.id,
            syncing = false,
            error = null
        )
    }

    fun saveError(message: String) {
        prefs.edit().putString(KEY_LAST_ERROR, message).apply()
        _state.value = _state.value.copy(syncing = false, error = message)
    }

    private fun loadBootstrap(instanceId: String): CloudBootstrap? {
        val raw = prefs.getString(bootstrapKey(instanceId), null) ?: return null
        return decodeBootstrap(raw)
    }

    private fun loadLegacyBootstrap(): CloudBootstrap? {
        val raw = prefs.getString(KEY_BOOTSTRAP_JSON, null) ?: return null
        return decodeBootstrap(raw)
    }

    private fun decodeBootstrap(raw: String): CloudBootstrap? =
        runCatching { json.decodeFromString(CloudBootstrap.serializer(), raw) }.getOrNull()

    private fun bootstrapKey(instanceId: String) = "bootstrap-json:$instanceId"

    private fun loadInstances(): List<CloudInstanceSummary> {
        val raw = prefs.getString(KEY_INSTANCES_JSON, null) ?: return emptyList()
        return runCatching { json.decodeFromString(instanceListSerializer, raw) }.getOrDefault(emptyList())
    }

    companion object {
        private const val KEY_BOOTSTRAP_JSON = "bootstrap-json"
        private const val KEY_INSTANCES_JSON = "instances-json"
        private const val KEY_LAST_SYNC_MS = "last-sync-ms"
        private const val KEY_LAST_ERROR = "last-error"
        private const val KEY_SELECTED_INSTANCE_ID = "selected-instance-id"
    }
}
