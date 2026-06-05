package com.sponic.langbang.cloud

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AuthState(
    val user: CloudAuthUser? = null,
    val sessionToken: String = "",
    val sessionExpiresAt: String = "",
    val skippedCustomSyncWarning: Boolean = false,
    val lastPhraseSyncMs: Long = 0L,
    val syncingPhrases: Boolean = false,
    val error: String? = null
) {
    val signedIn: Boolean get() = user != null && sessionToken.isNotBlank()
    val customItemGateSatisfied: Boolean get() = signedIn || skippedCustomSyncWarning
}

class AuthStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("langbang-auth", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(load())
    val state: StateFlow<AuthState> = _state.asStateFlow()

    fun saveAuth(auth: CloudAuthResponse) {
        prefs.edit()
            .putString(KEY_USER_ID, auth.user.id)
            .putString(KEY_EMAIL, auth.user.email)
            .putBoolean(KEY_EMAIL_VERIFIED, auth.user.emailVerified)
            .putString(KEY_DISPLAY_NAME, auth.user.displayName)
            .putString(KEY_PICTURE_URL, auth.user.pictureUrl)
            .putString(KEY_SESSION_TOKEN, auth.session.token)
            .putString(KEY_SESSION_EXPIRES_AT, auth.session.expiresAt)
            .putBoolean(KEY_SKIPPED_CUSTOM_SYNC_WARNING, false)
            .remove(KEY_ERROR)
            .apply()
        _state.value = load().copy(error = null)
    }

    fun clear() {
        prefs.edit().clear().apply()
        _state.value = AuthState()
    }

    fun skipCustomSyncWarning() {
        prefs.edit()
            .putBoolean(KEY_SKIPPED_CUSTOM_SYNC_WARNING, true)
            .remove(KEY_ERROR)
            .apply()
        _state.value = load().copy(error = null)
    }

    fun markPhraseSyncing() {
        _state.value = _state.value.copy(syncingPhrases = true, error = null)
    }

    fun savePhraseSyncSuccess() {
        val now = System.currentTimeMillis()
        prefs.edit()
            .putLong(KEY_LAST_PHRASE_SYNC_MS, now)
            .remove(KEY_ERROR)
            .apply()
        _state.value = _state.value.copy(
            lastPhraseSyncMs = now,
            syncingPhrases = false,
            error = null
        )
    }

    fun saveError(message: String) {
        prefs.edit().putString(KEY_ERROR, message).apply()
        _state.value = _state.value.copy(syncingPhrases = false, error = message)
    }

    private fun load(): AuthState {
        val token = prefs.getString(KEY_SESSION_TOKEN, "") ?: ""
        val userId = prefs.getString(KEY_USER_ID, "") ?: ""
        val email = prefs.getString(KEY_EMAIL, "") ?: ""
        val user = if (token.isNotBlank() && userId.isNotBlank() && email.isNotBlank()) {
            CloudAuthUser(
                id = userId,
                email = email,
                emailVerified = prefs.getBoolean(KEY_EMAIL_VERIFIED, false),
                displayName = prefs.getString(KEY_DISPLAY_NAME, "") ?: "",
                pictureUrl = prefs.getString(KEY_PICTURE_URL, "") ?: ""
            )
        } else {
            null
        }
        return AuthState(
            user = user,
            sessionToken = token,
            sessionExpiresAt = prefs.getString(KEY_SESSION_EXPIRES_AT, "") ?: "",
            skippedCustomSyncWarning = prefs.getBoolean(KEY_SKIPPED_CUSTOM_SYNC_WARNING, false),
            lastPhraseSyncMs = prefs.getLong(KEY_LAST_PHRASE_SYNC_MS, 0L),
            syncingPhrases = false,
            error = prefs.getString(KEY_ERROR, null)
        )
    }

    companion object {
        private const val KEY_USER_ID = "user-id"
        private const val KEY_EMAIL = "email"
        private const val KEY_EMAIL_VERIFIED = "email-verified"
        private const val KEY_DISPLAY_NAME = "display-name"
        private const val KEY_PICTURE_URL = "picture-url"
        private const val KEY_SESSION_TOKEN = "session-token"
        private const val KEY_SESSION_EXPIRES_AT = "session-expires-at"
        private const val KEY_SKIPPED_CUSTOM_SYNC_WARNING = "skipped-custom-sync-warning"
        private const val KEY_LAST_PHRASE_SYNC_MS = "last-phrase-sync-ms"
        private const val KEY_ERROR = "error"
    }
}
