package com.navio.damtests.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.navio.damtests.data.local.entity.TestAttempt
import com.navio.damtests.data.quiz.TestMode

/** History of finished tests (statistics and study streak). */
@Dao
interface TestAttemptDao {

    @Insert
    suspend fun insertTestAttempt(attempt: TestAttempt)

    /** Bulk insert, used when attempts are restored from the cloud. */
    @Insert
    suspend fun insertTestAttempts(attempts: List<TestAttempt>)

    /**
     * Scored attempts for a set of subjects (a course), newest last. Practice
     * tests (smart review and favourites) are stored for the study streak but
     * excluded here, so they never affect the statistics.
     */
    @Query(
        "SELECT * FROM test_attempts WHERE subjectId IN (:subjectIds) " +
            "AND topicId NOT IN ('" + TestMode.Ids.SMART_REVIEW + "', '" + TestMode.Ids.FAVORITES + "') " +
            "ORDER BY timestamp ASC"
    )
    suspend fun getAttemptsForSubjects(subjectIds: List<String>): List<TestAttempt>

    /** When every finished test happened (smart review included), for the study streak. */
    @Query("SELECT timestamp FROM test_attempts")
    suspend fun getAttemptTimestamps(): List<Long>

    /** All attempts, newest last. */
    @Query("SELECT * FROM test_attempts ORDER BY timestamp ASC")
    suspend fun getAllAttempts(): List<TestAttempt>
}
