package com.navio.damtests.ai

import com.aallam.openai.api.chat.ChatCompletionRequest
import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.api.chat.ChatRole
import com.aallam.openai.api.http.Timeout
import com.aallam.openai.api.logging.LogLevel
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.LoggingConfig
import com.aallam.openai.client.OpenAI
import com.aallam.openai.client.OpenAIHost
import com.navio.damtests.RemoteConfigManager
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds

/**
 * Provides immediate in-quiz AI feedback using Groq.
 * The model name is read from Remote Config ([RemoteConfigManager.KEY_GROQ_MODEL]).
 *
 * Returns two explanations separated by '|':
 *   Left  → why the chosen option is wrong (2-3 sentences)
 *   Right → why the correct option is right (2-3 sentences)
 *
 * One HTTP client is kept for the whole app (rebuilt only if the API key
 * changes in Remote Config) instead of a new, never-closed client per answer.
 * Request logging is off: it printed every request in release builds.
 */
@Singleton
class FastExplainer @Inject constructor() {

    private var client: OpenAI? = null
    private var clientKey: String? = null

    @Synchronized
    private fun clientFor(apiKey: String): OpenAI {
        client?.takeIf { clientKey == apiKey }?.let { return it }
        client?.close()
        return OpenAI(
            token   = apiKey,
            host    = OpenAIHost("https://api.groq.com/openai/v1/"),
            logging = LoggingConfig(logLevel = LogLevel.None),
            timeout = Timeout(socket = 30.seconds)
        ).also {
            client = it
            clientKey = apiKey
        }
    }

    suspend fun explicarRapido(pregunta: String, elegida: String, correcta: String): String {
        val apiKey = RemoteConfigManager.getString(RemoteConfigManager.KEY_GROQ)
        // No key yet (Remote Config not fetched): report it as a normal failure
        // so the user sees the friendly error, not a developer message.
        check(apiKey.isNotBlank()) { "groq_api_key missing in Remote Config" }

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

        val response = clientFor(apiKey).chatCompletion(
            ChatCompletionRequest(
                model    = ModelId(
                    RemoteConfigManager.getStringOr(
                        RemoteConfigManager.KEY_GROQ_MODEL,
                        RemoteConfigManager.DEFAULT_GROQ_MODEL
                    )
                ),
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
