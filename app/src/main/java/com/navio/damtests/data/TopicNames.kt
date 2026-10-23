package com.navio.damtests.data

/**
 * Human-readable names for topic ids, shared by every screen that shows a
 * topic outside its own list (Home, Statistics…), so internal ids such as
 * "caso_1" or "repaso_1" never reach the user.
 */
object TopicNames {

    fun displayName(topicId: String): String = when {
        topicId == "-1" -> "Test general (todo)"
        topicId == "-2" -> "Test general (temas 1-10)"
        topicId == "-3" -> "Test general (temas 11-20)"
        topicId == "-4" -> "Repaso inteligente"
        topicId.startsWith("tema_")   -> "Tema ${topicId.removePrefix("tema_")}"
        topicId.startsWith("caso_")   -> "Caso práctico ${topicId.removePrefix("caso_")}"
        topicId.startsWith("repaso_") -> "Repaso final"
        else -> topicId
    }
}
