package com.navio.damtests.data.quiz

import com.navio.damtests.data.catalog.TopicNames
import com.navio.damtests.data.exam.ExamRange
import com.navio.damtests.data.local.dao.QuestionStatsDao
import com.navio.damtests.data.local.dao.QuestionsDao
import com.navio.damtests.data.local.entity.Question
import com.navio.damtests.data.local.entity.Topic
import com.navio.damtests.data.topics.PracticeOptions
import com.navio.damtests.data.topics.StudyTopics
import javax.inject.Inject

/**
 * The question bank and the tests built from it: questions of a topic, of a
 * range of topics, of an exam or of the smart review, and the topics and
 * practice modes a subject offers.
 *
 * What the user does with those questions lives in
 * [com.navio.damtests.data.progress.ProgressRepository], and favourites in
 * [com.navio.damtests.data.favorites.FavoritesRepository].
 */
class QuizRepository @Inject constructor(
    private val questionsDao: QuestionsDao,
    private val questionStatsDao: QuestionStatsDao
) {

    // --- Question bank ---

    /** Replaces a topic's questions with a fresh download. */
    suspend fun updateTopicQuestions(subjectId: String, topicId: String, questions: List<Question>) =
        questionsDao.replaceTopicQuestions(subjectId, topicId, questions)

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

    // --- Tests ---

    suspend fun getQuestionsByTopic(subjectId: String, topicId: String, limit: Int): List<Question> =
        questionsDao.getRandomQuestionsForTopic(subjectId, topicId, limit)

    suspend fun getRandomQuestionsForGeneralTest(subjectId: String, limit: Int): List<Question> =
        questionsDao.getRandomQuestionsForGeneralTest(subjectId, limit)

    /** [limit] random questions of the topics numbered [topics] ("tema_1".."tema_10"…). */
    suspend fun getQuestionsForRange(subjectId: String, topics: IntRange, limit: Int): List<Question> =
        questionsDao.getRandomQuestionsForTopicList(subjectId, temaIds(topics), limit)

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
     * Builds a smart-review test for a subject: failed questions first (the most
     * failed and longest-unseen favoured), remaining slots filled by weighted
     * sampling. See [SmartReviewSelector].
     */
    suspend fun getSmartReviewQuestions(subjectId: String, limit: Int): List<Question> {
        val allQuestions = questionsDao.getAllQuestionsForSubject(subjectId)
        if (allQuestions.isEmpty()) return emptyList()

        val statsById = questionStatsDao.getStatsForSubject(subjectId)
            .associateBy { it.stableId }

        return SmartReviewSelector().select(allQuestions, statsById, limit)
    }

    // --- Exam mode ---

    /** How many questions an exam of [range] can draw from. */
    suspend fun countExamQuestions(subjectId: String, range: ExamRange): Int = when (range) {
        ExamRange.ALL -> questionsDao.countGeneralTestQuestions(subjectId)
        ExamRange.FIRST_HALF -> questionsDao.countQuestionsForTopicList(subjectId, temaIds(StudyTopics.FIRST_HALF))
        ExamRange.SECOND_HALF -> questionsDao.countQuestionsForTopicList(subjectId, temaIds(StudyTopics.SECOND_HALF))
    }

    /** [count] random questions of [range], without repeated texts. */
    suspend fun getExamQuestions(subjectId: String, range: ExamRange, count: Int): List<Question> =
        when (range) {
            ExamRange.ALL -> getRandomQuestionsForGeneralTest(subjectId, count)
            ExamRange.FIRST_HALF -> getQuestionsForRange(subjectId, StudyTopics.FIRST_HALF, count)
            ExamRange.SECOND_HALF -> getQuestionsForRange(subjectId, StudyTopics.SECOND_HALF, count)
        }.distinctBy { it.text }

    // --- Topics ---

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

    private fun temaIds(topics: IntRange) = topics.map(StudyTopics::temaId)
}
