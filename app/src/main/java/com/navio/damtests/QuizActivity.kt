package com.navio.damtests

import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.animation.AnimationUtils
import android.view.WindowInsetsController
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.viewModels
import dagger.hilt.android.AndroidEntryPoint
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.button.MaterialButton
import com.navio.damtests.auth.AuthUiHelper
import com.navio.damtests.settings.SettingsManager
import com.navio.damtests.ui.viewmodel.QuizViewModel
import com.navio.damtests.util.HapticFeedbackManager
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class QuizActivity : AppCompatActivity() {

    @Inject lateinit var settingsManager: SettingsManager

    private val viewModel: QuizViewModel by viewModels()
    private lateinit var tvQuestion: TextView
    private lateinit var btnA: Button
    private lateinit var btnB: Button
    private lateinit var btnC: Button
    private lateinit var btnD: Button
    private lateinit var tvFeedbackA: TextView
    private lateinit var tvFeedbackB: TextView
    private lateinit var tvFeedbackC: TextView
    private lateinit var tvFeedbackD: TextView
    private lateinit var tvCount: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var btnContextInfo: Button
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
        setContentView(R.layout.activity_quiz)

        haptic      = HapticFeedbackManager(this, settingsManager.hapticsEnabled)
        quizContent = findViewById(R.id.quizContentContainer)

        applyStatusBar()

        subjectId = intent.getStringExtra("SUBJECT_ID") ?: "programacion"
        topicId   = intent.getStringExtra("TOPIC_ID")   ?: "tema_1"

        tvQuestion     = findViewById(R.id.tvQuestionText)
        btnA           = findViewById(R.id.btnOptionA)
        btnB           = findViewById(R.id.btnOptionB)
        btnC           = findViewById(R.id.btnOptionC)
        btnD           = findViewById(R.id.btnOptionD)
        tvFeedbackA    = findViewById(R.id.tvFeedbackA)
        tvFeedbackB    = findViewById(R.id.tvFeedbackB)
        tvFeedbackC    = findViewById(R.id.tvFeedbackC)
        tvFeedbackD    = findViewById(R.id.tvFeedbackD)
        tvCount        = findViewById(R.id.tvQuestionCount)
        progressBar    = findViewById(R.id.quizProgressBar)
        btnContextInfo = findViewById(R.id.btnContextInfo)
        btnNext        = findViewById(R.id.btnNextQuestion)


        setupObservers()
        setupClickListeners()
        if (viewModel.questions.value.isEmpty()) {
            viewModel.loadQuestions(
                subjectId, topicId,
                resume = intent.getBooleanExtra(EXTRA_RESUME, false)
            )
        }
    }

    // --- Observers ---

    private fun setupObservers() {
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

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.currentAnswerState.collect { result ->
                    when {
                        result != null                  -> highlightAnswer(result)
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
    }

    // --- UI helpers ---

    private fun refreshCountAndProgress(index: Int, total: Int) {
        val pos = index + 1
        tvCount.text         = "$pos de $total"
        progressBar.progress = pos
        btnNext.text = if (pos == total) getString(R.string.finish_test)
        else getString(R.string.next_question)
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
        btnA.text       = current.options[0]
        btnB.text       = current.options[1]
        btnC.text       = current.options[2]
        btnD.text       = current.options[3]

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
        val buttons = listOf(btnA, btnB, btnC, btnD)
        setAnswerButtonsEnabled(false)

        buttons.forEachIndexed { index, button ->
            val mBtn          = button as MaterialButton
            val isCorrectBtn  = index == result.correctIndex
            val isSelectedBtn = index == result.selectedIndex

            mBtn.strokeWidth = when {
                isCorrectBtn                       -> 8
                isSelectedBtn && !result.isCorrect -> 8
                else                               -> 0
            }
            if (isCorrectBtn) {
                mBtn.setStrokeColorResource(R.color.state_success)
            } else if (isSelectedBtn && !result.isCorrect) {
                mBtn.setStrokeColorResource(R.color.state_error)
            }
        }

        btnNext.visibility = View.VISIBLE
    }

    /** Shows the instant AI explanation under the chosen and the correct option. */
    private fun showAiFeedback(feedback: QuizViewModel.AiFeedback?) {
        val result = viewModel.currentAnswerState.value
        if (feedback == null || result == null || result.isCorrect) return

        val feedbacks = listOf(tvFeedbackA, tvFeedbackB, tvFeedbackC, tvFeedbackD)
        val wrongView   = feedbacks.getOrNull(result.selectedIndex) ?: return
        val correctView = feedbacks.getOrNull(result.correctIndex) ?: return

        when (feedback) {
            QuizViewModel.AiFeedback.Loading -> {
                wrongView.show(getString(R.string.ai_analyzing), R.color.text_muted)
                correctView.show(getString(R.string.ai_preparing_correction), R.color.text_muted)
            }
            is QuizViewModel.AiFeedback.Ready -> {
                if (feedback.wrong != null) {
                    wrongView.show("❌ ${feedback.wrong}", R.color.state_error)
                } else {
                    wrongView.visibility = View.GONE
                }
                correctView.show("✅ ${feedback.correct}", R.color.state_success)
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
        val buttons   = listOf(btnA, btnB, btnC, btnD)
        val feedbacks = listOf(tvFeedbackA, tvFeedbackB, tvFeedbackC, tvFeedbackD)

        buttons.forEach { (it as MaterialButton).strokeWidth = 0 }
        feedbacks.forEach { it.visibility = View.GONE; it.text = "" }
        btnNext.visibility = View.GONE
        setAnswerButtonsEnabled(true)
    }

    // --- Click listeners ---

    private fun setupClickListeners() {
        btnA.setOnClickListener { processAnswer(0) }
        btnB.setOnClickListener { processAnswer(1) }
        btnC.setOnClickListener { processAnswer(2) }
        btnD.setOnClickListener { processAnswer(3) }
        btnNext.setOnClickListener { viewModel.goToNextQuestion() }
    }

    private fun processAnswer(uiIndex: Int) {
        val current = viewModel.currentQuestion.value ?: return
        val result  = viewModel.checkAnswer(current.options[uiIndex], current.options) ?: return
        // Vibrate here, on the tap itself, so it never repeats when the
        // answer is repainted after the app comes back to the foreground.
        if (result.isCorrect) haptic.correct() else haptic.wrong()
    }

    private fun setAnswerButtonsEnabled(enabled: Boolean) {
        listOf(btnA, btnB, btnC, btnD).forEach { it.isEnabled = enabled }
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
            Intent(this, ReviewActivity::class.java)
                .putExtra("SUBJECT_ID", subjectId)
                .putExtra("TOPIC_ID", topicId)
                .putExtra("SCORE", viewModel.score.value)
                .putExtra("TOTAL", results.size)
                .putParcelableArrayListExtra("RESULTS", ArrayList(results))
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

    private fun applyStatusBar() {
        window.statusBarColor = getColor(R.color.surface_bg)
        // Light status bar (dark icons) only in light mode; dark mode gets light icons.
        val lightBars = (resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK) != Configuration.UI_MODE_NIGHT_YES
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
            window.insetsController?.setSystemBarsAppearance(if (lightBars) mask else 0, mask)
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility =
                if (lightBars) View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR else 0
        }
    }

    companion object {
        /** Boolean extra: continue the saved test instead of starting a new one. */
        const val EXTRA_RESUME = "RESUME"
    }
}
