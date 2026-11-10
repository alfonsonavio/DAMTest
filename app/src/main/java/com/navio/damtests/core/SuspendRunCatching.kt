package com.navio.damtests.core

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * [runCatching] for suspend code: turns any failure of [block] into a
 * [Result], but never hides a cancellation of the caller.
 *
 * Plain `runCatching` (or `catch (e: Exception)`) also catches the
 * CancellationException a coroutine gets when its screen closes, so the
 * work goes on as if it had only "failed". Here, after any error, the
 * caller's coroutine is checked first: if it was cancelled, the
 * cancellation goes on up. A `withTimeout` inside [block] that runs out is
 * a failure of the block, not of the caller, so it comes back as a failure.
 *
 * This is the one place of the app that catches every exception: the
 * Firebase, Credential Manager and AI SDKs don't document what they throw.
 */
@Suppress("TooGenericExceptionCaught")
suspend inline fun <T> suspendRunCatching(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: Exception) {
        currentCoroutineContext().ensureActive()
        Result.failure(e)
    }
