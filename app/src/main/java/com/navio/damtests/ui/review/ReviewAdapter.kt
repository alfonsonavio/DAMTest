package com.navio.damtests.ui.review

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.navio.damtests.R
import com.navio.damtests.data.quiz.correctOptionText
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
        val tvOptions: TextView = view.findViewById(R.id.tvReviewOptions)
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
        holder.tvQuestion.text =
            holder.itemView.context.getString(R.string.review_question_title, position + 1, res.question.text)
        holder.tvOptions.text = optionsText(holder.itemView.context, res)
        holder.btnReport.setOnClickListener { onReportClick(res) }
        bindFavorite(holder, res)
        bindVerdict(holder, res)
    }

    /** Favourite star (hidden for questions without a stable id). */
    private fun bindFavorite(holder: ViewHolder, res: QuestionResult) {
        val stableId = res.question.stableId
        val isFavorite = stableId in favoriteIds
        holder.btnFavorite.visibility = if (stableId.isBlank()) View.GONE else View.VISIBLE
        holder.btnFavorite.setImageResource(if (isFavorite) R.drawable.ic_star else R.drawable.ic_star_outline)
        holder.btnFavorite.contentDescription = holder.itemView.context.getString(
            if (isFavorite) R.string.favorite_remove else R.string.favorite_add
        )
        holder.btnFavorite.setOnClickListener { onFavoriteClick(res, !isFavorite) }
    }

    /**
     * The options in the order they were shown, marking the right one and the
     * user's wrong choice. Options are compared by text: [QuestionResult.isCorrect]
     * and [QuestionResult.userSelectedIndex] refer to the SHUFFLED order, while
     * the question's correctOptionIndex refers to the original one.
     */
    private fun optionsText(context: Context, res: QuestionResult): String {
        val correctText = res.question.correctOptionText
        val userSelectedText = res.shuffledOptions.getOrElse(res.userSelectedIndex) { "" }

        val lines = res.shuffledOptions.mapIndexed { i, optionText ->
            val prefix = when {
                optionText == correctText -> CORRECT_MARK
                optionText == userSelectedText && !res.isCorrect -> WRONG_MARK
                else -> NO_MARK
            }
            "$prefix ${OPTION_LETTERS[i]}) $optionText"
        }
        // Exam questions left blank: say so and show only the right answer
        val header = if (res.isAnswered) emptyList() else listOf(context.getString(R.string.review_not_answered), "")
        return (header + lines).joinToString("\n")
    }

    /** Card colours by result; unanswered and wrong questions offer an explanation. */
    private fun bindVerdict(holder: ViewHolder, res: QuestionResult) {
        val (background, stroke) = when {
            !res.isAnswered -> R.color.surface to R.color.text_muted
            res.isCorrect -> R.color.state_success_bg to R.color.state_success
            else -> R.color.state_error_bg to R.color.state_error
        }
        val context = holder.card.context
        holder.card.setCardBackgroundColor(context.getColor(background))
        holder.card.strokeColor = context.getColor(stroke)

        val canExplain = !res.isAnswered || !res.isCorrect
        holder.btnExplain.visibility = if (canExplain) View.VISIBLE else View.GONE
        if (canExplain) holder.btnExplain.setOnClickListener { onExplainClick(res) }
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
