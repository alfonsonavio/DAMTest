package com.navio.damtests.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.navio.damtests.QuizRepository
import com.navio.damtests.data.local.entity.Question
import com.navio.damtests.data.local.entity.TopicProgress
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

class QuizViewModel(private val repository: QuizRepository) : ViewModel() {

    private val _questions = MutableStateFlow<List<Question>>(emptyList())
    val questions: StateFlow<List<Question>> = _questions

    private val _currentQuestionIndex = MutableStateFlow(0)
    val currentQuestionIndex: StateFlow<Int> = _currentQuestionIndex

    private val _score = MutableStateFlow(0)
    val score: StateFlow<Int> = _score

    private val _isTestFinished = MutableStateFlow(false)
    val isTestFinished: StateFlow<Boolean> = _isTestFinished

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _resultsList = mutableListOf<QuestionResult>()

    // Cargar preguntas al iniciar el test - Ahora recibe String
    fun loadQuestions(subjectId: String, topicId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _resultsList.clear()
            _score.value = 0
            _currentQuestionIndex.value = 0

            val limit = if (topicId == "-1") 20 else 10

            // CAMBIO AQUÍ: Decidimos qué consulta usar según el topicId
            val loadedQuestions = if (topicId == "-1") {
                // Llamamos a la nueva consulta de Test General (solo temas)
                repository.getRandomQuestionsForGeneralTest(subjectId, limit)
            } else {
                // Llamamos a la consulta normal para un tema/caso/repaso específico
                repository.getQuestionsByTopic(subjectId, topicId, limit)
            }

            _questions.value = loadedQuestions
            _isLoading.value = false
        }
    }

    // Comprobar respuesta
    fun checkAnswer(selectedIndex: Int, shuffledOptions: List<String>) {
        if (_isTestFinished.value) return

        val questionsList = _questions.value
        val currentIndex = _currentQuestionIndex.value
        val currentQuestion = questionsList.getOrNull(currentIndex)

        if (currentQuestion != null) {
            if (_resultsList.size > currentIndex) return
            _resultsList.add(QuestionResult(currentQuestion, selectedIndex, shuffledOptions))

            if (selectedIndex == currentQuestion.correctOptionIndex) {
                _score.value += 1
            }

            if (_currentQuestionIndex.value < _questions.value.size - 1) {
                _currentQuestionIndex.value += 1
            } else {
                _isTestFinished.value = true
                saveFinalProgress(currentQuestion.subjectId, currentQuestion.topicId)
            }
        }
    }

    // Guardar progreso - Cambiado a String
    private fun saveFinalProgress(subjectId: String, topicId: String) {
        viewModelScope.launch {
            // 1. Buscamos si ya existe progreso previo para este tema
            val currentProgress = repository.getProgress(subjectId, topicId)

            // 2. Calculamos el nuevo número de intentos
            val newAttemptsCount = (currentProgress?.attemptsCount ?: 0) + 1

            // 3. Creamos el objeto con la info actualizada
            val progress = TopicProgress(
                subjectId = subjectId,
                topicId = topicId, // Ahora es String
                lastScore = _score.value,
                totalQuestions = _questions.value.size,
                attemptsCount = newAttemptsCount,
                lastAttemptTimestamp = System.currentTimeMillis()
            )

            // 4. Guardamos en la DB
            repository.updateProgress(progress)
        }
    }

    fun getResults(): List<QuestionResult> = _resultsList
}