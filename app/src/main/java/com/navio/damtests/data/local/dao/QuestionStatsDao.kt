package com.navio.damtests.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.navio.damtests.data.local.entity.QuestionStats
import kotlinx.coroutines.flow.Flow

/** How each question has been answered so far (smart review and statistics). */
@Dao
interface QuestionStatsDao {

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

    @Query("SELECT COUNT(*) FROM question_stats WHERE subjectId = :subjectId AND timesWrong > 0")
    fun countFailedQuestionsFlow(subjectId: String): Flow<Int>
}
