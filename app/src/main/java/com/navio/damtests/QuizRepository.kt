package com.navio.damtests

import com.navio.damtests.data.local.entity.Question
import com.navio.damtests.data.local.entity.QuestionsDao
import com.navio.damtests.data.local.entity.Topic
import com.navio.damtests.data.local.entity.TopicProgress

/**
 * Single source of truth for all quiz data.
 * Abstracts the Room DAO from the rest of the app.
 */
class QuizRepository(private val questionsDao: QuestionsDao) {

    // --- Question management ---

    /** Replaces all questions for a given topic (used by [FirebaseSyncManager]). */
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

    /** Returns up to [limit] random questions for a specific topic. */
    suspend fun getQuestionsByTopic(subjectId: String, topicId: String, limit: Int): List<Question> =
        questionsDao.getRandomQuestionsForTopic(subjectId, topicId, limit)

    /** Returns up to [limit] random questions from ALL tema_* topics (full general test). */
    suspend fun getRandomQuestionsForGeneralTest(subjectId: String, limit: Int): List<Question> =
        questionsDao.getRandomQuestionsForGeneralTest(subjectId, limit)

    /**
     * Returns up to [limit] random questions from tema_[start] through tema_[end].
     * Used for the partial general tests (e.g. topics 1–10, 11–20).
     */
    suspend fun getQuestionsForRange(subjectId: String, start: Int, end: Int, limit: Int): List<Question> {
        val topicIds = (start..end).map { "tema_$it" }
        return questionsDao.getRandomQuestionsForTopicList(subjectId, topicIds, limit)
    }

    suspend fun getUniqueTopicIds(subjectId: String): List<String> =
        questionsDao.getUniqueTopicIds(subjectId)

    // --- Progress management ---

    suspend fun updateProgress(progress: TopicProgress) =
        questionsDao.saveProgress(progress)

    suspend fun getProgress(subjectId: String, topicId: String): TopicProgress? =
        questionsDao.getProgress(subjectId, topicId)

    fun getProgressFlow(subjectId: String) =
        questionsDao.getProgressFlow(subjectId)

    fun getAllProgress() =
        questionsDao.getAllProgress()

    // --- Topic helpers ---

    /**
     * Returns a sorted list of [Topic] objects for the given subject, plus three
     * general test entries at the end:
     *  - "-2" → Temas 1–10
     *  - "-3" → Temas 11–20
     *  - "-1" → All topics
     *
     * Sort order within regular topics: tema_* → caso_* → repaso_*, then numerically.
     */
    suspend fun getUniqueTopicsForSubject(subjectId: String): List<Topic> {
        val topicIds = questionsDao.getUniqueTopicIds(subjectId)
        val topics   = topicIds.map { id ->
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
}
