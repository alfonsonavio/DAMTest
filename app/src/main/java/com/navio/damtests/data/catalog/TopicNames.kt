package com.navio.damtests.data.catalog

import com.navio.damtests.R
import com.navio.damtests.core.UiText
import com.navio.damtests.data.quiz.TestMode

/**
 * Human-readable names for topic ids, shared by every screen that shows a
 * topic, so internal ids such as "caso_1" or "repaso_1" never reach the user.
 */
object TopicNames {

    private const val TEMA = "tema_"
    private const val CASO = "caso_"
    private const val REPASO = "repaso_"

    fun displayName(topicId: String): UiText {
        TestMode.fromTopicId(topicId)?.let { return UiText.Res(nameRes(it)) }
        return when {
            topicId.startsWith(TEMA) -> UiText.Res(R.string.topic_tema, listOf(topicId.removePrefix(TEMA)))
            topicId.startsWith(CASO) -> UiText.Res(R.string.topic_case, listOf(topicId.removePrefix(CASO)))
            topicId.startsWith(REPASO) -> UiText.Res(R.string.topic_final_review)
            else -> UiText.Raw(topicId)
        }
    }

    private fun nameRes(mode: TestMode): Int = when (mode) {
        TestMode.GENERAL_ALL -> R.string.mode_general_all
        TestMode.GENERAL_FIRST_HALF -> R.string.mode_general_first_half
        TestMode.GENERAL_SECOND_HALF -> R.string.mode_general_second_half
        TestMode.SMART_REVIEW -> R.string.mode_smart_review
        TestMode.FAVORITES -> R.string.mode_favorites
        TestMode.EXAM_ALL -> R.string.mode_exam_all
        TestMode.EXAM_FIRST_HALF -> R.string.mode_exam_first_half
        TestMode.EXAM_SECOND_HALF -> R.string.mode_exam_second_half
    }
}
