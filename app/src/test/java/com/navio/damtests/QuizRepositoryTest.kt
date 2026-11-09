package com.navio.damtests

import com.navio.damtests.auth.AuthManager
import com.navio.damtests.auth.StatsSyncRepository
import com.navio.damtests.auth.UserProgressRepository
import com.navio.damtests.data.exam.ExamRange
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
    private lateinit var statsSyncRepository: StatsSyncRepository
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
        statsSyncRepository = mockk(relaxed = true)
        repository = QuizRepository(dao, authManager, userProgressRepository, statsSyncRepository)
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
    fun `subject with up to 10 topics only gets the full general test`() = runTest {
        coEvery { dao.getUniqueTopicIds("sistemas") } returns listOf("tema_1", "tema_2", "tema_10")

        val ids = repository.getUniqueTopicsForSubject("sistemas").map { it.id }

        assertTrue(ids.contains("-1"))
        assertTrue(ids.contains("-4"))
        assertFalse(ids.contains("-2"))
        assertFalse(ids.contains("-3"))
    }

    @Test
    fun `subject with topics on both halves gets the 1-10 and 11-20 tests`() = runTest {
        coEvery { dao.getUniqueTopicIds("programacion") } returns listOf("tema_1", "tema_12")

        val ids = repository.getUniqueTopicsForSubject("programacion").map { it.id }

        assertTrue(ids.containsAll(listOf("-1", "-2", "-3", "-4")))
    }

    @Test
    fun `subject without tema topics gets no general test`() = runTest {
        coEvery { dao.getUniqueTopicIds("ipe") } returns listOf("caso_1")

        val ids = repository.getUniqueTopicsForSubject("ipe").map { it.id }

        assertFalse(ids.contains("-1"))
        assertTrue(ids.contains("-4"))
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

    @Test
    fun `getUniqueTopicsForSubject with only pdfs lists no general tests`() = runTest {
        coEvery { dao.getUniqueTopicIds("acceso_datos") } returns emptyList()

        val topics = repository.getUniqueTopicsForSubject("acceso_datos", setOf("tema_1"))

        assertEquals(listOf("tema_1"), topics.map { it.id })
    }

    // --- Statistics cloud sync ---

    @Test
    fun `recordTestAttempt saves locally and uploads when user is logged in`() = runTest {
        every { authManager.currentUid } returns "user123"

        repository.recordTestAttempt("programacion", "tema_1", score = 7, totalQuestions = 10)

        coVerify(exactly = 1) { dao.insertTestAttempt(match { it.subjectId == "programacion" && it.score == 7 }) }
        coVerify(exactly = 1) { statsSyncRepository.saveAttempt("user123", match { it.topicId == "tema_1" }) }
    }

    @Test
    fun `recordTestAttempt does not upload when no user is logged in`() = runTest {
        every { authManager.currentUid } returns null

        repository.recordTestAttempt("programacion", "tema_1", score = 7, totalQuestions = 10)

        coVerify(exactly = 1) { dao.insertTestAttempt(any()) }
        coVerify(exactly = 0) { statsSyncRepository.saveAttempt(any(), any()) }
    }

    @Test
    fun `recordAnswer uploads the updated question stats when user is logged in`() = runTest {
        every { authManager.currentUid } returns "user123"
        coEvery { dao.getQuestionStats("programacion_tema_1_p1") } returns null
        val q = question(1).copy(stableId = "programacion_tema_1_p1")

        repository.recordAnswer(q, wasCorrect = false)

        coVerify(exactly = 1) {
            statsSyncRepository.saveQuestionStats("user123", match {
                it.stableId == "programacion_tema_1_p1" && it.timesSeen == 1 && it.timesWrong == 1
            })
        }
    }

    // --- Study streak ---

    @Test
    fun `getStreak counts consecutive study days from the attempt history`() = runTest {
        // Noon of today and the two previous days, in the device's time zone
        val zone = java.time.ZoneId.systemDefault()
        val today = java.time.LocalDate.now(zone)
        fun noon(daysAgo: Long) =
            today.minusDays(daysAgo).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
        val now = noon(0)
        coEvery { dao.getAttemptTimestamps() } returns listOf(noon(2), noon(1), now)

        val streak = repository.getStreak(now)

        assertEquals(3, streak.days)
        assertTrue(streak.studiedToday)
    }

    // --- Favourites ---

    @Test
    fun `setFavorite saves locally and uploads when user is logged in`() = runTest {
        every { authManager.currentUid } returns "user123"
        val q = question(1).copy(stableId = "programacion_tema_1_p1")

        repository.setFavorite(q, favorite = true, now = 5000)

        coVerify(exactly = 1) {
            dao.saveFavorite(match { it.stableId == "programacion_tema_1_p1" && it.isFavorite && it.updatedAt == 5000L })
        }
        coVerify(exactly = 1) { statsSyncRepository.saveFavorite("user123", match { it.isFavorite }) }
    }

    @Test
    fun `setFavorite ignores questions without a stable id`() = runTest {
        every { authManager.currentUid } returns "user123"

        repository.setFavorite(question(1).copy(stableId = ""), favorite = true)

        coVerify(exactly = 0) { dao.saveFavorite(any()) }
        coVerify(exactly = 0) { statsSyncRepository.saveFavorite(any(), any()) }
    }

    @Test
    fun `favourites test of all subjects queries every subject and respects the limit`() = runTest {
        coEvery { dao.getFavoriteQuestions(null) } returns (1..30).map { question(it).copy(stableId = "q$it") }

        val result = repository.getFavoriteQuestions("all", limit = 20)

        assertEquals(20, result.size)
        coVerify(exactly = 1) { dao.getFavoriteQuestions(null) }
    }

    @Test
    fun `subject list shows the favourites row after smart review`() = runTest {
        coEvery { dao.getUniqueTopicIds("programacion") } returns listOf("tema_1")

        val ids = repository.getUniqueTopicsForSubject("programacion", emptySet()).map { it.id }

        assertTrue(ids.indexOf("-5") == ids.indexOf("-4") + 1)
    }

    // --- Exam mode ---

    @Test
    fun `subjects with tema topics list the exam mode`() = runTest {
        coEvery { dao.getUniqueTopicIds("programacion") } returns listOf("tema_1")

        val ids = repository.getUniqueTopicsForSubject("programacion").map { it.id }

        assertTrue(ids.contains("-6"))
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