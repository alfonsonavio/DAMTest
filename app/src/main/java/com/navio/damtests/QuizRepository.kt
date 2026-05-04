package com.navio.damtests

import com.navio.damtests.data.local.entity.Question
import com.navio.damtests.data.local.entity.QuestionsDao
import com.navio.damtests.data.local.entity.Topic
import com.navio.damtests.data.local.entity.TopicProgress

class QuizRepository(private val questionsDao: QuestionsDao) {

    // 1. Actualiza solo un tema específico (Lo que usará el sincronizador)
    suspend fun updateTopicQuestions(subjectId: String, topicId: String, questions: List<Question>) {
        questionsDao.deleteQuestionsByTopic(subjectId, topicId)
        questionsDao.insertQuestions(questions)
    }

    // 2. Mantenemos el refresh global por si acaso, pero cambiando el tipo de dato si fuera necesario
    suspend fun refreshQuestions(questions: List<Question>) {
        questionsDao.refreshAllQuestions(questions)
    }

//    // 3. Cambiamos topicId de Int a String
//    suspend fun getQuestionsByTopic(subjectId: String, topicId: String, limit: Int): List<Question> {
//        return if (topicId == "-1") {
//            questionsDao.getRandomQuestionsForGeneralTest(subjectId, limit)
//        } else {
//            questionsDao.getRandomQuestionsForTopic(subjectId, topicId, limit)
//        }
//    }

    suspend fun updateProgress(progress: TopicProgress) {
        questionsDao.saveProgress(progress)
    }

    fun getProgressFlow(subjectId: String) = questionsDao.getProgressFlow(subjectId)

    // 4. Cambiamos topicId de Int a String aquí también
    suspend fun getProgress(subjectId: String, topicId: String) = questionsDao.getProgress(subjectId, topicId)

    fun getAllProgress() = questionsDao.getAllProgress()

    suspend fun getUniqueTopicsForSubject(subjectId: String): List<Topic> {
        val topicIds = questionsDao.getUniqueTopicIds(subjectId)
        val list = topicIds.map { id ->
            val name = when {
                id.startsWith("tema_") -> "Tema ${id.removePrefix("tema_")}"
                id.startsWith("caso_") -> "Caso Práctico ${id.removePrefix("caso_")}"
                id.startsWith("repaso_") -> "Repaso Final"
                else -> id
            }
            Topic(id, name, subjectId)
        }.toMutableList()

        list.add(Topic("-1", "TEST GENERAL", subjectId))
        return list
    }

    // En QuizRepository.kt

    suspend fun getQuestionsByTopic(subjectId: String, topicId: String, limit: Int): List<Question> {
        return questionsDao.getRandomQuestionsForTopic(subjectId, topicId, limit)
    }

    suspend fun getRandomQuestionsForGeneralTest(subjectId: String, limit: Int): List<Question> {
        return questionsDao.getRandomQuestionsForGeneralTest(subjectId, limit)
    }

    suspend fun getUniqueTopicIds(subjectId: String): List<String> {
        return questionsDao.getUniqueTopicIds(subjectId)
    }

    // En QuizRepository.kt

    // Esta es la que te falta y da el error
    suspend fun insertQuestions(questions: List<Question>) {
        questionsDao.insertQuestions(questions)
    }

    // También asegúrate de tener esta para el Sync
    suspend fun deleteQuestionsByTopic(subjectId: String, topicId: String) {
        questionsDao.deleteQuestionsByTopic(subjectId, topicId)
    }
}