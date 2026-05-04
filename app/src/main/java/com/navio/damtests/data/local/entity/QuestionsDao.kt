package com.navio.damtests.data.local.entity

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface QuestionsDao {

    // --- Gestión de preguntas ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuestions(questions: List<Question>)

    @Query("SELECT * FROM questions WHERE subjectId = :subjectId AND topicId = :topicId ORDER BY RANDOM() LIMIT :limit")
    suspend fun getRandomQuestionsForTopic(subjectId: String, topicId: String, limit: Int): List<Question>

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

    // 2. Test General: SOLO coge preguntas de temas numéricos (ignora casos y repasos)
    @Query("SELECT * FROM questions WHERE subjectId = :subjectId AND topicId LIKE 'tema_%' ORDER BY RANDOM() LIMIT :limit")
    suspend fun getRandomQuestionsForGeneralTest(subjectId: String, limit: Int): List<Question>

    // 3. Borrar y meter (para el SyncManager)
    @Transaction
    suspend fun refreshTopicQuestions(subjectId: String, topicId: String, questions: List<Question>) {
        deleteQuestionsByTopic(subjectId, topicId)
        insertQuestions(questions)
    }

    // --- Gestión de Progreso/Notas ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveProgress(progress: TopicProgress)

    @Query("SELECT * FROM topic_progress WHERE subjectId = :subjectId")
    fun getProgressFlow(subjectId: String): Flow<List<TopicProgress>>

    // Cambiado topicId a String para que coincida con la entidad
    @Query("SELECT * FROM topic_progress WHERE subjectId = :subjectId AND topicId = :topicId")
    suspend fun getProgress(subjectId: String, topicId: String): TopicProgress?

    @Query("SELECT * FROM topic_progress")
    fun getAllProgress(): Flow<List<TopicProgress>>
}