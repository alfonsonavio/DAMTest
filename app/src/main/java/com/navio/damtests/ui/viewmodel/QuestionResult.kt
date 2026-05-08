package com.navio.damtests.ui.viewmodel

import android.os.Parcelable
import com.navio.damtests.data.local.entity.Question
import kotlinx.parcelize.Parcelize

/**
 * Immutable snapshot of a single answered question.
 *
 * Implements [Parcelable] so the full results list can be passed from
 * [com.navio.damtests.QuizActivity] to [com.navio.damtests.ReviewActivity]
 * via Intent — no global singleton needed.
 *
 * @param question           The original question entity.
 * @param userSelectedIndex  0-based index of the option the user chose (shuffled order).
 * @param shuffledOptions    Options as displayed on screen (already shuffled).
 * @param isCorrect          Whether the user's answer was correct.
 */
@Parcelize
data class QuestionResult(
    val question: Question,
    val userSelectedIndex: Int,
    val shuffledOptions: List<String>,
    val isCorrect: Boolean
) : Parcelable
