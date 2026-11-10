package com.navio.damtests.ui.exam

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.IntentCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.navio.damtests.R
import com.navio.damtests.data.exam.ExamConfig
import com.navio.damtests.data.exam.ExamGrader
import com.navio.damtests.ui.common.AuthUiHelper
import com.navio.damtests.ui.common.setupEdgeToEdge
import com.navio.damtests.ui.review.ReviewActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Timed exam screen. Answers can be changed until the exam is handed in; the
 * correction only appears afterwards, in the results dialog and the review.
 */
@AndroidEntryPoint
class ExamActivity : AppCompatActivity() {

    private val viewModel: ExamViewModel by viewModels()
    private lateinit var config: ExamConfig

    private lateinit var tvCount: TextView
    private lateinit var tvTimer: TextView
    private lateinit var tvAnswered: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvQuestion: TextView
    private lateinit var btnFlag: MaterialButton
    private lateinit var btnContext: MaterialButton
    private lateinit var options: List<MaterialButton>
    private lateinit var btnPrevious: MaterialButton
    private lateinit var btnNext: MaterialButton

    private var gridSheet: BottomSheetDialog? = null
    private var gridAdapter: ExamGridAdapter? = null
    private var resultDialog: AlertDialog? = null
    private var noQuestionsShown = false

    /** Previous remaining time, to warn once when 5 and 1 minutes are left. */
    private var lastRemaining = -1L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupEdgeToEdge(darkTop = false)
        setContentView(R.layout.activity_exam)
        // A timed exam shouldn't be interrupted by the screen switching off
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        tvCount = findViewById(R.id.tvExamCount)
        tvTimer = findViewById(R.id.tvExamTimer)
        tvAnswered = findViewById(R.id.tvExamAnswered)
        progressBar = findViewById(R.id.examProgressBar)
        tvQuestion = findViewById(R.id.tvExamQuestion)
        btnFlag = findViewById(R.id.btnFlagQuestion)
        btnContext = findViewById(R.id.btnExamContext)
        btnPrevious = findViewById(R.id.btnExamPrevious)
        btnNext = findViewById(R.id.btnExamNext)
        options = listOf(
            findViewById(R.id.btnExamOptionA),
            findViewById(R.id.btnExamOptionB),
            findViewById(R.id.btnExamOptionC),
            findViewById(R.id.btnExamOptionD)
        )

        config = IntentCompat.getParcelableExtra(intent, EXTRA_CONFIG, ExamConfig::class.java)
            ?: return finish()
        viewModel.start(config)

        setupClicks()
        setupObservers()

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() = confirmExit()
            }
        )
    }

    override fun onDestroy() {
        resultDialog?.dismiss()
        gridSheet?.dismiss()
        super.onDestroy()
    }

    // --- Setup ---

    private fun setupClicks() {
        findViewById<ImageButton>(R.id.btnExitExam).setOnClickListener { confirmExit() }
        options.forEachIndexed { i, button -> button.setOnClickListener { viewModel.selectOption(i) } }
        btnFlag.setOnClickListener { viewModel.toggleFlag() }
        btnPrevious.setOnClickListener { viewModel.previous() }
        btnNext.setOnClickListener {
            if (viewModel.state.value.isLast) confirmSubmit() else viewModel.next()
        }
        findViewById<MaterialButton>(R.id.btnExamGrid).setOnClickListener { showGrid() }
    }

    private fun setupObservers() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { render(it) }
            }
        }
        // Clock: ticks while the screen is visible. The time is measured from a
        // deadline, so on coming back it jumps straight to the real remaining
        // time (and hands the exam in if it ran out meanwhile).
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    viewModel.refreshTimer()
                    delay(TIMER_TICK_MS)
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.outcome.collect { outcome -> if (outcome != null) showResult(outcome) }
            }
        }
    }

    // --- Rendering ---

    private fun render(state: ExamViewModel.ExamState) {
        if (state.noQuestions) return showNoQuestions()
        val current = state.current ?: run {
            tvQuestion.text = getString(R.string.loading)
            return
        }

        tvCount.text = getString(R.string.exam_question_count, state.currentIndex + 1, state.total)
        progressBar.max = state.total
        progressBar.progress = state.answeredCount
        tvAnswered.text = resources.getQuantityString(
            R.plurals.exam_answered_count,
            state.total,
            state.answeredCount,
            state.total
        )
        renderTimer(state.remainingSeconds)

        tvQuestion.text = current.question.text
        val selected = state.answers[state.currentIndex]
        options.forEachIndexed { i, button ->
            button.text = current.options[i]
            paintOption(button, isSelected = i == selected)
        }

        val flagged = state.currentIndex in state.flagged
        btnFlag.setText(if (flagged) R.string.exam_flagged else R.string.exam_flag)
        btnFlag.iconTint = ColorStateList.valueOf(
            getColor(if (flagged) R.color.favorite_star else R.color.text_muted)
        )

        val contextText = current.question.contextText
        btnContext.visibility = if (contextText.isNullOrEmpty()) View.GONE else View.VISIBLE
        btnContext.setOnClickListener { contextText?.let { showContext(it) } }

        btnPrevious.isEnabled = state.currentIndex > 0
        btnNext.setText(if (state.isLast) R.string.exam_submit else R.string.exam_next)

        gridAdapter?.update(state)
        gridSheet?.findViewById<TextView>(R.id.tvSheetSummary)?.text = summary(state)
    }

    private fun paintOption(button: MaterialButton, isSelected: Boolean) {
        button.backgroundTintList = ColorStateList.valueOf(
            getColor(if (isSelected) R.color.brand_primary else R.color.surface)
        )
        button.setTextColor(getColor(if (isSelected) R.color.text_on_dark else R.color.text_primary))
        button.strokeColor = ColorStateList.valueOf(
            getColor(if (isSelected) R.color.brand_primary else R.color.surface_border)
        )
        button.strokeWidth = resources.displayMetrics.density.times(if (isSelected) 2 else 1).toInt()
    }

    private fun renderTimer(remaining: Long) {
        tvTimer.text = getString(R.string.exam_timer, remaining / SECONDS_PER_MINUTE, remaining % SECONDS_PER_MINUTE)
        val timerColor = if (remaining <= LAST_MINUTE_SECONDS) R.color.state_error else R.color.text_primary
        tvTimer.setTextColor(getColor(timerColor))

        // Warn once when crossing 5 and 1 minutes (not after a rotation)
        if (lastRemaining > 0 && viewModel.outcome.value == null) {
            when {
                lastRemaining > FIVE_MINUTES_SECONDS && remaining <= FIVE_MINUTES_SECONDS ->
                    warn(getString(R.string.exam_warning_five_minutes))
                lastRemaining > LAST_MINUTE_SECONDS && remaining <= LAST_MINUTE_SECONDS ->
                    warn(getString(R.string.exam_warning_one_minute))
            }
        }
        lastRemaining = remaining
    }

    private fun warn(message: String) {
        Snackbar.make(tvQuestion, message, Snackbar.LENGTH_LONG).show()
    }

    private fun summary(state: ExamViewModel.ExamState): String {
        val blank = state.total - state.answeredCount
        return counts(
            R.plurals.exam_answered to state.answeredCount,
            R.plurals.exam_blank to blank,
            R.plurals.exam_flagged_questions to state.flagged.size
        )
    }

    /** "9 respondidas · 1 en blanco · 1 dudosa": each count with its own plural. */
    private fun counts(vararg parts: Pair<Int, Int>): String =
        parts.joinToString(getString(R.string.list_separator)) { (plural, count) ->
            resources.getQuantityString(plural, count, count)
        }

    // --- Question grid ---

    private fun showGrid() {
        if (gridSheet?.isShowing == true) return
        val sheet = BottomSheetDialog(this)
        sheet.setContentView(R.layout.sheet_exam_grid)

        val adapter = ExamGridAdapter { index ->
            viewModel.goTo(index)
            sheet.dismiss()
        }
        sheet.findViewById<RecyclerView>(R.id.rvExamGrid)?.adapter = adapter
        sheet.findViewById<MaterialButton>(R.id.btnSheetSubmit)?.setOnClickListener {
            sheet.dismiss()
            confirmSubmit()
        }
        sheet.setOnDismissListener {
            gridSheet = null
            gridAdapter = null
        }

        gridSheet = sheet
        gridAdapter = adapter
        val state = viewModel.state.value
        adapter.update(state)
        sheet.findViewById<TextView>(R.id.tvSheetSummary)?.text = summary(state)
        sheet.show()
    }

    // --- Dialogs ---

    private fun confirmSubmit() {
        val state = viewModel.state.value
        val blank = state.total - state.answeredCount
        val details = buildList {
            if (blank > 0) add(resources.getQuantityString(R.plurals.exam_blank_questions, blank, blank))
            if (state.flagged.isNotEmpty()) {
                val flagged = state.flagged.size
                add(resources.getQuantityString(R.plurals.exam_flagged_questions, flagged, flagged))
            }
        }
        val message = if (details.isEmpty()) {
            getString(R.string.exam_submit_all_answered)
        } else {
            getString(R.string.exam_submit_pending, details.joinToString(getString(R.string.list_and)))
        }
        AuthUiHelper.showConfirm(
            this,
            getString(R.string.exam_submit_title),
            message,
            getString(R.string.exam_submit)
        ) { viewModel.submit() }
    }

    private fun confirmExit() {
        if (viewModel.outcome.value != null || viewModel.state.value.questions.isEmpty()) {
            finish()
            return
        }
        AuthUiHelper.showConfirm(
            this,
            getString(R.string.exam_exit_title),
            getString(R.string.exam_exit_message),
            getString(R.string.exam_exit_confirm)
        ) { finish() }
    }

    private fun showResult(outcome: ExamViewModel.ExamOutcome) {
        // The outcome is re-delivered when the screen comes back: show it once
        if (resultDialog?.isShowing == true) return
        gridSheet?.dismiss()

        val view = layoutInflater.inflate(R.layout.dialog_exam_result, null)
        val dialog = AlertDialog.Builder(this).setView(view).setCancelable(false).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val grade = outcome.grade
        view.findViewById<TextView>(R.id.tvExamResultTitle).text =
            getString(if (outcome.timeUp) R.string.exam_result_time_up else R.string.exam_result_submitted)
        view.findViewById<TextView>(R.id.tvExamGrade).apply {
            text = ExamGrader.format(grade.grade)
            setTextColor(getColor(if (grade.passed) R.color.state_success else R.color.state_error))
        }
        view.findViewById<TextView>(R.id.tvExamBreakdown).text =
            counts(
                R.plurals.exam_correct to grade.correct,
                R.plurals.exam_wrong to grade.wrong,
                R.plurals.exam_blank to grade.blank
            )
        view.findViewById<TextView>(R.id.tvExamPenaltyNote).visibility =
            if (grade.penalized) View.VISIBLE else View.GONE

        view.findViewById<MaterialButton>(R.id.btnExamReview).setOnClickListener {
            dialog.dismiss()
            openReview(outcome)
        }
        view.findViewById<MaterialButton>(R.id.btnExamExit).setOnClickListener {
            dialog.dismiss()
            finish()
        }
        resultDialog = dialog
        dialog.show()
    }

    private fun openReview(outcome: ExamViewModel.ExamOutcome) {
        val grade = outcome.grade
        startActivity(
            ReviewActivity.intent(
                context = this,
                subjectId = config.subjectId,
                topicId = config.range.mode.topicId,
                score = grade.correct,
                results = outcome.results,
                scoreText = getString(
                    R.string.exam_review_score,
                    ExamGrader.format(grade.grade),
                    grade.correct,
                    grade.total
                ),
                exam = config
            )
        )
        finish()
    }

    private fun showNoQuestions() {
        if (noQuestionsShown) return
        noQuestionsShown = true
        AuthUiHelper.showInfo(
            this,
            getString(R.string.no_questions_title),
            getString(R.string.no_questions_yet)
        ) { finish() }
    }

    private fun showContext(text: String) {
        AlertDialog.Builder(this)
            .setTitle(R.string.context_dialog_title)
            .setMessage(text)
            .setPositiveButton(R.string.close, null)
            .show()
    }

    companion object {
        private const val EXTRA_CONFIG = "EXAM_CONFIG"
        private const val TIMER_TICK_MS = 1_000L
        private const val SECONDS_PER_MINUTE = 60
        private const val LAST_MINUTE_SECONDS = 60
        private const val FIVE_MINUTES_SECONDS = 300

        fun intent(context: Context, config: ExamConfig): Intent =
            Intent(context, ExamActivity::class.java).putExtra(EXTRA_CONFIG, config)
    }
}
