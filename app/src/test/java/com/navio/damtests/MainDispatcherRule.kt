package com.navio.damtests

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * JUnit rule that replaces the Main dispatcher with a test dispatcher.
 *
 * WHY THIS EXISTS:
 * QuizViewModel launches coroutines with viewModelScope, which uses
 * Dispatchers.Main under the hood. In a plain unit test there is no Android
 * main looper, so those coroutines would crash. This rule swaps Main for a
 * test dispatcher before each test and restores it afterwards.
 *
 * Using UnconfinedTestDispatcher means launched coroutines run eagerly and
 * synchronously, so by the time the function under test returns, the coroutine
 * work is already done — which keeps the tests simple to read.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    private val dispatcher: TestDispatcher = UnconfinedTestDispatcher()
) : TestWatcher() {

    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}