package com.navio.damtests.data.topics

/**
 * Practice modes a subject can offer, worked out from the topics that have
 * questions. A subject with only PDFs so far offers none of them.
 *
 * @property hasQuestions   Smart review and favourites need any question.
 * @property hasGeneralTest The general test and the exam need "tema_" topics.
 * @property hasHalves      The 1-10 / 11-20 split needs questions on both
 *                          sides; otherwise one half would be empty or equal
 *                          to the full general test.
 */
data class PracticeOptions(
    val hasQuestions: Boolean,
    val hasGeneralTest: Boolean,
    val hasHalves: Boolean
) {
    companion object {
        val NONE = PracticeOptions(hasQuestions = false, hasGeneralTest = false, hasHalves = false)

        /** Options for a subject whose questions belong to [topicIds]. */
        fun from(topicIds: Collection<String>): PracticeOptions {
            if (topicIds.isEmpty()) return NONE
            val temaNumbers = topicIds.mapNotNull { StudyTopics.temaNumber(it) }
            return PracticeOptions(
                hasQuestions = true,
                hasGeneralTest = temaNumbers.isNotEmpty(),
                hasHalves = temaNumbers.any { it in StudyTopics.FIRST_HALF } &&
                    temaNumbers.any { it in StudyTopics.SECOND_HALF }
            )
        }
    }
}
