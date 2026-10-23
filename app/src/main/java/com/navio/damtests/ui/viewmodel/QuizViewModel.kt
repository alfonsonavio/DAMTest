package com.navio.damtests.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import com.navio.damtests.QuizRepository
import com.navio.damtests.ai.FastExplainer
import com.navio.damtests.shuffle
import com.navio.damtests.data.local.entity.Question
import com.navio.damtests.data.local.entity.TopicProgress
import com.navio.damtests.data.session.QuizSession
import com.navio.damtests.data.session.QuizSessionStore
import com.navio.damtests.data.session.SavedAnswer
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class QuizViewModel @Inject constructor(
    private val repository: QuizRepository,
    private val sessionStore: QuizSessionStore,
    private val fastExplainer: FastExplainer
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

    /** True when a test was asked for but there are no questions for it. */
    private val _noQuestions = MutableStateFlow(false)
    val noQuestions: StateFlow<Boolean> = _noQuestions

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

    /**
     * The question on screen with its options in the order they are shown.
     * The shuffle lives here, not in the Activity, so leaving the app or
     * rotating the phone never reshuffles a question (which used to misplace
     * the highlighted answer and repeat the vibration and the AI request).
     */
    data class CurrentQuestion(
        val index: Int,
        val question: Question,
        val options: List<String>
    )

    private val _currentQuestion = MutableStateFlow<CurrentQuestion?>(null)
    val currentQuestion: StateFlow<CurrentQuestion?> = _currentQuestion

    /** Instant AI feedback for a wrong answer to the current question. */
    sealed class AiFeedback {
        object Loading : AiFeedback()
        /** [wrong] is null when the model did not return the two-part format. */
        data class Ready(val wrong: String?, val correct: String) : AiFeedback()
        object Error : AiFeedback()
    }

    private val _aiFeedback = MutableStateFlow<AiFeedback?>(null)
    val aiFeedback: StateFlow<AiFeedback?> = _aiFeedback
    private var aiJob: Job? = null

    /** Options already shown per question index, so a question keeps its order. */
    private val optionsByIndex = mutableMapOf<Int, List<String>>()

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
            optionsByIndex.clear()
            clearAiFeedback()

            if (resume && restoreSession(subjectId, topicId)) {
                showQuestion(_currentQuestionIndex.value)
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

            showQuestion(0)
            _noQuestions.value = _questions.value.isEmpty()
            _isLoading.value = false
        }
    }

    /**
     * Evaluates the answer the user tapped.
     *
     * @param selectedText   Text of the button the user tapped (as shown on screen).
     * @param shuffledOptions All four options as displayed on screen.
     */
    fun checkAnswer(selectedText: String, shuffledOptions: List<String>): AnswerResult? {
        val currentQuestion = _questions.value.getOrNull(_currentQuestionIndex.value) ?: return null
        // Ignore extra taps after the answer is already recorded
        if (_currentAnswerState.value != null) return null

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

        val result = AnswerResult(uiIndex, correctUiIndex, isCorrect)
        _currentAnswerState.value = result
        if (!isCorrect) requestAiFeedback(currentQuestion.text, selectedText, correctText)
        return result
    }

    /** Advances to the next question, or finishes the test if this was the last one. */
    fun goToNextQuestion() {
        val isLastQuestion = _currentQuestionIndex.value >= _questions.value.size - 1

        if (isLastQuestion) {
            finishTest()
        } else {
            clearAiFeedback()
            _currentAnswerState.value = null
            _currentQuestionIndex.value += 1
            showQuestion(_currentQuestionIndex.value)
        }
    }

    fun getResults(): List<QuestionResult> = _resultsList.toList()

    // --- Private helpers ---

    /** Publishes the question at [index] with a stable option order. */
    private fun showQuestion(index: Int) {
        val question = _questions.value.getOrNull(index) ?: run {
            _currentQuestion.value = null
            return
        }
        val options = _resultsList.getOrNull(index)?.shuffledOptions
            ?: optionsByIndex.getOrPut(index) { question.shuffle().shuffledOptions }
        _currentQuestion.value = CurrentQuestion(index, question, options)
    }

    private fun requestAiFeedback(question: String, chosen: String, correct: String) {
        aiJob?.cancel()
        _aiFeedback.value = AiFeedback.Loading
        aiJob = viewModelScope.launch {
            _aiFeedback.value = try {
                val response = fastExplainer.explicarRapido(question, chosen, correct)
                val parts = response.split("|")
                if (parts.size >= 2) AiFeedback.Ready(parts[0].trim(), parts[1].trim())
                else AiFeedback.Ready(null, response.trim())
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                AiFeedback.Error
            }
        }
    }

    /** Drops the AI feedback, cancelling a request still in flight. */
    private fun clearAiFeedback() {
        aiJob?.cancel()
        aiJob = null
        _aiFeedback.value = null
    }

    private fun finishTest() {
        // Mark the test finished BEFORE clearing the answer state. Otherwise the
        // currentAnswerState collector receives null while isTestFinished is still
        // false and briefly repaints a clean question behind the results dialog.
        _isTestFinished.value = true
        clearAiFeedback()
        _currentAnswerState.value = null
        sessionStore.clear()

        // Smart review (-4) is practice only — it does not save a score.
        // The score belongs to the test that was launched (e.g. "-1" for the
        // full general test), not to the topic of its first question.
        if (currentTopicId != "-4" && _questions.value.isNotEmpty()) {
            saveFinalProgress(currentSubjectId, currentTopicId)
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
