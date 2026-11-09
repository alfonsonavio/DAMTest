package com.navio.damtests.ai

import com.navio.damtests.data.local.entity.Question
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExplanationPromptTest {

    private val question = Question(
        stableId = "q1", text = "¿Qué es una clase?",
        optionA = "Un molde", optionB = "Un objeto", optionC = "Un método", optionD = "Un paquete",
        correctOptionIndex = 0
    )
    // As shown on screen: the correct one ("Un molde") is option b)
    private val shown = listOf("Un objeto", "Un molde", "Un método", "Un paquete")

    @Test
    fun `options are lettered as the student saw them`() {
        val prompt = ExplanationPrompt.build(question, shown, selectedIndex = 0)

        assertTrue(prompt.contains("a) Un objeto"))
        assertTrue(prompt.contains("b) Un molde"))
        assertTrue(prompt.contains("marcó la opción a), pero la respuesta correcta es la opción b)"))
    }

    @Test
    fun `a blank answer only asks to explain the correct option`() {
        val prompt = ExplanationPrompt.build(question, shown, selectedIndex = -1)

        assertTrue(prompt.contains("dejó la pregunta en blanco"))
        assertFalse(prompt.contains("marcó la opción"))
    }

    @Test
    fun `case context is included when the question has it`() {
        val prompt = ExplanationPrompt.build(question.copy(contextText = "Empresa X"), shown, 0)

        assertTrue(prompt.contains("Enunciado/Contexto del caso:\nEmpresa X"))
    }
}