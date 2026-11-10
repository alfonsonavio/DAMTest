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
import com.navio.damtests.core.markOf
import com.navio.damtests.data.catalog.SubjectCatalog
import com.navio.damtests.data.catalog.TopicNames
import com.navio.damtests.data.local.entity.TopicProgress
import com.navio.damtests.data.progress.ProgressRepository
import com.navio.damtests.data.quiz.TestMode
import com.navio.damtests.data.session.QuizSession
import com.navio.damtests.data.session.QuizSessionStore
import com.navio.damtests.data.sync.StatsSyncRepository
import com.navio.damtests.ui.common.AuthUiHelper
import com.navio.damtests.ui.common.padForSystemBars
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

    @Inject lateinit var progressRepository: ProgressRepository

    @Inject lateinit var authManager: AuthManager

    @Inject lateinit var sessionStore: QuizSessionStore

    @Inject lateinit var statsSyncRepository: StatsSyncRepository

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<View>(R.id.homeHeader).padForSystemBars(top = true)

        view.findViewById<TextView>(R.id.tvGreeting).text = greetingForTimeOfDay()
        view.findViewById<TextView>(R.id.tvName).text =
            authManager.displayName ?: getString(R.string.default_user_name)

        setupShortcuts(view)
        observeStreak(view.findViewById(R.id.tvStreak))
        observeProgress(view)
    }

    private fun setupShortcuts(view: View) {
        // Smart review is per subject: ask which one, then launch it.
        view.findViewById<MaterialCardView>(R.id.cardSmartReview).setOnClickListener { pickSubjectForSmartReview() }

        // Favourites: list of marked questions and a test mixing every subject.
        view.findViewById<MaterialCardView>(R.id.cardFavorites).setOnClickListener {
            startActivity(Intent(requireContext(), FavoritesActivity::class.java))
        }

        view.findViewById<ImageButton>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(requireContext(), SettingsActivity::class.java))
        }
    }

    /**
     * Study streak: recalculated whenever Home is shown again (e.g. after a
     * test) and whenever a background sync brings tests from another device.
     */
    private fun observeStreak(tvStreak: TextView) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                statsSyncRepository.syncedAt.collect {
                    tvStreak.text = getString(R.string.home_streak, progressRepository.getStreak().days)
                }
            }
        }
    }

    private fun observeProgress(view: View) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                progressRepository.getAllProgress().collect { allProgress ->
                    renderQuickStats(view, allProgress)
                    // A test left half-way takes priority: the card resumes it.
                    val session = sessionStore.load()
                    if (session != null) renderSavedTest(view, session) else renderLastTopic(view, allProgress)
                }
            }
        }
    }

    private fun renderQuickStats(view: View, allProgress: List<TopicProgress>) {
        val average = markOf(allProgress.sumOf { it.lastScore }, allProgress.sumOf { it.totalQuestions })
        view.findViewById<TextView>(R.id.tvAvgScore).text = getString(R.string.score_one_decimal, average)
        view.findViewById<TextView>(R.id.tvTotalTests).text = allProgress.sumOf { it.attemptsCount }.toString()
    }

    /** "Continue" card for the test left half-way: resumes it where it was. */
    private fun renderSavedTest(view: View, session: QuizSession) {
        val context = requireContext()
        view.findViewById<TextView>(R.id.tvContinueHeader).isVisible = true
        view.findViewById<TextView>(R.id.tvContinueSubject).text =
            SubjectCatalog.nameFor(session.subjectId).asString(context)
        view.findViewById<TextView>(R.id.tvContinueDetail).text = getString(
            R.string.home_continue_session,
            TopicNames.displayName(session.topicId).asString(context),
            session.nextQuestionNumber,
            session.total
        )
        view.findViewById<LinearProgressIndicator>(R.id.progressContinue).apply {
            isVisible = true
            max = session.total
            progress = session.answeredCount
        }
        view.findViewById<MaterialCardView>(R.id.cardContinue).apply {
            isVisible = true
            setOnClickListener {
                QuizLauncher.start(context, session.subjectId, session.topicId, resume = true)
            }
        }
    }

    /** "Continue" card for the most recently practised topic, if any. */
    private fun renderLastTopic(view: View, allProgress: List<TopicProgress>) {
        val lastProgress = allProgress
            .filter { it.lastAttemptTimestamp > 0 }
            .maxByOrNull { it.lastAttemptTimestamp }
        val cardContinue = view.findViewById<MaterialCardView>(R.id.cardContinue)

        view.findViewById<LinearProgressIndicator>(R.id.progressContinue).isVisible = false
        view.findViewById<TextView>(R.id.tvContinueHeader).isVisible = lastProgress != null
        cardContinue.isVisible = lastProgress != null
        if (lastProgress == null) return

        val context = requireContext()
        view.findViewById<TextView>(R.id.tvContinueSubject).text =
            SubjectCatalog.nameFor(lastProgress.subjectId).asString(context)
        view.findViewById<TextView>(R.id.tvContinueDetail).text =
            TopicNames.displayName(lastProgress.topicId).asString(context)
        cardContinue.setOnClickListener {
            startActivity(TopicSelectionActivity.intent(context, lastProgress.subjectId))
        }
    }

    /**
     * Lets the user choose a subject and launches its smart review.
     * Only subjects with at least one failed question are offered.
     */
    private fun pickSubjectForSmartReview() {
        viewLifecycleOwner.lifecycleScope.launch {
            val available = SubjectCatalog.all.filter { progressRepository.subjectHasFailures(it.id) }

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
            in MORNING_HOURS -> getString(R.string.greeting_morning)
            in AFTERNOON_HOURS -> getString(R.string.greeting_afternoon)
            else -> getString(R.string.greeting_evening)
        }

    private companion object {
        val MORNING_HOURS = 6..12
        val AFTERNOON_HOURS = 13..20
    }
}
