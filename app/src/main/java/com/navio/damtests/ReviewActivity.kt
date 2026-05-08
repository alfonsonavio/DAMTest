package com.navio.damtests

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.navio.damtests.ai.GeminiExplainer
import com.navio.damtests.ui.viewmodel.QuestionResult
import kotlinx.coroutines.launch

class ReviewActivity : AppCompatActivity() {

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

        findViewById<TextView>(R.id.tvReviewScore).text =
            getString(R.string.review_final_score, score, total)

        val rv = findViewById<RecyclerView>(R.id.rvReview)
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = ReviewAdapter(results) { result -> showAiExplanation(result) }

        findViewById<MaterialButton>(R.id.btnBackToMenu).setOnClickListener {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            )
            finish()
        }

        findViewById<MaterialButton>(R.id.btnRepeatTest).setOnClickListener {
            startActivity(
                Intent(this, QuizActivity::class.java)
                    .putExtra("SUBJECT_ID", subjectId)
                    .putExtra("TOPIC_ID", topicId)
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

        btnClose.setOnClickListener { dialog.dismiss() }

        lifecycleScope.launch {
            try {
                val apiKey = RemoteConfigManager.getString(RemoteConfigManager.KEY_GEMINI)
                if (apiKey.isBlank()) {
                    tvMessage.text = getString(R.string.ai_key_not_available)
                    return@launch
                }
                tvMessage.text = GeminiExplainer(apiKey)
                    .explicarFallo(result.question, result.userSelectedIndex)
            } catch (e: Exception) {
                tvMessage.text = getString(R.string.ai_error, e.message)
            }
        }
    }
}
