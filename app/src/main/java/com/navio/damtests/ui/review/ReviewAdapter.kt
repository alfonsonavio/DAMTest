package com.navio.damtests.ui.review

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.navio.damtests.R
import com.navio.damtests.ui.quiz.QuestionResult

class ReviewAdapter(
    private val results: List<QuestionResult>,
    private val onExplainClick: (QuestionResult) -> Unit,
    private val onReportClick: (QuestionResult) -> Unit,
    private val onFavoriteClick: (QuestionResult, Boolean) -> Unit
) : RecyclerView.Adapter<ReviewAdapter.ViewHolder>() {

    /** Stable ids of the favourite questions, to draw each card's star. */
    private var favoriteIds: Set<String> = emptySet()

    @android.annotation.SuppressLint("NotifyDataSetChanged")
    fun updateFavorites(ids: Set<String>) {
        favoriteIds = ids
        notifyDataSetChanged()
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvQuestion: TextView = view.findViewById(R.id.tvReviewQuestion)
        val tvOptions: TextView  = view.findViewById(R.id.tvReviewOptions)
        val card: MaterialCardView = view.findViewById(R.id.cardReview)
        val btnExplain: Button = view.findViewById(R.id.btnExplain)
        val btnReport: Button = view.findViewById(R.id.btnReport)
        val btnFavorite: ImageButton = view.findViewById(R.id.btnFavorite)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_review, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val res = results[position]
        val q   = res.question

        // FIX: use the pre-computed isCorrect flag from QuizViewModel.
        // DO NOT recalculate as (userSelectedIndex == correctOptionIndex) because
        // userSelectedIndex is a position in the SHUFFLED list while
        // correctOptionIndex is a position in the ORIGINAL unshuffled options —
        // comparing them directly gives wrong results.
        val isCorrect = res.isCorrect

        holder.tvQuestion.text = "${position + 1}. ${q.text}"
        holder.btnReport.setOnClickListener { onReportClick(res) }

        // Favourite star (hidden for questions without a stable id)
        val isFavorite = q.stableId in favoriteIds
        holder.btnFavorite.visibility = if (q.stableId.isBlank()) View.GONE else View.VISIBLE
        holder.btnFavorite.setImageResource(if (isFavorite) R.drawable.ic_star else R.drawable.ic_star_outline)
        holder.btnFavorite.contentDescription = holder.itemView.context.getString(
            if (isFavorite) R.string.favorite_remove else R.string.favorite_add
        )
        holder.btnFavorite.setOnClickListener { onFavoriteClick(res, !isFavorite) }

        // The correct answer text (from original option order)
        val correctText = when (q.correctOptionIndex) {
            0    -> q.optionA
            1    -> q.optionB
            2    -> q.optionC
            else -> q.optionD
        }

        // FIX: the user's selected text comes from the SHUFFLED options list,
        // NOT from the original optionA/B/C/D indexed by userSelectedIndex.
        val userSelectedText = res.shuffledOptions.getOrElse(res.userSelectedIndex) { "" }

        val optionsText = StringBuilder()
        for (i in res.shuffledOptions.indices) {
            val optionText = res.shuffledOptions[i]
            val prefix = when {
                optionText == correctText                     -> CORRECT_MARK
                optionText == userSelectedText && !isCorrect -> WRONG_MARK
                else                                         -> NO_MARK
            }
            optionsText.append("$prefix ${OPTION_LETTERS[i]}) $optionText\n")
        }
        // Exam questions left blank: say so and show only the right answer
        if (!res.isAnswered) {
            optionsText.insert(0, holder.itemView.context.getString(R.string.review_not_answered) + "\n\n")
        }
        holder.tvOptions.text = optionsText.toString().trim()

        if (!res.isAnswered) {
            holder.card.setCardBackgroundColor(holder.card.context.getColor(R.color.surface))
            holder.card.strokeColor = holder.card.context.getColor(R.color.text_muted)
            holder.btnExplain.visibility = View.VISIBLE
            holder.btnExplain.setOnClickListener { onExplainClick(res) }
        } else if (isCorrect) {
            holder.card.setCardBackgroundColor(holder.card.context.getColor(R.color.state_success_bg))
            holder.card.strokeColor = holder.card.context.getColor(R.color.state_success)
            holder.btnExplain.visibility = View.GONE
        } else {
            holder.card.setCardBackgroundColor(holder.card.context.getColor(R.color.state_error_bg))
            holder.card.strokeColor = holder.card.context.getColor(R.color.state_error)
            holder.btnExplain.visibility = View.VISIBLE
            holder.btnExplain.setOnClickListener { onExplainClick(res) }
        }
    }

    override fun getItemCount() = results.size

    private companion object {
        val OPTION_LETTERS = listOf("a", "b", "c", "d")
        // Same width for every option, so the letters stay aligned
        const val CORRECT_MARK = "✅ "
        const val WRONG_MARK = "❌ "
        const val NO_MARK = "      "
    }
}
