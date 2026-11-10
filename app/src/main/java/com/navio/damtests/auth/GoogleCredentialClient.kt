package com.navio.damtests.auth

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.navio.damtests.R
import com.navio.damtests.core.suspendRunCatching
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Google sign-in through Android's Credential Manager, which replaces the
 * deprecated Google Sign-In for Android library (GoogleSignInClient).
 *
 * Uses the "Sign in with Google" button flow ([GetSignInWithGoogleOption]):
 * it always shows the account picker, including the option to add an account,
 * and returns a Google ID token that Firebase Auth then exchanges for a
 * Firebase session (see [AuthManager.signInWithGoogleIdToken]).
 */
@Singleton
class GoogleCredentialClient @Inject constructor(
    @ApplicationContext private val appContext: Context
) {

    sealed class Result {
        data class Success(val idToken: String) : Result()

        /** The user closed the account picker: not an error, show nothing. */
        object Cancelled : Result()

        /** There is no Google account the user can pick on this device. */
        object NoAccount : Result()
        data class Error(val cause: Throwable) : Result()
    }

    private val credentialManager by lazy { CredentialManager.create(appContext) }

    /**
     * Shows the Google account picker and returns the chosen account's ID token.
     * Needs an Activity context: the picker is shown on top of it.
     */
    suspend fun getGoogleIdToken(activity: Activity): Result {
        val option = GetSignInWithGoogleOption.Builder(
            activity.getString(R.string.default_web_client_id)
        ).build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(option)
            .build()

        return try {
            val credential = credentialManager.getCredential(activity, request).credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                Result.Success(GoogleIdTokenCredential.createFrom(credential.data).idToken)
            } else {
                Result.Error(IllegalStateException("Unexpected credential type: ${credential.type}"))
            }
        } catch (e: GetCredentialCancellationException) {
            Log.d(TAG, "Google sign-in cancelled: ${e.type}")
            Result.Cancelled
        } catch (e: NoCredentialException) {
            Log.d(TAG, "No Google account to sign in with: ${e.type}")
            Result.NoAccount
        } catch (e: GetCredentialException) {
            Log.e(TAG, "Google sign-in failed: ${e.type}", e)
            Result.Error(e)
        } catch (e: GoogleIdTokenParsingException) {
            Log.e(TAG, "Invalid Google ID token response", e)
            Result.Error(e)
        }
    }

    /**
     * Forgets the account used to sign in, so the next sign-in starts fresh.
     * Called on logout and account deletion. Never throws: failing to clear
     * this must not block signing out.
     */
    suspend fun clearCredentialState() {
        suspendRunCatching { credentialManager.clearCredentialState(ClearCredentialStateRequest()) }
            .onFailure { Log.w(TAG, "Could not clear credential state: ${it.message}") }
    }

    private companion object {
        const val TAG = "GoogleCredentialClient"
    }
}
