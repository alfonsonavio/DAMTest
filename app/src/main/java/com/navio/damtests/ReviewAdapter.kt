package com.navio.damtests

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.navio.damtests.ui.viewmodel.QuestionResult

class ReviewAdapter(
    private val results: List<QuestionResult>,
    private val onExplainClick: (QuestionResult) -> Unit // Añadimos esto
) : RecyclerView.Adapter<ReviewAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvQuestion: TextView = view.findViewById(R.id.tvReviewQuestion)
        val tvOptions: TextView = view.findViewById(R.id.tvReviewOptions)
        val card: MaterialCardView = view.findViewById(R.id.cardReview)
        val btnVerExplicacion: Button = view.findViewById(R.id.btnVerExplicacion) // Ahora sí existe
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_review, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val res = results[position]
        val q = res.question
        val isCorrect = res.userSelectedIndex == q.correctOptionIndex

        holder.tvQuestion.text = "${position + 1}. ${q.text}"

        val optionsText = StringBuilder()
        val labels = listOf("a", "b", "c", "d")
        val displayedOptions = res.shuffledOptions

        val correctText = when(q.correctOptionIndex) {
            0 -> q.optionA
            1 -> q.optionB
            2 -> q.optionC
            else -> q.optionD
        }

        val userSelectedText = when(res.userSelectedIndex) {
            0 -> q.optionA
            1 -> q.optionB
            2 -> q.optionC
            else -> q.optionD
        }

        for (i in displayedOptions.indices) {
            val currentOptionText = displayedOptions[i]
            val prefix = when {
                currentOptionText == correctText -> "✅ "
                currentOptionText == userSelectedText && !isCorrect -> "❌ "
                else -> "      "
            }
            optionsText.append("$prefix ${labels[i]}) $currentOptionText\n")
        }
        holder.tvOptions.text = optionsText.toString().trim()

        // LÓGICA DE COLORES Y BOTÓN
        if (isCorrect) {
            holder.card.setCardBackgroundColor(Color.parseColor("#DCFCE7"))
            holder.card.strokeColor = Color.parseColor("#22C55E")
            holder.btnVerExplicacion.visibility = View.GONE
        } else {
            holder.card.setCardBackgroundColor(Color.parseColor("#FEE2E2"))
            holder.card.strokeColor = Color.parseColor("#EF4444")
            holder.btnVerExplicacion.visibility = View.VISIBLE
            holder.btnVerExplicacion.setOnClickListener { onExplainClick(res) }
        }
    }

    override fun getItemCount() = results.size
}