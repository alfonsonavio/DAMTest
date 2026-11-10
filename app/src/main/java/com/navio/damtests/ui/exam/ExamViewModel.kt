package com.navio.damtests.ui.exam

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.navio.damtests.data.exam.Exam
import com.navio.damtests.data.exam.ExamClock
import com.navio.damtests.data.exam.ExamConfig
import com.navio.damtests.data.exam.ExamGrade
import com.navio.damtests.data.exam.ExamGrader
import com.navio.damtests.data.local.entity.Question
import com.navio.damtests.data.quiz.QuizRepository
import com.navio.damtests.data.quiz.shuffle
import com.navio.damtests.ui.quiz.QuestionResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Exam mode: a timed test with no feedback until it is handed in.
 *
 * The user can move freely between questions, change or clear an answer and
 * flag questions to check later. The exam is graded when it is submitted or
 * when the time runs out, whichever comes first. The timer works with a
 * deadline, not a countdown, so time keeps passing while the app is in the
 * background. The screen calls [refreshTimer] every second while visible;
 * the ViewModel runs no endless loop of its own (easy to test, nothing left
 * running when the screen is gone).
 *
 * A finished exam counts as a real test (history, statistics, streak and
 * per-question stats) but saves no topic progress: it covers many topics.
 */
@HiltViewModel
class ExamViewModel @Inject constructor(
    private val repository: QuizRepository,
    private val clock: ExamClock
) : ViewModel() {

    /** A question with its options in the order shown, fixed for the whole exam. */
    data class ExamQuestion(val question: Question, val options: List<String>) {
        val correctOption: String
            get() = when (question.correctOptionIndex) {
                0    -> question.optionA
                1    -> question.optionB
                2    -> question.optionC
                else -> question.optionD
            }
    }

    data class ExamState(
        val loading: Boolean = true,
        val noQuestions: Boolean = false,
        val questions: List<ExamQuestion> = emptyList(),
        /** Selected option (shown order) per question index; missing = blank. */
        val answers: Map<Int, Int> = emptyMap(),
        val flagged: Set<Int> = emptySet(),
        val currentIndex: Int = 0,
        val remainingSeconds: Long = 0
    ) {
        val total: Int get() = questions.size
        val answeredCount: Int get() = answers.size
        val current: ExamQuestion? get() = questions.getOrNull(currentIndex)
        val isLast: Boolean get() = currentIndex == questions.lastIndex
    }

    /** What the results dialog and the review screen need. */
    data class ExamOutcome(
        val grade: ExamGrade,
        val results: List<QuestionResult>,
        val timeUp: Boolean
    )

    private val _state = MutableStateFlow(ExamState())
    val state: StateFlow<ExamState> = _state

    private val _outcome = MutableStateFlow<ExamOutcome?>(null)
    val outcome: StateFlow<ExamOutcome?> = _outcome

    private lateinit var config: ExamConfig
    private var deadline = 0L
    private var started = false

    /** Loads the questions and starts the clock. Ignored after the first call. */
    fun start(config: ExamConfig) {
        if (started) return
        started = true
        this.config = config

        viewModelScope.launch {
            val questions = repository.getExamQuestions(config.subjectId, config.range, config.size)
                .map { ExamQuestion(it, it.shuffle().shuffledOptions) }

            if (questions.isEmpty()) {
                _state.value = ExamState(loading = false, noQuestions = true)
                return@launch
            }

            // The time depends on the questions actually drawn
            val duration = Exam.durationSeconds(questions.size)
            deadline = clock.now() + duration * 1000
            _state.value = ExamState(loading = false, questions = questions, remainingSeconds = duration)
        }
    }

    /** Selects an option of the current question; tapping it again clears it. */
    fun selectOption(optionIndex: Int) {
        if (isClosed()) return
        _state.update { s ->
            val answers = if (s.answers[s.currentIndex] == optionIndex) s.answers - s.currentIndex
            else s.answers + (s.currentIndex to optionIndex)
            s.copy(answers = answers)
        }
    }

    /** Flags the current question to check it before handing in, or unflags it. */
    fun toggleFlag() {
        if (isClosed()) return
        _state.update { s ->
            val flagged = if (s.currentIndex in s.flagged) s.flagged - s.currentIndex
            else s.flagged + s.currentIndex
            s.copy(flagged = flagged)
        }
    }

    fun goTo(index: Int) {
        if (isClosed()) return
        _state.update { s -> if (index in s.questions.indices) s.copy(currentIndex = index) else s }
    }

    fun next() = goTo(_state.value.currentIndex + 1)

    fun previous() = goTo(_state.value.currentIndex - 1)

    /** Hands the exam in: grades it and records it. Only the first call counts. */
    fun submit() = finish(timeUp = false)

    /**
     * Updates the remaining time and hands the exam in when it runs out.
     * Called every second by the screen and when it comes back to the front.
     */
    fun refreshTimer() {
        if (isClosed() || deadline == 0L) return
        val remaining = ((deadline - clock.now()) / 1000).coerceAtLeast(0)
        _state.update { it.copy(remainingSeconds = remaining) }
        if (remaining == 0L) finish(timeUp = true)
    }

    // --- Private helpers ---

    private fun isClosed() = _outcome.value != null || _state.value.questions.isEmpty()

    private fun finish(timeUp: Boolean) {
        if (isClosed()) return
        val s = _state.value

        val results = s.questions.mapIndexed { i, q ->
            val selected = s.answers[i] ?: QuestionResult.NOT_ANSWERED
            val isCorrect = selected != QuestionResult.NOT_ANSWERED &&
                    q.options[selected] == q.correctOption
            QuestionResult(q.question, selected, q.options, isCorrect)
        }
        val correct = results.count { it.isCorrect }
        val blank   = results.count { !it.isAnswered }
        val grade   = ExamGrader.grade(correct, results.size - correct - blank, blank, config.penalized)

        _outcome.value = ExamOutcome(grade, results, timeUp)
        if (timeUp) _state.update { it.copy(remainingSeconds = 0) }
        record(results, correct)
    }

    /**
     * Saves the per-question stats of the answered questions (a blank answer
     * says nothing about what the user knows) and the attempt, which counts
     * as one more test in Statistics and keeps the streak going.
     */
    private fun record(results: List<QuestionResult>, correct: Int) {
        viewModelScope.launch {
            // The attempt goes first: it is a single local write, so it is kept
            // even if the user leaves the screen straight away.
            repository.recordTestAttempt(
                subjectId = config.subjectId,
                topicId = config.range.mode.topicId,
                score = correct,
                totalQuestions = results.size
            )
            results.filter { it.isAnswered }
                .forEach { repository.recordAnswer(it.question, it.isCorrect) }
        }
    }
}