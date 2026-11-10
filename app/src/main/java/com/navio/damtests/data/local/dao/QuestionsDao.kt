package com.navio.damtests.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.navio.damtests.data.local.entity.Question

/** The question bank: shared content downloaded from Firebase, not user data. */
@Dao
interface QuestionsDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuestions(questions: List<Question>)

    @Query("DELETE FROM questions WHERE subjectId = :subjectId AND topicId = :topicId")
    suspend fun deleteQuestionsByTopic(subjectId: String, topicId: String)

    /** Replaces a topic's questions in one go, so a topic is never left half-updated. */
    @Transaction
    suspend fun replaceTopicQuestions(subjectId: String, topicId: String, questions: List<Question>) {
        deleteQuestionsByTopic(subjectId, topicId)
        insertQuestions(questions)
    }

    @Query("SELECT DISTINCT topicId FROM questions WHERE subjectId = :subjectId")
    suspend fun getUniqueTopicIds(subjectId: String): List<String>

    // Random questions for a test. Some questions appear in more than one topic
    // (or twice after a content update): GROUP BY text keeps one of each before
    // LIMIT, so a test of 20 always has 20 different questions.

    // Specific topic (tema_X, caso_X, repaso_X)
    @Query(
        "SELECT * FROM questions WHERE subjectId = :subjectId AND topicId = :topicId " +
            "GROUP BY text ORDER BY RANDOM() LIMIT :limit"
    )
    suspend fun getRandomQuestionsForTopic(subjectId: String, topicId: String, limit: Int): List<Question>

    // Full general test — all tema_* topics
    @Query(
        "SELECT * FROM questions WHERE subjectId = :subjectId AND topicId LIKE 'tema_%' " +
            "GROUP BY text ORDER BY RANDOM() LIMIT :limit"
    )
    suspend fun getRandomQuestionsForGeneralTest(subjectId: String, limit: Int): List<Question>

    // Ranged general test — e.g. tema_1..tema_10 or tema_11..tema_20
    @Query(
        "SELECT * FROM questions WHERE subjectId = :subjectId AND topicId IN (:topicIds) " +
            "GROUP BY text ORDER BY RANDOM() LIMIT :limit"
    )
    suspend fun getRandomQuestionsForTopicList(subjectId: String, topicIds: List<String>, limit: Int): List<Question>

    // Different questions available, to offer only the exam sizes a subject can fill
    @Query("SELECT COUNT(DISTINCT text) FROM questions WHERE subjectId = :subjectId AND topicId LIKE 'tema_%'")
    suspend fun countGeneralTestQuestions(subjectId: String): Int

    @Query("SELECT COUNT(DISTINCT text) FROM questions WHERE subjectId = :subjectId AND topicId IN (:topicIds)")
    suspend fun countQuestionsForTopicList(subjectId: String, topicIds: List<String>): Int

    /** Questions by stable id (unordered) — used to rebuild a resumed test. */
    @Query("SELECT * FROM questions WHERE stableId IN (:stableIds)")
    suspend fun getQuestionsByStableIds(stableIds: List<String>): List<Question>

    /** All tema_* questions for a subject (no limit) — used to weight smart review. */
    @Query("SELECT * FROM questions WHERE subjectId = :subjectId AND topicId LIKE 'tema_%'")
    suspend fun getAllQuestionsForSubject(subjectId: String): List<Question>
}
