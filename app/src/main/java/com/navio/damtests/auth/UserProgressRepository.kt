package com.navio.damtests.auth

import android.util.Log
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.navio.damtests.data.local.entity.TopicProgress
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Synchronises per-user progress between Room (local) and Cloud Firestore (remote).
 *
 * Now an injectable @Singleton class (was an `object`). FirebaseFirestore is
 * injected so tests can supply a mock instead of the real Firestore.
 *
 * Firestore structure: users/{uid}/progress/{subjectId}_{topicId}
 * Merge strategy: the record with the most recent lastAttemptTimestamp wins.
 */
@Singleton
class UserProgressRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {

    private fun progressDocId(progress: TopicProgress) =
        "${progress.subjectId}_${progress.topicId}"

    private fun progressRef(uid: String, progress: TopicProgress) =
        firestore.collection("users").document(uid)
            .collection("progress").document(progressDocId(progress))

    /**
     * Queues the progress write in Firestore without waiting for the server.
     *
     * Firestore stores the write in its local cache immediately and uploads it
     * when there is a connection, so awaiting the server only made callers hang
     * (forever when offline) and fail with "Job was cancelled" when the screen
     * that started the save was closed before the server answered.
     */
    fun saveTopicProgress(uid: String, progress: TopicProgress) {
        val docId = progressDocId(progress)
        progressRef(uid, progress).set(progress.toMap())
            .addOnSuccessListener { Log.d(TAG, "Saved $docId to Firestore") }
            .addOnFailureListener { e -> Log.e(TAG, "Failed to save progress $docId: ${e.message}") }
    }

    suspend fun uploadAllProgress(uid: String, allProgress: List<TopicProgress>) {
        allProgress.forEach { saveTopicProgress(uid, it) }
        Log.d(TAG, "Uploaded ${allProgress.size} progress records")
    }

    suspend fun downloadAllProgress(uid: String): List<TopicProgress> {
        return try {
            progressCollection(uid).get().await().documents.mapNotNull { it.toTopicProgress() }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download progress: ${e.message}")
            emptyList()
        }
    }

    /**
     * Makes sure the server has every local progress record, and waits for the
     * server to confirm the upload. Used before logout, when local data is about
     * to be wiped. Throws if the server can't be reached.
     */
    suspend fun pushAllProgress(uid: String, local: List<TopicProgress>) {
        val cloud = progressCollection(uid).get(Source.SERVER).await()
            .documents.mapNotNull { it.toTopicProgress() }
        progressToUpload(local, cloud).chunked(MAX_BATCH_WRITES).forEach { chunk ->
            val batch = firestore.batch()
            chunk.forEach { batch.set(progressRef(uid, it), it.toMap()) }
            batch.commit().await()
        }
    }

    /**
     * Local records the cloud is missing or has an older version of.
     * Pure function — easy to unit test.
     */
    fun progressToUpload(local: List<TopicProgress>, cloud: List<TopicProgress>): List<TopicProgress> {
        val cloudByKey = cloud.associateBy { "${it.subjectId}_${it.topicId}" }
        return local.filter { l ->
            val c = cloudByKey["${l.subjectId}_${l.topicId}"]
            c == null || l.lastAttemptTimestamp > c.lastAttemptTimestamp
        }
    }

    private fun progressCollection(uid: String) =
        firestore.collection("users").document(uid).collection("progress")

    private fun TopicProgress.toMap() = mapOf(
        "subjectId"            to subjectId,
        "topicId"              to topicId,
        "lastScore"            to lastScore,
        "totalQuestions"       to totalQuestions,
        "attemptsCount"        to attemptsCount,
        "lastAttemptTimestamp" to lastAttemptTimestamp
    )

    private fun DocumentSnapshot.toTopicProgress(): TopicProgress? {
        return TopicProgress(
            subjectId            = getString("subjectId") ?: return null,
            topicId              = getString("topicId") ?: return null,
            lastScore            = (getLong("lastScore") ?: 0).toInt(),
            totalQuestions       = (getLong("totalQuestions") ?: 0).toInt(),
            attemptsCount        = (getLong("attemptsCount") ?: 0).toInt(),
            lastAttemptTimestamp = getLong("lastAttemptTimestamp") ?: 0L
        )
    }

    /**
     * Merges cloud and local progress. For each topic, the record with the most
     * recent lastAttemptTimestamp wins. Pure function — easy to unit test.
     */
    fun mergeProgress(
        local: List<TopicProgress>,
        cloud: List<TopicProgress>
    ): List<TopicProgress> {
        val merged = mutableMapOf<String, TopicProgress>()
        (local + cloud).forEach { progress ->
            val key = "${progress.subjectId}_${progress.topicId}"
            val existing = merged[key]
            if (existing == null || progress.lastAttemptTimestamp > existing.lastAttemptTimestamp) {
                merged[key] = progress
            }
        }
        return merged.values.toList()
    }

    companion object {
        private const val TAG = "UserProgressRepo"
        private const val MAX_BATCH_WRITES = 500 // Firestore limit per batch
    }
}