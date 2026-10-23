package com.navio.damtests.ai

import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.navio.damtests.RemoteConfigManager
import com.navio.damtests.data.local.entity.Question

/**
 * Generates AI-powered explanations for incorrectly answered questions
 * using Gemini. The model name is read from Remote Config
 * ([RemoteConfigManager.KEY_GEMINI_MODEL]).
 *
 * The API key is injected at construction time and sourced from
 * [com.navio.damtests.RemoteConfigManager] — never hardcoded or stored in source control.
 *
 * @param apiKey  Gemini API key retrieved from Firebase Remote Config.
 */
class GeminiExplainer(apiKey: String) {

    private val generativeModel = GenerativeModel(
        modelName = RemoteConfigManager.getStringOr(
            RemoteConfigManager.KEY_GEMINI_MODEL,
            RemoteConfigManager.DEFAULT_GEMINI_MODEL
        ),
        apiKey = apiKey
    )

    /**
     * Returns a short, clear explanation of why the correct answer is correct
     * and why the student's selected answer was wrong.
     *
     * The options are given exactly as the student saw them (shuffled, lettered
     * a–d like on the review screen), so the explanation refers to the same
     * letters the student sees instead of the internal option order.
     *
     * @param pregunta      The question that was answered incorrectly.
     * @param shownOptions  The four options in the order shown on screen.
     * @param selectedIndex Position (0-3) in [shownOptions] the student chose.
     */
    suspend fun explicarFallo(pregunta: Question, shownOptions: List<String>, selectedIndex: Int): String {
        val correctText = listOf(pregunta.optionA, pregunta.optionB, pregunta.optionC, pregunta.optionD)
            .getOrElse(pregunta.correctOptionIndex) { pregunta.optionA }
        val correctIndex = shownOptions.indexOf(correctText)
        val letter = { index: Int -> LETTERS.getOrElse(index) { "?" } }

        val contexto = if (!pregunta.contextText.isNullOrEmpty())
            "Enunciado/Contexto del caso:\n${pregunta.contextText}\n\n"
        else ""

        val opciones = shownOptions.mapIndexed { i, text -> "${letter(i)}) $text" }.joinToString("\n            ")

        val prompt = """
            Actúa como un profesor experto en DAM (Desarrollo de Aplicaciones Multiplataforma).
            Un alumno ha respondido incorrectamente la siguiente pregunta de test.

            ${contexto}Pregunta: ${pregunta.text}

            Opciones (en el orden en que las ve el alumno):
            $opciones

            El alumno marcó la opción ${letter(selectedIndex)}), pero la respuesta correcta es la opción ${letter(correctIndex)}).

            Explica de forma breve y clara:
            1. Por qué la respuesta correcta, la ${letter(correctIndex)}), es la correcta.
            2. Por qué la opción elegida, la ${letter(selectedIndex)}), es incorrecta.

            Cuando nombres una opción, usa siempre su letra (a, b, c o d) tal y como aparece arriba; nunca uses números.
            Sé pedagógico y conciso. No uses listas con guiones ni formato Markdown, solo texto plano.
            Termina con una frase de ánimo breve.
        """.trimIndent()

        return try {
            val response = generativeModel.generateContent(prompt)
            response.text ?: "No hay explicación disponible en este momento."
        } catch (e: Exception) {
            Log.e("GeminiExplainer", "generateContent failed", e)
            "No se ha podido generar la explicación ahora mismo. Comprueba tu conexión e inténtalo de nuevo."
        }
    }

    private companion object {
        val LETTERS = listOf("a", "b", "c", "d")
    }
}