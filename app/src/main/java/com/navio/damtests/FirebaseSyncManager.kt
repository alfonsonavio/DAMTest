package com.navio.damtests

import android.content.Context
import android.util.Log
import com.google.firebase.database.FirebaseDatabase
import com.navio.damtests.data.local.entity.Question
import kotlinx.coroutines.tasks.await

/**
 * Synchronises questions from Firebase Realtime Database to the local Room cache.
 *
 * The sync is version-based: each subject/topic pair has an integer version stored
 * under `versiones/<subjectId>/<topicId>` in Firebase. A local copy of the last
 * seen version is kept in SharedPreferences. Questions are only downloaded when
 * the remote version is newer than the local one, minimising bandwidth usage.
 */
class FirebaseSyncManager(private val context: Context, private val repository: QuizRepository) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // Uses the URL from google-services.json automatically — no hardcoding needed.
    private val database = FirebaseDatabase.getInstance().reference

    /**
     * Checks all subject/topic version numbers and downloads any that are outdated.
     * Safe to call on any coroutine dispatcher (IO-safe via Firebase's own thread pool).
     */
    suspend fun syncQuestions() {
        try {
            Log.d(TAG, "Starting version check…")
            val versionsSnapshot = database.child("versiones").get().await()

            for (subjectSnapshot in versionsSnapshot.children) {
                val subjectId = subjectSnapshot.key ?: continue
                for (topicSnapshot in subjectSnapshot.children) {
                    val topicId = topicSnapshot.key ?: continue
                    val remoteVersion = topicSnapshot.getValue(Int::class.java) ?: 0
                    val prefKey = prefKey(subjectId, topicId)
                    val localVersion = prefs.getInt(prefKey, 0)

                    Log.d(TAG, "[$subjectId/$topicId] remote=$remoteVersion local=$localVersion")

                    if (remoteVersion > localVersion) {
                        downloadTopic(subjectId, topicId, remoteVersion)
                    }
                }
            }
            Log.d(TAG, "Sync completed.")
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
                qSnap.getValue(Question::class.java)
                    ?.copy(subjectId = subjectId, topicId = topicId)
            }

            if (questionsList.isNotEmpty()) {
                repository.updateTopicQuestions(subjectId, topicId, questionsList)
                prefs.edit().putInt(prefKey(subjectId, topicId), newVersion).apply()
                Log.d(TAG, "[$subjectId/$topicId] Downloaded ${questionsList.size} questions (v$newVersion).")
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
