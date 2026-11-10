package com.navio.damtests.ui.favorites

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.navio.damtests.R
import com.navio.damtests.data.catalog.SubjectCatalog
import com.navio.damtests.data.catalog.TopicNames
import com.navio.damtests.data.local.entity.Question

/** Favourite questions with their correct answer and a star to remove them. */
class FavoritesAdapter(
    private val onRemoveClick: (Question) -> Unit
) : ListAdapter<Question, FavoritesAdapter.ViewHolder>(DIFF) {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvSource: TextView = view.findViewById(R.id.tvFavoriteSource)
        val tvQuestion: TextView = view.findViewById(R.id.tvFavoriteQuestion)
        val tvAnswer: TextView = view.findViewById(R.id.tvFavoriteAnswer)
        val btnRemove: ImageButton = view.findViewById(R.id.btnRemoveFavorite)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(
            LayoutInflater.from(parent.context).inflate(R.layout.item_favorite, parent, false)
        )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val q = getItem(position)
        val correct = when (q.correctOptionIndex) {
            0 -> q.optionA
            1 -> q.optionB
            2 -> q.optionC
            else -> q.optionD
        }
        val context = holder.itemView.context
        holder.tvSource.text = context.getString(
            R.string.subject_and_topic,
            SubjectCatalog.nameFor(q.subjectId).asString(context),
            TopicNames.displayName(q.topicId).asString(context)
        )
        holder.tvQuestion.text = q.text
        holder.tvAnswer.text = context.getString(R.string.favorites_correct_answer, correct)
        holder.btnRemove.setOnClickListener { onRemoveClick(q) }
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<Question>() {
            override fun areItemsTheSame(a: Question, b: Question) = a.stableId == b.stableId
            override fun areContentsTheSame(a: Question, b: Question) = a == b
        }
    }
}
