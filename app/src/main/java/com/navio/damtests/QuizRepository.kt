package com.navio.damtests

import com.navio.damtests.auth.AuthManager
import com.navio.damtests.auth.UserProgressRepository
import com.navio.damtests.data.local.entity.Question
import com.navio.damtests.data.local.entity.QuestionsDao
import com.navio.damtests.data.local.entity.Topic
import com.navio.damtests.data.local.entity.TopicProgress
import com.navio.damtests.data.local.entity.QuestionStats
import com.navio.damtests.data.SmartReviewSelector
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
    private val userProgressRepository: UserProgressRepository
) {

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

        topics.add(Topic("-2", "TEST GENERAL (TEMAS 1-10)",  subjectId))
        topics.add(Topic("-3", "TEST GENERAL (TEMAS 11-20)", subjectId))
        topics.add(Topic("-1", "TEST GENERAL (TODO)",        subjectId))
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
    }

    /**
     * Builds a smart-review test for a subject: [limit] questions chosen by
     * weighted random sampling, prioritising the ones the user fails most and
     * hasn't seen recently. Falls back gracefully when there are few questions.
     */
    suspend fun getSmartReviewQuestions(subjectId: String, limit: Int = 20): List<Question> {
        val allQuestions = questionsDao.getAllQuestionsForSubject(subjectId)
        if (allQuestions.isEmpty()) return emptyList()

        val statsById = questionsDao.getStatsForSubject(subjectId)
            .associateBy { it.stableId }

        return SmartReviewSelector().select(allQuestions, statsById, limit)
    }
}