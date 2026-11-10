package com.navio.damtests.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.navio.damtests.data.local.entity.FavoriteQuestion
import com.navio.damtests.data.local.entity.Question
import kotlinx.coroutines.flow.Flow

/** Questions the user marked as favourite. */
@Dao
interface FavoriteQuestionDao {

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

    /** Favourites of a subject whose question is still in the local cache. */
    @Query(
        """
        SELECT COUNT(DISTINCT f.stableId) FROM favorite_questions f
        INNER JOIN questions q ON q.stableId = f.stableId
        WHERE f.isFavorite = 1 AND q.subjectId = :subjectId
        """
    )
    fun countFavoritesFlow(subjectId: String): Flow<Int>
}
