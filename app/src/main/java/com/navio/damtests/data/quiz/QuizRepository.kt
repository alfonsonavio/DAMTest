package com.navio.damtests.data.quiz

import com.navio.damtests.auth.AuthManager
import com.navio.damtests.data.catalog.TopicNames
import com.navio.damtests.data.exam.ExamRange
import com.navio.damtests.data.favorites.Favorites
import com.navio.damtests.data.local.dao.QuestionsDao
import com.navio.damtests.data.local.entity.FavoriteQuestion
import com.navio.damtests.data.local.entity.Question
import com.navio.damtests.data.local.entity.QuestionStats
import com.navio.damtests.data.local.entity.TestAttempt
import com.navio.damtests.data.local.entity.Topic
import com.navio.damtests.data.local.entity.TopicProgress
import com.navio.damtests.data.statistics.StatisticsCalculator
import com.navio.damtests.data.statistics.StatisticsData
import com.navio.damtests.data.streak.StreakCalculator
import com.navio.damtests.data.streak.StreakInfo
import com.navio.damtests.data.sync.StatsSyncRepository
import com.navio.damtests.data.sync.UserProgressRepository
import com.navio.damtests.data.topics.PracticeOptions
import com.navio.damtests.data.topics.StudyTopics
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
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
        val topicIds = (start..end).map(StudyTopics::temaId)
        return questionsDao.getRandomQuestionsForTopicList(subjectId, topicIds, limit)
    }

    suspend fun getUniqueTopicIds(subjectId: String): List<String> =
        questionsDao.getUniqueTopicIds(subjectId)

    // --- Exam mode ---

    /** How many questions an exam of [range] can draw from. */
    suspend fun countExamQuestions(subjectId: String, range: ExamRange): Int = when (range) {
        ExamRange.ALL         -> questionsDao.countGeneralTestQuestions(subjectId)
        ExamRange.FIRST_HALF  -> questionsDao.countQuestionsForTopicList(subjectId, temaIds(1, 10))
        ExamRange.SECOND_HALF -> questionsDao.countQuestionsForTopicList(subjectId, temaIds(11, 20))
    }

    /** [count] random questions of [range], without repeated texts. */
    suspend fun getExamQuestions(subjectId: String, range: ExamRange, count: Int): List<Question> =
        when (range) {
            ExamRange.ALL         -> getRandomQuestionsForGeneralTest(subjectId, count)
            ExamRange.FIRST_HALF  -> getQuestionsForRange(subjectId, 1, 10, count)
            ExamRange.SECOND_HALF -> getQuestionsForRange(subjectId, 11, 20, count)
        }.distinctBy { it.text }

    private fun temaIds(start: Int, end: Int) = (start..end).map(StudyTopics::temaId)

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

    /**
     * Study topics of a subject: those with questions plus those that so far
     * only have a PDF, in list order (see [StudyTopics.ORDER]).
     */
    suspend fun getStudyTopics(subjectId: String, pdfTopicIds: Set<String> = emptySet()): List<Topic> =
        (questionsDao.getUniqueTopicIds(subjectId) + pdfTopicIds)
            .distinct()
            .sortedWith(StudyTopics.ORDER)
            .map { id -> Topic(id, TopicNames.displayName(id), subjectId) }

    /** Practice modes the subject can offer with the questions it has. */
    suspend fun getPracticeOptions(subjectId: String): PracticeOptions =
        PracticeOptions.from(questionsDao.getUniqueTopicIds(subjectId))

    /** Questions of the subject failed at least once, updated live (smart review card). */
    fun getFailedCountFlow(subjectId: String): Flow<Int> =
        questionsDao.countFailedQuestionsFlow(subjectId)

    /** Favourite questions of the subject, updated live (favourites card). */
    fun getFavoriteCountFlow(subjectId: String): Flow<Int> =
        questionsDao.countFavoritesFlow(subjectId)

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

    // --- Favourite questions ---

    /** Stable ids of the questions marked as favourite, updated live. */
    fun getFavoriteIds(): Flow<Set<String>> =
        questionsDao.getFavoriteIdsFlow().map { it.toSet() }

    /** All favourite questions, newest first, updated live (favourites screen). */
    fun getFavoriteQuestionsFlow(): Flow<List<Question>> =
        questionsDao.getFavoriteQuestionsFlow().map { list -> list.distinctBy { it.stableId } }

    /**
     * Marks or unmarks [question] as favourite, locally and in the cloud.
     * Questions without a stable id can't be favourited (they couldn't be
     * found again after the next question sync).
     */
    suspend fun setFavorite(question: Question, favorite: Boolean, now: Long = System.currentTimeMillis()) {
        if (question.stableId.isBlank()) return
        val record = FavoriteQuestion(question.stableId, question.subjectId, favorite, now)
        questionsDao.saveFavorite(record)
        authManager.currentUid?.let { uid -> statsSyncRepository.saveFavorite(uid, record) }
    }

    /** True if [subjectId] (or any subject, for [Favorites.ALL_SUBJECTS]) has favourites. */
    suspend fun hasFavorites(subjectId: String): Boolean =
        getFavoriteQuestions(subjectId, Int.MAX_VALUE).isNotEmpty()

    /** Up to [limit] favourite questions in random order, for the favourites test. */
    suspend fun getFavoriteQuestions(subjectId: String, limit: Int = 20): List<Question> =
        questionsDao.getFavoriteQuestions(subjectId.takeUnless { it == Favorites.ALL_SUBJECTS })
            .distinctBy { it.stableId }
            .shuffled()
            .take(limit)

    /** Current study streak (see [StreakCalculator]). */
    suspend fun getStreak(now: Long = System.currentTimeMillis()): StreakInfo =
        StreakCalculator.calculate(questionsDao.getAttemptTimestamps(), now)

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