package com.navio.damtests

import com.navio.damtests.ai.FastExplainer
import com.navio.damtests.data.local.entity.Question
import com.navio.damtests.data.local.entity.TopicProgress
import com.navio.damtests.data.session.QuizSession
import com.navio.damtests.data.session.QuizSessionStore
import com.navio.damtests.data.session.SavedAnswer
import com.navio.damtests.ui.viewmodel.QuizViewModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.verify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
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
    private lateinit var sessionStore: QuizSessionStore
    private lateinit var fastExplainer: FastExplainer
    private lateinit var viewModel: QuizViewModel

    private fun question(
        id: Int,
        correct: Int = 0
    ) = Question(
        id = id,
        stableId = "q$id",
        subjectId = "programacion",
        topicId = "tema_1",
        text = "Pregunta $id",
        optionA = "A$id", optionB = "B$id", optionC = "C$id", optionD = "D$id",
        correctOptionIndex = correct
    )

    @Before
    fun setUp() {
        repository = mockk(relaxed = true)
        sessionStore = mockk(relaxed = true)
        fastExplainer = mockk(relaxed = true)
        viewModel = QuizViewModel(repository, sessionStore, fastExplainer)
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

    @Test
    fun `finishing a normal test records an attempt in history`() = runTest {
        val questions = listOf(question(1))
        coEvery { repository.getQuestionsByTopic(any(), any(), any()) } returns questions
        coEvery { repository.getProgress(any(), any()) } returns null
        viewModel.loadQuestions("programacion", "tema_1")

        viewModel.checkAnswer("A1", listOf("A1", "B1", "C1", "D1"))
        viewModel.goToNextQuestion() // finishes

        coVerify { repository.recordTestAttempt("programacion", "tema_1", any(), any()) }
    }

    @Test
    fun `finishing a test records the attempt before saving progress`() = runTest {
        val questions = listOf(question(1))
        coEvery { repository.getQuestionsByTopic(any(), any(), any()) } returns questions
        coEvery { repository.getProgress(any(), any()) } returns null
        viewModel.loadQuestions("programacion", "tema_1")

        viewModel.checkAnswer("A1", listOf("A1", "B1", "C1", "D1"))
        viewModel.goToNextQuestion() // finishes

        coVerifyOrder {
            repository.recordTestAttempt("programacion", "tema_1", any(), any())
            repository.updateProgress(any())
        }
    }

    @Test
    fun `attempt is recorded even if saving progress never completes`() = runTest {
        // Simulates the old bug: the progress save hangs (slow or offline cloud)
        // and the user leaves the screen. The attempt must already be recorded.
        val questions = listOf(question(1))
        coEvery { repository.getQuestionsByTopic(any(), any(), any()) } returns questions
        coEvery { repository.getProgress(any(), any()) } returns null
        coEvery { repository.updateProgress(any()) } coAnswers { awaitCancellation() }
        viewModel.loadQuestions("programacion", "tema_1")

        viewModel.checkAnswer("A1", listOf("A1", "B1", "C1", "D1"))
        viewModel.goToNextQuestion() // finishes; progress save hangs

        coVerify(exactly = 1) { repository.recordTestAttempt("programacion", "tema_1", any(), any()) }
    }

    @Test
    fun `finishing smart review does NOT record an attempt`() = runTest {
        val questions = listOf(question(1))
        coEvery { repository.getSmartReviewQuestions(any(), any()) } returns questions
        viewModel.loadQuestions("programacion", "-4") // smart review

        viewModel.checkAnswer("A1", listOf("A1", "B1", "C1", "D1"))
        viewModel.goToNextQuestion() // finishes

        // Smart review is practice-only — no attempt, no progress
        coVerify(exactly = 0) { repository.recordTestAttempt(any(), any(), any(), any()) }
    }

    // --- Resuming a test in progress ---

    private fun savedSession(answered: Int, score: Int) = QuizSession(
        subjectId = "programacion",
        topicId = "tema_1",
        questionIds = listOf("q1", "q2", "q3"),
        answers = (1..answered).map { SavedAnswer("q$it", 0, listOf("A$it", "B$it", "C$it", "D$it"), true) },
        score = score
    )

    @Test
    fun `checkAnswer saves the test in progress`() = runTest {
        coEvery { repository.getQuestionsByTopic(any(), any(), any()) } returns listOf(question(1), question(2))
        viewModel.loadQuestions("programacion", "tema_1")

        viewModel.checkAnswer("A1", listOf("A1", "B1", "C1", "D1"))

        verify {
            sessionStore.save(match {
                it.topicId == "tema_1" && it.questionIds == listOf("q1", "q2") &&
                    it.answeredCount == 1 && it.score == 1
            })
        }
    }

    @Test
    fun `finishing the test clears the saved session`() = runTest {
        coEvery { repository.getQuestionsByTopic(any(), any(), any()) } returns listOf(question(1))
        viewModel.loadQuestions("programacion", "tema_1")

        viewModel.checkAnswer("A1", listOf("A1", "B1", "C1", "D1"))
        viewModel.goToNextQuestion()

        verify { sessionStore.clear() }
    }

    @Test
    fun `resume restores position, score and answers`() = runTest {
        every { sessionStore.load() } returns savedSession(answered = 2, score = 2)
        coEvery { repository.getQuestionsInOrder(listOf("q1", "q2", "q3")) } returns
            listOf(question(1), question(2), question(3))

        viewModel.loadQuestions("programacion", "tema_1", resume = true)

        assertEquals(3, viewModel.questions.value.size)
        assertEquals(2, viewModel.currentQuestionIndex.value)
        assertEquals(2, viewModel.score.value)
        assertEquals(2, viewModel.getResults().size)
        assertFalse(viewModel.isTestFinished.value)
        coVerify(exactly = 0) { repository.getQuestionsByTopic(any(), any(), any()) }
    }

    @Test
    fun `resume starts a new test when saved questions are gone`() = runTest {
        every { sessionStore.load() } returns savedSession(answered = 1, score = 1)
        coEvery { repository.getQuestionsInOrder(any()) } returns null
        coEvery { repository.getQuestionsByTopic(any(), any(), any()) } returns listOf(question(7))

        viewModel.loadQuestions("programacion", "tema_1", resume = true)

        assertEquals(listOf("q7"), viewModel.questions.value.map { it.stableId })
        assertEquals(0, viewModel.currentQuestionIndex.value)
        verify { sessionStore.clear() }
    }

    @Test
    fun `resume of a test with every question answered finishes it`() = runTest {
        every { sessionStore.load() } returns savedSession(answered = 3, score = 3)
        coEvery { repository.getQuestionsInOrder(any()) } returns
            listOf(question(1), question(2), question(3))
        coEvery { repository.getProgress(any(), any()) } returns null

        viewModel.loadQuestions("programacion", "tema_1", resume = true)

        assertTrue(viewModel.isTestFinished.value)
        coVerify { repository.updateProgress(any()) }
    }

    // --- Stable question state (survives leaving the app / rotating) ---

    @Test
    fun `current question keeps the same option order until the next question`() = runTest {
        coEvery { repository.getQuestionsByTopic(any(), any(), any()) } returns listOf(question(1), question(2))
        viewModel.loadQuestions("programacion", "tema_1")

        val first = viewModel.currentQuestion.value!!
        assertEquals(0, first.index)
        assertEquals(setOf("A1", "B1", "C1", "D1"), first.options.toSet())

        // Answering does not reshuffle the question on screen
        viewModel.checkAnswer(first.options[0], first.options)
        assertEquals(first.options, viewModel.currentQuestion.value!!.options)

        viewModel.goToNextQuestion()
        val second = viewModel.currentQuestion.value!!
        assertEquals(1, second.index)
        assertEquals(setOf("A2", "B2", "C2", "D2"), second.options.toSet())
    }

    @Test
    fun `resumed question shows the options exactly as they were answered`() = runTest {
        every { sessionStore.load() } returns savedSession(answered = 2, score = 2)
        coEvery { repository.getQuestionsInOrder(any()) } returns listOf(question(1), question(2), question(3))

        viewModel.loadQuestions("programacion", "tema_1", resume = true)

        assertEquals(2, viewModel.currentQuestion.value!!.index)
        assertEquals(listOf("A1", "B1", "C1", "D1"), viewModel.getResults()[0].shuffledOptions)
    }

    @Test
    fun `wrong answer asks the AI once and correct answer never does`() = runTest {
        coEvery { repository.getQuestionsByTopic(any(), any(), any()) } returns listOf(question(1), question(2))
        coEvery { fastExplainer.explicarRapido(any(), any(), any()) } returns "Mal | Bien"
        viewModel.loadQuestions("programacion", "tema_1")

        viewModel.checkAnswer("B1", listOf("A1", "B1", "C1", "D1")) // wrong
        viewModel.checkAnswer("C1", listOf("A1", "B1", "C1", "D1")) // ignored

        coVerify(exactly = 1) { fastExplainer.explicarRapido("Pregunta 1", "B1", "A1") }
        assertEquals(QuizViewModel.AiFeedback.Ready("Mal", "Bien"), viewModel.aiFeedback.value)

        viewModel.goToNextQuestion()
        viewModel.checkAnswer("A2", listOf("A2", "B2", "C2", "D2")) // correct
        coVerify(exactly = 1) { fastExplainer.explicarRapido(any(), any(), any()) }
    }

    @Test
    fun `moving to the next question clears the AI feedback`() = runTest {
        coEvery { repository.getQuestionsByTopic(any(), any(), any()) } returns listOf(question(1), question(2))
        coEvery { fastExplainer.explicarRapido(any(), any(), any()) } returns "Mal | Bien"
        viewModel.loadQuestions("programacion", "tema_1")

        viewModel.checkAnswer("B1", listOf("A1", "B1", "C1", "D1"))
        viewModel.goToNextQuestion()

        assertEquals(null, viewModel.aiFeedback.value)
    }

    @Test
    fun `AI failure is reported as an error state`() = runTest {
        coEvery { repository.getQuestionsByTopic(any(), any(), any()) } returns listOf(question(1))
        coEvery { fastExplainer.explicarRapido(any(), any(), any()) } throws RuntimeException("offline")
        viewModel.loadQuestions("programacion", "tema_1")

        viewModel.checkAnswer("B1", listOf("A1", "B1", "C1", "D1"))

        assertEquals(QuizViewModel.AiFeedback.Error, viewModel.aiFeedback.value)
    }

    @Test
    fun `a test with no questions is reported instead of loading forever`() = runTest {
        coEvery { repository.getQuestionsForRange(any(), any(), any(), any()) } returns emptyList()

        viewModel.loadQuestions("sistemas", "-3")

        assertTrue(viewModel.noQuestions.value)
        assertFalse(viewModel.isLoading.value)
    }

    @Test
    fun `a general test is saved under its own id, not its first question's topic`() = runTest {
        coEvery { repository.getRandomQuestionsForGeneralTest(any(), any()) } returns listOf(question(1))
        coEvery { repository.getProgress(any(), any()) } returns null
        viewModel.loadQuestions("programacion", "-1")

        viewModel.checkAnswer("A1", listOf("A1", "B1", "C1", "D1"))
        viewModel.goToNextQuestion() // finishes

        coVerify { repository.recordTestAttempt("programacion", "-1", any(), any()) }
        coVerify { repository.updateProgress(match { it.topicId == "-1" }) }
    }
}
