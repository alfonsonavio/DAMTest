package com.navio.damtests

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.navio.damtests.data.local.db.AppDatabase
import com.navio.damtests.data.local.entity.Subject
import com.navio.damtests.ui.SubjectAdapter
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var repository: QuizRepository
    private lateinit var tvAvgScore: TextView
    private lateinit var tvTotalTests: TextView
    private lateinit var syncManager: FirebaseSyncManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvAvgScore = findViewById(R.id.tvAvgScore)
        tvTotalTests = findViewById(R.id.tvTotalTests)

        val database = AppDatabase.getDatabase(this)
        repository = QuizRepository(database.questionsDao())

        syncManager = FirebaseSyncManager(this, repository)

        lifecycleScope.launch {
            Log.d("SYNC", "Iniciando sincronización desde MainActivity...")
            syncManager.syncQuestions()
        }

        setupDashboardStats()
        setupSubjectList()
    }

    private fun setupDashboardStats() {
        lifecycleScope.launchWhenStarted {
            repository.getAllProgress().collect { allProgress ->
                if (allProgress.isNotEmpty()) {
                    val totalTests = allProgress.sumOf { it.attemptsCount }
                    val totalScore = allProgress.sumOf { it.lastScore }
                    val totalQuestions = allProgress.sumOf { it.totalQuestions }

                    val average = if (totalQuestions > 0)
                        (totalScore.toDouble() / totalQuestions * 10) else 0.0

                    tvAvgScore.text = String.format("%.1f", average)
                    tvTotalTests.text = totalTests.toString()
                }
            }
        }
    }

    private fun setupSubjectList() {
        val rv = findViewById<RecyclerView>(R.id.rvSubjects)
        rv.layoutManager = GridLayoutManager(this, 2)

        lifecycleScope.launchWhenStarted {
            repository.getAllProgress().collect { progressList ->
                rv.adapter = SubjectAdapter(getSubjectsList(), progressList) { subject ->
                    val intent = Intent(this@MainActivity, TopicSelectionActivity::class.java)
                    intent.putExtra("SUBJECT_ID", subject.id)
                    startActivity(intent)
                }
            }
        }
    }

    private fun getSubjectsList(): List<Subject> {
        return listOf(
            Subject("programacion", "Programación", R.drawable.ic_terminal, R.color.bg_prog),
            Subject("base_de_datos", "Base de Datos", R.drawable.ic_storage, R.color.bg_db),
            Subject("sistemas", "Sistemas", R.drawable.ic_memory, R.color.bg_sistemas),
            Subject("marcas", "Leng. Marcas", R.drawable.ic_description, R.color.bg_marcas),
            Subject("entornos", "Entornos", R.drawable.ic_code, R.color.bg_entornos),
            Subject("digitalizacion", "Digitalización", R.drawable.ic_computer, R.color.bg_digital),
            Subject("ipe", "IPE", R.drawable.ic_assessment, R.color.bg_ipe),
            Subject("sostenibilidad", "Sostenibilidad", R.drawable.ic_eco, R.color.bg_sostenibilidad)
        )
    }
}