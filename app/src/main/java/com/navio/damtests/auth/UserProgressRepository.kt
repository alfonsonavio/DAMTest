package com.navio.damtests.auth

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
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

    suspend fun saveTopicProgress(uid: String, progress: TopicProgress) {
        try {
            val data = mapOf(
                "subjectId"            to progress.subjectId,
                "topicId"              to progress.topicId,
                "lastScore"            to progress.lastScore,
                "totalQuestions"       to progress.totalQuestions,
                "attemptsCount"        to progress.attemptsCount,
                "lastAttemptTimestamp" to progress.lastAttemptTimestamp
            )
            progressRef(uid, progress).set(data).await()
            Log.d(TAG, "Saved ${progressDocId(progress)} to Firestore")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save progress: ${e.message}")
        }
    }

    suspend fun uploadAllProgress(uid: String, allProgress: List<TopicProgress>) {
        allProgress.forEach { saveTopicProgress(uid, it) }
        Log.d(TAG, "Uploaded ${allProgress.size} progress records")
    }

    suspend fun downloadAllProgress(uid: String): List<TopicProgress> {
        return try {
            val snapshot = firestore.collection("users").document(uid)
                .collection("progress").get().await()
            snapshot.documents.mapNotNull { doc ->
                try {
                    TopicProgress(
                        subjectId            = doc.getString("subjectId") ?: return@mapNotNull null,
                        topicId              = doc.getString("topicId") ?: return@mapNotNull null,
                        lastScore            = (doc.getLong("lastScore") ?: 0).toInt(),
                        totalQuestions       = (doc.getLong("totalQuestions") ?: 0).toInt(),
                        attemptsCount        = (doc.getLong("attemptsCount") ?: 0).toInt(),
                        lastAttemptTimestamp = doc.getLong("lastAttemptTimestamp") ?: 0L
                    )
                } catch (e: Exception) {
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download progress: ${e.message}")
            emptyList()
        }
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
    }
}