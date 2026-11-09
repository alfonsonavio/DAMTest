package com.navio.damtests.data.exam

/**
 * Rules of the exam mode: which topics it covers, how many questions it can
 * have and how long it lasts.
 *
 * The topic list shows a single "Modo examen" row ([TOPIC_ID]); each finished
 * exam is recorded with the attempt id of its [ExamRange], so the history can
 * tell an exam of every topic from one of topics 1-10.
 */
object Exam {

    /** Topic list row that opens the exam setup. */
    const val TOPIC_ID = "-6"

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

    /** True for the topic list row and every exam attempt id. */
    fun isExamTopic(topicId: String): Boolean =
        topicId == TOPIC_ID || ExamRange.fromAttemptTopicId(topicId) != null
}

/** Topics an exam draws its questions from. */
enum class ExamRange(val attemptTopicId: String, val label: String) {
    ALL("-6", "Todos los temas"),
    FIRST_HALF("-7", "Temas 1-10"),
    SECOND_HALF("-8", "Temas 11-20");

    companion object {
        fun fromAttemptTopicId(topicId: String): ExamRange? =
            entries.firstOrNull { it.attemptTopicId == topicId }
    }
}