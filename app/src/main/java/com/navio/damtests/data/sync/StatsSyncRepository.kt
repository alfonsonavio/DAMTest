package com.navio.damtests.data.sync

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import com.navio.damtests.core.suspendRunCatching
import com.navio.damtests.data.local.dao.FavoriteQuestionDao
import com.navio.damtests.data.local.dao.QuestionStatsDao
import com.navio.damtests.data.local.dao.TestAttemptDao
import com.navio.damtests.data.local.entity.FavoriteQuestion
import com.navio.damtests.data.local.entity.QuestionStats
import com.navio.damtests.data.local.entity.TestAttempt
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps the statistics history (test attempts and per-question stats) and the
 * favourite questions in sync with Cloud Firestore, so they survive reinstalls
 * and device changes.
 *
 * Firestore structure:
 *   users/{uid}/attempts/{subjectId}_{topicId}_{timestamp}
 *   users/{uid}/questionStats/{stableId}
 *   users/{uid}/favorites/{stableId}
 *
 * - Every new attempt / answer / favourite change is uploaded as it happens
 *   ([saveAttempt], [saveQuestionStats], [saveFavorite]). Writes are not awaited: Firestore queues them in its
 *   local cache and uploads them when there is a connection.
 * - [sync] runs at startup. The first time for a user on a device it does a
 *   full two-way merge (downloads everything, uploads what the cloud lacks).
 *   After that it only downloads what changed since the last sync, to bring
 *   in tests taken on other devices without re-reading the whole history.
 */
@Singleton
class StatsSyncRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val testAttemptDao: TestAttemptDao,
    private val questionStatsDao: QuestionStatsDao,
    private val favoriteQuestionDao: FavoriteQuestionDao,
    @ApplicationContext context: Context
) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _syncedAt = MutableStateFlow(0L)

    /** Changes every time a sync finishes, so open screens can refresh. */
    val syncedAt: StateFlow<Long> = _syncedAt

    private fun userDoc(uid: String) = firestore.collection("users").document(uid)
    private fun attempts(uid: String) = userDoc(uid).collection("attempts")
    private fun questionStats(uid: String) = userDoc(uid).collection("questionStats")
    private fun favorites(uid: String) = userDoc(uid).collection("favorites")

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

    fun saveFavorite(uid: String, favorite: FavoriteQuestion) {
        favorites(uid).document(favorite.stableId).set(favorite.toMap())
            .addOnFailureListener { e -> Log.e(TAG, "Failed to upload favourite ${favorite.stableId}: ${e.message}") }
    }

    // --- Startup sync ---

    suspend fun sync(uid: String) {
        val lastSync = prefs.getLong(lastSyncKey(uid), 0L)
        val isFirstSync = lastSync == 0L
        val syncStartedAt = System.currentTimeMillis()
        // Overlap window so a slightly skewed clock on another device can't
        // make us skip its records. Merging is idempotent, so overlap is harmless.
        val since = if (isFirstSync) null else lastSync - OVERLAP_MS

        suspendRunCatching {
            val cloudAttempts = download(attempts(uid), "timestamp", since).mapNotNull { it.toTestAttempt() }
            val cloudStats = download(
                questionStats(uid),
                "lastSeenTimestamp",
                since
            ).mapNotNull { it.toQuestionStats() }
            val cloudFavorites = download(favorites(uid), "updatedAt", since).mapNotNull { it.toFavorite() }

            val attemptsPlan = StatsMerger.mergeAttempts(
                local = testAttemptDao.getAllAttempts(),
                cloud = cloudAttempts,
                uploadLocalOnly = isFirstSync
            )
            val statsPlan = StatsMerger.mergeQuestionStats(
                local = questionStatsDao.getAllQuestionStats(),
                cloud = cloudStats,
                uploadLocalNewer = isFirstSync
            )
            val favoritesPlan = StatsMerger.mergeFavorites(
                local = favoriteQuestionDao.getAllFavoriteRecords(),
                cloud = cloudFavorites,
                uploadLocalNewer = isFirstSync
            )

            if (attemptsPlan.toLocal.isNotEmpty()) testAttemptDao.insertTestAttempts(attemptsPlan.toLocal)
            if (statsPlan.toLocal.isNotEmpty()) questionStatsDao.saveQuestionStatsList(statsPlan.toLocal)
            if (favoritesPlan.toLocal.isNotEmpty()) favoriteQuestionDao.saveFavorites(favoritesPlan.toLocal)
            uploadInBatches(uid, attemptsPlan.toCloud, statsPlan.toCloud, favoritesPlan.toCloud).forEach { task ->
                task.addOnFailureListener { e -> Log.e(TAG, "Stats batch upload failed: ${e.message}") }
            }

            prefs.edit { putLong(lastSyncKey(uid), syncStartedAt) }
            _syncedAt.value = System.currentTimeMillis()
            Log.d(
                TAG,
                "Stats sync (${if (isFirstSync) "full" else "incremental"}) done: " +
                    "attempts ↓${attemptsPlan.toLocal.size} ↑${attemptsPlan.toCloud.size}, " +
                    "questionStats ↓${statsPlan.toLocal.size} ↑${statsPlan.toCloud.size}, " +
                    "favorites ↓${favoritesPlan.toLocal.size} ↑${favoritesPlan.toCloud.size}"
            )
        }.onFailure {
            // Offline or Firestore error: keep lastSync untouched so it retries next launch.
            Log.e(TAG, "Stats sync failed: ${it.message}")
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
        val query: Query = if (since == null) {
            collection
        } else {
            collection.whereGreaterThan(timestampField, since)
        }
        return query.get(Source.SERVER).await().documents
    }

    /**
     * Makes sure the server has the user's whole local statistics history, and
     * waits for the server to confirm. Used before logout, when local data is
     * about to be wiped. Throws if the server can't be reached.
     */
    suspend fun pushAll(uid: String) {
        val cloudAttempts = download(attempts(uid), "timestamp", null).mapNotNull { it.toTestAttempt() }
        val cloudStats = download(questionStats(uid), "lastSeenTimestamp", null).mapNotNull { it.toQuestionStats() }
        val cloudFavorites = download(favorites(uid), "updatedAt", null).mapNotNull { it.toFavorite() }

        val attemptsToUpload = StatsMerger.mergeAttempts(
            testAttemptDao.getAllAttempts(),
            cloudAttempts,
            uploadLocalOnly = true
        ).toCloud
        val statsToUpload = StatsMerger.mergeQuestionStats(
            questionStatsDao.getAllQuestionStats(),
            cloudStats,
            uploadLocalNewer = true
        ).toCloud
        val favoritesToUpload = StatsMerger.mergeFavorites(
            favoriteQuestionDao.getAllFavoriteRecords(),
            cloudFavorites,
            uploadLocalNewer = true
        ).toCloud

        uploadInBatches(uid, attemptsToUpload, statsToUpload, favoritesToUpload).forEach { it.await() }
        Log.d(
            TAG,
            "Stats backup confirmed: attempts ↑${attemptsToUpload.size}, " +
                "questionStats ↑${statsToUpload.size}, favorites ↑${favoritesToUpload.size}"
        )
    }

    /** Forgets that this user was synced on this device, so the next login does a full sync. */
    fun forgetSyncState(uid: String) = prefs.edit { remove(lastSyncKey(uid)) }

    private fun uploadInBatches(
        uid: String,
        attemptsToUpload: List<TestAttempt>,
        statsToUpload: List<QuestionStats>,
        favoritesToUpload: List<FavoriteQuestion>
    ): List<Task<Void>> {
        val writes = attemptsToUpload.map { a -> attempts(uid).document(StatsMerger.attemptKey(a)) to a.toMap() } +
            statsToUpload.map { s -> questionStats(uid).document(s.stableId) to s.toMap() } +
            favoritesToUpload.map { f -> favorites(uid).document(f.stableId) to f.toMap() }

        return writes.chunked(MAX_BATCH_WRITES).map { chunk ->
            val batch = firestore.batch()
            chunk.forEach { (ref, data) -> batch.set(ref, data) }
            batch.commit()
        }
    }

    private fun lastSyncKey(uid: String) = "last_sync_$uid"

    // --- Mapping ---

    private fun TestAttempt.toMap() = mapOf(
        "subjectId" to subjectId,
        "topicId" to topicId,
        "score" to score,
        "totalQuestions" to totalQuestions,
        "timestamp" to timestamp
    )

    private fun QuestionStats.toMap() = mapOf(
        "subjectId" to subjectId,
        "timesSeen" to timesSeen,
        "timesCorrect" to timesCorrect,
        "timesWrong" to timesWrong,
        "lastSeenTimestamp" to lastSeenTimestamp
    )

    private fun FavoriteQuestion.toMap() = mapOf(
        "subjectId" to subjectId,
        "isFavorite" to isFavorite,
        "updatedAt" to updatedAt
    )

    /** Null if a required field is missing (a document from an older or broken write). */
    private fun DocumentSnapshot.toFavorite(): FavoriteQuestion? {
        val subjectId = getString("subjectId") ?: return null
        val isFavorite = getBoolean("isFavorite") ?: return null
        val updatedAt = getLong("updatedAt") ?: return null
        return FavoriteQuestion(stableId = id, subjectId = subjectId, isFavorite = isFavorite, updatedAt = updatedAt)
    }

    /** Null if a required field is missing (a document from an older or broken write). */
    private fun DocumentSnapshot.toTestAttempt(): TestAttempt? {
        val subjectId = getString("subjectId") ?: return null
        val topicId = getString("topicId") ?: return null
        val score = getLong("score") ?: return null
        val totalQuestions = getLong("totalQuestions") ?: return null
        val timestamp = getLong("timestamp") ?: return null
        return TestAttempt(
            subjectId = subjectId,
            topicId = topicId,
            score = score.toInt(),
            totalQuestions = totalQuestions.toInt(),
            timestamp = timestamp
        )
    }

    private fun DocumentSnapshot.toQuestionStats(): QuestionStats? {
        return QuestionStats(
            stableId = id,
            subjectId = getString("subjectId") ?: return null,
            timesSeen = getLong("timesSeen")?.toInt() ?: 0,
            timesCorrect = getLong("timesCorrect")?.toInt() ?: 0,
            timesWrong = getLong("timesWrong")?.toInt() ?: 0,
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
