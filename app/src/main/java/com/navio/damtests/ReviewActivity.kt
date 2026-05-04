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
import com.navio.damtests.ai.GeminiExplainer // Asegúrate de que el paquete sea este
import com.navio.damtests.ui.viewmodel.QuestionResult
import kotlinx.coroutines.launch

class ReviewActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_review)

        // 1. Recuperamos los datos reales enviados desde QuizActivity
        val score = intent.getIntExtra("SCORE", 0)
        val results = TestDataHolder.lastResults

        val total = results.size

        // 2. Actualizamos el texto con valores REALES
        findViewById<TextView>(R.id.tvReviewScore).text = "Resultado final: $score / $total"

        // 3. Configuramos el RecyclerView con la IA
        val rv = findViewById<RecyclerView>(R.id.rvReview)
        rv.layoutManager = LinearLayoutManager(this)

        // Pasamos una función (lambda) al adapter para manejar el click en "Ver Explicación"
        rv.adapter = ReviewAdapter(TestDataHolder.lastResults) { result ->
            showAiExplanation(result)
        }

        // 4. Botones de navegación
        findViewById<MaterialButton>(R.id.btnBackToMenu).setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
            startActivity(intent)
            finish()
        }

        findViewById<MaterialButton>(R.id.btnRepeatTest).setOnClickListener {
            val intent = Intent(this, QuizActivity::class.java).apply {
                putExtra("SUBJECT_ID", TestDataHolder.currentSubjectId)
                putExtra("TOPIC_ID", TestDataHolder.currentTopicId)
            }
            startActivity(intent)
            finish()
        }
    }

    /**
     * Muestra un diálogo con la explicación generada por IA
     */
    private fun showAiExplanation(result: QuestionResult) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_explanation, null)
        val tvMessage = dialogView.findViewById<TextView>(R.id.tvAiExplanation)
        val btnClose = dialogView.findViewById<MaterialButton>(R.id.btnDialogClose)

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
                val explanation = GeminiExplainer().explicarFallo(result.question, result.userSelectedIndex)
                tvMessage.text = explanation
            } catch (e: Exception) {
                tvMessage.text = "Error: ${e.message}"
            }
        }
    }
}