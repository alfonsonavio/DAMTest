package com.navio.damtests.exam

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
        val ctx      = holder.cell.context
        val answered = position in state.answers
        val flagged  = position in state.flagged
        val current  = position == state.currentIndex
        val density  = ctx.resources.displayMetrics.density

        holder.cell.text = "${position + 1}"
        holder.cell.setTextColor(ctx.getColor(if (answered) R.color.text_on_dark else R.color.text_primary))
        holder.cell.background = GradientDrawable().apply {
            cornerRadius = 12 * density
            setColor(ctx.getColor(if (answered) R.color.brand_primary else R.color.surface))
            val (width, color) = when {
                flagged -> 3 to R.color.favorite_star
                current -> 3 to R.color.brand_navy
                else    -> 1 to R.color.surface_border
            }
            setStroke((width * density).toInt(), ctx.getColor(color))
        }
        holder.cell.contentDescription = buildString {
            append("Pregunta ${position + 1}")
            append(if (answered) ", respondida" else ", en blanco")
            if (flagged) append(", dudosa")
        }
        holder.cell.setOnClickListener { onClick(position) }
    }

    override fun getItemCount() = state.total
}