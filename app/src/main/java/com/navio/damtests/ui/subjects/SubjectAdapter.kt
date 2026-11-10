package com.navio.damtests.ui.subjects

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.navio.damtests.R
import com.navio.damtests.core.markOf
import com.navio.damtests.data.local.entity.Subject
import com.navio.damtests.data.local.entity.TopicProgress

class SubjectAdapter(
    private val subjects: List<Subject>,
    private val allProgress: List<TopicProgress>,
    private val onSubjectClick: (Subject) -> Unit
) : RecyclerView.Adapter<SubjectAdapter.SubjectViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SubjectViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_subject_dashboard, parent, false)
        return SubjectViewHolder(view)
    }

    override fun onBindViewHolder(holder: SubjectViewHolder, position: Int) {
        val subject = subjects[position]
        val subjectProgress = allProgress.filter { it.subjectId == subject.id }
        holder.bind(subject, subjectProgress)
        holder.itemView.setOnClickListener { onSubjectClick(subject) }
    }

    override fun getItemCount() = subjects.size

    class SubjectViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val name: TextView = view.findViewById(R.id.tvSubjectName)
        private val icon: ImageView = view.findViewById(R.id.ivSubjectIcon)
        private val stats: TextView = view.findViewById(R.id.tvSubjectStats)
        private val card: MaterialCardView = view.findViewById(R.id.cardSubject)

        fun bind(subject: Subject, progress: List<TopicProgress>) {
            name.setText(subject.nameRes)
            icon.setImageResource(subject.iconRes)
            card.setCardBackgroundColor(itemView.context.getColor(subject.colorRes))

            if (progress.isEmpty()) {
                stats.setText(R.string.subject_no_tests)
            } else {
                val numTests = progress.sumOf { it.attemptsCount }
                val avg = markOf(progress.sumOf { it.lastScore }, progress.sumOf { it.totalQuestions })

                stats.text = itemView.resources.getQuantityString(R.plurals.subject_stats, numTests, numTests, avg)
            }
        }
    }
}
