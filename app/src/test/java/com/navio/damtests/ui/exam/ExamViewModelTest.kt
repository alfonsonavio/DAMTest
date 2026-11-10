package com.navio.damtests.ui.exam

import com.navio.damtests.data.exam.ExamClock
import com.navio.damtests.data.exam.ExamRange
import com.navio.damtests.data.local.entity.Question
import com.navio.damtests.data.quiz.QuizRepository
import com.navio.damtests.testing.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ExamViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var repository: QuizRepository
    private lateinit var clock: ExamClock
    private lateinit var viewModel: ExamViewModel
    private var now = 0L

    private fun question(id: Int) = Question(
        id = id,
        stableId = "q$id",
        subjectId = "programacion",
        topicId = "tema_1",
        text = "Pregunta $id",
        optionA = "A$id", optionB = "B$id", optionC = "C$id", optionD = "D$id",
        correctOptionIndex = 0
    )

    @Before
    fun setUp() {
        repository = mockk(relaxed = true)
        clock = mockk()
        every { clock.now() } answers { now }
        viewModel = ExamViewModel(repository, clock)
    }

    /** Starts an exam with [count] questions and returns how to pick the correct option. */
    private fun startExam(count: Int, penalized: Boolean = false) {
        coEvery { repository.getExamQuestions("programacion", ExamRange.ALL, count) } returns
                (1..count).map { question(it) }
        viewModel.start("programacion", ExamRange.ALL, count, penalized)
    }

    private fun correctIndexOfCurrent(): Int {
        val current = viewModel.state.value.current!!
        return current.options.indexOf(current.correctOption)
    }

    private fun wrongIndexOfCurrent(): Int = (correctIndexOfCurrent() + 1) % 4

    @Test
    fun `starting loads the questions and gives a minute and a half per question`() = runTest {
        startExam(10)

        val state = viewModel.state.value
        assertFalse(state.loading)
        assertEquals(10, state.total)
        assertEquals(15 * 60L, state.remainingSeconds)
    }

    @Test
    fun `tapping the selected option again leaves the question blank`() = runTest {
        startExam(2)

        viewModel.selectOption(1)
        assertEquals(1, viewModel.state.value.answers[0])
        viewModel.selectOption(1)
        assertNull(viewModel.state.value.answers[0])
    }

    @Test
    fun `answers are kept when moving between questions`() = runTest {
        startExam(3)

        viewModel.selectOption(2)
        viewModel.next()
        viewModel.toggleFlag()
        viewModel.previous()

        val state = viewModel.state.value
        assertEquals(0, state.currentIndex)
        assertEquals(2, state.answers[0])
        assertEquals(setOf(1), state.flagged)
    }

    @Test
    fun `submitting grades correct, wrong and blank answers`() = runTest {
        startExam(4, penalized = true)

        viewModel.selectOption(correctIndexOfCurrent())   // q1 correct
        viewModel.next()
        viewModel.selectOption(correctIndexOfCurrent())   // q2 correct
        viewModel.next()
        viewModel.selectOption(wrongIndexOfCurrent())     // q3 wrong, q4 blank
        viewModel.submit()

        val grade = viewModel.outcome.value!!.grade
        assertEquals(2, grade.correct)
        assertEquals(1, grade.wrong)
        assertEquals(1, grade.blank)
        // (2 - 1/3) / 4 * 10 = 4.17
        assertEquals(4.17, grade.grade, 0.0)
    }

    @Test
    fun `a finished exam is recorded as a test and blank answers are not counted as seen`() = runTest {
        startExam(2)

        viewModel.selectOption(correctIndexOfCurrent())
        viewModel.submit()

        coVerify(exactly = 1) { repository.recordTestAttempt("programacion", "-6", 1, 2) }
        coVerify(exactly = 1) { repository.recordAnswer(match { it.stableId == "q1" }, true) }
        coVerify(exactly = 0) { repository.recordAnswer(match { it.stableId == "q2" }, any()) }
        coVerify(exactly = 0) { repository.updateProgress(any()) }
    }

    @Test
    fun `the exam is handed in automatically when the time runs out`() = runTest {
        startExam(10)
        viewModel.selectOption(correctIndexOfCurrent())

        now += 15 * 60 * 1000L
        viewModel.refreshTimer()

        val outcome = viewModel.outcome.value
        assertNotNull(outcome)
        assertTrue(outcome!!.timeUp)
        assertEquals(1, outcome.grade.correct)
        assertEquals(9, outcome.grade.blank)
    }

    @Test
    fun `nothing changes after handing in and it is recorded only once`() = runTest {
        startExam(2)
        viewModel.submit()

        viewModel.selectOption(0)
        viewModel.submit()

        assertTrue(viewModel.state.value.answers.isEmpty())
        coVerify(exactly = 1) { repository.recordTestAttempt(any(), any(), any(), any()) }
    }

    @Test
    fun `blank questions reach the review as not answered`() = runTest {
        startExam(1)
        viewModel.submit()

        val result = viewModel.outcome.value!!.results.single()
        assertFalse(result.isAnswered)
        assertFalse(result.isCorrect)
    }

    @Test
    fun `an exam without questions reports it`() = runTest {
        coEvery { repository.getExamQuestions(any(), any(), any()) } returns emptyList()

        viewModel.start("programacion", ExamRange.ALL, 20, false)

        assertTrue(viewModel.state.value.noQuestions)
    }
}