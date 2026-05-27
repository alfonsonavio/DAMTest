package com.navio.damtests

import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsetsController
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.button.MaterialButton
import com.navio.damtests.ai.FastExplainer
import com.navio.damtests.data.local.db.AppDatabase
import com.navio.damtests.data.local.entity.Question
import com.navio.damtests.ui.viewmodel.QuizViewModel
import com.navio.damtests.ui.viewmodel.QuizViewModelFactory
import kotlinx.coroutines.launch

class QuizActivity : AppCompatActivity() {

    private lateinit var viewModel: QuizViewModel
    private lateinit var tvQuestion: TextView
    private lateinit var btnA: Button
    private lateinit var btnB: Button
    private lateinit var btnC: Button
    private lateinit var btnD: Button
    private lateinit var tvFeedbackA: TextView
    private lateinit var tvFeedbackB: TextView
    private lateinit var tvFeedbackC: TextView
    private lateinit var tvFeedbackD: TextView
    private lateinit var tvCount: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var btnContextInfo: Button
    private lateinit var btnNext: Button

    private var currentShuffledQuestion: ShuffledQuestion? = null
    private val groq = FastExplainer()

    // Subject and topic received from TopicSelectionActivity
    private lateinit var subjectId: String
    private lateinit var topicId: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_quiz)

        applyLightStatusBar()

        subjectId = intent.getStringExtra("SUBJECT_ID") ?: "programacion"
        topicId   = intent.getStringExtra("TOPIC_ID")   ?: "tema_1"

        tvQuestion     = findViewById(R.id.tvQuestionText)
        btnA           = findViewById(R.id.btnOptionA)
        btnB           = findViewById(R.id.btnOptionB)
        btnC           = findViewById(R.id.btnOptionC)
        btnD           = findViewById(R.id.btnOptionD)
        tvFeedbackA    = findViewById(R.id.tvFeedbackA)
        tvFeedbackB    = findViewById(R.id.tvFeedbackB)
        tvFeedbackC    = findViewById(R.id.tvFeedbackC)
        tvFeedbackD    = findViewById(R.id.tvFeedbackD)
        tvCount        = findViewById(R.id.tvQuestionCount)
        progressBar    = findViewById(R.id.quizProgressBar)
        btnContextInfo = findViewById(R.id.btnContextInfo)
        btnNext        = findViewById(R.id.btnNextQuestion)

        val database   = AppDatabase.getDatabase(this)
        val repository = QuizRepository(database.questionsDao())
        viewModel = ViewModelProvider(this, QuizViewModelFactory(repository))[QuizViewModel::class.java]

        setupObservers()
        setupClickListeners()
        if (viewModel.questions.value.isEmpty()) {
            viewModel.loadQuestions(subjectId, topicId)
        }
    }

    // --- Observers ---

    private fun setupObservers() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.questions.collect { questions ->
                    if (questions.isNotEmpty()) {
                        progressBar.max = questions.size
                        refreshCountAndProgress(viewModel.currentQuestionIndex.value, questions.size)
                        updateUI(questions[viewModel.currentQuestionIndex.value])
                    }
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.currentQuestionIndex.collect { index ->
                    val questions = viewModel.questions.value
                    if (questions.isNotEmpty()) {
                        refreshCountAndProgress(index, questions.size)
                        updateUI(questions[index])
                    }
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.isLoading.collect { loading ->
                    setAnswerButtonsEnabled(!loading)
                    if (loading) tvQuestion.text = getString(R.string.loading)
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.isTestFinished.collect { finished ->
                    if (finished) showResultsDialog(viewModel.score.value)
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.currentAnswerState.collect { result ->
                    when {
                        result != null                  -> highlightAnswer(result)
                        !viewModel.isTestFinished.value -> resetAnswerUI()
                    }
                }
            }
        }
    }

    // --- UI helpers ---

    private fun refreshCountAndProgress(index: Int, total: Int) {
        val pos = index + 1
        tvCount.text         = "$pos de $total"
        progressBar.progress = pos
        btnNext.text = if (pos == total) getString(R.string.finish_test)
        else getString(R.string.next_question)
    }

    private fun updateUI(question: Question) {
        val shuffled = question.shuffle()
        currentShuffledQuestion = shuffled

        tvQuestion.text = shuffled.originalQuestion.text
        btnA.text       = shuffled.shuffledOptions[0]
        btnB.text       = shuffled.shuffledOptions[1]
        btnC.text       = shuffled.shuffledOptions[2]
        btnD.text       = shuffled.shuffledOptions[3]

        if (!question.contextText.isNullOrEmpty()) {
            btnContextInfo.visibility = View.VISIBLE
            btnContextInfo.setOnClickListener { showContextDialog(question.contextText) }
        } else {
            btnContextInfo.visibility = View.GONE
        }
    }

    private fun highlightAnswer(result: QuizViewModel.AnswerResult) {
        val buttons   = listOf(btnA, btnB, btnC, btnD)
        val feedbacks = listOf(tvFeedbackA, tvFeedbackB, tvFeedbackC, tvFeedbackD)

        setAnswerButtonsEnabled(false)

        val shuffled    = currentShuffledQuestion ?: return
        val question    = shuffled.originalQuestion
        val correctText = when (question.correctOptionIndex) {
            0    -> question.optionA
            1    -> question.optionB
            2    -> question.optionC
            else -> question.optionD
        }

        buttons.forEachIndexed { index, button ->
            val mBtn          = button as MaterialButton
            val isCorrectBtn  = mBtn.text == correctText
            val isSelectedBtn = index == result.selectedIndex

            mBtn.strokeWidth = when {
                isCorrectBtn                       -> 8
                isSelectedBtn && !result.isCorrect -> 8
                else                               -> 0
            }
            if (isCorrectBtn) {
                mBtn.setStrokeColorResource(android.R.color.holo_green_dark)
            } else if (isSelectedBtn && !result.isCorrect) {
                mBtn.setStrokeColorResource(android.R.color.holo_red_dark)
            }
        }

        if (!result.isCorrect) {
            feedbacks[result.selectedIndex].apply {
                visibility = View.VISIBLE
                text       = getString(R.string.ai_analyzing)
                setTextColor(Color.GRAY)
            }
            feedbacks[result.correctIndex].apply {
                visibility = View.VISIBLE
                text       = getString(R.string.ai_preparing_correction)
                setTextColor(Color.GRAY)
            }

            lifecycleScope.launch {
                try {
                    val response = groq.explicarRapido(
                        pregunta = question.text,
                        elegida  = buttons[result.selectedIndex].text.toString(),
                        correcta = buttons[result.correctIndex].text.toString()
                    )
                    val parts = response.split("|")
                    if (parts.size >= 2) {
                        feedbacks[result.selectedIndex].apply {
                            text = "❌ ${parts[0].trim()}"
                            setTextColor(Color.parseColor("#EF4444"))
                        }
                        feedbacks[result.correctIndex].apply {
                            text = "✅ ${parts[1].trim()}"
                            setTextColor(Color.parseColor("#10B981"))
                        }
                    } else {
                        feedbacks[result.correctIndex].apply {
                            text = "✅ $response"
                            setTextColor(Color.parseColor("#10B981"))
                        }
                        feedbacks[result.selectedIndex].visibility = View.GONE
                    }
                } catch (e: Exception) {
                    feedbacks[result.selectedIndex].apply {
                        text = getString(R.string.ai_connection_error)
                        setTextColor(Color.parseColor("#EF4444"))
                    }
                }
            }
        }

        btnNext.visibility = View.VISIBLE
    }

    private fun resetAnswerUI() {
        val buttons   = listOf(btnA, btnB, btnC, btnD)
        val feedbacks = listOf(tvFeedbackA, tvFeedbackB, tvFeedbackC, tvFeedbackD)

        buttons.forEach { (it as MaterialButton).strokeWidth = 0 }
        feedbacks.forEach { it.visibility = View.GONE; it.text = "" }
        btnNext.visibility = View.GONE
        setAnswerButtonsEnabled(true)
    }

    // --- Click listeners ---

    private fun setupClickListeners() {
        btnA.setOnClickListener { processAnswer(0) }
        btnB.setOnClickListener { processAnswer(1) }
        btnC.setOnClickListener { processAnswer(2) }
        btnD.setOnClickListener { processAnswer(3) }
        btnNext.setOnClickListener { viewModel.goToNextQuestion() }
    }

    private fun processAnswer(uiIndex: Int) {
        val shuffled     = currentShuffledQuestion ?: return
        val selectedText = listOf(btnA, btnB, btnC, btnD)[uiIndex].text.toString()
        viewModel.checkAnswer(selectedText, shuffled.shuffledOptions)
    }

    private fun setAnswerButtonsEnabled(enabled: Boolean) {
        listOf(btnA, btnB, btnC, btnD).forEach { it.isEnabled = enabled }
    }

    // --- Dialogs ---

    private fun showResultsDialog(score: Int) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_results, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialogView.findViewById<TextView>(R.id.tvDialogMessage).text =
            getString(R.string.quiz_result_message, score)

        dialogView.findViewById<Button>(R.id.btnDialogReview).setOnClickListener {
            dialog.dismiss()
            showReviewScreen()
        }
        dialogView.findViewById<Button>(R.id.btnDialogExit).setOnClickListener {
            dialog.dismiss()
            finish()
        }
        dialog.show()
    }

    private fun showReviewScreen() {
        val results = viewModel.getResults()
        startActivity(
            Intent(this, ReviewActivity::class.java)
                .putExtra("SUBJECT_ID", subjectId)
                .putExtra("TOPIC_ID", topicId)
                .putExtra("SCORE", viewModel.score.value)
                .putExtra("TOTAL", results.size)
                .putParcelableArrayListExtra("RESULTS", ArrayList(results))
        )
        finish()
    }

    private fun showContextDialog(text: String) {
        AlertDialog.Builder(this)
            .setTitle(R.string.context_dialog_title)
            .setMessage(text)
            .setPositiveButton(R.string.close, null)
            .show()
    }

    private fun applyLightStatusBar() {
        window.statusBarColor = Color.parseColor("#F8FAFC")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.setSystemBarsAppearance(
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
            )
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        }
    }
}