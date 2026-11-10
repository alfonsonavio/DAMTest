package com.navio.damtests.auth

import android.util.Log
import com.navio.damtests.core.suspendRunCatching
import com.navio.damtests.data.local.LocalUserData
import com.navio.damtests.data.sync.CloudBackup
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Signs the user out without losing anything.
 *
 * Local user data is wiped on logout (so another account on the same device
 * never sees or uploads it), which is only safe if the cloud already has all
 * of it. So before signing out, [CloudBackup] uploads everything and waits
 * for the server to confirm it. Only when all of that is confirmed is local data wiped and the user signed
 * out. If anything fails (no connection, timeout, server error) nothing is
 * deleted and the user stays signed in.
 */
@Singleton
class LogoutManager @Inject constructor(
    private val authManager: AuthManager,
    private val cloudBackup: CloudBackup,
    private val localUserData: LocalUserData,
    private val googleCredentialClient: GoogleCredentialClient
) {

    /** @return true if the user was signed out; false if the backup couldn't be confirmed. */
    suspend fun backupAndSignOut(): Boolean {
        val uid = authManager.currentUid
        if (uid != null) {
            val backedUp = suspendRunCatching { withTimeout(BACKUP_TIMEOUT_MS) { cloudBackup.confirmAll(uid) } }
                .onFailure { Log.e(TAG, "Backup before logout failed, staying signed in: ${it.message}") }
                .isSuccess
            if (!backedUp) return false

            localUserData.clear(uid)
            Log.d(TAG, "All data confirmed in the cloud; local user data cleared")
        }
        authManager.signOut()
        googleCredentialClient.clearCredentialState()
        return true
    }

    private companion object {
        const val TAG = "LogoutManager"
        const val BACKUP_TIMEOUT_MS = 30_000L
    }
}
