package com.navio.damtests

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.navio.damtests.auth.AuthManager
import com.navio.damtests.notifications.ReminderScheduler
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Host activity for the bottom-navigation shell. Holds the NavHostFragment and
 * the BottomNavigationView; the actual screens live in HomeFragment,
 * SubjectsFragment and StatisticsFragment.
 *
 * App-level concerns (question sync, remote config, notification permission and
 * reminder scheduling) stay here because they should run once per session, not
 * per fragment.
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject lateinit var repository: QuizRepository
    @Inject lateinit var authManager: AuthManager
    private lateinit var syncManager: FirebaseSyncManager

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* result ignored: declining just disables reminders */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!authManager.isLoggedIn) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_main)
        setupBottomNavigation()

        syncManager = FirebaseSyncManager(this, repository)

        lifecycleScope.launch {
            Log.d(TAG, "Starting Remote Config fetch…")
            RemoteConfigManager.fetchAndActivate()
        }
        lifecycleScope.launch {
            Log.d(TAG, "Starting Firebase question sync…")
            syncManager.syncQuestions()
        }

        requestNotificationPermissionIfNeeded()
        ReminderScheduler.schedule(this)
    }

    private fun setupBottomNavigation() {
        val navHost = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHost.navController
        findViewById<BottomNavigationView>(R.id.bottomNav)
            .setupWithNavController(navController)
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}