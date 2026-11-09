package com.navio.damtests.ai

import com.navio.damtests.data.local.entity.Question

/**
 * Prompt of the review's "Ver explicación", shared by every AI provider so
 * Gemini and the Groq fallback explain the same thing in the same way.
 *
 * The options are given exactly as the student saw them (shuffled, lettered
 * a–d like on the review screen), so the explanation refers to the same
 * letters the student sees instead of the internal option order.
 */
object ExplanationPrompt {

    private val LETTERS = listOf("a", "b", "c", "d")

    /**
     * @param shownOptions  The four options in the order shown on screen.
     * @param selectedIndex Position (0-3) the student chose, or a negative
     *                      value for a question left blank in an exam.
     */
    fun build(question: Question, shownOptions: List<String>, selectedIndex: Int): String {
        val correctText = listOf(question.optionA, question.optionB, question.optionC, question.optionD)
            .getOrElse(question.correctOptionIndex) { question.optionA }
        val correct = letter(shownOptions.indexOf(correctText))
        val answered = selectedIndex >= 0

        return buildString {
            appendLine("Actúa como un profesor experto en DAM (Desarrollo de Aplicaciones Multiplataforma).")
            appendLine("Un alumno no ha acertado la siguiente pregunta de test.")
            appendLine()
            if (!question.contextText.isNullOrEmpty()) {
                appendLine("Enunciado/Contexto del caso:")
                appendLine(question.contextText)
                appendLine()
            }
            appendLine("Pregunta: ${question.text}")
            appendLine()
            appendLine("Opciones (en el orden en que las ve el alumno):")
            shownOptions.forEachIndexed { i, text -> appendLine("${letter(i)}) $text") }
            appendLine()
            if (answered) {
                val chosen = letter(selectedIndex)
                appendLine("El alumno marcó la opción $chosen), pero la respuesta correcta es la opción $correct).")
                appendLine()
                appendLine("Explica de forma breve y clara:")
                appendLine("1. Por qué la respuesta correcta, la $correct), es la correcta.")
                appendLine("2. Por qué la opción elegida, la $chosen), es incorrecta.")
            } else {
                // A blank exam answer only needs the correct option explained
                appendLine("El alumno dejó la pregunta en blanco. La respuesta correcta es la opción $correct).")
                appendLine()
                appendLine("Explica de forma breve y clara:")
                appendLine("1. Por qué la respuesta correcta, la $correct), es la correcta.")
                appendLine("2. Qué distingue a las otras opciones de la correcta.")
            }
            appendLine()
            appendLine("Cuando nombres una opción, usa siempre su letra (a, b, c o d) tal y como aparece arriba; nunca uses números.")
            appendLine("Sé pedagógico y conciso. No uses listas con guiones ni formato Markdown, solo texto plano.")
            append("Termina con una frase de ánimo breve.")
        }
    }

    private fun letter(index: Int) = LETTERS.getOrElse(index) { "?" }
}