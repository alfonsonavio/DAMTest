package com.navio.damtests.data.sync

import android.content.Context
import android.util.Log
import com.google.firebase.database.FirebaseDatabase
import com.navio.damtests.core.suspendRunCatching
import com.navio.damtests.data.local.entity.Question
import com.navio.damtests.data.quiz.QuizRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
        _syncing.value = true
        try {
            suspendRunCatching { downloadOutdatedTopics() }
                .onFailure { Log.e(TAG, "Sync failed: ${it.message}") }
        } finally {
            _syncing.value = false
        }
    }

    private suspend fun downloadOutdatedTopics() {
        val versionsSnapshot = database.child("versiones").get().await()
        val topics = versionsSnapshot.children.flatMap { subjectSnapshot ->
            val subjectId = subjectSnapshot.key ?: return@flatMap emptyList<RemoteTopic>()
            subjectSnapshot.children.mapNotNull { topicSnapshot ->
                val topicId = topicSnapshot.key ?: return@mapNotNull null
                RemoteTopic(subjectId, topicId, version = topicSnapshot.getValue(Int::class.java) ?: 0)
            }
        }

        val outdated = topics.filter { isOutdated(it) }
        outdated.forEach { downloadTopic(it) }
        Log.d(TAG, "Sync completed: ${outdated.size} downloaded, ${topics.size - outdated.size} up to date.")
    }

    /**
     * True if the remote version is newer OR the local cache is empty (covers
     * the case where Room was wiped but the version prefs survived).
     */
    private suspend fun isOutdated(topic: RemoteTopic): Boolean =
        topic.version > prefs.getInt(prefKey(topic.subjectId, topic.topicId), 0) ||
            !repository.hasQuestions(topic.subjectId, topic.topicId)

    private suspend fun downloadTopic(topic: RemoteTopic) {
        val (subjectId, topicId) = topic
        suspendRunCatching {
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
                prefs.edit().putInt(prefKey(subjectId, topicId), topic.version).apply()
            }
        }.onFailure { Log.e(TAG, "Error downloading [$subjectId/$topicId]: ${it.message}") }
    }

    /** A topic listed under `versiones`, with its current version. */
    private data class RemoteTopic(val subjectId: String, val topicId: String, val version: Int)

    private fun prefKey(subjectId: String, topicId: String) = "version_${subjectId}_$topicId"

    companion object {
        private const val TAG = "FirebaseSyncManager"

        private val _syncing = MutableStateFlow(false)

        /**
         * True while questions are being downloaded. On a first launch the
         * local cache is still empty, so screens use this to wait for the
         * download instead of reporting a subject as "coming soon".
         */
        val syncing: StateFlow<Boolean> = _syncing
        private const val PREFS_NAME = "sync_prefs"
    }
}
