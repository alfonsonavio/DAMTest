package com.navio.damtests

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.navio.damtests.auth.AuthManager
import com.navio.damtests.auth.AuthUiHelper
import com.navio.damtests.auth.LogoutManager
import com.navio.damtests.notifications.ReminderScheduler
import com.navio.damtests.settings.SettingsManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
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
    @Inject lateinit var logoutManager: LogoutManager

    private lateinit var switchReminders: MaterialSwitch

    // Android 13+: asked when the user turns the reminder on. If denied, the
    // switch goes back off, since the reminder could not be shown anyway.
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            ReminderScheduler.schedule(this, settings.reminderHour, replace = true)
        } else {
            switchReminders.isChecked = false
            showNotificationsBlocked()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        setupProfile()
        setupReminders()
        setupTheme()
        setupHaptics()
        setupLogout()

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
    }

    private fun setupTheme() {
        val labels = arrayOf("Sistema", "Claro", "Oscuro")
        val tvValue = findViewById<TextView>(R.id.tvThemeValue)
        tvValue.text = labels[settings.themeMode]

        findViewById<LinearLayout>(R.id.rowTheme).setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle("Tema")
                .setSingleChoiceItems(labels, settings.themeMode) { dialog, which ->
                    settings.themeMode = which
                    tvValue.text = labels[which]
                    dialog.dismiss()
                    // Recreates the activities with the chosen theme immediately.
                    AppCompatDelegate.setDefaultNightMode(settings.nightModeFlag())
                }
                .show()
        }
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
        switchReminders = switch
        val rowHour = findViewById<LinearLayout>(R.id.rowReminderHour)
        val tvHour = findViewById<TextView>(R.id.tvReminderHour)

        switch.isChecked = settings.remindersEnabled
        updateHourRowState(rowHour, tvHour, settings.remindersEnabled)
        tvHour.text = formatHour(settings.reminderHour)

        switch.setOnCheckedChangeListener { _, isChecked ->
            settings.remindersEnabled = isChecked
            updateHourRowState(rowHour, tvHour, isChecked)
            // Apply immediately (replace=true so a re-enable picks up the current hour)
            if (isChecked) {
                if (hasNotificationPermission()) {
                    ReminderScheduler.schedule(this, settings.reminderHour, replace = true)
                } else {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            } else {
                ReminderScheduler.cancel(this)
            }
        }

        rowHour.setOnClickListener {
            if (!settings.remindersEnabled) return@setOnClickListener
            android.app.TimePickerDialog(
                this,
                { _, hour, _ ->
                    settings.reminderHour = hour
                    tvHour.text = formatHour(hour)
                    // Reschedule with the new hour
                    ReminderScheduler.schedule(this, hour, replace = true)
                },
                settings.reminderHour, 0, true
            ).show()
        }
    }

    private fun hasNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED

    /**
     * Explains why the reminder stayed off. If Android no longer shows the
     * permission dialog (denied before), the user can enable it from the
     * app's notification settings.
     */
    private fun showNotificationsBlocked() {
        AuthUiHelper.showConfirm(
            context = this,
            title = "Notificaciones desactivadas",
            message = "Para recibir el recordatorio diario, permite las notificaciones de DAMTest en los ajustes del sistema.",
            confirmText = "Abrir ajustes"
        ) {
            startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            )
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
            ) { logout() }
        }
    }

    /**
     * Backs everything up to the cloud before signing out. If the backup can't
     * be confirmed (e.g. no connection), the user stays signed in and nothing
     * local is deleted.
     */
    private fun logout() {
        val loading = AuthUiHelper.showLoading(
            this,
            getString(R.string.logout_saving_title),
            getString(R.string.logout_saving_message)
        )
        lifecycleScope.launch {
            val signedOut = logoutManager.backupAndSignOut()
            loading.dismiss()
            if (signedOut) {
                ReminderScheduler.cancel(this@SettingsActivity)
                startActivity(
                    Intent(this@SettingsActivity, LoginActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } else {
                AuthUiHelper.showInfo(
                    this@SettingsActivity,
                    getString(R.string.logout_failed_title),
                    getString(R.string.logout_failed_message)
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