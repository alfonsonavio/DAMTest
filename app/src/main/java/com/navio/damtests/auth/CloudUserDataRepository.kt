package com.navio.damtests.auth

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Deletes everything a user has stored in Cloud Firestore, for account
 * deletion. Waits for the server to confirm each deletion and throws if it
 * can't, so the caller never reports a deletion that didn't happen.
 */
@Singleton
class CloudUserDataRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {

    suspend fun deleteAll(uid: String) {
        val userDoc = firestore.collection("users").document(uid)
        for (name in USER_COLLECTIONS) {
            val docs = userDoc.collection(name).get(Source.SERVER).await().documents
            docs.chunked(MAX_BATCH_WRITES).forEach { chunk ->
                val batch = firestore.batch()
                chunk.forEach { batch.delete(it.reference) }
                batch.commit().await()
            }
        }
        userDoc.delete().await()
    }

    private companion object {
        /** Every subcollection the app writes under users/{uid}. */
        val USER_COLLECTIONS = listOf("progress", "attempts", "questionStats", "backup")
        const val MAX_BATCH_WRITES = 500 // Firestore limit per batch
    }
}
