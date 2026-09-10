package com.navio.damtests.ui.statistics

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.google.android.material.button.MaterialButtonToggleGroup
import com.navio.damtests.QuizRepository
import com.navio.damtests.R
import com.navio.damtests.data.statistics.EvolutionPoint
import com.navio.damtests.data.statistics.StatisticsData
import com.navio.damtests.data.statistics.SubjectAccuracy
import com.navio.damtests.data.statistics.WeakTopic
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Statistics screen: KPIs, per-subject accuracy bars and weakest topics, filtered
 * by course via the 1º/2º segmented control. The evolution chart is added in a
 * later commit into #chartContainer.
 */
@AndroidEntryPoint
class StatisticsFragment : Fragment(R.layout.fragment_statistics) {

    @Inject lateinit var repository: QuizRepository

    private var selectedCourse = 1

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

        loadStatistics(view)
    }

    private fun loadStatistics(view: View) {
        viewLifecycleOwner.lifecycleScope.launch {
            val subjectIds = subjectIdsForCourse(selectedCourse)
            val data = repository.getStatistics(subjectIds)
            render(view, data)
        }
    }

    private fun render(view: View, data: StatisticsData) {
        val emptyState   = view.findViewById<LinearLayout>(R.id.emptyState)
        val contentState = view.findViewById<LinearLayout>(R.id.contentState)

        if (data.isEmpty) {
            emptyState.isVisible = true
            contentState.isVisible = false
            return
        }
        emptyState.isVisible = false
        contentState.isVisible = true

        // KPIs
        view.findViewById<TextView>(R.id.tvAccuracy).text = "${data.globalAccuracy}%"
        view.findViewById<TextView>(R.id.tvTotalTests).text = data.totalTests.toString()
        view.findViewById<TextView>(R.id.tvSubjectsStudied).text = data.bySubject.size.toString()

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

        val dataSet = LineDataSet(entries, "Nota").apply {
            color = primary
            lineWidth = 2.5f
            mode = LineDataSet.Mode.CUBIC_BEZIER   // smooth curve
            setDrawValues(false)
            setDrawCircles(true)
            setCircleColor(primary)
            circleRadius = 4f
            setDrawCircleHole(true)
            circleHoleRadius = 2f
            // Gradient fill fading downwards
            setDrawFilled(true)
            fillDrawable = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(
                    (primary and 0x00FFFFFF) or 0x40000000, // ~25% alpha
                    (primary and 0x00FFFFFF)                 // 0% alpha
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
                axisMaximum = 100f
                setDrawGridLines(true)
                gridColor = Color.parseColor("#F1F5F9")
                textColor = Color.parseColor("#94A3B8")
                setDrawAxisLine(false)
            }
            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                setDrawAxisLine(false)
                setDrawLabels(false)
            }
            animateX(600)
            invalidate()
        }
    }

    private fun renderSubjectBars(view: View, bySubject: List<SubjectAccuracy>) {
        val container = view.findViewById<LinearLayout>(R.id.subjectBarsContainer)
        container.removeAllViews()
        val inflater = LayoutInflater.from(requireContext())

        bySubject.forEach { item ->
            val row = inflater.inflate(R.layout.item_subject_bar, container, false)
            row.findViewById<TextView>(R.id.tvSubjectName).text = subjectDisplayName(item.subjectId)
            row.findViewById<TextView>(R.id.tvSubjectPercent).text = "${item.accuracy}%"
            val bar = row.findViewById<ProgressBar>(R.id.progressSubject)
            bar.progress = item.accuracy
            // Tint the percentage text by level
            val colorRes = when {
                item.accuracy >= 70 -> R.color.state_success
                item.accuracy >= 45 -> R.color.brand_primary
                else                -> R.color.state_error
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
            row.findViewById<TextView>(R.id.tvWeakTopic).text =
                "${subjectDisplayName(topic.subjectId)} · ${topicDisplayName(topic.topicId)}"
            row.findViewById<TextView>(R.id.tvWeakDetail).text =
                "Fallas el ${topic.failurePercent}% de las preguntas"
            container.addView(row)
        }
    }

    private fun subjectIdsForCourse(course: Int): List<String> = if (course == 1) {
        listOf("programacion", "base_de_datos", "sistemas", "marcas",
            "entornos", "digitalizacion", "ipe", "sostenibilidad")
    } else {
        listOf("acceso_datos", "interfaces", "multimedia", "servicios", "gestion", "eie")
    }

    private fun subjectDisplayName(subjectId: String): String = when (subjectId) {
        "programacion"   -> "Programación"
        "base_de_datos"  -> "Base de Datos"
        "sistemas"       -> "Sistemas"
        "marcas"         -> "Leng. Marcas"
        "entornos"       -> "Entornos"
        "digitalizacion" -> "Digitalización"
        "ipe"            -> "IPE"
        "sostenibilidad" -> "Sostenibilidad"
        "acceso_datos"   -> "Acceso a Datos"
        "interfaces"     -> "Desarrollo de Interfaces"
        "multimedia"     -> "Prog. Multimedia"
        "servicios"      -> "Prog. Servicios"
        "gestion"        -> "Gestión Empresarial"
        "eie"            -> "EIE"
        else             -> subjectId
    }

    private fun topicDisplayName(topicId: String): String = when {
        topicId == "-1" -> "Test general"
        topicId == "-2" -> "Test general (1-10)"
        topicId == "-3" -> "Test general (11-20)"
        topicId.startsWith("tema_") -> "Tema ${topicId.removePrefix("tema_")}"
        else -> topicId
    }
}