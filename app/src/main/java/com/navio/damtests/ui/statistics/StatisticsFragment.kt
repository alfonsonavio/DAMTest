package com.navio.damtests.ui.statistics

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.google.android.material.button.MaterialButtonToggleGroup
import com.navio.damtests.R
import com.navio.damtests.data.catalog.SubjectCatalog
import com.navio.damtests.data.catalog.TopicNames
import com.navio.damtests.data.progress.ProgressRepository
import com.navio.damtests.data.statistics.EvolutionPoint
import com.navio.damtests.data.statistics.StatisticsData
import com.navio.damtests.data.statistics.SubjectAccuracy
import com.navio.damtests.data.statistics.WeakTopic
import com.navio.damtests.data.sync.StatsSyncRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Statistics screen: KPIs, per-subject accuracy bars and weakest topics, filtered
 * by course via the 1º/2º segmented control. The evolution chart is added in a
 * later commit into #chartContainer.
 */
@AndroidEntryPoint
class StatisticsFragment : Fragment(R.layout.fragment_statistics) {

    @Inject lateinit var progressRepository: ProgressRepository

    @Inject lateinit var statsSyncRepository: StatsSyncRepository

    private var selectedCourse = 1
    private var loadJob: Job? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val toggle = view.findViewById<MaterialButtonToggleGroup>(R.id.toggleCourse)
        toggle.check(R.id.btnCourse1)
        toggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                selectedCourse = if (checkedId == R.id.btnCourse2) 2 else 1
                loadStatistics(view)
            }
        }

        // Reload whenever the screen is shown again (e.g. back from a test) and
        // whenever a background statistics sync brings new data.
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                statsSyncRepository.syncedAt.collect { loadStatistics(view) }
            }
        }
    }

    private fun loadStatistics(view: View) {
        loadJob?.cancel() // a newer load (e.g. a quick 1º/2º switch) wins
        loadJob = viewLifecycleOwner.lifecycleScope.launch {
            val subjectIds = subjectIdsForCourse(selectedCourse)
            val data = progressRepository.getStatistics(subjectIds)
            render(view, data)
        }
    }

    private fun render(view: View, data: StatisticsData) {
        val emptyState = view.findViewById<LinearLayout>(R.id.emptyState)
        val contentState = view.findViewById<LinearLayout>(R.id.contentState)

        if (data.isEmpty) {
            emptyState.isVisible = true
            contentState.isVisible = false
            return
        }
        emptyState.isVisible = false
        contentState.isVisible = true

        // KPIs
        view.findViewById<TextView>(R.id.tvAccuracy).text = getString(R.string.percent, data.globalAccuracy)
        view.findViewById<TextView>(R.id.tvTotalTests).text = getString(R.string.number, data.totalTests)
        view.findViewById<TextView>(R.id.tvSubjectsStudied).text = getString(R.string.number, data.bySubject.size)

        renderEvolutionChart(view, data.evolution)
        renderSubjectBars(view, data.bySubject)
        renderWeakTopics(view, data.weakestTopics)
    }

    private fun renderEvolutionChart(view: View, evolution: List<EvolutionPoint>) {
        val chart = view.findViewById<LineChart>(R.id.evolutionChart)

        if (evolution.size < 2) {
            // Not enough points for a meaningful line
            chart.isVisible = false
            return
        }
        chart.isVisible = true

        val entries = evolution.mapIndexed { index, point ->
            Entry(index.toFloat(), point.percentage.toFloat())
        }

        val primary = ContextCompat.getColor(requireContext(), R.color.brand_primary)

        val dataSet = LineDataSet(entries, getString(R.string.statistics_chart_label)).apply {
            color = primary
            lineWidth = LINE_WIDTH_DP
            // Smooth curve that never goes past its points: a cubic one overshoots
            // below 0 between two 0 % tests and that stretch gets clipped.
            mode = LineDataSet.Mode.HORIZONTAL_BEZIER
            setDrawValues(false)
            setDrawCircles(true)
            setCircleColor(primary)
            circleRadius = POINT_RADIUS_DP
            setDrawCircleHole(true)
            circleHoleRadius = POINT_HOLE_RADIUS_DP
            // Gradient fill fading downwards
            setDrawFilled(true)
            fillDrawable = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(
                    ColorUtils.setAlphaComponent(primary, FILL_TOP_ALPHA),
                    ColorUtils.setAlphaComponent(primary, 0)
                )
            )
            highLightColor = primary
        }

        chart.apply {
            data = LineData(dataSet)
            description.isEnabled = false
            legend.isEnabled = false
            setTouchEnabled(true)
            setScaleEnabled(false)
            setDrawGridBackground(false)
            axisRight.isEnabled = false
            axisLeft.apply {
                axisMinimum = 0f
                axisMaximum = MAX_PERCENT
                setDrawGridLines(true)
                gridColor = ContextCompat.getColor(requireContext(), R.color.surface_border)
                textColor = ContextCompat.getColor(requireContext(), R.color.text_secondary)
                setDrawAxisLine(false)
            }
            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                setDrawAxisLine(false)
                setDrawLabels(false)
            }
            animateX(CHART_ANIMATION_MS)
            invalidate()
        }
    }

    private fun renderSubjectBars(view: View, bySubject: List<SubjectAccuracy>) {
        val container = view.findViewById<LinearLayout>(R.id.subjectBarsContainer)
        container.removeAllViews()
        val inflater = LayoutInflater.from(requireContext())

        bySubject.forEach { item ->
            val row = inflater.inflate(R.layout.item_subject_bar, container, false)
            row.findViewById<TextView>(R.id.tvSubjectName).text =
                SubjectCatalog.nameFor(item.subjectId).asString(requireContext())
            row.findViewById<TextView>(R.id.tvSubjectPercent).text = getString(R.string.percent, item.accuracy)
            val bar = row.findViewById<ProgressBar>(R.id.progressSubject)
            bar.progress = item.accuracy
            // Tint the percentage text by level
            val colorRes = when {
                item.accuracy >= GOOD_ACCURACY -> R.color.state_success
                item.accuracy >= FAIR_ACCURACY -> R.color.brand_primary
                else -> R.color.state_error
            }
            row.findViewById<TextView>(R.id.tvSubjectPercent)
                .setTextColor(ContextCompat.getColor(requireContext(), colorRes))
            container.addView(row)
        }
    }

    private fun renderWeakTopics(view: View, weakest: List<WeakTopic>) {
        val card = view.findViewById<View>(R.id.cardWeakest)
        val container = view.findViewById<LinearLayout>(R.id.weakestContainer)
        container.removeAllViews()

        if (weakest.isEmpty()) {
            card.isVisible = false
            return
        }
        card.isVisible = true
        val inflater = LayoutInflater.from(requireContext())

        weakest.forEach { topic ->
            val row = inflater.inflate(R.layout.item_weak_topic, container, false)
            row.findViewById<TextView>(R.id.tvWeakTopic).text = getString(
                R.string.subject_and_topic,
                SubjectCatalog.nameFor(topic.subjectId).asString(requireContext()),
                TopicNames.displayName(topic.topicId).asString(requireContext())
            )
            row.findViewById<TextView>(R.id.tvWeakDetail).text =
                getString(R.string.statistics_weak_topic_detail, topic.failurePercent)
            container.addView(row)
        }
    }

    private fun subjectIdsForCourse(course: Int): List<String> =
        SubjectCatalog.all.filter { it.course == course }.map { it.id }

    private companion object {
        // Evolution chart
        const val LINE_WIDTH_DP = 2.5f
        const val POINT_RADIUS_DP = 4f
        const val POINT_HOLE_RADIUS_DP = 2f
        const val FILL_TOP_ALPHA = 0x40 // ~25 %, fading to transparent
        const val MAX_PERCENT = 100f
        const val CHART_ANIMATION_MS = 600

        // Accuracy (%) from which a subject bar is shown as good or fair
        const val GOOD_ACCURACY = 70
        const val FAIR_ACCURACY = 45
    }
}
