package com.navio.damtests.settings

import android.Manifest
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.materialswitch.MaterialSwitch
import com.navio.damtests.LoginActivity
import com.navio.damtests.R
import com.navio.damtests.auth.AuthManager
import com.navio.damtests.auth.AuthUiHelper
import com.navio.damtests.notifications.ReminderScheduler
import dagger.hilt.android.AndroidEntryPoint
import java.util.Locale
import javax.inject.Inject

/**
 * Settings screen: profile summary, daily-reminder toggle + hour, haptics
 * toggle, and logout. Reads and writes [SettingsManager]; changes to the
 * reminder are applied to WorkManager immediately.
 */
@AndroidEntryPoint
class SettingsActivity : AppCompatActivity() {

    @Inject lateinit var settingsManager: SettingsManager
    @Inject lateinit var authManager: AuthManager

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* declining just means no notification is shown; the schedule still exists */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        setupProfile()
        setupReminders()
        setupHaptics()
        setupLogout()
    }

    private fun setupProfile() {
        val name = authManager.displayName
        findViewById<TextView>(R.id.tvProfileInitial).text =
            name.firstOrNull()?.uppercase() ?: "?"
        findViewById<TextView>(R.id.tvProfileName).text = name
        findViewById<TextView>(R.id.tvProfileEmail).text = authManager.email
    }

    private fun setupReminders() {
        val switch = findViewById<MaterialSwitch>(R.id.switchReminders)
        val rowHour = findViewById<View>(R.id.rowReminderHour)
        val tvHour = findViewById<TextView>(R.id.tvReminderHour)

        fun renderHour() {
            tvHour.text = String.format(Locale.getDefault(), "%02d:00", settingsManager.reminderHour)
        }

        fun setHourRowEnabled(enabled: Boolean) {
            rowHour.isEnabled = enabled
            rowHour.alpha = if (enabled) 1f else 0.4f
        }

        switch.isChecked = settingsManager.remindersEnabled
        renderHour()
        setHourRowEnabled(settingsManager.remindersEnabled)

        switch.setOnCheckedChangeListener { _, isChecked ->
            settingsManager.remindersEnabled = isChecked
            setHourRowEnabled(isChecked)
            if (isChecked) {
                requestNotificationPermissionIfNeeded()
                ReminderScheduler.schedule(this, settingsManager.reminderHour, replace = true)
            } else {
                ReminderScheduler.cancel(this)
            }
        }

        rowHour.setOnClickListener {
            if (!settingsManager.remindersEnabled) return@setOnClickListener
            TimePickerDialog(
                this,
                { _, hourOfDay, _ ->
                    settingsManager.reminderHour = hourOfDay
                    renderHour()
                    ReminderScheduler.schedule(this, hourOfDay, replace = true)
                },
                settingsManager.reminderHour,
                0,
                true
            ).show()
        }
    }

    private fun setupHaptics() {
        val switch = findViewById<MaterialSwitch>(R.id.switchHaptics)
        switch.isChecked = settingsManager.hapticsEnabled
        switch.setOnCheckedChangeListener { _, isChecked ->
            settingsManager.hapticsEnabled = isChecked
        }
    }

    private fun setupLogout() {
        findViewById<View>(R.id.cardLogout).setOnClickListener {
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
                finish()
            }
        }
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
}