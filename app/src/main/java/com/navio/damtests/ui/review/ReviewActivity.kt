package com.navio.damtests.ui.review

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.IntentCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.navio.damtests.R
import com.navio.damtests.ai.ExplanationService
import com.navio.damtests.ai.FastExplainer
import com.navio.damtests.data.exam.ExamConfig
import com.navio.damtests.data.quiz.QuizRepository
import com.navio.damtests.data.report.ReportRepository
import com.navio.damtests.ui.common.setupEdgeToEdge
import com.navio.damtests.ui.exam.ExamActivity
import com.navio.damtests.ui.main.MainActivity
import com.navio.damtests.ui.quiz.QuestionResult
import com.navio.damtests.ui.quiz.QuizActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ReviewActivity : AppCompatActivity() {

    @Inject lateinit var reportRepository: ReportRepository
    @Inject lateinit var repository: QuizRepository
    @Inject lateinit var fastExplainer: FastExplainer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupEdgeToEdge(darkTop = false)
        setContentView(R.layout.activity_review)

        // All data arrives via Intent (see [intent]) — no global singleton needed
        val subjectId = intent.getStringExtra(EXTRA_SUBJECT_ID) ?: return finish()
        val topicId   = intent.getStringExtra(EXTRA_TOPIC_ID) ?: return finish()
        val score     = intent.getIntExtra(EXTRA_SCORE, 0)
        val results: List<QuestionResult> =
            IntentCompat.getParcelableArrayListExtra(intent, EXTRA_RESULTS, QuestionResult::class.java).orEmpty()
        val total     = results.size
        val exam      = IntentCompat.getParcelableExtra(intent, EXTRA_EXAM, ExamConfig::class.java)

        // Exams show their mark; the other tests, correct answers out of total
        findViewById<TextView>(R.id.tvReviewScore).text =
            intent.getStringExtra(EXTRA_SCORE_TEXT)
                ?: getString(R.string.review_final_score, score, total)

        val rv = findViewById<RecyclerView>(R.id.rvReview)
        rv.layoutManager = LinearLayoutManager(this)
        val adapter = ReviewAdapter(
            results,
            onExplainClick  = { result -> showAiExplanation(result) },
            onReportClick   = { result -> showReportDialog(result) },
            onFavoriteClick = { result, favorite ->
                lifecycleScope.launch { repository.setFavorite(result.question, favorite) }
            }
        )
        rv.adapter = adapter

        // Stars update live when a question is marked or unmarked
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                repository.getFavoriteIds().collect { ids -> adapter.updateFavorites(ids) }
            }
        }

        findViewById<MaterialButton>(R.id.btnBackToMenu).setOnClickListener {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            )
            finish()
        }

        findViewById<MaterialButton>(R.id.btnRepeatTest).setOnClickListener {
            // An exam repeats with the same topics, size and penalty (new questions)
            startActivity(
                if (exam != null) ExamActivity.intent(this, exam)
                else QuizActivity.intent(this, subjectId, topicId)
            )
            finish()
        }
    }

    private fun showAiExplanation(result: QuestionResult) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_explanation, null)
        val tvMessage  = dialogView.findViewById<TextView>(R.id.tvAiExplanation)
        val btnClose   = dialogView.findViewById<MaterialButton>(R.id.btnDialogClose)

        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()

        val width = (resources.displayMetrics.widthPixels * 0.90).toInt()
        dialog.window?.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)

        val job = lifecycleScope.launch {
            // Busy models can take a while: say so instead of looking frozen
            val slowNotice = launch {
                delay(SLOW_ANSWER_NOTICE_MS)
                tvMessage.text = getString(R.string.ai_taking_longer)
            }
            val explanation = ExplanationService.create(fastExplainer)
                .explain(result.question, result.shuffledOptions, result.userSelectedIndex)
            slowNotice.cancel()
            tvMessage.text = explanation ?: getString(R.string.ai_error)
        }
        // Closing the dialog cancels the request still in flight
        dialog.setOnDismissListener { job.cancel() }
        btnClose.setOnClickListener { dialog.dismiss() }
    }

    /** Lets the user flag a question with a wrong answer or typos. */
    private fun showReportDialog(result: QuestionResult) {
        val view = layoutInflater.inflate(R.layout.dialog_report, null)
        val reasons  = view.findViewById<RadioGroup>(R.id.rgReportReason)
        val comment  = view.findViewById<TextInputEditText>(R.id.etReportComment)

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.report_title)
            .setView(view)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.report_send) { _, _ ->
                val reason = when (reasons.checkedRadioButtonId) {
                    R.id.rbWrongAnswer -> ReportRepository.Reason.WRONG_ANSWER
                    R.id.rbTypo        -> ReportRepository.Reason.TYPO
                    else               -> ReportRepository.Reason.OTHER
                }
                reportRepository.reportQuestion(
                    result.question,
                    reason,
                    comment.text?.toString()?.trim().orEmpty()
                )
                Toast.makeText(this, R.string.report_thanks, Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    companion object {
        private const val EXTRA_SUBJECT_ID = "SUBJECT_ID"
        private const val EXTRA_TOPIC_ID = "TOPIC_ID"
        private const val EXTRA_SCORE = "SCORE"
        private const val EXTRA_RESULTS = "RESULTS"
        private const val EXTRA_SCORE_TEXT = "SCORE_TEXT"
        private const val EXTRA_EXAM = "EXAM"

        /** After this long without an answer the dialog says it is taking longer. */
        private const val SLOW_ANSWER_NOTICE_MS = 8_000L

        /**
         * Review of a finished test.
         *
         * @param scoreText Header replacing "Resultado final: x / y" (the exam mark).
         * @param exam      Set for exams, so "Repetir" starts the same exam again.
         */
        fun intent(
            context: Context,
            subjectId: String,
            topicId: String,
            score: Int,
            results: List<QuestionResult>,
            scoreText: String? = null,
            exam: ExamConfig? = null
        ): Intent = Intent(context, ReviewActivity::class.java)
            .putExtra(EXTRA_SUBJECT_ID, subjectId)
            .putExtra(EXTRA_TOPIC_ID, topicId)
            .putExtra(EXTRA_SCORE, score)
            .putParcelableArrayListExtra(EXTRA_RESULTS, ArrayList(results))
            .putExtra(EXTRA_SCORE_TEXT, scoreText)
            .putExtra(EXTRA_EXAM, exam)
    }
}
