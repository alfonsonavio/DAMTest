package com.navio.damtests.ai

import android.util.Log
import com.navio.damtests.core.RemoteConfigManager
import com.navio.damtests.data.local.entity.Question
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeout

/**
 * Gets the review explanation without leaving the user waiting forever:
 *
 *  1. Asks Gemini, waiting at most [attemptTimeoutMs].
 *  2. If Gemini is busy (503 "high demand", 5xx), waits [retryDelayMs] and asks
 *     once more. A timeout is not retried: a slow model will be slow again.
 *  3. If Gemini still fails, asks the fallback (Groq, the fast model of the
 *     instant feedback) with the same prompt.
 *  4. Returns null only when both fail, so the screen shows its error message.
 *
 * Cancelling the caller (closing the dialog) cancels the request in flight
 * and stops there: no retry, no fallback, nothing logged as an error.
 * Providers are plain functions so the policy can be tested without network.
 *
 * Logcat (tag "ExplanationService") says which provider answered.
 */
class ExplanationService(
    private val primary: (suspend (String) -> String)?,
    private val fallback: suspend (String) -> String,
    private val attemptTimeoutMs: Long = ATTEMPT_TIMEOUT_MS,
    private val retryDelayMs: Long = RETRY_DELAY_MS,
    private val onError: (String, Throwable?) -> Unit = { msg, e -> Log.w(TAG, msg, e) },
    private val onInfo: (String) -> Unit = { msg -> Log.i(TAG, msg) }
) {

    suspend fun explain(question: Question, shownOptions: List<String>, selectedIndex: Int): String? {
        val prompt = ExplanationPrompt.build(question, shownOptions, selectedIndex)
        primary?.let { generate ->
            tryPrimary(generate, prompt)?.let {
                onInfo("Explanation by Gemini")
                return it
            }
        }
        return attempt("Groq fallback failed") { fallback(prompt) }.getOrNull()
            ?.also { onInfo("Explanation by Groq (fallback)") }
    }

    private suspend fun tryPrimary(generate: suspend (String) -> String, prompt: String): String? {
        for (attemptNumber in 1..PRIMARY_ATTEMPTS) {
            val result = attempt("Gemini failed (attempt $attemptNumber)") { generate(prompt) }
            result.getOrNull()?.let { return it }
            val error = result.exceptionOrNull()
            if (attemptNumber == PRIMARY_ATTEMPTS || error == null || !isBusy(error)) return null
            delay(retryDelayMs)
        }
        return null
    }

    /**
     * Runs one request with the time limit. Cancellation of the caller is
     * rethrown. The Gemini SDK wraps cancellation in its own exception, so
     * after any error we first check whether the caller was cancelled (the
     * dialog was closed) and, if it is still active, a wrapped cancellation
     * can only be our own time limit.
     */
    private suspend fun attempt(errorMessage: String, block: suspend () -> String): Result<String> =
        try {
            Result.success(withTimeout(attemptTimeoutMs) { block() })
        } catch (e: TimeoutCancellationException) {
            onError("$errorMessage: no answer in $attemptTimeoutMs ms", null)
            Result.failure(e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            if (isCancellation(e)) {
                onError("$errorMessage: no answer in $attemptTimeoutMs ms", null)
            } else {
                onError(errorMessage, e)
            }
            Result.failure(e)
        }

    companion object {
        private const val TAG = "ExplanationService"
        const val ATTEMPT_TIMEOUT_MS = 20_000L
        const val RETRY_DELAY_MS = 1_500L
        private const val PRIMARY_ATTEMPTS = 2

        /** HTTP 500/502/503 as whole numbers (not inside "15000 ms") or Google's busy texts. */
        private val BUSY = Regex("""\b50[023]\b|UNAVAILABLE|high demand|overloaded""", RegexOption.IGNORE_CASE)

        /** True for "server busy" errors, which usually pass in a moment. */
        fun isBusy(error: Throwable): Boolean =
            generateSequence(error) { it.cause }.take(5).any { BUSY.containsMatchIn(it.message.orEmpty()) }

        private fun isCancellation(error: Throwable): Boolean =
            generateSequence(error) { it.cause }.take(5).any { it is java.util.concurrent.CancellationException }

        /** Gemini first (when its key is configured), Groq as the fallback. */
        fun create(fastExplainer: FastExplainer): ExplanationService {
            val geminiKey = RemoteConfigManager.getString(RemoteConfigManager.KEY_GEMINI)
            val gemini = geminiKey.takeIf { it.isNotBlank() }?.let { GeminiExplainer(it) }
            return ExplanationService(
                primary = gemini?.let { it::generate },
                fallback = fastExplainer::generate
            )
        }
    }
}
