package com.navio.damtests.ui.topics

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.navio.damtests.R
import com.navio.damtests.data.catalog.SubjectCatalog
import com.navio.damtests.data.exam.ExamConfig
import com.navio.damtests.data.exam.ExamRange
import com.navio.damtests.data.quiz.QuizRepository
import com.navio.damtests.data.quiz.TestMode
import com.navio.damtests.data.session.QuizSessionStore
import com.navio.damtests.ui.common.AuthUiHelper
import com.navio.damtests.ui.exam.ExamActivity
import com.navio.damtests.ui.exam.ExamSetupDialog
import com.navio.damtests.ui.quiz.QuizLauncher
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * A subject: the practice modes on top (exam, smart review, general test and
 * favourites), always in view, and its topics below with their last score
 * and PDF notes.
 */
@AndroidEntryPoint
class TopicSelectionActivity : AppCompatActivity() {

    @Inject lateinit var repository: QuizRepository
    @Inject lateinit var sessionStore: QuizSessionStore

    private val viewModel: TopicSelectionViewModel by viewModels()
    private val pdfOpener = TopicPdfOpener(this)

    private lateinit var subjectId: String
    private lateinit var adapter: TopicAdapter

    private lateinit var practiceSection: View
    private lateinit var cardExam: MaterialCardView
    private lateinit var cardReview: MaterialCardView
    private lateinit var cardGeneralTest: MaterialCardView
    private lateinit var cardFavorites: MaterialCardView
    private lateinit var tvTopicsHeader: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_topic_selection)

        subjectId = intent.getStringExtra(EXTRA_SUBJECT_ID) ?: return finish()

        setSupportActionBar(findViewById<Toolbar>(R.id.toolbar_topics))
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title     = SubjectCatalog.nameFor(subjectId).asString(this@TopicSelectionActivity)
                .uppercase(resources.configuration.locales[0])
            elevation = 0f
        }
        @Suppress("DEPRECATION")
        window.statusBarColor = getColor(R.color.brand_navy)

        bindViews()
        setupPracticeCards()

        adapter = TopicAdapter(
            onTopicClick = { row -> openTopic(row.topic.id) },
            onPdfClick   = { row -> pdfOpener.open(subjectId, row.topic.id) }
        )
        findViewById<RecyclerView>(R.id.rvTopics).apply {
            layoutManager = LinearLayoutManager(this@TopicSelectionActivity)
            adapter = this@TopicSelectionActivity.adapter
        }

        viewModel.load(subjectId)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { render(it) }
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    // --- Setup ---

    private fun bindViews() {
        practiceSection = findViewById(R.id.practiceSection)
        cardExam        = findViewById(R.id.cardExam)
        cardReview      = findViewById(R.id.cardSmartReview)
        cardGeneralTest = findViewById(R.id.cardGeneralTest)
        cardFavorites   = findViewById(R.id.cardFavorites)
        tvTopicsHeader  = findViewById(R.id.tvTopicsHeader)
    }

    private fun setupPracticeCards() {
        setCard(cardExam, R.string.practice_icon_exam, R.string.practice_exam_title)
        setCard(cardReview, R.string.practice_icon_review, R.string.practice_review_title)
        setCard(cardGeneralTest, R.string.practice_icon_general, R.string.practice_general_title)
        setCard(cardFavorites, R.string.practice_icon_favorites, R.string.practice_favorites_title)

        cardExam.setOnClickListener { lifecycleScope.launch { showExamSetup() } }
        cardReview.setOnClickListener { openSmartReview() }
        cardGeneralTest.setOnClickListener { chooseGeneralTest() }
        cardFavorites.setOnClickListener { openFavorites() }
    }

    private fun setCard(card: View, icon: Int, title: Int) {
        card.findViewById<TextView>(R.id.tvPracticeIcon).setText(icon)
        card.findViewById<TextView>(R.id.tvPracticeTitle).setText(title)
    }

    private fun setSubtitle(card: View, text: String) {
        card.findViewById<TextView>(R.id.tvPracticeSubtitle).text = text
    }

    // --- Rendering ---

    private fun render(state: TopicSelectionViewModel.UiState) {
        if (state.loading) return
        val practice = state.practice

        // A subject with only PDFs so far has nothing to practise
        practiceSection.visibility = if (practice.hasQuestions) View.VISIBLE else View.GONE
        cardExam.visibility        = if (practice.hasGeneralTest) View.VISIBLE else View.GONE
        cardGeneralTest.visibility = if (practice.hasGeneralTest) View.VISIBLE else View.GONE

        setSubtitle(cardExam, getString(R.string.practice_exam_subtitle))
        setSubtitle(
            cardGeneralTest,
            getString(if (practice.hasHalves) R.string.practice_general_subtitle_ranges
            else R.string.practice_general_subtitle)
        )
        setSubtitle(
            cardReview,
            if (state.failedCount == 0) getString(R.string.practice_review_none)
            else resources.getQuantityString(R.plurals.practice_review_count, state.failedCount, state.failedCount)
        )
        setSubtitle(
            cardFavorites,
            if (state.favoriteCount == 0) getString(R.string.practice_favorites_none)
            else resources.getQuantityString(R.plurals.practice_favorites_count, state.favoriteCount, state.favoriteCount)
        )
        // Modes without anything to practise yet look inactive (but still explain why on tap)
        cardReview.alpha    = if (state.failedCount == 0) INACTIVE_ALPHA else 1f
        cardFavorites.alpha = if (state.favoriteCount == 0) INACTIVE_ALPHA else 1f

        tvTopicsHeader.text = getString(R.string.topics_header, state.topics.size)
        adapter.submitList(state.topics)
    }

    // --- Practice modes ---

    private suspend fun showExamSetup() {
        val counts = ExamRange.entries.associateWith { repository.countExamQuestions(subjectId, it) }
        val ranges = ExamSetupDialog.availableRanges(counts)
        if (ranges.isEmpty()) {
            AuthUiHelper.showInfo(this, getString(R.string.no_questions_title), getString(R.string.no_questions_yet))
            return
        }
        ExamSetupDialog.show(this, ranges) { range, size, penalized ->
            startActivity(ExamActivity.intent(this, ExamConfig(subjectId, range, size, penalized)))
        }
    }

    private fun openSmartReview() {
        if (viewModel.state.value.failedCount == 0) {
            AuthUiHelper.showInfo(
                this, getString(R.string.practice_review_title), getString(R.string.practice_review_empty)
            )
        } else {
            openTopic(TestMode.SMART_REVIEW.topicId)
        }
    }

    private fun openFavorites() {
        if (viewModel.state.value.favoriteCount == 0) {
            AuthUiHelper.showInfo(
                this, getString(R.string.practice_favorites_title), getString(R.string.practice_favorites_empty)
            )
        } else {
            openTopic(TestMode.FAVORITES.topicId)
        }
    }

    /** The whole syllabus, or a half of it when the subject has both. */
    private fun chooseGeneralTest() {
        if (!viewModel.state.value.practice.hasHalves) {
            openTopic(TestMode.GENERAL_ALL.topicId)
            return
        }
        val options = listOf(
            R.string.general_test_all to TestMode.GENERAL_ALL,
            R.string.general_test_first_half to TestMode.GENERAL_FIRST_HALF,
            R.string.general_test_second_half to TestMode.GENERAL_SECOND_HALF
        )
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.practice_general_title)
            .setItems(options.map { getString(it.first) }.toTypedArray()) { _, which ->
                openTopic(options[which].second.topicId)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // --- Topics ---

    /** Opens a test, offering to resume it if it was left half-way. */
    private fun openTopic(topicId: String) {
        lifecycleScope.launch {
            val isTestMode = TestMode.fromTopicId(topicId) != null
            when {
                sessionStore.load()?.isFor(subjectId, topicId) == true ->
                    QuizLauncher.open(this@TopicSelectionActivity, sessionStore, subjectId, topicId)
                isTestMode || repository.hasQuestions(subjectId, topicId) ->
                    QuizLauncher.start(this@TopicSelectionActivity, subjectId, topicId, resume = false)
                else -> AuthUiHelper.showInfo(
                    this@TopicSelectionActivity,
                    getString(R.string.no_questions_title),
                    getString(R.string.no_questions_yet)
                )
            }
        }
    }

    companion object {
        const val EXTRA_SUBJECT_ID = "SUBJECT_ID"

        private const val INACTIVE_ALPHA = 0.6f

        fun intent(context: Context, subjectId: String): Intent =
            Intent(context, TopicSelectionActivity::class.java).putExtra(EXTRA_SUBJECT_ID, subjectId)
    }
}