package com.navio.damtests.data.session

/**
 * A test left half-way, saved so it can be resumed later.
 *
 * Questions are stored by [com.navio.damtests.data.local.entity.Question.stableId]
 * (it survives re-syncs) in the order they were shown. Each answer keeps the
 * options exactly as they were displayed, so the review screen at the end is
 * identical to an uninterrupted test.
 */
data class QuizSession(
    val subjectId: String,
    val topicId: String,
    val questionIds: List<String>,
    val answers: List<SavedAnswer>,
    val score: Int,
    val savedAt: Long = System.currentTimeMillis()
) {
    val total: Int get() = questionIds.size
    val answeredCount: Int get() = answers.size

    /** 1-based number of the question the user will see when resuming. */
    val nextQuestionNumber: Int get() = (answeredCount + 1).coerceAtMost(total)

    fun isFor(subjectId: String, topicId: String): Boolean =
        this.subjectId == subjectId && this.topicId == topicId
}

/** One answered question of a [QuizSession]. */
data class SavedAnswer(
    val stableId: String,
    val selectedIndex: Int,
    val shuffledOptions: List<String>,
    val isCorrect: Boolean
)
