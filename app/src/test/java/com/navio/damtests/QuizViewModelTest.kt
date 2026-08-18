package com.navio.damtests

import com.navio.damtests.data.local.entity.Question
import com.navio.damtests.data.local.entity.TopicProgress
import com.navio.damtests.ui.viewmodel.QuizViewModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Unit tests for QuizViewModel.
 *
 * The ViewModel depends on QuizRepository, which we replace with a MockK fake.
 * The @get:Rule MainDispatcherRule swaps Dispatchers.Main for a test dispatcher
 * so viewModelScope coroutines run synchronously during tests.
 *
 * Because we use UnconfinedTestDispatcher inside the rule, calling
 * loadQuestions(...) completes its coroutine immediately, so right after the
 * call we can read the resulting StateFlow values with .value.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class QuizViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var repository: QuizRepository
    private lateinit var viewModel: QuizViewModel

    private fun question(
        id: Int,
        correct: Int = 0
    ) = Question(
        id = id,
        subjectId = "programacion",
        topicId = "tema_1",
        text = "Pregunta $id",
        optionA = "A$id", optionB = "B$id", optionC = "C$id", optionD = "D$id",
        correctOptionIndex = correct
    )

    @Before
    fun setUp() {
        repository = mockk(relaxed = true)
        viewModel = QuizViewModel(repository)
    }

    @Test
    fun `loadQuestions populates questions and clears loading`() = runTest {
        val questions = listOf(question(1), question(2))
        coEvery { repository.getQuestionsByTopic("programacion", "tema_1", 10) } returns questions

        viewModel.loadQuestions("programacion", "tema_1")

        assertEquals(2, viewModel.questions.value.size)
        assertFalse(viewModel.isLoading.value)
        assertEquals(0, viewModel.currentQuestionIndex.value)
        assertEquals(0, viewModel.score.value)
    }

    @Test
    fun `checkAnswer with correct answer increases score`() = runTest {
        // Question 1 has correctOptionIndex = 0, so optionA ("A1") is correct
        val q = question(1, correct = 0)
        coEvery { repository.getQuestionsByTopic(any(), any(), any()) } returns listOf(q)
        viewModel.loadQuestions("programacion", "tema_1")

        // The shuffled options shown on screen — order doesn't matter for scoring,
        // what matters is that the selected text equals the correct option's text.
        val shuffled = listOf("A1", "B1", "C1", "D1")

        viewModel.checkAnswer("A1", shuffled) // "A1" is the correct text

        assertEquals(1, viewModel.score.value)
    }

    @Test
    fun `checkAnswer with wrong answer does not increase score`() = runTest {
        val q = question(1, correct = 0) // "A1" is correct
        coEvery { repository.getQuestionsByTopic(any(), any(), any()) } returns listOf(q)
        viewModel.loadQuestions("programacion", "tema_1")

        val shuffled = listOf("A1", "B1", "C1", "D1")

        viewModel.checkAnswer("B1", shuffled) // wrong

        assertEquals(0, viewModel.score.value)
    }

    @Test
    fun `checkAnswer sets currentAnswerState with correct flag`() = runTest {
        val q = question(1, correct = 0)
        coEvery { repository.getQuestionsByTopic(any(), any(), any()) } returns listOf(q)
        viewModel.loadQuestions("programacion", "tema_1")

        viewModel.checkAnswer("A1", listOf("A1", "B1", "C1", "D1"))

        val state = viewModel.currentAnswerState.value
        assertTrue(state != null)
        assertTrue(state!!.isCorrect)
    }

    @Test
    fun `second checkAnswer on same question is ignored`() = runTest {
        val q = question(1, correct = 0)
        coEvery { repository.getQuestionsByTopic(any(), any(), any()) } returns listOf(q)
        viewModel.loadQuestions("programacion", "tema_1")

        val shuffled = listOf("A1", "B1", "C1", "D1")
        viewModel.checkAnswer("A1", shuffled)  // correct → score 1
        viewModel.checkAnswer("A1", shuffled)  // should be ignored

        // Score must still be 1, not 2 — double taps don't count twice
        assertEquals(1, viewModel.score.value)
    }

    @Test
    fun `goToNextQuestion advances index`() = runTest {
        val questions = listOf(question(1), question(2), question(3))
        coEvery { repository.getQuestionsByTopic(any(), any(), any()) } returns questions
        viewModel.loadQuestions("programacion", "tema_1")

        viewModel.checkAnswer("A1", listOf("A1", "B1", "C1", "D1"))
        viewModel.goToNextQuestion()

        assertEquals(1, viewModel.currentQuestionIndex.value)
        assertFalse(viewModel.isTestFinished.value)
    }

    @Test
    fun `goToNextQuestion on last question finishes test and saves progress`() = runTest {
        val questions = listOf(question(1))
        coEvery { repository.getQuestionsByTopic(any(), any(), any()) } returns questions
        coEvery { repository.getProgress(any(), any()) } returns null
        viewModel.loadQuestions("programacion", "tema_1")

        viewModel.checkAnswer("A1", listOf("A1", "B1", "C1", "D1"))
        viewModel.goToNextQuestion() // last question → finish

        assertTrue(viewModel.isTestFinished.value)
        // Progress should have been saved through the repository
        coVerify { repository.updateProgress(any()) }
    }

    @Test
    fun `getResults returns one result per answered question`() = runTest {
        val questions = listOf(question(1), question(2))
        coEvery { repository.getQuestionsByTopic(any(), any(), any()) } returns questions
        viewModel.loadQuestions("programacion", "tema_1")

        viewModel.checkAnswer("A1", listOf("A1", "B1", "C1", "D1"))
        viewModel.goToNextQuestion()
        viewModel.checkAnswer("B2", listOf("A2", "B2", "C2", "D2"))

        assertEquals(2, viewModel.getResults().size)
    }

    @Test
    fun `checkAnswer records question stats`() = runTest {
        val q = question(1, correct = 0)
        coEvery { repository.getQuestionsByTopic(any(), any(), any()) } returns listOf(q)
        viewModel.loadQuestions("programacion", "tema_1")

        viewModel.checkAnswer("A1", listOf("A1", "B1", "C1", "D1")) // correct

        // The ViewModel must record the outcome for smart review
        coVerify { repository.recordAnswer(q, true) }
    }
}