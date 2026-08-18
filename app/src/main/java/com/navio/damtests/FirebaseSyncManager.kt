package com.navio.damtests

import android.content.Context
import android.util.Log
import com.google.firebase.database.FirebaseDatabase
import com.navio.damtests.data.local.entity.Question
import kotlinx.coroutines.tasks.await

/**
 * Synchronises questions from Firebase Realtime Database to the local Room cache.
 *
 * Version-based sync: each subject/topic has an integer version under
 * `versiones/<subjectId>/<topicId>`. The last seen version is cached in
 * SharedPreferences, so topics are only downloaded when the remote version is newer.
 *
 * IMPORTANT: the local question cache (Room) and the version cache
 * (SharedPreferences) can fall out of sync — e.g. after a destructive Room
 * migration that wipes questions but leaves the version prefs intact. To recover,
 * a topic is also re-downloaded when its version matches but it has zero questions
 * locally.
 */
class FirebaseSyncManager(private val context: Context, private val repository: QuizRepository) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val database = FirebaseDatabase.getInstance().reference

    suspend fun syncQuestions() {
        try {
            val versionsSnapshot = database.child("versiones").get().await()

            var downloaded = 0
            var upToDate = 0

            for (subjectSnapshot in versionsSnapshot.children) {
                val subjectId = subjectSnapshot.key ?: continue
                for (topicSnapshot in subjectSnapshot.children) {
                    val topicId = topicSnapshot.key ?: continue
                    val remoteVersion = topicSnapshot.getValue(Int::class.java) ?: 0
                    val localVersion = prefs.getInt(prefKey(subjectId, topicId), 0)

                    // Re-download if version is newer OR if the local cache is empty
                    // (covers the case where Room was wiped but prefs survived).
                    val needsDownload = remoteVersion > localVersion ||
                            !repository.hasQuestions(subjectId, topicId)

                    if (needsDownload) {
                        downloadTopic(subjectId, topicId, remoteVersion)
                        downloaded++
                    } else {
                        upToDate++
                    }
                }
            }
            Log.d(TAG, "Sync completed: $downloaded downloaded, $upToDate up to date.")
        } catch (e: Exception) {
            Log.e(TAG, "Sync failed: ${e.message}")
        }
    }

    private suspend fun downloadTopic(subjectId: String, topicId: String, newVersion: Int) {
        try {
            val questionsSnapshot = database
                .child("preguntas")
                .child(subjectId)
                .child(topicId)
                .get().await()

            val questionsList = questionsSnapshot.children.mapNotNull { qSnap ->
                val firebaseKey = qSnap.key ?: return@mapNotNull null
                qSnap.getValue(Question::class.java)
                    ?.copy(
                        subjectId = subjectId,
                        topicId = topicId,
                        stableId = "${subjectId}_${topicId}_$firebaseKey"
                    )
            }

            if (questionsList.isNotEmpty()) {
                repository.updateTopicQuestions(subjectId, topicId, questionsList)
                prefs.edit().putInt(prefKey(subjectId, topicId), newVersion).apply()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading [$subjectId/$topicId]: ${e.message}")
        }
    }

    private fun prefKey(subjectId: String, topicId: String) = "version_${subjectId}_$topicId"

    companion object {
        private const val TAG = "FirebaseSyncManager"
        private const val PREFS_NAME = "sync_prefs"
    }
}