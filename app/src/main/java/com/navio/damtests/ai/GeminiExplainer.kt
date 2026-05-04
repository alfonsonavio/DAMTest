package com.navio.damtests.ai // Ajusta según tu paquete real

import com.google.ai.client.generativeai.GenerativeModel
import com.navio.damtests.BuildConfig
import com.navio.damtests.data.local.entity.Question // He visto que esta es tu clase base

class GeminiExplainer {
    private val generativeModel = GenerativeModel(
        modelName = "gemini-2.5-flash",
        apiKey = BuildConfig.GEMINI_API_KEY
    )

    suspend fun explicarFallo(pregunta: Question, respuestaUsuario: Int): String {
        val prompt = """
            Actúa como un profesor de DAM. Una alumna llamada Mari Carmen ha fallado una pregunta de test.
            Enunciado general: ${pregunta.contextText} ? "no tiene"
            Pregunta: ${pregunta.text}
            Opciones:
            0: ${pregunta.optionA}
            1: ${pregunta.optionB}
            2: ${pregunta.optionC}
            3: ${pregunta.optionD}
            El alumno marcó la opción $respuestaUsuario, pero la correcta es la ${pregunta.correctOptionIndex}.
            Explica de forma breve y clara por qué la respuesta correcta es esa. No te extiendas demasiado pero sé muy claro en tu respuesta,
            como si fueras un profesor de DAM.
            Al final de la respuesta, añade alguna frase de animo como si tu fueras yo, es decir por ejemplo "Animo, te amo muchisimo!" o algo por el estilo pero como si yo fuera quien habla. Pero algo muy breve como el ejemplo que te he puesto o "Tu puedes vida mia" o "Todo va a ir genial amor mio". Ella es mujer por cierto, para la hora de hablarle.
        """.trimIndent()

        return try {
            val response = generativeModel.generateContent(prompt)
            response.text ?: "No tengo una explicación disponible ahora mismo."
        } catch (e: Exception) {
            "Error al obtener explicación: ${e.localizedMessage}"
        }
    }
}