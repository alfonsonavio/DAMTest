package com.navio.damtests.data

import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.navio.damtests.BuildConfig
import com.navio.damtests.data.local.entity.Question
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sends "this question is wrong" reports to Cloud Firestore (collection
 * `reports`), to be reviewed from the Firebase console. Not awaited: Firestore
 * queues the write and sends it when there is a connection.
 *
 * Reports carry no user id: they are about the content, not the person.
 */
@Singleton
class ReportRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {

    fun reportQuestion(question: Question, reason: String, comment: String) {
        val data = mapOf(
            "stableId"     to question.stableId,
            "subjectId"    to question.subjectId,
            "topicId"      to question.topicId,
            "questionText" to question.text,
            "reason"       to reason,
            "comment"      to comment,
            "appVersion"   to BuildConfig.VERSION_NAME,
            "createdAt"    to FieldValue.serverTimestamp()
        )
        firestore.collection("reports").add(data)
            .addOnFailureListener { e -> Log.e(TAG, "Failed to send report: ${e.message}") }
    }

    private companion object {
        const val TAG = "ReportRepository"
    }
}
