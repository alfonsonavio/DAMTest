package com.navio.damtests.auth

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import com.google.android.gms.tasks.Task
import com.navio.damtests.data.local.entity.QuestionStats
import com.navio.damtests.data.local.entity.QuestionsDao
import com.navio.damtests.data.local.entity.TestAttempt
import com.navio.damtests.data.sync.StatsMerger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps the statistics history (test attempts and per-question stats) in sync
 * with Cloud Firestore, so Statistics survive reinstalls and device changes.
 *
 * Firestore structure:
 *   users/{uid}/attempts/{subjectId}_{topicId}_{timestamp}
 *   users/{uid}/questionStats/{stableId}
 *
 * - Every new attempt / answer is uploaded as it happens ([saveAttempt],
 *   [saveQuestionStats]). Writes are not awaited: Firestore queues them in its
 *   local cache and uploads them when there is a connection.
 * - [sync] runs at startup. The first time for a user on a device it does a
 *   full two-way merge (downloads everything, uploads what the cloud lacks).
 *   After that it only downloads what changed since the last sync, to bring
 *   in tests taken on other devices without re-reading the whole history.
 */
@Singleton
class StatsSyncRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val questionsDao: QuestionsDao,
    @ApplicationContext context: Context
) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun userDoc(uid: String) = firestore.collection("users").document(uid)
    private fun attempts(uid: String) = userDoc(uid).collection("attempts")
    private fun questionStats(uid: String) = userDoc(uid).collection("questionStats")

    // --- Live uploads ---

    fun saveAttempt(uid: String, attempt: TestAttempt) {
        val key = StatsMerger.attemptKey(attempt)
        attempts(uid).document(key).set(attempt.toMap())
            .addOnFailureListener { e -> Log.e(TAG, "Failed to upload attempt $key: ${e.message}") }
    }

    fun saveQuestionStats(uid: String, stats: QuestionStats) {
        questionStats(uid).document(stats.stableId).set(stats.toMap())
            .addOnFailureListener { e -> Log.e(TAG, "Failed to upload stats ${stats.stableId}: ${e.message}") }
    }

    // --- Startup sync ---

    suspend fun sync(uid: String) {
        val lastSync = prefs.getLong(lastSyncKey(uid), 0L)
        val isFirstSync = lastSync == 0L
        val syncStartedAt = System.currentTimeMillis()
        // Overlap window so a slightly skewed clock on another device can't
        // make us skip its records. Merging is idempotent, so overlap is harmless.
        val since = if (isFirstSync) null else lastSync - OVERLAP_MS

        try {
            val cloudAttempts = download(attempts(uid), "timestamp", since).mapNotNull { it.toTestAttempt() }
            val cloudStats    = download(questionStats(uid), "lastSeenTimestamp", since).mapNotNull { it.toQuestionStats() }

            val attemptsPlan = StatsMerger.mergeAttempts(
                local = questionsDao.getAllAttempts(),
                cloud = cloudAttempts,
                uploadLocalOnly = isFirstSync
            )
            val statsPlan = StatsMerger.mergeQuestionStats(
                local = questionsDao.getAllQuestionStats(),
                cloud = cloudStats,
                uploadLocalNewer = isFirstSync
            )

            if (attemptsPlan.toLocal.isNotEmpty()) questionsDao.insertTestAttempts(attemptsPlan.toLocal)
            if (statsPlan.toLocal.isNotEmpty()) questionsDao.saveQuestionStatsList(statsPlan.toLocal)
            uploadInBatches(uid, attemptsPlan.toCloud, statsPlan.toCloud).forEach { task ->
                task.addOnFailureListener { e -> Log.e(TAG, "Stats batch upload failed: ${e.message}") }
            }

            prefs.edit { putLong(lastSyncKey(uid), syncStartedAt) }
            Log.d(
                TAG, "Stats sync (${if (isFirstSync) "full" else "incremental"}) done: " +
                        "attempts ↓${attemptsPlan.toLocal.size} ↑${attemptsPlan.toCloud.size}, " +
                        "questionStats ↓${statsPlan.toLocal.size} ↑${statsPlan.toCloud.size}"
            )
        } catch (e: Exception) {
            // Offline or Firestore error: keep lastSync untouched so it retries next launch.
            Log.e(TAG, "Stats sync failed: ${e.message}")
        }
    }

    /**
     * Reads from the SERVER only. A cache read while offline would return an
     * empty or partial history, and a first sync would then wrongly consider
     * itself complete.
     */
    private suspend fun download(
        collection: CollectionReference,
        timestampField: String,
        since: Long?
    ): List<DocumentSnapshot> {
        val query: Query = if (since == null) collection
        else collection.whereGreaterThan(timestampField, since)
        return query.get(Source.SERVER).await().documents
    }

    /**
     * Makes sure the server has the user's whole local statistics history, and
     * waits for the server to confirm. Used before logout, when local data is
     * about to be wiped. Throws if the server can't be reached.
     */
    suspend fun pushAll(uid: String) {
        val cloudAttempts = download(attempts(uid), "timestamp", null).mapNotNull { it.toTestAttempt() }
        val cloudStats    = download(questionStats(uid), "lastSeenTimestamp", null).mapNotNull { it.toQuestionStats() }

        val attemptsToUpload = StatsMerger.mergeAttempts(
            questionsDao.getAllAttempts(), cloudAttempts, uploadLocalOnly = true
        ).toCloud
        val statsToUpload = StatsMerger.mergeQuestionStats(
            questionsDao.getAllQuestionStats(), cloudStats, uploadLocalNewer = true
        ).toCloud

        uploadInBatches(uid, attemptsToUpload, statsToUpload).forEach { it.await() }
        Log.d(TAG, "Stats backup confirmed: attempts ↑${attemptsToUpload.size}, questionStats ↑${statsToUpload.size}")
    }

    /** Forgets that this user was synced on this device, so the next login does a full sync. */
    fun forgetSyncState(uid: String) = prefs.edit { remove(lastSyncKey(uid)) }

    private fun uploadInBatches(
        uid: String,
        attemptsToUpload: List<TestAttempt>,
        statsToUpload: List<QuestionStats>
    ): List<Task<Void>> {
        val writes = attemptsToUpload.map { a -> attempts(uid).document(StatsMerger.attemptKey(a)) to a.toMap() } +
                statsToUpload.map { s -> questionStats(uid).document(s.stableId) to s.toMap() }

        return writes.chunked(MAX_BATCH_WRITES).map { chunk ->
            val batch = firestore.batch()
            chunk.forEach { (ref, data) -> batch.set(ref, data) }
            batch.commit()
        }
    }

    private fun lastSyncKey(uid: String) = "last_sync_$uid"

    // --- Mapping ---

    private fun TestAttempt.toMap() = mapOf(
        "subjectId"      to subjectId,
        "topicId"        to topicId,
        "score"          to score,
        "totalQuestions" to totalQuestions,
        "timestamp"      to timestamp
    )

    private fun QuestionStats.toMap() = mapOf(
        "subjectId"         to subjectId,
        "timesSeen"         to timesSeen,
        "timesCorrect"      to timesCorrect,
        "timesWrong"        to timesWrong,
        "lastSeenTimestamp" to lastSeenTimestamp
    )

    private fun DocumentSnapshot.toTestAttempt(): TestAttempt? {
        return TestAttempt(
            subjectId      = getString("subjectId") ?: return null,
            topicId        = getString("topicId") ?: return null,
            score          = getLong("score")?.toInt() ?: return null,
            totalQuestions = getLong("totalQuestions")?.toInt() ?: return null,
            timestamp      = getLong("timestamp") ?: return null
        )
    }

    private fun DocumentSnapshot.toQuestionStats(): QuestionStats? {
        return QuestionStats(
            stableId          = id,
            subjectId         = getString("subjectId") ?: return null,
            timesSeen         = getLong("timesSeen")?.toInt() ?: 0,
            timesCorrect      = getLong("timesCorrect")?.toInt() ?: 0,
            timesWrong        = getLong("timesWrong")?.toInt() ?: 0,
            lastSeenTimestamp = getLong("lastSeenTimestamp") ?: 0L
        )
    }

    companion object {
        private const val TAG = "StatsSyncRepo"
        private const val PREFS_NAME = "damtest_stats_sync"
        private const val OVERLAP_MS = 24 * 60 * 60 * 1000L
        private const val MAX_BATCH_WRITES = 500 // Firestore limit per batch
    }
}