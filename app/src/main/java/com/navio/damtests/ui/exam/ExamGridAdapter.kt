package com.navio.damtests.ui.exam

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.navio.damtests.R

/** Grid of question numbers to jump to any question of the exam. */
class ExamGridAdapter(
    private val onClick: (Int) -> Unit
) : RecyclerView.Adapter<ExamGridAdapter.ViewHolder>() {

    private var state = ExamViewModel.ExamState()

    @android.annotation.SuppressLint("NotifyDataSetChanged")
    fun update(newState: ExamViewModel.ExamState) {
        state = newState
        notifyDataSetChanged()
    }

    class ViewHolder(val cell: TextView) : RecyclerView.ViewHolder(cell)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        LayoutInflater.from(parent.context).inflate(R.layout.item_exam_cell, parent, false) as TextView
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val ctx = holder.cell.context
        val answered = position in state.answers
        val flagged = position in state.flagged
        val current = position == state.currentIndex

        holder.cell.text = "${position + 1}"
        holder.cell.setTextColor(ctx.getColor(if (answered) R.color.text_on_dark else R.color.text_primary))
        holder.cell.background = GradientDrawable().apply {
            cornerRadius = ctx.resources.getDimension(R.dimen.exam_cell_corner_radius)
            setColor(ctx.getColor(if (answered) R.color.brand_primary else R.color.surface))
            val (width, color) = when {
                flagged -> R.dimen.exam_cell_stroke_highlighted to R.color.favorite_star
                current -> R.dimen.exam_cell_stroke_highlighted to R.color.brand_navy
                else -> R.dimen.exam_cell_stroke to R.color.surface_border
            }
            setStroke(ctx.resources.getDimensionPixelSize(width), ctx.getColor(color))
        }
        holder.cell.contentDescription = ctx.getString(
            when {
                flagged && answered -> R.string.exam_cell_answered_flagged
                flagged -> R.string.exam_cell_blank_flagged
                answered -> R.string.exam_cell_answered
                else -> R.string.exam_cell_blank
            },
            position + 1
        )
        holder.cell.setOnClickListener { onClick(position) }
    }

    override fun getItemCount() = state.total
}
