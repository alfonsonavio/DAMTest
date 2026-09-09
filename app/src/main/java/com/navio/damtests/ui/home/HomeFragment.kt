package com.navio.damtests.ui.home

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.card.MaterialCardView
import com.navio.damtests.QuizRepository
import com.navio.damtests.R
import com.navio.damtests.TopicSelectionActivity
import com.navio.damtests.auth.AuthManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

/**
 * Home screen: greeting, quick stats, "continue where you left off" and a
 * shortcut to smart review. Light on detail by design — subject selection lives
 * in SubjectsFragment and detailed charts in Statistics.
 */
@AndroidEntryPoint
class HomeFragment : Fragment(R.layout.fragment_home) {

    @Inject lateinit var repository: QuizRepository
    @Inject lateinit var authManager: AuthManager

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val tvGreeting     = view.findViewById<TextView>(R.id.tvGreeting)
        val tvName         = view.findViewById<TextView>(R.id.tvName)
        val tvAvgScore     = view.findViewById<TextView>(R.id.tvAvgScore)
        val tvTotalTests   = view.findViewById<TextView>(R.id.tvTotalTests)
        val cardContinue   = view.findViewById<MaterialCardView>(R.id.cardContinue)
        val tvContinueHeader  = view.findViewById<TextView>(R.id.tvContinueHeader)
        val tvContinueSubject = view.findViewById<TextView>(R.id.tvContinueSubject)
        val tvContinueDetail  = view.findViewById<TextView>(R.id.tvContinueDetail)
        val cardSmartReview   = view.findViewById<MaterialCardView>(R.id.cardSmartReview)
        val btnSettings       = view.findViewById<ImageButton>(R.id.btnSettings)

        tvGreeting.text = greetingForTimeOfDay()
        tvName.text     = authManager.displayName

        // Smart review shortcut → launches smart review for… well, it needs a subject.
        // For now it opens Subjects so the user picks; refined later.
        cardSmartReview.setOnClickListener {
            // Placeholder: smart review is per-subject, so send the user to pick one.
            // (Kept simple until we add a global smart-review entry.)
        }

        // Settings button: screen comes in the `settings` branch. No-op for now.
        btnSettings.setOnClickListener { /* TODO: open Settings (settings branch) */ }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                repository.getAllProgress().collect { allProgress ->
                    // Quick stats
                    val totalTests     = allProgress.sumOf { it.attemptsCount }
                    val totalScore     = allProgress.sumOf { it.lastScore }
                    val totalQuestions = allProgress.sumOf { it.totalQuestions }
                    val average = if (totalQuestions > 0)
                        totalScore.toDouble() / totalQuestions * 10 else 0.0
                    tvAvgScore.text   = String.format("%.1f", average)
                    tvTotalTests.text = totalTests.toString()

                    // Continue where you left off: most recent progress by timestamp
                    val lastProgress = allProgress
                        .filter { it.lastAttemptTimestamp > 0 }
                        .maxByOrNull { it.lastAttemptTimestamp }

                    val hasHistory = lastProgress != null
                    cardContinue.isVisible = hasHistory
                    tvContinueHeader.isVisible = hasHistory

                    if (lastProgress != null) {
                        tvContinueSubject.text = subjectDisplayName(lastProgress.subjectId)
                        tvContinueDetail.text  = topicDisplayName(lastProgress.topicId)
                        cardContinue.setOnClickListener {
                            startActivity(
                                Intent(requireContext(), TopicSelectionActivity::class.java)
                                    .putExtra("SUBJECT_ID", lastProgress.subjectId)
                            )
                        }
                    }
                }
            }
        }
    }

    private fun greetingForTimeOfDay(): String =
        when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
            in 6..12  -> "Buenos días"
            in 13..20 -> "Buenas tardes"
            else      -> "Buenas noches"
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
        else             -> subjectId
    }

    private fun topicDisplayName(topicId: String): String = when {
        topicId == "-1" -> "Test general (todo)"
        topicId == "-2" -> "Test general (temas 1-10)"
        topicId == "-3" -> "Test general (temas 11-20)"
        topicId == "-4" -> "Repaso inteligente"
        topicId.startsWith("tema_") -> "Tema ${topicId.removePrefix("tema_")}"
        else -> topicId
    }
}