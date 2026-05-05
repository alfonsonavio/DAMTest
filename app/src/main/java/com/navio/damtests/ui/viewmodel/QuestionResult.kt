package com.navio.damtests.ui.viewmodel

import com.navio.damtests.data.local.entity.Question

/**
 * Immutable snapshot of a single answered question.
 *
 * @param question           The original question entity.
 * @param userSelectedIndex  0-based index of the option the user chose (in original order).
 * @param shuffledOptions    Options as they were displayed on screen (already shuffled).
 * @param isCorrect          Whether the user's answer was correct.
 */
data class QuestionResult(
    val question: Question,
    val userSelectedIndex: Int,
    val shuffledOptions: List<String>,
    val isCorrect: Boolean
)
