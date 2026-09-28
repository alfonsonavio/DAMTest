package com.navio.damtests

import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.materialswitch.MaterialSwitch
import com.navio.damtests.auth.AuthManager
import com.navio.damtests.auth.AuthUiHelper
import com.navio.damtests.notifications.ReminderScheduler
import com.navio.damtests.settings.SettingsManager
import dagger.hilt.android.AndroidEntryPoint
import java.util.Locale
import javax.inject.Inject

/**
 * Settings screen: profile, notification toggle + hour, haptics toggle, and
 * logout (anchored to the bottom). Reads/writes via SettingsManager and applies
 * changes immediately (reminders reschedule, etc.).
 */
@AndroidEntryPoint
class SettingsActivity : AppCompatActivity() {

    @Inject lateinit var settings: SettingsManager
    @Inject lateinit var authManager: AuthManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        setupProfile()
        setupReminders()
        setupHaptics()
        setupLogout()

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
    }

    private fun setupProfile() {
        val name  = authManager.displayName
        findViewById<TextView>(R.id.tvProfileName).text = name
        findViewById<TextView>(R.id.tvProfileEmail).text = authManager.email
        findViewById<TextView>(R.id.tvAvatar).text =
            name.firstOrNull()?.uppercase() ?: "U"
    }

    private fun setupReminders() {
        val switch = findViewById<MaterialSwitch>(R.id.switchReminders)
        val rowHour = findViewById<LinearLayout>(R.id.rowReminderHour)
        val tvHour = findViewById<TextView>(R.id.tvReminderHour)

        switch.isChecked = settings.remindersEnabled
        updateHourRowState(rowHour, tvHour, settings.remindersEnabled)
        tvHour.text = formatHour(settings.reminderHour)

        switch.setOnCheckedChangeListener { _, isChecked ->
            settings.remindersEnabled = isChecked
            updateHourRowState(rowHour, tvHour, isChecked)
            // Apply immediately
            if (isChecked) ReminderScheduler.schedule(this) else ReminderScheduler.cancel(this)
        }

        rowHour.setOnClickListener {
            if (!settings.remindersEnabled) return@setOnClickListener
            android.app.TimePickerDialog(
                this,
                { _, hour, _ ->
                    settings.reminderHour = hour
                    tvHour.text = formatHour(hour)
                    // Reschedule with the new hour
                    ReminderScheduler.schedule(this)
                },
                settings.reminderHour, 0, true
            ).show()
        }
    }

    private fun setupHaptics() {
        val switch = findViewById<MaterialSwitch>(R.id.switchHaptics)
        switch.isChecked = settings.hapticsEnabled
        switch.setOnCheckedChangeListener { _, isChecked ->
            settings.hapticsEnabled = isChecked
        }
    }

    private fun setupLogout() {
        findViewById<LinearLayout>(R.id.rowLogout).setOnClickListener {
            AuthUiHelper.showConfirm(
                context = this,
                title = getString(R.string.logout_title),
                message = getString(R.string.logout_message),
                confirmText = getString(R.string.logout_confirm)
            ) {
                authManager.signOut()
                ReminderScheduler.cancel(this)
                startActivity(
                    android.content.Intent(this, LoginActivity::class.java)
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK or
                                android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
    }

    private fun updateHourRowState(row: View, tvHour: TextView, enabled: Boolean) {
        row.alpha = if (enabled) 1f else 0.4f
        row.isClickable = enabled
    }

    private fun formatHour(hour: Int): String = String.format(Locale.getDefault(), "%02d:00", hour)
}