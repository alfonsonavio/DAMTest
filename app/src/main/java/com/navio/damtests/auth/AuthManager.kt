package com.navio.damtests.auth

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import com.navio.damtests.core.suspendRunCatching
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles all Firebase Authentication operations.
 *
 * Previously an `object` (global singleton). Now an injectable @Singleton class:
 * FirebaseAuth is injected via the constructor, which means tests can pass a
 * mocked FirebaseAuth instead of the real one. Hilt still guarantees a single
 * instance app-wide thanks to @Singleton.
 */
@Singleton
class AuthManager @Inject constructor(
    private val auth: FirebaseAuth
) {

    val currentUser: FirebaseUser? get() = auth.currentUser
    val isLoggedIn: Boolean get() = auth.currentUser != null
    val currentUid: String? get() = auth.currentUser?.uid

    /** The user's name, or null if the account has none (the UI shows a default). */
    val displayName: String? get() = auth.currentUser?.displayName?.takeIf { it.isNotBlank() }
    val email: String get() = auth.currentUser?.email ?: ""

    /** When the current user last signed in (epoch ms), or null if unknown. */
    val lastSignInAt: Long? get() = auth.currentUser?.metadata?.lastSignInTimestamp

    // --- Email / Password ---

    suspend fun signInWithEmail(email: String, password: String): Result<FirebaseUser> = suspendRunCatching {
        val result = auth.signInWithEmailAndPassword(email, password).await()
        result.user!!
    }

    suspend fun register(
        email: String,
        password: String,
        displayName: String
    ): Result<FirebaseUser> = suspendRunCatching {
        val result = auth.createUserWithEmailAndPassword(email, password).await()
        val user = result.user!!
        val profileUpdate = UserProfileChangeRequest.Builder()
            .setDisplayName(displayName)
            .build()
        user.updateProfile(profileUpdate).await()
        user
    }

    suspend fun sendPasswordResetEmail(email: String): Result<Unit> = suspendRunCatching {
        auth.sendPasswordResetEmail(email).await()
        Unit
    }

    // --- Google Sign-In ---

    /** Signs in to Firebase with a Google ID token from [GoogleCredentialClient]. */
    suspend fun signInWithGoogleIdToken(idToken: String): Result<FirebaseUser> = suspendRunCatching {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        val result = auth.signInWithCredential(credential).await()
        result.user!!
    }

    // --- Session ---

    fun signOut() = auth.signOut()

    /** Permanently deletes the signed-in Firebase account. Throws on failure. */
    suspend fun deleteCurrentUser() {
        val user = auth.currentUser ?: error("No signed-in user")
        user.delete().await()
    }
}
