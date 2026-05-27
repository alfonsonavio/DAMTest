package com.navio.damtests.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.navio.damtests.QuizRepository
import com.navio.damtests.data.local.entity.Question
import com.navio.damtests.data.local.entity.TopicProgress
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class QuizViewModel(private val repository: QuizRepository) : ViewModel() {

    // --- Public state ---

    private val _questions = MutableStateFlow<List<Question>>(emptyList())
    val questions: StateFlow<List<Question>> = _questions

    private val _currentQuestionIndex = MutableStateFlow(0)
    val currentQuestionIndex: StateFlow<Int> = _currentQuestionIndex

    private val _score = MutableStateFlow(0)
    val score: StateFlow<Int> = _score

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _isTestFinished = MutableStateFlow(false)
    val isTestFinished: StateFlow<Boolean> = _isTestFinished

    /**
     * Non-null while the user has answered the current question and is reading feedback.
     * Resets to null when [goToNextQuestion] is called.
     */
    private val _currentAnswerState = MutableStateFlow<AnswerResult?>(null)
    val currentAnswerState: StateFlow<AnswerResult?> = _currentAnswerState

    /** Result of a single answered question, used to drive UI highlighting. */
    data class AnswerResult(
        val selectedIndex: Int,
        val correctIndex: Int,
        val isCorrect: Boolean
    )

    private val _resultsList = mutableListOf<QuestionResult>()

    // --- Actions ---

    /**
     * Loads questions for the given subject and topic.
     *
     * Special topicId values:
     *  - "-1" → all topics (full general test, 20 questions)
     *  - "-2" → topics 1–10 (20 questions)
     *  - "-3" → topics 11–20 (20 questions)
     *  - any other → specific topic (10 questions)
     */
    fun loadQuestions(subjectId: String, topicId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _resultsList.clear()
            _score.value = 0
            _currentQuestionIndex.value = 0
            _currentAnswerState.value = null

            val limit = if (topicId.startsWith("-")) 20 else 10

            _questions.value = when (topicId) {
                "-1"  -> repository.getRandomQuestionsForGeneralTest(subjectId, limit)
                "-2"  -> repository.getQuestionsForRange(subjectId, 1,  10, limit)
                "-3"  -> repository.getQuestionsForRange(subjectId, 11, 20, limit)
                else  -> repository.getQuestionsByTopic(subjectId, topicId, limit)
            }.distinctBy { it.text }  // prevent duplicates if Room has stale rows

            _isLoading.value = false
        }
    }

    /**
     * Evaluates the answer the user tapped.
     *
     * @param selectedText   Text of the button the user tapped (as shown on screen).
     * @param shuffledOptions All four options as displayed on screen.
     */
    fun checkAnswer(selectedText: String, shuffledOptions: List<String>) {
        val currentQuestion = _questions.value.getOrNull(_currentQuestionIndex.value) ?: return
        // Ignore extra taps after the answer is already recorded
        if (_currentAnswerState.value != null) return

        val correctText = when (currentQuestion.correctOptionIndex) {
            0    -> currentQuestion.optionA
            1    -> currentQuestion.optionB
            2    -> currentQuestion.optionC
            else -> currentQuestion.optionD
        }

        val isCorrect      = selectedText == correctText
        val uiIndex        = shuffledOptions.indexOf(selectedText)
        val correctUiIndex = shuffledOptions.indexOf(correctText)

        _resultsList.add(QuestionResult(currentQuestion, uiIndex, shuffledOptions, isCorrect))
        if (isCorrect) _score.value += 1

        _currentAnswerState.value = AnswerResult(uiIndex, correctUiIndex, isCorrect)
    }

    /** Advances to the next question, or finishes the test if this was the last one. */
    fun goToNextQuestion() {
        _currentAnswerState.value = null
        if (_currentQuestionIndex.value < _questions.value.size - 1) {
            _currentQuestionIndex.value += 1
        } else {
            _isTestFinished.value = true
            _questions.value.firstOrNull()?.let {
                saveFinalProgress(it.subjectId, it.topicId)
            }
        }
    }

    fun getResults(): List<QuestionResult> = _resultsList.toList()

    // --- Private helpers ---

    private fun saveFinalProgress(subjectId: String, topicId: String) {
        viewModelScope.launch {
            val previous        = repository.getProgress(subjectId, topicId)
            val newAttemptsCount = (previous?.attemptsCount ?: 0) + 1

            repository.updateProgress(
                TopicProgress(
                    subjectId             = subjectId,
                    topicId               = topicId,
                    lastScore             = _score.value,
                    totalQuestions        = _questions.value.size,
                    attemptsCount         = newAttemptsCount,
                    lastAttemptTimestamp  = System.currentTimeMillis()
                )
            )
        }
    }
}