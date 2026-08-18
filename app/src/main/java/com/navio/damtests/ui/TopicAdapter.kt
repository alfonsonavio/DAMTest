package com.navio.damtests.ui

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.navio.damtests.R
import com.navio.damtests.data.local.entity.Topic
import com.navio.damtests.data.local.entity.TopicProgress

class TopicAdapter(
    private val topics: List<Topic>,
    private val progressList: List<TopicProgress>,
    private val pdfTopicIds: Set<String>,
    private val onTopicClick: (Topic) -> Unit,
    private val onPdfClick: (Topic) -> Unit
) : RecyclerView.Adapter<TopicAdapter.TopicViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TopicViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_topic, parent, false)
        return TopicViewHolder(view)
    }

    override fun onBindViewHolder(holder: TopicViewHolder, position: Int) {
        val topic    = topics[position]
        val progress = progressList.find { it.topicId == topic.id }
        holder.bind(topic, progress, pdfTopicIds, onPdfClick)
        holder.itemView.setOnClickListener { onTopicClick(topic) }
    }

    override fun getItemCount() = topics.size

    class TopicViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val tvTitle: TextView = view.findViewById(R.id.tvTopicName)
        private val tvStats: TextView = view.findViewById(R.id.tvTopicStats)
        private val btnPdf: View      = view.findViewById(R.id.btnOpenPdf)

        fun bind(
            topic: Topic,
            progress: TopicProgress?,
            pdfTopicIds: Set<String>,
            onPdfClick: (Topic) -> Unit
        ) {
            tvTitle.text = topic.title

            when {
                // Smart review is a practice tool — it never shows a score
                topic.id == "-4" -> {
                    tvStats.text = "Practica tus fallos"
                    tvStats.setTextColor(Color.parseColor("#94A3B8"))
                }
                progress == null -> {
                    tvStats.text = "Pendiente de realizar"
                    tvStats.setTextColor(Color.parseColor("#94A3B8"))
                }
                else -> {
                    val percent = (progress.lastScore.toFloat() / progress.totalQuestions * 100).toInt()
                    tvStats.text = "Última nota: $percent%"
                    tvStats.setTextColor(
                        if (percent >= 50) Color.parseColor("#22C55E")
                        else Color.parseColor("#EF4444")
                    )
                }
            }

            // Show PDF button only if a PDF exists for this topic in the release
            val hasPdf = topic.id in pdfTopicIds
            btnPdf.visibility = if (hasPdf) View.VISIBLE else View.GONE
            if (hasPdf) {
                btnPdf.setOnClickListener { onPdfClick(topic) }
            }
        }
    }
}