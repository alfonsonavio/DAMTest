package com.navio.damtests

import com.navio.damtests.auth.AuthManager
import com.navio.damtests.auth.UserProgressRepository
import com.navio.damtests.data.local.entity.Question
import com.navio.damtests.data.local.entity.QuestionsDao
import com.navio.damtests.data.local.entity.TopicProgress
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for QuizRepository.
 *
 * QuizRepository now depends on three things — QuestionsDao, AuthManager and
 * UserProgressRepository — all injected. We mock all three so no real database
 * or Firebase is touched.
 */
class QuizRepositoryTest {

    private lateinit var dao: QuestionsDao
    private lateinit var authManager: AuthManager
    private lateinit var userProgressRepository: UserProgressRepository
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
        authManager = mockk(relaxed = true)
        userProgressRepository = mockk(relaxed = true)
        repository = QuizRepository(dao, authManager, userProgressRepository)
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

        val result = repository.getQuestionsForRange("programacion", 1, 3, 20)

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
    fun `updateTopicQuestions deletes old and inserts new`() = runTest {
        val questions = listOf(question(1), question(2))

        repository.updateTopicQuestions("programacion", "tema_1", questions)

        coVerify(exactly = 1) { dao.deleteQuestionsByTopic("programacion", "tema_1") }
        coVerify(exactly = 1) { dao.insertQuestions(questions) }
    }

    @Test
    fun `updateProgress saves to dao and to cloud when user is logged in`() = runTest {
        val progress = TopicProgress("programacion", "tema_1", 8, 10, 1)
        // Simulate a logged-in user
        every { authManager.currentUid } returns "user123"

        repository.updateProgress(progress)

        // Saved locally...
        coVerify(exactly = 1) { dao.saveProgress(progress) }
        // ...and synced to the cloud with the user's uid
        coVerify(exactly = 1) { userProgressRepository.saveTopicProgress("user123", progress) }
    }

    @Test
    fun `updateProgress saves only to dao when no user is logged in`() = runTest {
        val progress = TopicProgress("programacion", "tema_1", 8, 10, 1)
        // No logged-in user
        every { authManager.currentUid } returns null

        repository.updateProgress(progress)

        coVerify(exactly = 1) { dao.saveProgress(progress) }
        // Cloud sync must NOT happen
        coVerify(exactly = 0) { userProgressRepository.saveTopicProgress(any(), any()) }
    }

    @Test
    fun `getUniqueTopicsForSubject always adds three general test entries`() = runTest {
        coEvery { dao.getUniqueTopicIds("programacion") } returns listOf("tema_1", "tema_2")

        val topics = repository.getUniqueTopicsForSubject("programacion")

        val ids = topics.map { it.id }
        assertTrue(ids.contains("-1"))
        assertTrue(ids.contains("-2"))
        assertTrue(ids.contains("-3"))
    }

    @Test
    fun `getUniqueTopicsForSubject merges db topics and pdf topics`() = runTest {
        coEvery { dao.getUniqueTopicIds("programacion") } returns listOf("tema_1")
        val pdfTopics = setOf("tema_1", "tema_20")

        val topics = repository.getUniqueTopicsForSubject("programacion", pdfTopics)

        val ids = topics.map { it.id }
        assertTrue(ids.contains("tema_1"))
        assertTrue(ids.contains("tema_20"))
    }
}