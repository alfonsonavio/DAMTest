package com.navio.damtests.data.local

import com.navio.damtests.data.local.dao.UserDataDao
import com.navio.damtests.data.session.QuizSessionStore
import com.navio.damtests.data.sync.StatsSyncRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Everything of the signed-in user stored on this device: progress, stats,
 * favorites, the test in progress and the sync bookmarks. Wiped on logout and
 * on account deletion, so another account on the same device never sees or
 * uploads it.
 */
@Singleton
class LocalUserData @Inject constructor(
    private val userDataDao: UserDataDao,
    private val sessionStore: QuizSessionStore,
    private val statsSyncRepository: StatsSyncRepository
) {

    suspend fun clear(uid: String) {
        userDataDao.clearUserData()
        sessionStore.clear()
        statsSyncRepository.forgetSyncState(uid)
    }
}
