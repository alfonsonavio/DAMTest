package com.navio.damtests.ui.quiz

import android.os.Parcelable
import com.navio.damtests.data.local.entity.Question
import kotlinx.parcelize.Parcelize

/**
 * Immutable snapshot of a single answered question.
 *
 * Implements [Parcelable] so the full results list can be passed from
 * [com.navio.damtests.ui.quiz.QuizActivity] to [com.navio.damtests.ui.review.ReviewActivity]
 * via Intent — no global singleton needed.
 *
 * @param question           The original question entity.
 * @param userSelectedIndex  0-based index of the option the user chose (shuffled order),
 *                           or [NOT_ANSWERED] for a question left blank in an exam.
 * @param shuffledOptions    Options as displayed on screen (already shuffled).
 * @param isCorrect          Whether the user's answer was correct.
 */
@Parcelize
data class QuestionResult(
    val question: Question,
    val userSelectedIndex: Int,
    val shuffledOptions: List<String>,
    val isCorrect: Boolean
) : Parcelable {

    val isAnswered: Boolean get() = userSelectedIndex != NOT_ANSWERED

    companion object {
        const val NOT_ANSWERED = -1
    }
}
