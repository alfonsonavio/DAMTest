package com.navio.damtests.auth

import com.navio.damtests.core.suspendRunCatching
import com.navio.damtests.data.local.LocalUserData
import com.navio.damtests.data.sync.CloudUserDataRepository
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Deletes the user's account and all their data (right to erasure).
 *
 * Firebase only lets an account be deleted shortly after signing in, so that
 * is checked first: if the sign-in is too old, nothing is touched and the user
 * is asked to sign in again. Then the cloud data is deleted (while the user can
 * still access it), then the account, and finally the local copy.
 */
@Singleton
class AccountDeletionManager @Inject constructor(
    private val authManager: AuthManager,
    private val cloudUserData: CloudUserDataRepository,
    private val localUserData: LocalUserData,
    private val googleCredentialClient: GoogleCredentialClient
) {

    enum class Result {
        /** Account and all data deleted; the user is signed out. */
        DELETED,

        /** Nothing deleted: the user must sign in again first. */
        NEEDS_RECENT_LOGIN,

        /** Data deleted but the account itself could not be; the user is signed out. */
        DATA_DELETED_ACCOUNT_PENDING,

        /** Nothing deleted (no connection, server error…). */
        FAILED
    }

    suspend fun deleteAccount(now: Long = System.currentTimeMillis()): Result {
        val uid = authManager.currentUid ?: return Result.FAILED

        val lastSignIn = authManager.lastSignInAt ?: 0L
        if (now - lastSignIn > RECENT_LOGIN_MS) return Result.NEEDS_RECENT_LOGIN

        val cloudDeleted = suspendRunCatching { withTimeout(TIMEOUT_MS) { cloudUserData.deleteAll(uid) } }.isSuccess
        if (!cloudDeleted) return Result.FAILED

        val accountDeleted = suspendRunCatching { authManager.deleteCurrentUser() }.isSuccess

        // The cloud data is gone either way: wipe the local copy too, so it
        // can't be uploaded again, and leave the user signed out.
        localUserData.clear(uid)
        authManager.signOut()
        googleCredentialClient.clearCredentialState()

        return if (accountDeleted) Result.DELETED else Result.DATA_DELETED_ACCOUNT_PENDING
    }

    private companion object {
        /** Firebase asks for a sign-in within about the last 5 minutes. */
        const val RECENT_LOGIN_MS = 5 * 60 * 1000L
        const val TIMEOUT_MS = 30_000L
    }
}
