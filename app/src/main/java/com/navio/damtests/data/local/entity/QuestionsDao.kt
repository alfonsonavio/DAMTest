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

    // Question counts, to offer only the exam sizes a subject can fill
    @Query("SELECT COUNT(*) FROM questions WHERE subjectId = :subjectId AND topicId LIKE 'tema_%'")
    suspend fun countGeneralTestQuestions(subjectId: String): Int

    @Query("SELECT COUNT(*) FROM questions WHERE subjectId = :subjectId AND topicId IN (:topicIds)")
    suspend fun countQuestionsForTopicList(subjectId: String, topicIds: List<String>): Int

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

    /** Bulk save, used when stats are restored from the cloud. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveQuestionStatsList(stats: List<QuestionStats>)

    @Query("SELECT * FROM question_stats WHERE stableId = :stableId")
    suspend fun getQuestionStats(stableId: String): QuestionStats?

    @Query("SELECT * FROM question_stats WHERE subjectId = :subjectId")
    suspend fun getStatsForSubject(subjectId: String): List<QuestionStats>

    @Query("SELECT * FROM question_stats")
    suspend fun getAllQuestionStats(): List<QuestionStats>

    /** How many questions of a subject were failed at least once (smart review). */
    @Query("SELECT COUNT(*) FROM question_stats WHERE subjectId = :subjectId AND timesWrong > 0")
    suspend fun countFailedQuestions(subjectId: String): Int

    /** Questions by stable id (unordered) — used to rebuild a resumed test. */
    @Query("SELECT * FROM questions WHERE stableId IN (:stableIds)")
    suspend fun getQuestionsByStableIds(stableIds: List<String>): List<Question>

    /** All tema_* questions for a subject (no limit) — used to weight smart review. */
    @Query("SELECT * FROM questions WHERE subjectId = :subjectId AND topicId LIKE 'tema_%'")
    suspend fun getAllQuestionsForSubject(subjectId: String): List<Question>

    @Insert
    suspend fun insertTestAttempt(attempt: TestAttempt)

    /** Bulk insert, used when attempts are restored from the cloud. */
    @Insert
    suspend fun insertTestAttempts(attempts: List<TestAttempt>)

    /**
     * Scored attempts for a set of subjects (a course), newest last. Practice
     * tests (smart review "-4" and favourites "-5") are stored for the study
     * streak but excluded here, so they never affect the statistics.
     */
    @Query("SELECT * FROM test_attempts WHERE subjectId IN (:subjectIds) AND topicId NOT IN ('-4', '-5') ORDER BY timestamp ASC")
    suspend fun getAttemptsForSubjects(subjectIds: List<String>): List<TestAttempt>

    /** When every finished test happened (smart review included), for the study streak. */
    @Query("SELECT timestamp FROM test_attempts")
    suspend fun getAttemptTimestamps(): List<Long>

    /** All attempts, newest last. */
    @Query("SELECT * FROM test_attempts ORDER BY timestamp ASC")
    suspend fun getAllAttempts(): List<TestAttempt>

    // --- Favourite questions ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveFavorite(favorite: FavoriteQuestion)

    /** Bulk save, used when favourites are restored from the cloud. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveFavorites(favorites: List<FavoriteQuestion>)

    /** Every favourite record, unmarked ones included (for syncing). */
    @Query("SELECT * FROM favorite_questions")
    suspend fun getAllFavoriteRecords(): List<FavoriteQuestion>

    @Query("SELECT stableId FROM favorite_questions WHERE isFavorite = 1")
    fun getFavoriteIdsFlow(): Flow<List<String>>

    /** Favourite questions, newest first; [subjectId] null means every subject. */
    @Query(
        "SELECT q.* FROM questions q INNER JOIN favorite_questions f ON q.stableId = f.stableId " +
                "WHERE f.isFavorite = 1 AND (:subjectId IS NULL OR q.subjectId = :subjectId) " +
                "ORDER BY f.updatedAt DESC"
    )
    suspend fun getFavoriteQuestions(subjectId: String?): List<Question>

    /** All favourite questions, newest first, updated live (favourites screen). */
    @Query(
        "SELECT q.* FROM questions q INNER JOIN favorite_questions f ON q.stableId = f.stableId " +
                "WHERE f.isFavorite = 1 ORDER BY f.updatedAt DESC"
    )
    fun getFavoriteQuestionsFlow(): Flow<List<Question>>

    // --- Logout ---

    @Query("DELETE FROM topic_progress")
    suspend fun deleteAllProgress()

    @Query("DELETE FROM question_stats")
    suspend fun deleteAllQuestionStats()

    @Query("DELETE FROM test_attempts")
    suspend fun deleteAllAttempts()

    @Query("DELETE FROM favorite_questions")
    suspend fun deleteAllFavorites()

    /**
     * Removes everything that belongs to the signed-in user (progress, answer
     * stats, attempt history and favourites). Questions are shared content and are kept.
     * Only call after the data has been confirmed in the cloud.
     */
    @Transaction
    suspend fun clearUserData() {
        deleteAllProgress()
        deleteAllQuestionStats()
        deleteAllAttempts()
        deleteAllFavorites()
    }
}