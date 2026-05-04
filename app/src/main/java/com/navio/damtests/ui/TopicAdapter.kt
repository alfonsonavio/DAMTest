package com.navio.damtests.ui

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.navio.damtests.R
import com.navio.damtests.data.local.entity.Topic // <--- ASEGÚRATE DE ESTE IMPORT
import com.navio.damtests.data.local.entity.TopicProgress

class TopicAdapter(
    private val topics: List<Topic>,
    private val progressList: List<TopicProgress>,
    private val onTopicClick: (Topic) -> Unit,
    private val onPdfClick: (Topic) -> Unit
) : RecyclerView.Adapter<TopicAdapter.TopicViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TopicViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_topic, parent, false)
        return TopicViewHolder(view)
    }

    override fun onBindViewHolder(holder: TopicViewHolder, position: Int) {
        val topic = topics[position]
        val progress = progressList.find { it.topicId == topic.id }

        holder.bind(topic, progress, onPdfClick) // Pasamos el listener al bind

        holder.itemView.setOnClickListener { onTopicClick(topic) }
    }

    override fun getItemCount() = topics.size

    class TopicViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val tvTitle: TextView = view.findViewById(R.id.tvTopicName)
        private val tvTopicStats: TextView = view.findViewById(R.id.tvTopicStats)
        private val btnPdf: View = view.findViewById(R.id.btnOpenPdf)

        fun bind(topic: Topic, progress: TopicProgress?, onPdfClick: (Topic) -> Unit) {
            tvTitle.text = topic.title

            if (progress == null) {
                tvTopicStats.text = "Pendiente de realizar"
                tvTopicStats.setTextColor(Color.parseColor("#94A3B8"))
            } else {
                val percent = (progress.lastScore.toFloat() / progress.totalQuestions * 100).toInt()
                tvTopicStats.text = "Última nota: $percent%"
                tvTopicStats.setTextColor(if (percent >= 50) Color.parseColor("#22C55E") else Color.parseColor("#EF4444"))
            }

            if (!topic.id.contains("tema_")) {
                // Si es Test General, ocultamos el botón de PDF
                btnPdf.visibility = View.GONE
            } else {
                btnPdf.visibility = View.VISIBLE
                btnPdf.setOnClickListener {
                    // Usamos stopPropagation o simplemente llamamos al listener
                    // Esto evita que al pulsar el PDF se abra también el examen
                    onPdfClick(topic)
                }
            }
        }
    }
}