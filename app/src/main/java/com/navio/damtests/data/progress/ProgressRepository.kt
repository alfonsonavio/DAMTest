package com.navio.damtests.data.progress

import com.navio.damtests.auth.AuthManager
import com.navio.damtests.data.local.dao.QuestionStatsDao
import com.navio.damtests.data.local.dao.TestAttemptDao
import com.navio.damtests.data.local.dao.TopicProgressDao
import com.navio.damtests.data.local.entity.Question
import com.navio.damtests.data.local.entity.QuestionStats
import com.navio.damtests.data.local.entity.TestAttempt
import com.navio.damtests.data.local.entity.TopicProgress
import com.navio.damtests.data.statistics.StatisticsCalculator
import com.navio.damtests.data.statistics.StatisticsData
import com.navio.damtests.data.streak.StreakCalculator
import com.navio.damtests.data.streak.StreakInfo
import com.navio.damtests.data.sync.StatsSyncRepository
import com.navio.damtests.data.sync.UserProgressRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Everything the user has done: last result per topic, how each question was
 * answered, finished tests, and what is built from them (study streak and
 * statistics).
 *
 * Every change is saved in Room and, if a user is signed in, uploaded to
 * Firestore right away.
 */
class ProgressRepository @Inject constructor(
    private val topicProgressDao: TopicProgressDao,
    private val questionStatsDao: QuestionStatsDao,
    private val testAttemptDao: TestAttemptDao,
    private val authManager: AuthManager,
    private val userProgressRepository: UserProgressRepository,
    private val statsSyncRepository: StatsSyncRepository
) {

    private val statisticsCalculator = StatisticsCalculator()

    // --- Topic progress ---

    suspend fun updateProgress(progress: TopicProgress) {
        topicProgressDao.saveProgress(progress)
        authManager.currentUid?.let { uid ->
            userProgressRepository.saveTopicProgress(uid, progress)
        }
    }

    suspend fun getProgress(subjectId: String, topicId: String): TopicProgress? =
        topicProgressDao.getProgress(subjectId, topicId)

    fun getProgressFlow(subjectId: String): Flow<List<TopicProgress>> =
        topicProgressDao.getProgressFlow(subjectId)

    fun getAllProgress(): Flow<List<TopicProgress>> =
        topicProgressDao.getAllProgress()

    // --- Answers ---

    /**
     * Records the outcome of a single answered question into QuestionStats.
     * Called every time the user answers, in any test mode, so the smart-review
     * data stays complete. Uses the question's stableId as the key.
     */
    suspend fun recordAnswer(question: Question, wasCorrect: Boolean) {
        if (question.stableId.isBlank()) return // safety: skip un-synced questions

        val existing = questionStatsDao.getQuestionStats(question.stableId)
        val updated = if (existing == null) {
            QuestionStats(
                stableId = question.stableId,
                subjectId = question.subjectId,
                timesSeen = 1,
                timesCorrect = if (wasCorrect) 1 else 0,
                timesWrong = if (wasCorrect) 0 else 1,
                lastSeenTimestamp = System.currentTimeMillis()
            )
        } else {
            existing.copy(
                timesSeen = existing.timesSeen + 1,
                timesCorrect = existing.timesCorrect + if (wasCorrect) 1 else 0,
                timesWrong = existing.timesWrong + if (wasCorrect) 0 else 1,
                lastSeenTimestamp = System.currentTimeMillis()
            )
        }
        questionStatsDao.saveQuestionStats(updated)
        authManager.currentUid?.let { uid ->
            statsSyncRepository.saveQuestionStats(uid, updated)
        }
    }

    /**
     * True if the user has failed at least one question of the subject. Smart
     * review is only offered for those subjects (same rule on Home and on the
     * topic list).
     */
    suspend fun subjectHasFailures(subjectId: String): Boolean =
        questionStatsDao.countFailedQuestions(subjectId) > 0

    /** Questions of the subject failed at least once, updated live (smart review card). */
    fun getFailedCountFlow(subjectId: String): Flow<Int> =
        questionStatsDao.countFailedQuestionsFlow(subjectId)

    // --- Finished tests ---

    /**
     * Records a completed test in the immutable attempt history (statistics
     * and study streak).
     */
    suspend fun recordTestAttempt(
        subjectId: String,
        topicId: String,
        score: Int,
        totalQuestions: Int
    ) {
        val attempt = TestAttempt(
            subjectId = subjectId,
            topicId = topicId,
            score = score,
            totalQuestions = totalQuestions
        )
        testAttemptDao.insertTestAttempt(attempt)
        authManager.currentUid?.let { uid ->
            statsSyncRepository.saveAttempt(uid, attempt)
        }
    }

    /** Current study streak (see [StreakCalculator]). */
    suspend fun getStreak(now: Long = System.currentTimeMillis()): StreakInfo =
        StreakCalculator.calculate(testAttemptDao.getAttemptTimestamps(), now)

    /**
     * Builds the statistics for a course, given that course's subject ids.
     * Gathers attempts and question stats for those subjects and delegates the
     * maths to [StatisticsCalculator].
     */
    suspend fun getStatistics(subjectIds: List<String>): StatisticsData {
        val attempts = testAttemptDao.getAttemptsForSubjects(subjectIds)
        val stats = questionStatsDao.getAllQuestionStats()
            .filter { it.subjectId in subjectIds }
        // Same "tests done" figure as Home and Subjects (topic progress)
        val completedTests = topicProgressDao.getAllProgressOnce()
            .filter { it.subjectId in subjectIds }
            .sumOf { it.attemptsCount }
        return statisticsCalculator.calculate(attempts, stats, completedTests)
    }
}
