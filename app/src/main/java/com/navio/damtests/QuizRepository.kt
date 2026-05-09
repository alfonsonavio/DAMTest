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

    /** Returns true if there is at least one question for this topic in the local cache. */
    suspend fun hasQuestions(subjectId: String, topicId: String): Boolean =
        questionsDao.getRandomQuestionsForTopic(subjectId, topicId, 1).isNotEmpty()

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
     * Builds the topic list for a subject merging two sources:
     *  - Room DB  → topics that have questions
     *  - [pdfTopicIds] → topics that have a PDF in the GitHub Release
     *
     * A topic appears if it exists in either source.
     * The caller is responsible for fetching [pdfTopicIds] via [PdfReleaseRepository].
     */
    suspend fun getUniqueTopicsForSubject(
        subjectId: String,
        pdfTopicIds: Set<String> = emptySet()
    ): List<Topic> {
        val dbTopicIds = questionsDao.getUniqueTopicIds(subjectId).toSet()
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
}
