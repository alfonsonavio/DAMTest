package com.navio.damtests

import android.content.Intent
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

        val score   = intent.getIntExtra("SCORE", 0)
        val results = TestDataHolder.lastResults

        findViewById<TextView>(R.id.tvReviewScore).text =
            getString(R.string.review_final_score, score, results.size)

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
                    .putExtra("SUBJECT_ID", TestDataHolder.currentSubjectId)
                    .putExtra("TOPIC_ID",   TestDataHolder.currentTopicId)
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
                // API key fetched from Firebase Remote Config — not stored in source code
                val apiKey = RemoteConfigManager.getString(RemoteConfigManager.KEY_GEMINI)
                if (apiKey.isBlank()) {
                    tvMessage.text = getString(R.string.ai_key_not_available)
                    return@launch
                }
                val explanation = GeminiExplainer(apiKey)
                    .explicarFallo(result.question, result.userSelectedIndex)
                tvMessage.text = explanation
            } catch (e: Exception) {
                tvMessage.text = getString(R.string.ai_error, e.message)
            }
        }
    }
}
