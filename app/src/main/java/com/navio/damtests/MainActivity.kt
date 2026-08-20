package com.navio.damtests

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.ImageButton
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.navio.damtests.auth.AuthManager
import com.navio.damtests.notifications.ReminderScheduler
import com.navio.damtests.auth.AuthUiHelper
import com.navio.damtests.data.local.entity.Subject
import com.navio.damtests.ui.SubjectAdapter
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject lateinit var repository: QuizRepository
    @Inject lateinit var authManager: AuthManager
    private lateinit var tvAvgScore: TextView
    private lateinit var tvTotalTests: TextView
    private lateinit var tvWelcome: TextView
    private lateinit var syncManager: FirebaseSyncManager

    // Notification permission launcher (Android 13+). Result ignored: if the user
    // declines, reminders simply won't show — no need to block anything.
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* granted or not, nothing else to do here */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Guard: if not logged in, redirect to LoginActivity
        if (!authManager.isLoggedIn) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_main)

        tvAvgScore   = findViewById(R.id.tvAvgScore)
        tvTotalTests = findViewById(R.id.tvTotalTests)
        tvWelcome    = findViewById(R.id.tvWelcome)

        tvWelcome.text = getString(R.string.welcome_user, authManager.displayName)

        // Logout button in the custom header
        findViewById<ImageButton>(R.id.btnLogout).setOnClickListener { confirmLogout() }

        syncManager  = FirebaseSyncManager(this, repository)

        lifecycleScope.launch {
            Log.d(TAG, "Starting Remote Config fetch…")
            RemoteConfigManager.fetchAndActivate()
        }

        lifecycleScope.launch {
            Log.d(TAG, "Starting Firebase question sync…")
            syncManager.syncQuestions()
        }

        setupDashboardStats()
        setupSubjectList()

        requestNotificationPermissionIfNeeded()

        // User is logged in (guard above) → schedule the daily study reminder
        ReminderScheduler.schedule(this)
    }

    /**
     * On Android 13+ the POST_NOTIFICATIONS permission must be requested at
     * runtime. On older versions notifications work without it, so we skip.
     */
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

        val alreadyGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED

        if (!alreadyGranted) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun confirmLogout() {
        AuthUiHelper.showConfirm(
            context = this,
            title = getString(R.string.logout_title),
            message = getString(R.string.logout_message),
            confirmText = getString(R.string.logout_confirm)
        ) {
            authManager.signOut()
            ReminderScheduler.cancel(this)
            startActivity(
                Intent(this, LoginActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    private fun setupDashboardStats() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                repository.getAllProgress().collect { allProgress ->
                    if (allProgress.isNotEmpty()) {
                        val totalTests     = allProgress.sumOf { it.attemptsCount }
                        val totalScore     = allProgress.sumOf { it.lastScore }
                        val totalQuestions = allProgress.sumOf { it.totalQuestions }
                        val average = if (totalQuestions > 0)
                            totalScore.toDouble() / totalQuestions * 10 else 0.0

                        tvAvgScore.text   = String.format("%.1f", average)
                        tvTotalTests.text = totalTests.toString()
                    }
                }
            }
        }
    }

    private fun setupSubjectList() {
        val rv = findViewById<RecyclerView>(R.id.rvSubjects)
        rv.layoutManager = GridLayoutManager(this, 2)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                repository.getAllProgress().collect { progressList ->
                    rv.adapter = SubjectAdapter(getSubjectsList(), progressList) { subject ->
                        startActivity(
                            Intent(this@MainActivity, TopicSelectionActivity::class.java)
                                .putExtra("SUBJECT_ID", subject.id)
                        )
                    }
                }
            }
        }
    }

    private fun getSubjectsList(): List<Subject> = listOf(
        Subject("programacion",   "Programación",   R.drawable.ic_terminal,    R.color.bg_prog),
        Subject("base_de_datos",  "Base de Datos",  R.drawable.ic_storage,     R.color.bg_db),
        Subject("sistemas",       "Sistemas",        R.drawable.ic_memory,      R.color.bg_sistemas),
        Subject("marcas",         "Leng. Marcas",    R.drawable.ic_description, R.color.bg_marcas),
        Subject("entornos",       "Entornos",        R.drawable.ic_code,        R.color.bg_entornos),
        Subject("digitalizacion", "Digitalización",  R.drawable.ic_computer,    R.color.bg_digital),
        Subject("ipe",            "IPE",             R.drawable.ic_assessment,  R.color.bg_ipe),
        Subject("sostenibilidad", "Sostenibilidad",  R.drawable.ic_eco,         R.color.bg_sostenibilidad)
    )

    companion object {
        private const val TAG = "MainActivity"
    }
}