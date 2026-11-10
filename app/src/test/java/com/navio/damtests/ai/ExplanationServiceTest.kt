package com.navio.damtests.ai

import com.navio.damtests.data.local.entity.Question
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExplanationServiceTest {

    private val question = Question(
        stableId = "q1",
        text = "¿Qué es una clase?",
        optionA = "Un molde",
        optionB = "Un objeto",
        optionC = "Un método",
        optionD = "Un paquete",
        correctOptionIndex = 0
    )
    private val options = listOf("Un objeto", "Un molde", "Un método", "Un paquete")

    private var primaryCalls = 0
    private var fallbackCalls = 0

    private fun service(
        primary: (suspend (String) -> String)?,
        fallback: suspend (String) -> String = {
            fallbackCalls++
            "groq"
        }
    ): ExplanationService {
        // Counts the calls to the primary provider
        val countedPrimary: (suspend (String) -> String)? = primary?.let { p ->
            val wrapped: suspend (String) -> String = { prompt ->
                primaryCalls++
                p(prompt)
            }
            wrapped
        }
        return ExplanationService(
            primary = countedPrimary,
            fallback = fallback,
            onError = { _, _ -> },
            onInfo = {}
        )
    }

    private val busy = IllegalStateException(
        """Unexpected Response: {"error": {"code": 503, "status": "UNAVAILABLE"}}"""
    )

    @Test
    fun `gemini answer is used when it works`() = runTest {
        val result = service(primary = { "gemini" }).explain(question, options, 0)

        assertEquals("gemini", result)
        assertEquals(0, fallbackCalls)
    }

    @Test
    fun `a busy gemini is retried once before giving up on it`() = runTest {
        var calls = 0
        val result = service(primary = { if (++calls == 1) throw busy else "gemini" })
            .explain(question, options, 0)

        assertEquals("gemini", result)
        assertEquals(2, primaryCalls)
        assertEquals(0, fallbackCalls)
    }

    @Test
    fun `groq answers when gemini stays busy`() = runTest {
        val result = service(primary = { throw busy }).explain(question, options, 0)

        assertEquals("groq", result)
        assertEquals(2, primaryCalls)
        assertEquals(1, fallbackCalls)
    }

    @Test
    fun `a slow gemini goes straight to groq after the time limit`() = runTest {
        val result = service(primary = { awaitCancellation() }).explain(question, options, 0)

        assertEquals("groq", result)
        assertEquals(1, primaryCalls)
        assertEquals(ExplanationService.ATTEMPT_TIMEOUT_MS, currentTime)
    }

    @Test
    fun `a time limit hidden inside the Gemini SDK error goes to groq without retry`() = runTest {
        // The Gemini SDK reports our time limit as its own exception
        val result = service(primary = {
            throw IllegalStateException("Something unexpected happened.", CancellationException("Timed out"))
        }).explain(question, options, 0)

        assertEquals("groq", result)
        assertEquals(1, primaryCalls)
    }

    @Test
    fun `closing the dialog stops everything, even when the SDK wraps the cancellation`() = runTest {
        val explainer = service(primary = {
            try {
                awaitCancellation()
            } catch (e: CancellationException) {
                throw IllegalStateException("Something unexpected happened.", e)
            }
        })
        var result: String? = "not finished"

        val job = launch { result = explainer.explain(question, options, 0) }
        advanceTimeBy(1_000)
        job.cancel()
        advanceUntilIdle()

        assertEquals("not finished", result)
        assertEquals(0, fallbackCalls)
    }

    @Test
    fun `other gemini errors are not retried`() = runTest {
        val result = service(primary = { throw IllegalArgumentException("API key not valid") })
            .explain(question, options, 0)

        assertEquals("groq", result)
        assertEquals(1, primaryCalls)
    }

    @Test
    fun `without a gemini key groq is used directly`() = runTest {
        assertEquals("groq", service(primary = null).explain(question, options, 0))
    }

    @Test
    fun `null when every provider fails`() = runTest {
        val result = service(primary = { throw busy }, fallback = { error("no network") })
            .explain(question, options, 0)

        assertNull(result)
    }

    @Test
    fun `busy errors are recognised through their causes but timeouts in ms are not`() {
        assertTrue(ExplanationService.isBusy(RuntimeException("wrapper", busy)))
        assertTrue(ExplanationService.isBusy(RuntimeException("This model is currently experiencing high demand")))
        assertFalse(ExplanationService.isBusy(RuntimeException("socket_timeout=15000 ms")))
    }
}
