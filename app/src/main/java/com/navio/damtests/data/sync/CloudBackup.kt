package com.navio.damtests.data.sync

import com.google.firebase.firestore.FirebaseFirestore
import com.navio.damtests.data.local.dao.TopicProgressDao
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Makes sure the cloud has everything the user has on this device:
 *  1. Compares local data with the SERVER copy and uploads whatever is missing
 *     or older: topic progress, test attempts and per-question stats.
 *  2. Backs up the test in progress.
 *  3. Waits until Firestore confirms every pending write, including the live
 *     uploads queued earlier (e.g. while offline).
 */
@Singleton
class CloudBackup @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val topicProgressDao: TopicProgressDao,
    private val userProgressRepository: UserProgressRepository,
    private val statsSyncRepository: StatsSyncRepository,
    private val sessionBackupRepository: SessionBackupRepository
) {

    /** Returns once the server has confirmed everything. Throws if it can't. */
    suspend fun confirmAll(uid: String) {
        userProgressRepository.pushAllProgress(uid, topicProgressDao.getAllProgressOnce())
        statsSyncRepository.pushAll(uid)
        sessionBackupRepository.backup(uid)
        firestore.waitForPendingWrites().await()
    }
}
