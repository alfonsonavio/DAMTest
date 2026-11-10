package com.navio.damtests.data.catalog

import com.navio.damtests.data.quiz.TestMode

/**
 * Human-readable names for topic ids, shared by every screen that shows a
 * topic outside its own list (Home, Statistics…), so internal ids such as
 * "caso_1" or "repaso_1" never reach the user.
 */
object TopicNames {

    fun displayName(topicId: String): String = TestMode.fromTopicId(topicId)?.let(::displayName) ?: when {
        topicId.startsWith("tema_")   -> "Tema ${topicId.removePrefix("tema_")}"
        topicId.startsWith("caso_")   -> "Caso práctico ${topicId.removePrefix("caso_")}"
        topicId.startsWith("repaso_") -> "Repaso final"
        else -> topicId
    }

    private fun displayName(mode: TestMode): String = when (mode) {
        TestMode.GENERAL_ALL         -> "Test general (todo)"
        TestMode.GENERAL_FIRST_HALF  -> "Test general (temas 1-10)"
        TestMode.GENERAL_SECOND_HALF -> "Test general (temas 11-20)"
        TestMode.SMART_REVIEW        -> "Repaso inteligente"
        TestMode.FAVORITES           -> "Preguntas favoritas"
        TestMode.EXAM_ALL            -> "Examen (todo)"
        TestMode.EXAM_FIRST_HALF     -> "Examen (temas 1-10)"
        TestMode.EXAM_SECOND_HALF    -> "Examen (temas 11-20)"
    }
}