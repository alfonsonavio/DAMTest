package com.navio.damtests

import com.navio.damtests.auth.AuthManager
import com.navio.damtests.auth.StatsSyncRepository
import com.navio.damtests.auth.UserProgressRepository
import com.navio.damtests.data.local.entity.Question
import com.navio.damtests.data.local.entity.QuestionsDao
import com.navio.damtests.data.local.entity.Topic
import com.navio.damtests.data.local.entity.TopicProgress
import com.navio.damtests.data.local.entity.QuestionStats
import com.navio.damtests.data.SmartReviewSelector
import com.navio.damtests.data.local.entity.TestAttempt
import com.navio.damtests.data.statistics.StatisticsCalculator
import com.navio.damtests.data.statistics.StatisticsData
import javax.inject.Inject

/**
 * Single source of truth for all quiz data.
 *
 * AuthManager and UserProgressRepository are now INJECTED (previously they were
 * global objects called directly). This is what makes updateProgress() testable:
 * a unit test can pass mocked versions and verify behaviour without touching
 * real Firebase.
 */
class QuizRepository @Inject constructor(
    private val questionsDao: QuestionsDao,
    private val authManager: AuthManager,
    private val userProgressRepository: UserProgressRepository,
    private val statsSyncRepository: StatsSyncRepository
) {

    private val statisticsCalculator = StatisticsCalculator()

    // --- Question management ---

    suspend fun updateTopicQuestions(subjectId: String, topicId: String, questions: List<Question>) {
        questionsDao.deleteQuestionsByTopic(subjectId, topicId)
        questionsDao.insertQuestions(questions)
    }

    suspend fun insertQuestions(questions: List<Question>) =
        questionsDao.insertQuestions(questions)

    suspend fun deleteQuestionsByTopic(subjectId: String, topicId: String) =
        questionsDao.deleteQuestionsByTopic(subjectId, topicId)

    suspend fun refreshQuestions(questions: List<Question>) =
        questionsDao.refreshAllQuestions(questions)

    suspend fun getQuestionsByTopic(subjectId: String, topicId: String, limit: Int): List<Question> =
        questionsDao.getRandomQuestionsForTopic(subjectId, topicId, limit)

    suspend fun getRandomQuestionsForGeneralTest(subjectId: String, limit: Int): List<Question> =
        questionsDao.getRandomQuestionsForGeneralTest(subjectId, limit)

    suspend fun getQuestionsForRange(subjectId: String, start: Int, end: Int, limit: Int): List<Question> {
        val topicIds = (start..end).map { "tema_$it" }
        return questionsDao.getRandomQuestionsForTopicList(subjectId, topicIds, limit)
    }

    suspend fun getUniqueTopicIds(subjectId: String): List<String> =
        questionsDao.getUniqueTopicIds(subjectId)

    suspend fun hasQuestions(subjectId: String, topicId: String): Boolean =
        questionsDao.getRandomQuestionsForTopic(subjectId, topicId, 1).isNotEmpty()

    /**
     * Whether a subject has any downloaded content yet. Used to show a
     * "coming soon" message for placeholder subjects (e.g. second-year ones
     * whose questions aren't in Firebase yet). Detected automatically from the
     * local cache — no manual availability flag to keep in sync.
     */
    suspend fun subjectHasContent(subjectId: String): Boolean =
        questionsDao.getUniqueTopicIds(subjectId).isNotEmpty()

    // --- Progress management ---

    /**
     * Saves progress to Room and, if a user is logged in, also to Firestore.
     */
    suspend fun updateProgress(progress: TopicProgress) {
        questionsDao.saveProgress(progress)
        authManager.currentUid?.let { uid ->
            userProgressRepository.saveTopicProgress(uid, progress)
        }
    }

    suspend fun getProgress(subjectId: String, topicId: String): TopicProgress? =
        questionsDao.getProgress(subjectId, topicId)

    fun getProgressFlow(subjectId: String) =
        questionsDao.getProgressFlow(subjectId)

    fun getAllProgress() =
        questionsDao.getAllProgress()

    // --- Topic helpers ---

    suspend fun getUniqueTopicsForSubject(
        subjectId: String,
        pdfTopicIds: Set<String> = emptySet()
    ): List<Topic> {
        val dbTopicIds  = questionsDao.getUniqueTopicIds(subjectId).toSet()
        val allTopicIds = dbTopicIds + pdfTopicIds

        val topics = allTopicIds.map { id ->
            val title = when {
                id.startsWith("tema_")   -> "Tema ${id.removePrefix("tema_")}"
                id.startsWith("caso_")   -> "Caso Práctico ${id.removePrefix("caso_")}"
                id.startsWith("repaso_") -> "Repaso Final"
                else -> id
            }
            Topic(id, title, subjectId)
        }.toMutableList()

        // General tests and smart review need questions: a subject with only
        // PDFs so far lists just its topics.
        if (dbTopicIds.isNotEmpty()) {
            val temaNumbers = dbTopicIds
                .filter { it.startsWith("tema_") }
                .mapNotNull { it.removePrefix("tema_").toIntOrNull() }
            // The 1-10 / 11-20 split only makes sense when there are questions
            // on both sides; otherwise one of them would be empty (and the test
            // would never load) or identical to the full general test.
            val hasFirstHalf  = temaNumbers.any { it in 1..10 }
            val hasSecondHalf = temaNumbers.any { it in 11..20 }
            if (hasFirstHalf && hasSecondHalf) {
                topics.add(Topic("-2", "TEST GENERAL (TEMAS 1-10)",  subjectId))
                topics.add(Topic("-3", "TEST GENERAL (TEMAS 11-20)", subjectId))
            }
            if (temaNumbers.isNotEmpty()) {
                topics.add(Topic("-1", "TEST GENERAL (TODO)", subjectId))
            }
            topics.add(Topic("-4", "REPASO INTELIGENTE", subjectId))
        }
        return topics
    }

    /**
     * Records the outcome of a single answered question into QuestionStats.
     * Called every time the user answers, in any test mode, so the smart-review
     * data stays complete. Uses the question's stableId as the key.
     */
    suspend fun recordAnswer(question: Question, wasCorrect: Boolean) {
        if (question.stableId.isBlank()) return  // safety: skip un-synced questions

        val existing = questionsDao.getQuestionStats(question.stableId)
        val updated = if (existing == null) {
            QuestionStats(
                stableId          = question.stableId,
                subjectId         = question.subjectId,
                timesSeen         = 1,
                timesCorrect      = if (wasCorrect) 1 else 0,
                timesWrong        = if (wasCorrect) 0 else 1,
                lastSeenTimestamp = System.currentTimeMillis()
            )
        } else {
            existing.copy(
                timesSeen         = existing.timesSeen + 1,
                timesCorrect      = existing.timesCorrect + if (wasCorrect) 1 else 0,
                timesWrong        = existing.timesWrong + if (wasCorrect) 0 else 1,
                lastSeenTimestamp = System.currentTimeMillis()
            )
        }
        questionsDao.saveQuestionStats(updated)
        authManager.currentUid?.let { uid ->
            statsSyncRepository.saveQuestionStats(uid, updated)
        }
    }

    /**
     * Returns the questions with the given stable ids in that same order, or null
     * if any is missing (e.g. the bank changed after a re-sync), in which case a
     * saved test can no longer be resumed.
     */
    suspend fun getQuestionsInOrder(stableIds: List<String>): List<Question>? {
        val byId = questionsDao.getQuestionsByStableIds(stableIds).associateBy { it.stableId }
        return stableIds.map { byId[it] ?: return null }
    }

    /**
     * True if the user has failed at least one question of the subject. Smart
     * review is only offered for those subjects (same rule on Home and on the
     * topic list).
     */
    suspend fun subjectHasFailures(subjectId: String): Boolean =
        questionsDao.countFailedQuestions(subjectId) > 0

    /**
     * Builds a smart-review test for a subject: failed questions first (the most
     * failed and longest-unseen favoured), remaining slots filled by weighted
     * sampling. See [SmartReviewSelector].
     */
    suspend fun getSmartReviewQuestions(subjectId: String, limit: Int = 20): List<Question> {
        val allQuestions = questionsDao.getAllQuestionsForSubject(subjectId)
        if (allQuestions.isEmpty()) return emptyList()

        val statsById = questionsDao.getStatsForSubject(subjectId)
            .associateBy { it.stableId }

        return SmartReviewSelector().select(allQuestions, statsById, limit)
    }

    /**
     * Records a completed test in the immutable attempt history (for statistics).
     * Not called for smart review — that's practice-only.
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
        questionsDao.insertTestAttempt(attempt)
        authManager.currentUid?.let { uid ->
            statsSyncRepository.saveAttempt(uid, attempt)
        }
    }

    /**
     * Builds the statistics for a course, given that course's subject ids.
     * Gathers attempts and question stats for those subjects and delegates the
     * maths to [StatisticsCalculator].
     */
    suspend fun getStatistics(subjectIds: List<String>): StatisticsData {
        val attempts = questionsDao.getAttemptsForSubjects(subjectIds)
        val stats    = questionsDao.getAllQuestionStats()
            .filter { it.subjectId in subjectIds }
        // Same "tests done" figure as Home and Subjects (topic progress)
        val completedTests = questionsDao.getAllProgressOnce()
            .filter { it.subjectId in subjectIds }
            .sumOf { it.attemptsCount }
        return statisticsCalculator.calculate(attempts, stats, completedTests)
    }
}