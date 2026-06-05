package com.sponic.langbang.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AccountPrefsState(
    val email: String? = null,
    val provider: String? = null,
    val skippedSignIn: Boolean = false,
    val googleRequested: Boolean = false
) {
    val signedIn: Boolean get() = !email.isNullOrBlank() || provider == PROVIDER_GOOGLE
    val customItemGateSatisfied: Boolean get() = signedIn || skippedSignIn

    companion object {
        const val PROVIDER_EMAIL = "email"
        const val PROVIDER_GOOGLE = "google"
    }
}

class AccountPrefsStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("account-prefs", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(load())
    val state: StateFlow<AccountPrefsState> = _state.asStateFlow()

    fun signInWithEmail(email: String) {
        val cleaned = email.trim()
        if (cleaned.isEmpty()) return
        save(
            AccountPrefsState(
                email = cleaned,
                provider = AccountPrefsState.PROVIDER_EMAIL,
                skippedSignIn = false,
                googleRequested = false
            )
        )
    }

    fun signInWithGooglePlaceholder() {
        save(
            AccountPrefsState(
                email = null,
                provider = AccountPrefsState.PROVIDER_GOOGLE,
                skippedSignIn = false,
                googleRequested = true
            )
        )
    }

    fun skipSignIn() {
        save(
            AccountPrefsState(
                email = null,
                provider = null,
                skippedSignIn = true,
                googleRequested = false
            )
        )
    }

    fun signOut() {
        save(AccountPrefsState())
    }

    private fun load(): AccountPrefsState =
        AccountPrefsState(
            email = prefs.getString(KEY_EMAIL, null)?.takeIf { it.isNotBlank() },
            provider = prefs.getString(KEY_PROVIDER, null)?.takeIf { it.isNotBlank() },
            skippedSignIn = prefs.getBoolean(KEY_SKIPPED, false),
            googleRequested = prefs.getBoolean(KEY_GOOGLE_REQUESTED, false)
        )

    private fun save(state: AccountPrefsState) {
        prefs.edit()
            .putString(KEY_EMAIL, state.email)
            .putString(KEY_PROVIDER, state.provider)
            .putBoolean(KEY_SKIPPED, state.skippedSignIn)
            .putBoolean(KEY_GOOGLE_REQUESTED, state.googleRequested)
            .apply()
        _state.value = state
    }

    companion object {
        private const val KEY_EMAIL = "email"
        private const val KEY_PROVIDER = "provider"
        private const val KEY_SKIPPED = "skipped-sign-in"
        private const val KEY_GOOGLE_REQUESTED = "google-requested"
    }
}
