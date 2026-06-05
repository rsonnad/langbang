package com.sponic.langbang.analytics

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import com.sponic.langbang.BuildConfig
import com.sponic.langbang.cloud.CloudConfigStore
import com.sponic.langbang.data.LbJson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.util.Locale
import java.util.UUID

@Serializable
data class ProductAnalyticsProfile(
    val profileId: String? = null,
    val provider: String? = null,
    val providerSubject: String? = null,
    val email: String? = null,
    val displayName: String? = null,
    val locale: String? = null,
    val signupState: String? = null,
    val properties: Map<String, String> = emptyMap()
)

@Serializable
data class ProductAnalyticsApp(
    val platform: String = "android",
    val appPackage: String,
    val appVersionCode: Int,
    val appVersionName: String,
    val buildNumber: Int,
    val flavor: String,
    val instanceId: String,
    val deviceModel: String,
    val osVersion: String,
    val locale: String,
    val properties: Map<String, String> = emptyMap()
)

@Serializable
data class ProductAnalyticsEvent(
    val id: String,
    val name: String,
    val feature: String? = null,
    val action: String? = null,
    val screen: String? = null,
    val occurredAt: String,
    val durationMs: Long? = null,
    val instanceId: String? = null,
    val properties: Map<String, String> = emptyMap()
)

@Serializable
data class ProductAnalyticsEnvelope(
    val installationId: String,
    val sessionId: String,
    val profile: ProductAnalyticsProfile? = null,
    val app: ProductAnalyticsApp,
    val events: List<ProductAnalyticsEvent>
)

class ProductAnalytics(
    context: Context,
    private val cloudConfig: CloudConfigStore,
    private val client: ProductAnalyticsClient,
    private val scope: CoroutineScope
) {
    private val appContext = context.applicationContext
    private val prefs: SharedPreferences =
        appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val json = LbJson.pretty
    private val eventListSerializer = ListSerializer(ProductAnalyticsEvent.serializer())
    private val lock = Any()
    private var flushJob: Job? = null
    private var profile: ProductAnalyticsProfile? = loadProfile()
    private val installationId: String = prefs.getString(KEY_INSTALLATION_ID, null)
        ?: UUID.randomUUID().toString().also {
            prefs.edit().putString(KEY_INSTALLATION_ID, it).apply()
        }
    private val sessionId: String = UUID.randomUUID().toString()
    private val pending = ArrayDeque(loadPending())

    fun trackSessionStart() {
        track(
            name = "session_start",
            feature = "app",
            action = "start",
            properties = mapOf("buildNumber" to BuildConfig.BUILD_NUMBER.toString())
        )
    }

    fun trackSessionEnd() {
        track(name = "session_end", feature = "app", action = "end")
        flushSoon(delayMs = 0L)
    }

    fun setProfile(profile: ProductAnalyticsProfile?) {
        if (this.profile == profile) return
        this.profile = profile
        prefs.edit()
            .putString(
                KEY_PROFILE_JSON,
                profile?.let { json.encodeToString(ProductAnalyticsProfile.serializer(), it) }
            )
            .apply()
        track(
            name = if (profile == null) "profile_cleared" else "profile_identified",
            feature = "profile",
            action = if (profile == null) "clear" else "identify",
            properties = mapOf(
                "provider" to (profile?.provider ?: "anonymous"),
                "signupState" to (profile?.signupState ?: "anonymous")
            )
        )
    }

    fun track(
        name: String,
        feature: String? = null,
        action: String? = null,
        screen: String? = null,
        durationMs: Long? = null,
        properties: Map<String, String> = emptyMap()
    ) {
        val event = ProductAnalyticsEvent(
            id = UUID.randomUUID().toString(),
            name = normalizeName(name),
            feature = feature?.let(::normalizeName),
            action = action?.let(::normalizeName),
            screen = screen?.let(::normalizeName),
            occurredAt = Instant.now().toString(),
            durationMs = durationMs?.coerceAtLeast(0L),
            instanceId = cloudConfig.state.value.selectedInstanceId,
            properties = properties.cleanAnalyticsProperties()
        )
        synchronized(lock) {
            pending.addLast(event)
            while (pending.size > MAX_PENDING_EVENTS) pending.removeFirst()
            persistPendingLocked()
        }
        flushSoon()
    }

    fun flushSoon(delayMs: Long = FLUSH_DELAY_MS) {
        if (flushJob?.isActive == true) return
        flushJob = scope.launch {
            if (delayMs > 0L) delay(delayMs)
            flush()
        }
    }

    private suspend fun flush() {
        val batch = synchronized(lock) { pending.take(BATCH_SIZE) }
        if (batch.isEmpty()) return
        val envelope = ProductAnalyticsEnvelope(
            installationId = installationId,
            sessionId = sessionId,
            profile = profile,
            app = appPayload(),
            events = batch
        )
        val sent = client.send(envelope)
        if (sent.isSuccess) {
            val sentIds = batch.map { it.id }.toSet()
            synchronized(lock) {
                pending.removeAll { it.id in sentIds }
                persistPendingLocked()
            }
            if (pending.isNotEmpty()) flushSoon(delayMs = 250L)
        }
    }

    private fun appPayload(): ProductAnalyticsApp {
        val locale = Locale.getDefault().toLanguageTag()
        return ProductAnalyticsApp(
            appPackage = appContext.packageName,
            appVersionCode = BuildConfig.VERSION_CODE,
            appVersionName = BuildConfig.VERSION_NAME,
            buildNumber = BuildConfig.BUILD_NUMBER,
            flavor = BuildConfig.LANGBANGML_INSTANCE_ID,
            instanceId = cloudConfig.state.value.selectedInstanceId,
            deviceModel = listOf(Build.MANUFACTURER, Build.MODEL)
                .joinToString(" ")
                .trim()
                .ifBlank { "Android" },
            osVersion = "Android ${Build.VERSION.RELEASE} (${Build.VERSION.SDK_INT})",
            locale = locale,
            properties = mapOf("buildTimestamp" to BuildConfig.BUILD_TIMESTAMP)
        )
    }

    private fun loadProfile(): ProductAnalyticsProfile? {
        val raw = prefs.getString(KEY_PROFILE_JSON, null) ?: return null
        return runCatching { json.decodeFromString(ProductAnalyticsProfile.serializer(), raw) }
            .getOrNull()
    }

    private fun loadPending(): List<ProductAnalyticsEvent> {
        val raw = prefs.getString(KEY_PENDING_JSON, null) ?: return emptyList()
        return runCatching { json.decodeFromString(eventListSerializer, raw) }
            .getOrDefault(emptyList())
    }

    private fun persistPendingLocked() {
        prefs.edit()
            .putString(KEY_PENDING_JSON, json.encodeToString(eventListSerializer, pending.toList()))
            .apply()
    }

    private fun normalizeName(value: String): String =
        value.trim()
            .lowercase(Locale.US)
            .replace(Regex("[^a-z0-9._-]+"), "_")
            .trim('_')
            .ifBlank { "unknown" }
            .take(80)

    private fun Map<String, String>.cleanAnalyticsProperties(): Map<String, String> =
        entries
            .asSequence()
            .filter { it.key.isNotBlank() }
            .take(MAX_PROPERTY_KEYS)
            .associate { (key, value) ->
                normalizeName(key).take(60) to value.take(MAX_PROPERTY_VALUE_CHARS)
            }

    companion object {
        private const val PREFS = "product_analytics"
        private const val KEY_INSTALLATION_ID = "installation-id"
        private const val KEY_PROFILE_JSON = "profile-json"
        private const val KEY_PENDING_JSON = "pending-events-json"
        private const val MAX_PENDING_EVENTS = 500
        private const val BATCH_SIZE = 50
        private const val FLUSH_DELAY_MS = 1500L
        private const val MAX_PROPERTY_KEYS = 30
        private const val MAX_PROPERTY_VALUE_CHARS = 240
    }
}

class ProductAnalyticsClient(private val apiBase: String) {
    private val json = LbJson.pretty

    suspend fun send(envelope: ProductAnalyticsEnvelope): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val endpoint = "${apiBase.trimEnd('/')}/v1/analytics/events"
                val body = json.encodeToString(ProductAnalyticsEnvelope.serializer(), envelope)
                val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    connectTimeout = 8000
                    readTimeout = 12000
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("Content-Type", "application/json")
                }
                try {
                    conn.outputStream.use {
                        it.write(body.toByteArray(Charsets.UTF_8))
                    }
                    val code = conn.responseCode
                    if (code !in 200..299) {
                        val err = conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                        error("Analytics HTTP $code: ${err.take(180)}")
                    }
                } finally {
                    conn.disconnect()
                }
            }
        }
}
