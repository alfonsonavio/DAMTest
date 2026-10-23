package com.navio.damtests.auth

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.navio.damtests.data.local.entity.QuestionsDao
import com.navio.damtests.data.session.QuizSessionStore
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Signs the user out without losing anything.
 *
 * Local user data is wiped on logout (so another account on the same device
 * never sees or uploads it), which is only safe if the cloud already has all
 * of it. So before signing out this:
 *  1. Compares local data with the SERVER copy and uploads whatever is missing
 *     or older: topic progress, test attempts and per-question stats.
 *  2. Backs up the test in progress.
 *  3. Waits until Firestore confirms every pending write, including the live
 *     uploads queued earlier (e.g. while offline).
 * Only when all of that is confirmed is local data wiped and the user signed
 * out. If anything fails (no connection, timeout, server error) nothing is
 * deleted and the user stays signed in.
 */
@Singleton
class LogoutManager @Inject constructor(
    private val authManager: AuthManager,
    private val firestore: FirebaseFirestore,
    private val questionsDao: QuestionsDao,
    private val sessionStore: QuizSessionStore,
    private val userProgressRepository: UserProgressRepository,
    private val statsSyncRepository: StatsSyncRepository,
    private val sessionBackupRepository: SessionBackupRepository
) {

    /** @return true if the user was signed out; false if the backup couldn't be confirmed. */
    suspend fun backupAndSignOut(): Boolean {
        val uid = authManager.currentUid
        if (uid != null) {
            try {
                withTimeout(BACKUP_TIMEOUT_MS) {
                    userProgressRepository.pushAllProgress(uid, questionsDao.getAllProgressOnce())
                    statsSyncRepository.pushAll(uid)
                    sessionBackupRepository.backup(uid)
                    firestore.waitForPendingWrites().await()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Backup before logout failed, staying signed in: ${e.message}")
                return false
            }

            questionsDao.clearUserData()
            sessionStore.clear()
            statsSyncRepository.forgetSyncState(uid)
            Log.d(TAG, "All data confirmed in the cloud; local user data cleared")
        }
        authManager.signOut()
        return true
    }

    private companion object {
        const val TAG = "LogoutManager"
        const val BACKUP_TIMEOUT_MS = 30_000L
    }
}