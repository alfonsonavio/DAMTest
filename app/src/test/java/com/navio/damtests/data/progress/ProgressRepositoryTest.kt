package com.navio.damtests.data.progress

import com.navio.damtests.auth.AuthManager
import com.navio.damtests.data.local.dao.QuestionStatsDao
import com.navio.damtests.data.local.dao.TestAttemptDao
import com.navio.damtests.data.local.dao.TopicProgressDao
import com.navio.damtests.data.local.entity.Question
import com.navio.damtests.data.local.entity.TopicProgress
import com.navio.damtests.data.sync.StatsSyncRepository
import com.navio.damtests.data.sync.UserProgressRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for ProgressRepository. The DAOs, AuthManager and the cloud
 * repositories are mocked, so no real database or Firebase is touched.
 */
class ProgressRepositoryTest {

    private lateinit var progressDao: TopicProgressDao
    private lateinit var statsDao: QuestionStatsDao
    private lateinit var attemptDao: TestAttemptDao
    private lateinit var authManager: AuthManager
    private lateinit var userProgressRepository: UserProgressRepository
    private lateinit var statsSyncRepository: StatsSyncRepository
    private lateinit var repository: ProgressRepository

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
        progressDao = mockk(relaxed = true)
        statsDao = mockk(relaxed = true)
        attemptDao = mockk(relaxed = true)
        authManager = mockk(relaxed = true)
        userProgressRepository = mockk(relaxed = true)
        statsSyncRepository = mockk(relaxed = true)
        repository = ProgressRepository(
            progressDao,
            statsDao,
            attemptDao,
            authManager,
            userProgressRepository,
            statsSyncRepository
        )
    }

    @Test
    fun `updateProgress saves to dao and to cloud when user is logged in`() = runTest {
        val progress = TopicProgress("programacion", "tema_1", 8, 10, 1)
        // Simulate a logged-in user
        every { authManager.currentUid } returns "user123"

        repository.updateProgress(progress)

        // Saved locally...
        coVerify(exactly = 1) { progressDao.saveProgress(progress) }
        // ...and synced to the cloud with the user's uid
        coVerify(exactly = 1) { userProgressRepository.saveTopicProgress("user123", progress) }
    }

    @Test
    fun `updateProgress saves only to dao when no user is logged in`() = runTest {
        val progress = TopicProgress("programacion", "tema_1", 8, 10, 1)
        // No logged-in user
        every { authManager.currentUid } returns null

        repository.updateProgress(progress)

        coVerify(exactly = 1) { progressDao.saveProgress(progress) }
        // Cloud sync must NOT happen
        coVerify(exactly = 0) { userProgressRepository.saveTopicProgress(any(), any()) }
    }

    @Test
    fun `recordTestAttempt saves locally and uploads when user is logged in`() = runTest {
        every { authManager.currentUid } returns "user123"

        repository.recordTestAttempt("programacion", "tema_1", score = 7, totalQuestions = 10)

        coVerify(exactly = 1) {
            attemptDao.insertTestAttempt(match { it.subjectId == "programacion" && it.score == 7 })
        }
        coVerify(exactly = 1) { statsSyncRepository.saveAttempt("user123", match { it.topicId == "tema_1" }) }
    }

    @Test
    fun `recordTestAttempt does not upload when no user is logged in`() = runTest {
        every { authManager.currentUid } returns null

        repository.recordTestAttempt("programacion", "tema_1", score = 7, totalQuestions = 10)

        coVerify(exactly = 1) { attemptDao.insertTestAttempt(any()) }
        coVerify(exactly = 0) { statsSyncRepository.saveAttempt(any(), any()) }
    }

    @Test
    fun `recordAnswer uploads the updated question stats when user is logged in`() = runTest {
        every { authManager.currentUid } returns "user123"
        coEvery { statsDao.getQuestionStats("programacion_tema_1_p1") } returns null
        val q = question(1).copy(stableId = "programacion_tema_1_p1")

        repository.recordAnswer(q, wasCorrect = false)

        coVerify(exactly = 1) {
            statsSyncRepository.saveQuestionStats(
                "user123",
                match {
                    it.stableId == "programacion_tema_1_p1" && it.timesSeen == 1 && it.timesWrong == 1
                }
            )
        }
    }

    @Test
    fun `getStreak counts consecutive study days from the attempt history`() = runTest {
        // Noon of today and the two previous days, in the device's time zone
        val zone = java.time.ZoneId.systemDefault()
        val today = java.time.LocalDate.now(zone)
        fun noon(daysAgo: Long) =
            today.minusDays(daysAgo).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
        val now = noon(0)
        coEvery { attemptDao.getAttemptTimestamps() } returns listOf(noon(2), noon(1), now)

        val streak = repository.getStreak(now)

        assertEquals(3, streak.days)
        assertTrue(streak.studiedToday)
    }
}
