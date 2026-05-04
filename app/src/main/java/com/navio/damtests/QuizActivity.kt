package com.navio.damtests

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.navio.damtests.data.local.db.AppDatabase
import com.navio.damtests.data.local.entity.Question
import com.navio.damtests.ui.viewmodel.QuizViewModel
import com.navio.damtests.ui.viewmodel.QuizViewModelFactory

class QuizActivity : AppCompatActivity() {

    private lateinit var viewModel: QuizViewModel
    private lateinit var tvQuestion: TextView
    private lateinit var btnA: Button
    private lateinit var btnB: Button
    private lateinit var btnC: Button
    private lateinit var btnD: Button
    private lateinit var tvCount: TextView
    private lateinit var progressBar: ProgressBar
    private var currentShuffledQuestion: ShuffledQuestion? = null
    private lateinit var btnContextInfo: Button // Al principio de la clase con los demás

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_quiz)

        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        window.statusBarColor = Color.parseColor("#F8FAFC")

        tvQuestion = findViewById(R.id.tvQuestionText)
        btnA = findViewById(R.id.btnOptionA)
        btnB = findViewById(R.id.btnOptionB)
        btnC = findViewById(R.id.btnOptionC)
        btnD = findViewById(R.id.btnOptionD)
        tvCount = findViewById(R.id.tvQuestionCount)
        progressBar = findViewById(R.id.quizProgressBar)
        btnContextInfo = findViewById(R.id.btnContextInfo)

        val database = AppDatabase.getDatabase(this)
        val repository = QuizRepository(database.questionsDao())
        val factory = QuizViewModelFactory(repository)
        viewModel = ViewModelProvider(this, factory)[QuizViewModel::class.java]

        val subjectId = intent.getStringExtra("SUBJECT_ID") ?: "programacion"
        val topicId = intent.getStringExtra("TOPIC_ID") ?: "1"

        val limit = if (topicId == "-1") 20 else 10

        TestDataHolder.currentSubjectId = subjectId
        TestDataHolder.currentTopicId = topicId

        setupObservers()
        setupClickListeners()

        viewModel.loadQuestions(subjectId, topicId)
    }

    private fun setupObservers() {
        // 1. Observador de la lista de preguntas
        lifecycleScope.launchWhenStarted {
            viewModel.questions.collect { questions ->
                if (questions.isNotEmpty()) {
                    // Configuramos la barra
                    progressBar.max = questions.size

                    // FORZAMOS el texto inicial aquí mismo
                    val initialPos = viewModel.currentQuestionIndex.value + 1
                    tvCount.text = "$initialPos de ${questions.size}"
                    progressBar.progress = initialPos

                    updateUI(questions[viewModel.currentQuestionIndex.value])
                }
            }
        }

        // 2. Observador del índice (para cuando pases a la siguiente pregunta)
        lifecycleScope.launchWhenStarted {
            viewModel.currentQuestionIndex.collect { index ->
                val questions = viewModel.questions.value
                if (questions.isNotEmpty()) {
                    val currentPos = index + 1
                    tvCount.text = "$currentPos de ${questions.size}"
                    progressBar.progress = currentPos
                    updateUI(questions[index])
                }
            }
        }

        lifecycleScope.launchWhenStarted {
            viewModel.isLoading.collect { loading ->
                setButtonsEnabled(!loading)
                if (loading) tvQuestion.text = "Cargando..."
            }
        }

        lifecycleScope.launchWhenStarted {
            viewModel.isTestFinished.collect { finished ->
                if (finished) showResultsDialog(viewModel.score.value)
            }
        }
    }

    private fun updateUI(question: Question) {
        val shuffled = question.shuffle()
        currentShuffledQuestion = shuffled

        tvQuestion.text = shuffled.originalQuestion.text
        btnA.text = shuffled.shuffledOptions[0]
        btnB.text = shuffled.shuffledOptions[1]
        btnC.text = shuffled.shuffledOptions[2]
        btnD.text = shuffled.shuffledOptions[3]

        // Lógica del enunciado/contexto
        if (!question.contextText.isNullOrEmpty()) {
            btnContextInfo.visibility = View.VISIBLE
            btnContextInfo.setOnClickListener {
                showContextDialog(question.contextText)
            }
        } else {
            btnContextInfo.visibility = View.GONE
        }
    }

    private fun setupClickListeners() {
        btnA.setOnClickListener { processAnswer(0) }
        btnB.setOnClickListener { processAnswer(1) }
        btnC.setOnClickListener { processAnswer(2) }
        btnD.setOnClickListener { processAnswer(3) }
    }

    private fun processAnswer(uiSelectedIndex: Int) {
        val shuffled = currentShuffledQuestion ?: return

        val textSelected = shuffled.shuffledOptions[uiSelectedIndex]
        val originalIndex = when (textSelected) {
            shuffled.originalQuestion.optionA -> 0
            shuffled.originalQuestion.optionB -> 1
            shuffled.originalQuestion.optionC -> 2
            shuffled.originalQuestion.optionD -> 3
            else -> -1
        }

        // AHORA PASAMOS LA LISTA MEZCLADA TAMBIÉN
        viewModel.checkAnswer(originalIndex, shuffled.shuffledOptions)
    }

    private fun setButtonsEnabled(enabled: Boolean) {
        listOf(btnA, btnB, btnC, btnD).forEach { it.isEnabled = enabled }
    }

    private fun showResultsDialog(score: Int) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_results, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialogView.findViewById<TextView>(R.id.tvDialogMessage).text = "Has acertado $score preguntas."

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
        // Obtenemos los resultados reales del ViewModel
        val results = viewModel.getResults()
        val totalQuestions = results.size
        val correctAnswers = results.count { it.userSelectedIndex == it.question.correctOptionIndex }

        // Guardamos en el Holder para la lista
        TestDataHolder.lastResults = results

        // Enviamos los datos por Intent para el encabezado
        val intent = Intent(this, ReviewActivity::class.java).apply {
            putExtra("SCORE", viewModel.score.value)
            putExtra("TOTAL", results.size)
        }
        startActivity(intent)
        finish()
    }

    private fun showContextDialog(text: String) {
        AlertDialog.Builder(this)
            .setTitle("Enunciado del Caso")
            .setMessage(text)
            .setPositiveButton("Cerrar", null)
            .show()
    }
}