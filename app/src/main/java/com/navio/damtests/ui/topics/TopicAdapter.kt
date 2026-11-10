package com.navio.damtests.ui.topics

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.navio.damtests.R

/** Topics of a subject: name, last score with its bar and the PDF button. */
class TopicAdapter(
    private val onTopicClick: (TopicRow) -> Unit,
    private val onPdfClick: (TopicRow) -> Unit
) : ListAdapter<TopicRow, TopicAdapter.ViewHolder>(DIFF) {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvTopicName)
        val tvScore: TextView = view.findViewById(R.id.tvTopicScore)
        val progress: LinearProgressIndicator = view.findViewById(R.id.topicProgress)
        val btnPdf: ImageButton = view.findViewById(R.id.btnOpenPdf)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        LayoutInflater.from(parent.context).inflate(R.layout.item_topic, parent, false)
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val row = getItem(position)
        val context = holder.itemView.context
        val percent = row.lastPercent

        holder.tvName.text = row.topic.title.asString(context)
        holder.progress.progress = percent ?: 0
        if (percent == null) {
            holder.tvScore.text = context.getString(R.string.topic_not_done)
            holder.tvScore.setTextColor(context.getColor(R.color.text_muted))
        } else {
            val color = context.getColor(if (percent >= PASS_PERCENT) R.color.state_success else R.color.state_error)
            holder.tvScore.text = context.getString(R.string.topic_last_score, percent)
            holder.tvScore.setTextColor(color)
            holder.progress.setIndicatorColor(color)
        }

        holder.btnPdf.visibility = if (row.hasPdf) View.VISIBLE else View.INVISIBLE
        holder.btnPdf.setOnClickListener { onPdfClick(row) }
        holder.itemView.setOnClickListener { onTopicClick(row) }
    }

    private companion object {
        const val PASS_PERCENT = 50

        val DIFF = object : DiffUtil.ItemCallback<TopicRow>() {
            override fun areItemsTheSame(a: TopicRow, b: TopicRow) = a.topic.id == b.topic.id
            override fun areContentsTheSame(a: TopicRow, b: TopicRow) = a == b
        }
    }
}
