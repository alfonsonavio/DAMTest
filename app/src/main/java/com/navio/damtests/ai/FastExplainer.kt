package com.navio.damtests.ai

import com.aallam.openai.api.chat.ChatCompletionRequest
import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.api.chat.ChatRole
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.OpenAI
import com.aallam.openai.client.OpenAIHost
import com.navio.damtests.RemoteConfigManager

/**
 * Provides immediate in-quiz AI feedback using Groq (llama-3.3-70b-versatile).
 *
 * Returns two explanations separated by '|':
 *   Left  → why the chosen option is wrong (2-3 sentences)
 *   Right → why the correct option is right (2-3 sentences)
 */
class FastExplainer {

    suspend fun explicarRapido(pregunta: String, elegida: String, correcta: String): String {
        val apiKey = RemoteConfigManager.getString(RemoteConfigManager.KEY_GROQ)
        if (apiKey.isBlank()) return "Clave de IA no disponible | Configura groq_api_key en Remote Config"

        val client = OpenAI(
            host  = OpenAIHost("https://api.groq.com/openai/v1/"),
            token = apiKey
        )

        val systemPrompt = """
            Eres un profesor de informática de grado superior explicando a un alumno por qué falló una pregunta de test.
            
            Responde SIEMPRE en este formato exacto, separado por el símbolo |:
            [Por qué la opción elegida es incorrecta] | [Por qué la opción correcta es la correcta]
            
            Reglas:
            - Cada parte debe tener entre 2 y 4 frases cortas.
            - Explica el concepto técnico real, no digas solo "es incorrecta" o "es correcta".
            - Usa lenguaje claro y directo, como si hablaras con el alumno.
            - No uses markdown, ni asteriscos, ni listas. Solo texto plano.
            - El separador | debe aparecer exactamente una vez.
        """.trimIndent()

        val userContent = """
            Pregunta: $pregunta
            Opción elegida por el alumno: $elegida
            Opción correcta: $correcta
        """.trimIndent()

        val response = client.chatCompletion(
            ChatCompletionRequest(
                model    = ModelId("llama-3.3-70b-versatile"),
                messages = listOf(
                    ChatMessage(role = ChatRole.System, content = systemPrompt),
                    ChatMessage(role = ChatRole.User,   content = userContent)
                ),
                temperature = 0.4
            )
        )
        return response.choices.first().message.content
            ?: "No se pudo generar explicación | Inténtalo de nuevo"
    }
}