package com.navio.damtests.ui.quiz

import android.content.Context
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.navio.damtests.R
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
            .setTitle(R.string.resume_test_title)
            .setMessage(context.getString(R.string.resume_test_message, session.nextQuestionNumber, session.total))
            .setPositiveButton(R.string.resume_test_continue) { _, _ ->
                start(context, subjectId, topicId, resume = true)
            }
            .setNegativeButton(R.string.resume_test_restart) { _, _ ->
                sessionStore.clear()
                start(context, subjectId, topicId, resume = false)
            }
            .show()
    }

    fun start(context: Context, subjectId: String, topicId: String, resume: Boolean) {
        context.startActivity(QuizActivity.intent(context, subjectId, topicId, resume))
    }
}
