package com.navio.damtests.ui.exam

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.materialswitch.MaterialSwitch
import com.navio.damtests.R
import com.navio.damtests.data.exam.Exam
import com.navio.damtests.data.exam.ExamRange

/**
 * Exam setup: topics, number of questions (the time follows from it) and
 * whether wrong answers subtract.
 */
object ExamSetupDialog {

    /**
     * Ranges the subject can offer, with how many questions each has. Like the
     * general tests, the 1-10 / 11-20 split is offered only when both halves
     * have questions.
     */
    fun availableRanges(counts: Map<ExamRange, Int>): Map<ExamRange, Int> {
        val all = counts[ExamRange.ALL] ?: 0
        val first = counts[ExamRange.FIRST_HALF] ?: 0
        val second = counts[ExamRange.SECOND_HALF] ?: 0
        if (all == 0) return emptyMap()
        return if (first > 0 && second > 0) {
            linkedMapOf(ExamRange.ALL to all, ExamRange.FIRST_HALF to first, ExamRange.SECOND_HALF to second)
        } else {
            linkedMapOf(ExamRange.ALL to all)
        }
    }

    fun show(
        context: Context,
        ranges: Map<ExamRange, Int>,
        onStart: (range: ExamRange, size: Int, penalized: Boolean) -> Unit
    ) {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_exam_setup, null)
        val dialog = AlertDialog.Builder(context).setView(view).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val cgRange = view.findViewById<ChipGroup>(R.id.cgExamRange)
        val cgSize = view.findViewById<ChipGroup>(R.id.cgExamSize)
        val swPenalty = view.findViewById<MaterialSwitch>(R.id.swExamPenalty)

        // With a single range there is nothing to choose
        if (ranges.size == 1) {
            cgRange.visibility = View.GONE
            view.findViewById<TextView>(R.id.tvSetupRangeLabel).visibility = View.GONE
        }

        fun fillSizes(range: ExamRange) {
            cgSize.removeAllViews()
            Exam.availableSizes(ranges[range] ?: 0).forEach { size ->
                val duration = Exam.durationLabel(size).asString(context)
                val label = context.getString(R.string.exam_size_option, size, duration)
                cgSize.addView(choiceChip(cgSize, label, tag = size))
            }
            // Default size when possible, otherwise the largest one available
            val sizes = (0 until cgSize.childCount).map { cgSize.getChildAt(it) as Chip }
            (sizes.firstOrNull { it.tag == Exam.DEFAULT_SIZE } ?: sizes.lastOrNull())?.isChecked = true
        }

        ranges.keys.forEach { range ->
            cgRange.addView(choiceChip(cgRange, context.getString(range.labelRes), tag = range))
        }
        cgRange.setOnCheckedStateChangeListener { group, ids ->
            val range = ids.firstOrNull()?.let { group.findViewById<Chip>(it).tag as ExamRange }
            if (range != null) fillSizes(range)
        }
        (cgRange.getChildAt(0) as? Chip)?.isChecked = true

        view.findViewById<MaterialButton>(R.id.btnStartExam).setOnClickListener {
            val range = cgRange.checkedChipId.let { cgRange.findViewById<Chip>(it)?.tag as? ExamRange }
            val size = cgSize.checkedChipId.let { cgSize.findViewById<Chip>(it)?.tag as? Int }
            if (range != null && size != null) {
                dialog.dismiss()
                onStart(range, size, swPenalty.isChecked)
            }
        }
        view.findViewById<MaterialButton>(R.id.btnCancelExam).setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun choiceChip(group: ChipGroup, text: String, tag: Any): Chip =
        (LayoutInflater.from(group.context).inflate(R.layout.item_exam_chip, group, false) as Chip).apply {
            id = View.generateViewId()
            this.text = text
            this.tag = tag
        }
}
