package com.navio.damtests.data.quiz

/**
 * Tests that are not a single topic: general tests, smart review, favourites
 * and exams. Each one has a fixed topic id that is stored with attempts,
 * progress and saved sessions (Room and Firestore), so the ids must never
 * change.
 */
enum class TestMode(val topicId: String) {
    GENERAL_ALL(Ids.GENERAL_ALL),
    GENERAL_FIRST_HALF(Ids.GENERAL_FIRST_HALF),
    GENERAL_SECOND_HALF(Ids.GENERAL_SECOND_HALF),
    SMART_REVIEW(Ids.SMART_REVIEW),
    FAVORITES(Ids.FAVORITES),
    EXAM_ALL(Ids.EXAM_ALL),
    EXAM_FIRST_HALF(Ids.EXAM_FIRST_HALF),
    EXAM_SECOND_HALF(Ids.EXAM_SECOND_HALF);

    /** Practice only: no score or topic progress, ignored by the statistics. */
    val isPractice: Boolean get() = this == SMART_REVIEW || this == FAVORITES

    val isExam: Boolean get() = this == EXAM_ALL || this == EXAM_FIRST_HALF || this == EXAM_SECOND_HALF

    /**
     * The ids as compile-time constants, for the places that need one
     * (Room queries). Everywhere else, use the enum.
     */
    object Ids {
        const val GENERAL_ALL = "-1"
        const val GENERAL_FIRST_HALF = "-2"
        const val GENERAL_SECOND_HALF = "-3"
        const val SMART_REVIEW = "-4"
        const val FAVORITES = "-5"
        const val EXAM_ALL = "-6"
        const val EXAM_FIRST_HALF = "-7"
        const val EXAM_SECOND_HALF = "-8"
    }

    companion object {
        /** The mode of [topicId], or null for a regular topic ("tema_3", "caso_1"…). */
        fun fromTopicId(topicId: String): TestMode? = entries.firstOrNull { it.topicId == topicId }
    }
}
