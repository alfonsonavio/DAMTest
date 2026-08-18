package com.navio.damtests.data.local.entity

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface QuestionsDao {

    // --- Question management ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuestions(questions: List<Question>)

    @Query("DELETE FROM questions WHERE subjectId = :subjectId AND topicId = :topicId")
    suspend fun deleteQuestionsByTopic(subjectId: String, topicId: String)

    @Query("DELETE FROM questions")
    suspend fun deleteAllQuestions()

    @Transaction
    suspend fun refreshAllQuestions(questions: List<Question>) {
        deleteAllQuestions()
        insertQuestions(questions)
    }

    @Query("SELECT DISTINCT topicId FROM questions WHERE subjectId = :subjectId")
    suspend fun getUniqueTopicIds(subjectId: String): List<String>

    // Specific topic (tema_X, caso_X, repaso_X)
    @Query("SELECT * FROM questions WHERE subjectId = :subjectId AND topicId = :topicId ORDER BY RANDOM() LIMIT :limit")
    suspend fun getRandomQuestionsForTopic(subjectId: String, topicId: String, limit: Int): List<Question>

    // Full general test — all tema_* topics
    @Query("SELECT * FROM questions WHERE subjectId = :subjectId AND topicId LIKE 'tema_%' ORDER BY RANDOM() LIMIT :limit")
    suspend fun getRandomQuestionsForGeneralTest(subjectId: String, limit: Int): List<Question>

    // Ranged general test — e.g. tema_1..tema_10 or tema_11..tema_20
    @Query("SELECT * FROM questions WHERE subjectId = :subjectId AND topicId IN (:topicIds) ORDER BY RANDOM() LIMIT :limit")
    suspend fun getRandomQuestionsForTopicList(subjectId: String, topicIds: List<String>, limit: Int): List<Question>

    // --- Progress management ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveProgress(progress: TopicProgress)

    @Query("SELECT * FROM topic_progress WHERE subjectId = :subjectId")
    fun getProgressFlow(subjectId: String): Flow<List<TopicProgress>>

    @Query("SELECT * FROM topic_progress WHERE subjectId = :subjectId AND topicId = :topicId")
    suspend fun getProgress(subjectId: String, topicId: String): TopicProgress?

    @Query("SELECT * FROM topic_progress")
    fun getAllProgress(): Flow<List<TopicProgress>>

    /** One-shot (non-Flow) query used for cloud sync on login/register. */
    @Query("SELECT * FROM topic_progress")
    suspend fun getAllProgressOnce(): List<TopicProgress>

    // --- Question statistics (smart review) ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveQuestionStats(stats: QuestionStats)

    @Query("SELECT * FROM question_stats WHERE stableId = :stableId")
    suspend fun getQuestionStats(stableId: String): QuestionStats?

    @Query("SELECT * FROM question_stats WHERE subjectId = :subjectId")
    suspend fun getStatsForSubject(subjectId: String): List<QuestionStats>

    @Query("SELECT * FROM question_stats")
    suspend fun getAllQuestionStats(): List<QuestionStats>
}