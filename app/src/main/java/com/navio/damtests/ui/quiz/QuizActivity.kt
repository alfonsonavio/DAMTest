package com.navio.damtests.ui.quiz

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.Button
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.button.MaterialButton
import com.navio.damtests.R
import com.navio.damtests.core.HapticFeedbackManager
import com.navio.damtests.data.settings.SettingsManager
import com.navio.damtests.ui.common.AuthUiHelper
import com.navio.damtests.ui.common.setupEdgeToEdge
import com.navio.damtests.ui.review.ReviewActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class QuizActivity : AppCompatActivity() {

    @Inject lateinit var settingsManager: SettingsManager

    private val viewModel: QuizViewModel by viewModels()
    private lateinit var tvQuestion: TextView

    /** The four answer buttons and the AI feedback under each one, in option order (a-d). */
    private lateinit var optionButtons: List<MaterialButton>
    private lateinit var feedbackViews: List<TextView>
    private lateinit var tvCount: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var btnContextInfo: Button
    private lateinit var btnFavorite: ImageButton
    private lateinit var btnNext: Button

    private lateinit var haptic: HapticFeedbackManager
    private var resultsDialog: AlertDialog? = null
    private var noQuestionsShown = false

    /** Last question index drawn, so the slide-in animation runs once per question. */
    private var renderedIndex = -1
    private lateinit var quizContent: View

    // Subject and topic received from TopicSelectionActivity
    private lateinit var subjectId: String
    private lateinit var topicId: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupEdgeToEdge(darkTop = false)
        setContentView(R.layout.activity_quiz)

        haptic = HapticFeedbackManager(this, settingsManager.hapticsEnabled)
        quizContent = findViewById(R.id.quizContentContainer)

        subjectId = intent.getStringExtra(EXTRA_SUBJECT_ID) ?: return finish()
        topicId = intent.getStringExtra(EXTRA_TOPIC_ID) ?: return finish()

        tvQuestion = findViewById(R.id.tvQuestionText)
        optionButtons = listOf(R.id.btnOptionA, R.id.btnOptionB, R.id.btnOptionC, R.id.btnOptionD)
            .map { findViewById<MaterialButton>(it) }
        feedbackViews = listOf(R.id.tvFeedbackA, R.id.tvFeedbackB, R.id.tvFeedbackC, R.id.tvFeedbackD)
            .map { findViewById<TextView>(it) }
        tvCount = findViewById(R.id.tvQuestionCount)
        progressBar = findViewById(R.id.quizProgressBar)
        btnContextInfo = findViewById(R.id.btnContextInfo)
        btnFavorite = findViewById(R.id.btnFavorite)
        btnNext = findViewById(R.id.btnNextQuestion)

        observeQuestion()
        observeAnswer()
        setupClickListeners()
        if (viewModel.questions.value.isEmpty()) {
            viewModel.loadQuestions(
                subjectId,
                topicId,
                resume = intent.getBooleanExtra(EXTRA_RESUME, false)
            )
        }
    }

    // --- Observers ---

    /** The question on screen, loading, and the end of the test. */
    private fun observeQuestion() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.currentQuestion.collect { current ->
                    if (current != null) {
                        refreshCountAndProgress(current.index, viewModel.questions.value.size)
                        updateUI(current)
                    }
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.isLoading.collect { loading ->
                    setAnswerButtonsEnabled(!loading)
                    if (loading) tvQuestion.text = getString(R.string.loading)
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.noQuestions.collect { empty ->
                    if (empty) showNoQuestions()
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.isTestFinished.collect { finished ->
                    if (finished) showResultsDialog(viewModel.score.value)
                }
            }
        }
    }

    /** The answer given to the current question, its AI feedback and the favourite star. */
    private fun observeAnswer() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.currentAnswerState.collect { result ->
                    when {
                        result != null -> highlightAnswer(result)
                        !viewModel.isTestFinished.value -> resetAnswerUI()
                    }
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.aiFeedback.collect { feedback -> showAiFeedback(feedback) }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.isCurrentFavorite.collect { favorite ->
                    btnFavorite.setImageResource(
                        if (favorite) R.drawable.ic_star else R.drawable.ic_star_outline
                    )
                    btnFavorite.contentDescription =
                        getString(if (favorite) R.string.favorite_remove else R.string.favorite_add)
                }
            }
        }
    }

    // --- UI helpers ---

    private fun refreshCountAndProgress(index: Int, total: Int) {
        val pos = index + 1
        tvCount.text = getString(R.string.quiz_question_count, pos, total)
        // Set the max every time: after a rotation the bar is a new view whose
        // max would otherwise stay at the default 100.
        progressBar.max = total
        progressBar.progress = pos
        btnNext.text = if (pos == total) {
            getString(R.string.finish_test)
        } else {
            getString(R.string.next_question)
        }
    }

    private fun updateUI(current: QuizViewModel.CurrentQuestion) {
        // Slide the question content in from the right, once per question
        if (current.index != renderedIndex) {
            quizContent.startAnimation(
                AnimationUtils.loadAnimation(this, R.anim.slide_in_right)
            )
            renderedIndex = current.index
        }

        val question = current.question
        tvQuestion.text = question.text
        // Questions without a stable id can't be saved as favourites
        btnFavorite.visibility = if (question.stableId.isBlank()) View.GONE else View.VISIBLE
        optionButtons.forEachIndexed { index, button -> button.text = current.options[index] }

        val contextText = question.contextText
        if (!contextText.isNullOrEmpty()) {
            btnContextInfo.visibility = View.VISIBLE
            btnContextInfo.setOnClickListener { showContextDialog(contextText) }
        } else {
            btnContextInfo.visibility = View.GONE
        }
    }

    /** Paints the answer outlines. Safe to call again after the app comes back. */
    private fun highlightAnswer(result: QuizViewModel.AnswerResult) {
        setAnswerButtonsEnabled(false)
        val outline = resources.getDimensionPixelSize(R.dimen.answer_outline_width)

        optionButtons.forEachIndexed { index, button ->
            val isCorrectBtn = index == result.correctIndex
            val isWrongChoice = index == result.selectedIndex && !result.isCorrect

            button.strokeWidth = if (isCorrectBtn || isWrongChoice) outline else 0
            if (isCorrectBtn) {
                button.setStrokeColorResource(R.color.state_success)
            } else if (isWrongChoice) {
                button.setStrokeColorResource(R.color.state_error)
            }
        }

        btnNext.visibility = View.VISIBLE
    }

    /** Shows the instant AI explanation under the chosen and the correct option. */
    private fun showAiFeedback(feedback: QuizViewModel.AiFeedback?) {
        val result = viewModel.currentAnswerState.value
        if (feedback == null || result == null || result.isCorrect) return

        val wrongView = feedbackViews.getOrNull(result.selectedIndex) ?: return
        val correctView = feedbackViews.getOrNull(result.correctIndex) ?: return

        when (feedback) {
            QuizViewModel.AiFeedback.Loading -> {
                wrongView.show(getString(R.string.ai_analyzing), R.color.text_muted)
                correctView.show(getString(R.string.ai_preparing_correction), R.color.text_muted)
            }
            is QuizViewModel.AiFeedback.Ready -> {
                if (feedback.wrong != null) {
                    wrongView.show(getString(R.string.ai_feedback_wrong, feedback.wrong), R.color.state_error)
                } else {
                    wrongView.visibility = View.GONE
                }
                correctView.show(getString(R.string.ai_feedback_correct, feedback.correct), R.color.state_success)
            }
            QuizViewModel.AiFeedback.Error -> {
                wrongView.show(getString(R.string.ai_connection_error), R.color.state_error)
                correctView.visibility = View.GONE
            }
        }
    }

    private fun TextView.show(message: String, colorRes: Int) {
        visibility = View.VISIBLE
        text = message
        setTextColor(getColor(colorRes))
    }

    private fun resetAnswerUI() {
        optionButtons.forEach { it.strokeWidth = 0 }
        feedbackViews.forEach {
            it.visibility = View.GONE
            it.text = ""
        }
        btnNext.visibility = View.GONE
        setAnswerButtonsEnabled(true)
    }

    // --- Click listeners ---

    private fun setupClickListeners() {
        optionButtons.forEachIndexed { index, button -> button.setOnClickListener { processAnswer(index) } }
        btnNext.setOnClickListener { viewModel.goToNextQuestion() }
        btnFavorite.setOnClickListener { viewModel.toggleFavorite() }
    }

    private fun processAnswer(uiIndex: Int) {
        val current = viewModel.currentQuestion.value ?: return
        val result = viewModel.checkAnswer(current.options[uiIndex], current.options) ?: return
        // Vibrate here, on the tap itself, so it never repeats when the
        // answer is repainted after the app comes back to the foreground.
        if (result.isCorrect) haptic.correct() else haptic.wrong()
    }

    private fun setAnswerButtonsEnabled(enabled: Boolean) {
        optionButtons.forEach { it.isEnabled = enabled }
    }

    // --- Dialogs ---

    private fun showResultsDialog(score: Int) {
        // The finished state is re-delivered whenever the screen comes back:
        // show the dialog only once.
        if (resultsDialog?.isShowing == true) return
        val dialogView = layoutInflater.inflate(R.layout.dialog_results, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialogView.findViewById<TextView>(R.id.tvDialogMessage).text =
            resources.getQuantityString(R.plurals.quiz_result_message, score, score)

        dialogView.findViewById<Button>(R.id.btnDialogReview).setOnClickListener {
            dialog.dismiss()
            showReviewScreen()
        }
        dialogView.findViewById<Button>(R.id.btnDialogExit).setOnClickListener {
            dialog.dismiss()
            finish()
        }
        resultsDialog = dialog
        dialog.show()
    }

    override fun onDestroy() {
        resultsDialog?.dismiss()
        resultsDialog = null
        super.onDestroy()
    }

    private fun showReviewScreen() {
        val results = viewModel.getResults()
        startActivity(
            ReviewActivity.intent(this, subjectId, topicId, viewModel.score.value, results)
        )
        finish()
    }

    /** Safety net: never leave the user on an endless "Cargando…" screen. */
    private fun showNoQuestions() {
        if (noQuestionsShown) return
        noQuestionsShown = true
        AuthUiHelper.showInfo(
            this,
            getString(R.string.no_questions_title),
            getString(R.string.no_questions_yet)
        ) { finish() }
    }

    private fun showContextDialog(text: String) {
        AlertDialog.Builder(this)
            .setTitle(R.string.context_dialog_title)
            .setMessage(text)
            .setPositiveButton(R.string.close, null)
            .show()
    }

    companion object {
        private const val EXTRA_SUBJECT_ID = "SUBJECT_ID"
        private const val EXTRA_TOPIC_ID = "TOPIC_ID"

        /** Continue the saved test instead of starting a new one. */
        private const val EXTRA_RESUME = "RESUME"

        /** A test of [topicId] (a regular topic or a [com.navio.damtests.data.quiz.TestMode] id). */
        fun intent(context: Context, subjectId: String, topicId: String, resume: Boolean = false): Intent =
            Intent(context, QuizActivity::class.java)
                .putExtra(EXTRA_SUBJECT_ID, subjectId)
                .putExtra(EXTRA_TOPIC_ID, topicId)
                .putExtra(EXTRA_RESUME, resume)
    }
}
