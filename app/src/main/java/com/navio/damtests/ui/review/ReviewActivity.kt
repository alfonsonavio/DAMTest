package com.navio.damtests.ui.review

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
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
import com.navio.damtests.data.exam.Exam
import com.navio.damtests.data.exam.ExamRange
import com.navio.damtests.data.quiz.QuizRepository
import com.navio.damtests.data.report.ReportRepository
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
        setContentView(R.layout.activity_review)

        // All data arrives via Intent — no global singleton needed
        val score     = intent.getIntExtra("SCORE", 0)
        val total     = intent.getIntExtra("TOTAL", 0)
        val subjectId = intent.getStringExtra("SUBJECT_ID") ?: "programacion"
        val topicId   = intent.getStringExtra("TOPIC_ID")   ?: "tema_1"

        @Suppress("DEPRECATION")
        val results: List<QuestionResult> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayListExtra("RESULTS", QuestionResult::class.java) ?: emptyList()
        } else {
            intent.getParcelableArrayListExtra("RESULTS") ?: emptyList()
        }

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
            val examRange = ExamRange.fromAttemptTopicId(topicId)
            if (examRange != null) {
                // Same exam again: same topics, size and penalty, new questions
                ExamActivity.start(
                    this, subjectId, examRange,
                    intent.getIntExtra(ExamActivity.EXTRA_SIZE, Exam.SIZES[1]),
                    intent.getBooleanExtra(ExamActivity.EXTRA_PENALTY, false)
                )
            } else {
                startActivity(
                    Intent(this, QuizActivity::class.java)
                        .putExtra("SUBJECT_ID", subjectId)
                        .putExtra("TOPIC_ID", topicId)
                )
            }
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
            .setTitle("Reportar pregunta")
            .setView(view)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Enviar") { _, _ ->
                val reason = when (reasons.checkedRadioButtonId) {
                    R.id.rbWrongAnswer -> "respuesta_incorrecta"
                    R.id.rbTypo        -> "errata"
                    else               -> "otro"
                }
                reportRepository.reportQuestion(
                    result.question,
                    reason,
                    comment.text?.toString()?.trim().orEmpty()
                )
                Toast.makeText(this, "Gracias, revisaremos la pregunta", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    companion object {
        /** Optional header text replacing "Resultado final: x / y" (exam mark). */
        const val EXTRA_SCORE_TEXT = "SCORE_TEXT"

        /** After this long without an answer the dialog says it is taking longer. */
        private const val SLOW_ANSWER_NOTICE_MS = 8_000L
    }
}