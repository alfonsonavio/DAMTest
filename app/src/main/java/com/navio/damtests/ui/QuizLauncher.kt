package com.navio.damtests.ui

import android.content.Context
import android.content.Intent
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.navio.damtests.QuizActivity
import com.navio.damtests.data.session.QuizSessionStore

/**
 * Single way to open a test, so every entry point treats a saved test the
 * same: if the user opens the test they left half-way, they are asked whether
 * to continue it or start again.
 */
object QuizLauncher {

    fun open(context: Context, sessionStore: QuizSessionStore, subjectId: String, topicId: String) {
        val session = sessionStore.load()?.takeIf { it.isFor(subjectId, topicId) }
        if (session == null) {
            start(context, subjectId, topicId, resume = false)
            return
        }

        MaterialAlertDialogBuilder(context)
            .setTitle("Tienes este test a medias")
            .setMessage("Vas por la pregunta ${session.nextQuestionNumber} de ${session.total}.")
            .setPositiveButton("Seguir") { _, _ ->
                start(context, subjectId, topicId, resume = true)
            }
            .setNegativeButton("Empezar de nuevo") { _, _ ->
                sessionStore.clear()
                start(context, subjectId, topicId, resume = false)
            }
            .show()
    }

    fun start(context: Context, subjectId: String, topicId: String, resume: Boolean) {
        context.startActivity(
            Intent(context, QuizActivity::class.java)
                .putExtra("SUBJECT_ID", subjectId)
                .putExtra("TOPIC_ID", topicId)
                .putExtra(QuizActivity.EXTRA_RESUME, resume)
        )
    }
}
