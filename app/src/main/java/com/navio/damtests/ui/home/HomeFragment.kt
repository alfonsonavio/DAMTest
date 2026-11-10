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
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.navio.damtests.R
import com.navio.damtests.auth.AuthManager
import com.navio.damtests.data.catalog.SubjectCatalog
import com.navio.damtests.data.catalog.TopicNames
import com.navio.damtests.data.quiz.QuizRepository
import com.navio.damtests.data.quiz.TestMode
import com.navio.damtests.data.session.QuizSessionStore
import com.navio.damtests.data.sync.StatsSyncRepository
import com.navio.damtests.ui.common.AuthUiHelper
import com.navio.damtests.ui.favorites.FavoritesActivity
import com.navio.damtests.ui.quiz.QuizLauncher
import com.navio.damtests.ui.settings.SettingsActivity
import com.navio.damtests.ui.topics.TopicSelectionActivity
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
    @Inject lateinit var sessionStore: QuizSessionStore
    @Inject lateinit var statsSyncRepository: StatsSyncRepository

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val tvGreeting     = view.findViewById<TextView>(R.id.tvGreeting)
        val tvName         = view.findViewById<TextView>(R.id.tvName)
        val tvStreak       = view.findViewById<TextView>(R.id.tvStreak)
        val tvAvgScore     = view.findViewById<TextView>(R.id.tvAvgScore)
        val tvTotalTests   = view.findViewById<TextView>(R.id.tvTotalTests)
        val cardContinue   = view.findViewById<MaterialCardView>(R.id.cardContinue)
        val tvContinueHeader  = view.findViewById<TextView>(R.id.tvContinueHeader)
        val tvContinueSubject = view.findViewById<TextView>(R.id.tvContinueSubject)
        val tvContinueDetail  = view.findViewById<TextView>(R.id.tvContinueDetail)
        val progressContinue  = view.findViewById<LinearProgressIndicator>(R.id.progressContinue)
        val cardSmartReview   = view.findViewById<MaterialCardView>(R.id.cardSmartReview)
        val btnSettings       = view.findViewById<ImageButton>(R.id.btnSettings)

        tvGreeting.text = greetingForTimeOfDay()
        tvName.text     = authManager.displayName ?: getString(R.string.default_user_name)

        // Smart review is per subject: ask which one, then launch it.
        cardSmartReview.setOnClickListener { pickSubjectForSmartReview() }

        // Favourites: list of marked questions and a test mixing every subject.
        view.findViewById<MaterialCardView>(R.id.cardFavorites).setOnClickListener {
            startActivity(Intent(requireContext(), FavoritesActivity::class.java))
        }

        // Settings button opens the settings screen.
        btnSettings.setOnClickListener {
            startActivity(Intent(requireContext(), SettingsActivity::class.java))
        }

        // Study streak: recalculated whenever Home is shown again (e.g. after a
        // test) and whenever a background sync brings tests from another device.
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                statsSyncRepository.syncedAt.collect {
                    tvStreak.text = getString(R.string.home_streak, repository.getStreak().days)
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                repository.getAllProgress().collect { allProgress ->
                    // Quick stats
                    val totalTests     = allProgress.sumOf { it.attemptsCount }
                    val totalScore     = allProgress.sumOf { it.lastScore }
                    val totalQuestions = allProgress.sumOf { it.totalQuestions }
                    val average = if (totalQuestions > 0)
                        totalScore.toDouble() / totalQuestions * 10 else 0.0
                    tvAvgScore.text   = getString(R.string.score_one_decimal, average)
                    tvTotalTests.text = totalTests.toString()

                    // A test left half-way takes priority: the card resumes it.
                    val session = sessionStore.load()
                    if (session != null) {
                        cardContinue.isVisible = true
                        tvContinueHeader.isVisible = true
                        tvContinueSubject.text = SubjectCatalog.nameFor(session.subjectId).asString(requireContext())
                        tvContinueDetail.text = getString(
                            R.string.home_continue_session,
                            TopicNames.displayName(session.topicId).asString(requireContext()),
                            session.nextQuestionNumber,
                            session.total
                        )
                        progressContinue.isVisible = true
                        progressContinue.max = session.total
                        progressContinue.progress = session.answeredCount
                        cardContinue.setOnClickListener {
                            QuizLauncher.start(requireContext(), session.subjectId, session.topicId, resume = true)
                        }
                        return@collect
                    }
                    progressContinue.isVisible = false

                    // Otherwise: the most recent progress by timestamp
                    val lastProgress = allProgress
                        .filter { it.lastAttemptTimestamp > 0 }
                        .maxByOrNull { it.lastAttemptTimestamp }

                    val hasHistory = lastProgress != null
                    cardContinue.isVisible = hasHistory
                    tvContinueHeader.isVisible = hasHistory

                    if (lastProgress != null) {
                        val context = requireContext()
                        tvContinueSubject.text = SubjectCatalog.nameFor(lastProgress.subjectId).asString(context)
                        tvContinueDetail.text  = TopicNames.displayName(lastProgress.topicId).asString(context)
                        cardContinue.setOnClickListener {
                            startActivity(TopicSelectionActivity.intent(requireContext(), lastProgress.subjectId))
                        }
                    }
                }
            }
        }
    }

    /**
     * Lets the user choose a subject and launches its smart review.
     * Only subjects with at least one failed question are offered.
     */
    private fun pickSubjectForSmartReview() {
        viewLifecycleOwner.lifecycleScope.launch {
            val available = SubjectCatalog.all.filter { repository.subjectHasFailures(it.id) }

            if (available.isEmpty()) {
                AuthUiHelper.showInfo(
                    requireContext(),
                    getString(R.string.practice_review_title),
                    getString(R.string.home_review_empty)
                )
                return@launch
            }

            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.practice_review_title)
                .setItems(available.map { getString(it.nameRes) }.toTypedArray()) { _, which ->
                    val subjectId = available[which].id
                    QuizLauncher.open(requireContext(), sessionStore, subjectId, TestMode.SMART_REVIEW.topicId)
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
    }

    private fun greetingForTimeOfDay(): String =
        when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
            in MORNING_HOURS   -> getString(R.string.greeting_morning)
            in AFTERNOON_HOURS -> getString(R.string.greeting_afternoon)
            else               -> getString(R.string.greeting_evening)
        }

    private companion object {
        val MORNING_HOURS = 6..12
        val AFTERNOON_HOURS = 13..20
    }
}
