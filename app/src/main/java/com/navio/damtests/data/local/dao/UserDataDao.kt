package com.navio.damtests.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction

/**
 * Removes everything that belongs to the signed-in user (progress, answer
 * stats, attempt history and favourites) in a single transaction. Questions
 * are shared content and are kept.
 */
@Dao
interface UserDataDao {

    @Query("DELETE FROM topic_progress")
    suspend fun deleteAllProgress()

    @Query("DELETE FROM question_stats")
    suspend fun deleteAllQuestionStats()

    @Query("DELETE FROM test_attempts")
    suspend fun deleteAllAttempts()

    @Query("DELETE FROM favorite_questions")
    suspend fun deleteAllFavorites()

    /** Only call after the data has been confirmed in the cloud (or deleted from it). */
    @Transaction
    suspend fun clearUserData() {
        deleteAllProgress()
        deleteAllQuestionStats()
        deleteAllAttempts()
        deleteAllFavorites()
    }
}
