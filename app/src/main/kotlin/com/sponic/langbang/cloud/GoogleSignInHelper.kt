package com.sponic.langbang.cloud

import android.content.Context
import android.util.Base64
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import java.security.SecureRandom

data class GoogleIdTokenResult(
    val idToken: String,
    val nonce: String
)

class GoogleSignInHelper(
    private val context: Context
) {
    private val credentialManager = CredentialManager.create(context)

    suspend fun requestIdToken(webClientId: String): Result<GoogleIdTokenResult> = runCatching {
        if (webClientId.isBlank()) error("Google sign-in is not configured.")
        val nonce = secureNonce()
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(webClientId)
            .setNonce(nonce)
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()
        val result = credentialManager.getCredential(context, request)
        val credential = result.credential
        if (credential !is CustomCredential ||
            credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            error("Google did not return an ID token.")
        }
        val googleCredential = GoogleIdTokenCredential.createFrom(credential.data)
        GoogleIdTokenResult(
            idToken = googleCredential.idToken,
            nonce = nonce
        )
    }

    suspend fun clearCredentialState() {
        runCatching {
            credentialManager.clearCredentialState(ClearCredentialStateRequest())
        }
    }

    private fun secureNonce(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(
            bytes,
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )
    }
}
