package com.navio.damtests.data.exam

import androidx.annotation.StringRes
import com.navio.damtests.R
import com.navio.damtests.core.UiText
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

    /** Size selected when the setup opens, if the topics can fill it. */
    const val DEFAULT_SIZE = 20

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
    fun durationLabel(questions: Int): UiText {
        val seconds = durationSeconds(questions)
        val minutes = seconds / SECONDS_PER_MINUTE
        val rest    = seconds % SECONDS_PER_MINUTE
        return if (rest == 0L) UiText.Res(R.string.exam_duration_minutes, listOf(minutes))
        else UiText.Res(R.string.exam_duration_minutes_seconds, listOf(minutes, rest))
    }

    private const val SECONDS_PER_MINUTE = 60L
}

/** Topics an exam draws its questions from. */
enum class ExamRange(val mode: TestMode, @StringRes val labelRes: Int) {
    ALL(TestMode.EXAM_ALL, R.string.exam_range_all),
    FIRST_HALF(TestMode.EXAM_FIRST_HALF, R.string.exam_range_first_half),
    SECOND_HALF(TestMode.EXAM_SECOND_HALF, R.string.exam_range_second_half);

    companion object {
        /** The range of an exam attempt, or null if [topicId] is not an exam. */
        fun fromTopicId(topicId: String): ExamRange? = entries.firstOrNull { it.mode.topicId == topicId }
    }
}