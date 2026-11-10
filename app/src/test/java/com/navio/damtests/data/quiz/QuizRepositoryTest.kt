package com.navio.damtests.data.quiz

import com.navio.damtests.data.catalog.TopicNames
import com.navio.damtests.data.exam.ExamRange
import com.navio.damtests.data.local.dao.QuestionStatsDao
import com.navio.damtests.data.local.dao.QuestionsDao
import com.navio.damtests.data.local.entity.Question
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Unit tests for QuizRepository, with mocked DAOs (no real database). */
class QuizRepositoryTest {

    private lateinit var dao: QuestionsDao
    private lateinit var statsDao: QuestionStatsDao
    private lateinit var repository: QuizRepository

    private fun question(id: Int, topic: String = "tema_1") = Question(
        id = id,
        subjectId = "programacion",
        topicId = topic,
        text = "Pregunta $id",
        optionA = "A", optionB = "B", optionC = "C", optionD = "D",
        correctOptionIndex = 0
    )

    @Before
    fun setUp() {
        dao = mockk(relaxed = true)
        statsDao = mockk(relaxed = true)
        repository = QuizRepository(dao, statsDao)
    }

    @Test
    fun `getQuestionsByTopic returns questions from dao`() = runTest {
        val expected = listOf(question(1), question(2))
        coEvery { dao.getRandomQuestionsForTopic("programacion", "tema_1", 10) } returns expected

        val result = repository.getQuestionsByTopic("programacion", "tema_1", 10)

        assertEquals(expected, result)
    }

    @Test
    fun `getQuestionsForRange builds correct topic id list`() = runTest {
        val expected = listOf(question(1))
        coEvery {
            dao.getRandomQuestionsForTopicList("programacion", listOf("tema_1", "tema_2", "tema_3"), 20)
        } returns expected

        val result = repository.getQuestionsForRange("programacion", 1..3, 20)

        assertEquals(expected, result)
        coVerify {
            dao.getRandomQuestionsForTopicList("programacion", listOf("tema_1", "tema_2", "tema_3"), 20)
        }
    }

    @Test
    fun `hasQuestions returns true when dao has at least one question`() = runTest {
        coEvery { dao.getRandomQuestionsForTopic("programacion", "tema_5", 1) } returns listOf(question(1))

        val result = repository.hasQuestions("programacion", "tema_5")

        assertTrue(result)
    }

    @Test
    fun `hasQuestions returns false when dao is empty`() = runTest {
        coEvery { dao.getRandomQuestionsForTopic("programacion", "tema_20", 1) } returns emptyList()

        val result = repository.hasQuestions("programacion", "tema_20")

        assertFalse(result)
    }

    @Test
    fun `updateTopicQuestions replaces the topic's questions in one transaction`() = runTest {
        val questions = listOf(question(1), question(2))

        repository.updateTopicQuestions("programacion", "tema_1", questions)

        coVerify(exactly = 1) { dao.replaceTopicQuestions("programacion", "tema_1", questions) }
    }

    @Test
    fun `study topics merge db and pdf topics in list order`() = runTest {
        coEvery { dao.getUniqueTopicIds("programacion") } returns listOf("tema_10", "caso_1", "tema_2")

        val topics = repository.getStudyTopics("programacion", setOf("tema_2", "tema_20", "repaso_1"))

        assertEquals(listOf("tema_2", "tema_10", "tema_20", "caso_1", "repaso_1"), topics.map { it.id })
        assertEquals(TopicNames.displayName("tema_10"), topics[1].title)
    }

    @Test
    fun `practice options come from the topics with questions`() = runTest {
        coEvery { dao.getUniqueTopicIds("programacion") } returns listOf("tema_1", "tema_12")

        val options = repository.getPracticeOptions("programacion")

        assertTrue(options.hasGeneralTest)
        assertTrue(options.hasHalves)
    }

    @Test
    fun `exam of topics 11-20 counts and draws only those topics`() = runTest {
        val secondHalf = (11..20).map { "tema_$it" }
        coEvery { dao.countQuestionsForTopicList("programacion", secondHalf) } returns 25
        coEvery { dao.getRandomQuestionsForTopicList("programacion", secondHalf, 20) } returns
            (1..20).map { question(it) }

        assertEquals(25, repository.countExamQuestions("programacion", ExamRange.SECOND_HALF))
        assertEquals(20, repository.getExamQuestions("programacion", ExamRange.SECOND_HALF, 20).size)
    }
}
