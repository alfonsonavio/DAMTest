package com.navio.damtests.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.navio.damtests.data.local.entity.TopicProgress
import kotlinx.coroutines.flow.Flow

/** Last result per topic (what the topic list and Home show). */
@Dao
interface TopicProgressDao {

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
}
