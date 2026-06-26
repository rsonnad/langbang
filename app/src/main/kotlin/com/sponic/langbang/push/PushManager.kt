package com.sponic.langbang.push

import android.content.Context
import com.google.firebase.messaging.FirebaseMessaging
import com.sponic.langbang.BuildConfig
import com.sponic.langbang.cloud.AuthStore
import com.sponic.langbang.cloud.CloudBackendClient
import com.sponic.langbang.cloud.CloudConfigStore
import com.sponic.langbang.cloud.CloudPushRegisterRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.util.Locale

class PushManager(
    context: Context,
    private val backend: CloudBackendClient,
    private val authStore: AuthStore,
    private val cloudConfig: CloudConfigStore,
    private val installationId: String,
    private val scope: CoroutineScope
) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("push-registration", Context.MODE_PRIVATE)
    private var token: String = prefs.getString(KEY_TOKEN, "") ?: ""
    private var started = false

    fun start() {
        if (started || !FirebasePushConfig.initialize(appContext)) return
        started = true
        FirebaseMessaging.getInstance().token
            .addOnSuccessListener(::onNewToken)
        scope.launch {
            authStore.state.collect {
                registerCurrentToken()
            }
        }
        scope.launch {
            cloudConfig.state.collect {
                registerCurrentToken()
            }
        }
    }

    fun onNewToken(newToken: String) {
        if (newToken.isBlank()) return
        token = newToken
        prefs.edit()
            .putString(KEY_TOKEN, newToken)
            .remove(KEY_REGISTERED_KEY)
            .apply()
        scope.launch { registerCurrentToken() }
    }

    private suspend fun registerCurrentToken() {
        val currentToken = token
        if (currentToken.isBlank()) return
        val auth = authStore.state.value
        val instanceId = cloudConfig.state.value.selectedInstanceId
        val registrationKey = listOf(
            currentToken,
            auth.user?.id.orEmpty(),
            instanceId,
            BuildConfig.APPLICATION_ID,
            BuildConfig.BUILD_NUMBER.toString()
        ).joinToString("|")
        if (prefs.getString(KEY_REGISTERED_KEY, "") == registrationKey) return
        val request = CloudPushRegisterRequest(
            token = currentToken,
            instanceId = instanceId,
            installationId = installationId,
            appPackage = BuildConfig.APPLICATION_ID,
            appVersionCode = BuildConfig.VERSION_CODE,
            appVersionName = BuildConfig.VERSION_NAME,
            buildNumber = BuildConfig.BUILD_NUMBER,
            locale = Locale.getDefault().toLanguageTag()
        )
        backend.registerPushToken(
            sessionToken = auth.sessionToken.takeIf { auth.signedIn },
            request = request
        ).onSuccess {
            prefs.edit()
                .putString(KEY_REGISTERED_KEY, registrationKey)
                .remove(KEY_LAST_ERROR)
                .apply()
        }.onFailure { t ->
            prefs.edit()
                .putString(KEY_LAST_ERROR, t.message ?: t.javaClass.simpleName)
                .apply()
        }
    }

    companion object {
        private const val KEY_TOKEN = "token"
        private const val KEY_REGISTERED_KEY = "registered-key"
        private const val KEY_LAST_ERROR = "last-error"
    }
}
