package com.navio.damtests.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import com.navio.damtests.QuizRepository
import com.navio.damtests.data.local.entity.Question
import com.navio.damtests.data.local.entity.TopicProgress
import com.navio.damtests.data.session.QuizSession
import com.navio.damtests.data.session.QuizSessionStore
import com.navio.damtests.data.session.SavedAnswer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class QuizViewModel @Inject constructor(
    private val repository: QuizRepository,
    private val sessionStore: QuizSessionStore
) : ViewModel() {

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

    /** The subject and topicId this test was launched with (e.g. "-4" for smart review). */
    private var currentSubjectId: String = ""
    private var currentTopicId: String = ""

    // --- Actions ---

    /**
     * Loads questions for the given subject and topic.
     *
     * Special topicId values:
     *  - "-1" → all topics (full general test, 20 questions)
     *  - "-2" → topics 1–10 (20 questions)
     *  - "-3" → topics 11–20 (20 questions)
     *  - any other → specific topic (10 questions)
     *
     * With [resume] = true it continues the saved test for this subject/topic
     * instead, falling back to a new test if it can no longer be rebuilt.
     */
    fun loadQuestions(subjectId: String, topicId: String, resume: Boolean = false) {
        currentSubjectId = subjectId
        currentTopicId = topicId
        viewModelScope.launch {
            _isLoading.value = true
            _resultsList.clear()
            _score.value = 0
            _currentQuestionIndex.value = 0
            _currentAnswerState.value = null

            if (resume && restoreSession(subjectId, topicId)) {
                _isLoading.value = false
                // Every question was already answered: go straight to the results.
                if (_resultsList.size >= _questions.value.size) finishTest()
                return@launch
            }

            val limit = if (topicId.startsWith("-")) 20 else 10

            _questions.value = when (topicId) {
                "-1"  -> repository.getRandomQuestionsForGeneralTest(subjectId, limit)
                "-2"  -> repository.getQuestionsForRange(subjectId, 1,  10, limit)
                "-3"  -> repository.getQuestionsForRange(subjectId, 11, 20, limit)
                "-4"  -> repository.getSmartReviewQuestions(subjectId, limit)
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

        viewModelScope.launch {
            repository.recordAnswer(currentQuestion, isCorrect)
        }
        saveSession()

        _currentAnswerState.value = AnswerResult(uiIndex, correctUiIndex, isCorrect)
    }

    /** Advances to the next question, or finishes the test if this was the last one. */
    fun goToNextQuestion() {
        val isLastQuestion = _currentQuestionIndex.value >= _questions.value.size - 1

        if (isLastQuestion) {
            finishTest()
        } else {
            _currentAnswerState.value = null
            _currentQuestionIndex.value += 1
        }
    }

    fun getResults(): List<QuestionResult> = _resultsList.toList()

    // --- Private helpers ---

    private fun finishTest() {
        // Mark the test finished BEFORE clearing the answer state. Otherwise the
        // currentAnswerState collector receives null while isTestFinished is still
        // false and briefly repaints a clean question behind the results dialog.
        _isTestFinished.value = true
        _currentAnswerState.value = null
        sessionStore.clear()

        // Smart review (-4) is practice only — it does not save a score.
        if (currentTopicId != "-4") {
            _questions.value.firstOrNull()?.let {
                saveFinalProgress(it.subjectId, it.topicId)
            }
        }
    }

    /** Saves the test in progress after every answer, so it can be resumed. */
    private fun saveSession() {
        val questions = _questions.value
        // Questions without a stable id couldn't be found again on resume.
        if (questions.isEmpty() || questions.any { it.stableId.isBlank() }) return

        sessionStore.save(
            QuizSession(
                subjectId   = currentSubjectId,
                topicId     = currentTopicId,
                questionIds = questions.map { it.stableId },
                answers     = _resultsList.map {
                    SavedAnswer(it.question.stableId, it.userSelectedIndex, it.shuffledOptions, it.isCorrect)
                },
                score       = _score.value
            )
        )
    }

    /**
     * Rebuilds the saved test into the current state. Returns false (and drops
     * the saved test) if there is none for this subject/topic or its questions
     * are no longer in the database.
     */
    private suspend fun restoreSession(subjectId: String, topicId: String): Boolean {
        val session = sessionStore.load()?.takeIf { it.isFor(subjectId, topicId) } ?: return false
        val questions = repository.getQuestionsInOrder(session.questionIds)
        if (questions == null || session.answeredCount > questions.size) {
            sessionStore.clear()
            return false
        }

        _questions.value = questions
        session.answers.forEachIndexed { i, a ->
            _resultsList.add(QuestionResult(questions[i], a.selectedIndex, a.shuffledOptions, a.isCorrect))
        }
        _score.value = session.score
        _currentQuestionIndex.value = session.answeredCount.coerceAtMost(questions.lastIndex)
        return true
    }

    private fun saveFinalProgress(subjectId: String, topicId: String) {
        viewModelScope.launch {
            // Local writes first: the user may leave the screen right after the
            // results dialog, which cancels viewModelScope. Recording the attempt
            // before anything that touches the network guarantees it reaches
            // Statistics even if the coroutine is cancelled afterwards.
            // Reached only for non-smart-review tests (guarded in finishTest).
            repository.recordTestAttempt(
                subjectId = subjectId,
                topicId = topicId,
                score = _score.value,
                totalQuestions = _questions.value.size
            )

            val previous         = repository.getProgress(subjectId, topicId)
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