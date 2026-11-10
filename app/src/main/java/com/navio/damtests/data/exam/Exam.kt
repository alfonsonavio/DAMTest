package com.navio.damtests.data.exam

import com.navio.damtests.data.quiz.TestMode

/**
 * Rules of the exam mode: which topics it covers, how many questions it can
 * have and how long it lasts.
 *
 * Each finished exam is recorded with the test mode of its [ExamRange], so
 * the history can tell an exam of every topic from one of topics 1-10.
 */
object Exam {

    /** Time allowed per question: 1.5 minutes. */
    const val SECONDS_PER_QUESTION = 90L

    /** Exam sizes offered, when the topics have enough questions. */
    val SIZES = listOf(10, 20, 30, 40)

    /**
     * Sizes that fit in [available] questions. If there are fewer than the
     * smallest size, the only option is an exam with every question available.
     */
    fun availableSizes(available: Int): List<Int> {
        if (available <= 0) return emptyList()
        return SIZES.filter { it <= available }.ifEmpty { listOf(available) }
    }

    fun durationSeconds(questions: Int): Long = questions * SECONDS_PER_QUESTION

    /** "15 min", or "10 min 30 s" for an odd number of questions. */
    fun durationLabel(questions: Int): String {
        val seconds = durationSeconds(questions)
        val minutes = seconds / 60
        val rest    = seconds % 60
        return if (rest == 0L) "$minutes min" else "$minutes min $rest s"
    }
}

/** Topics an exam draws its questions from. */
enum class ExamRange(val mode: TestMode, val label: String) {
    ALL(TestMode.EXAM_ALL, "Todos los temas"),
    FIRST_HALF(TestMode.EXAM_FIRST_HALF, "Temas 1-10"),
    SECOND_HALF(TestMode.EXAM_SECOND_HALF, "Temas 11-20");

    companion object {
        /** The range of an exam attempt, or null if [topicId] is not an exam. */
        fun fromTopicId(topicId: String): ExamRange? = entries.firstOrNull { it.mode.topicId == topicId }
    }
}