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
     * @param pregunta      The question that was answered incorrectly.
     * @param selectedText  Text of the option the student chose. The options are
     *   shuffled on screen, so the position the student tapped does NOT match
     *   the order (optionA..D) used in the prompt; the text is mapped back here.
     */
    suspend fun explicarFallo(pregunta: Question, selectedText: String): String {
        val options = listOf(pregunta.optionA, pregunta.optionB, pregunta.optionC, pregunta.optionD)
        val respuestaUsuario = options.indexOf(selectedText)
        val contexto = if (!pregunta.contextText.isNullOrEmpty())
            "Enunciado/Contexto del caso:\n${pregunta.contextText}\n\n"
        else ""

        val prompt = """
            Actúa como un profesor experto en DAM (Desarrollo de Aplicaciones Multiplataforma).
            Un alumno ha respondido incorrectamente la siguiente pregunta de test.

            ${contexto}Pregunta: ${pregunta.text}

            Opciones:
            0: ${pregunta.optionA}
            1: ${pregunta.optionB}
            2: ${pregunta.optionC}
            3: ${pregunta.optionD}

            El alumno marcó la opción $respuestaUsuario, pero la respuesta correcta es la opción ${pregunta.correctOptionIndex}.

            Explica de forma breve y clara:
            1. Por qué la respuesta correcta (opción ${pregunta.correctOptionIndex}) es la correcta.
            2. Por qué la opción elegida ($respuestaUsuario) es incorrecta.

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
}