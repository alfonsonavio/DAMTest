package com.navio.damtests.auth

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.navio.damtests.data.session.QuizSessionStore
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Carries the test in progress across a logout: it is backed up to the cloud
 * when the user signs out and restored the next time they sign in.
 *
 * Firestore structure: users/{uid}/backup/quizSession  { json, savedAt }
 */
@Singleton
class SessionBackupRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val sessionStore: QuizSessionStore
) {

    private fun ref(uid: String) =
        firestore.collection("users").document(uid).collection("backup").document("quizSession")

    /** Uploads the saved test (or deletes the old backup if there is none) and waits for the server. */
    suspend fun backup(uid: String) {
        val json = sessionStore.exportJson()
        if (json == null) {
            ref(uid).delete().await()
        } else {
            ref(uid).set(mapOf("json" to json, "savedAt" to System.currentTimeMillis())).await()
        }
    }

    /**
     * Restores the backed-up test, if any, and removes it from the cloud so a
     * finished test can't come back later on another device.
     */
    suspend fun restore(uid: String) {
        try {
            val doc = ref(uid).get(Source.SERVER).await()
            val json = doc.getString("json") ?: return
            if (sessionStore.exportJson() == null) sessionStore.importJson(json)
            ref(uid).delete()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restore saved test: ${e.message}")
        }
    }

    private companion object {
        const val TAG = "SessionBackupRepo"
    }
}