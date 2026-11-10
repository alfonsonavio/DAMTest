package com.navio.damtests.ui.settings

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
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import com.navio.damtests.R
import com.navio.damtests.auth.AccountDeletionManager
import com.navio.damtests.auth.AuthManager
import com.navio.damtests.auth.LogoutManager
import com.navio.damtests.data.settings.SettingsManager
import com.navio.damtests.notifications.ReminderScheduler
import com.navio.damtests.ui.auth.LoginActivity
import com.navio.damtests.ui.common.AuthUiHelper
import com.navio.damtests.ui.common.padForSystemBars
import com.navio.damtests.ui.common.setupEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
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

    @Inject lateinit var accountDeletionManager: AccountDeletionManager

    private lateinit var switchReminders: MaterialSwitch

    // Android 13+: asked when the user turns the reminder on. If denied, the
    // switch goes back off, since the reminder could not be shown anyway.
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            ReminderScheduler.schedule(this, settings.reminderHour, settings.reminderMinute, replace = true)
        } else {
            switchReminders.isChecked = false
            showNotificationsBlocked()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupEdgeToEdge(darkTop = true)
        setContentView(R.layout.activity_settings)
        findViewById<View>(R.id.settingsHeader).padForSystemBars(top = true)
        findViewById<View>(R.id.settingsRoot).padForSystemBars(bottom = true)

        setupProfile()
        setupReminders()
        setupTheme()
        setupHaptics()
        setupLogout()
        setupDeleteAccount()

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
    }

    private fun setupTheme() {
        val labels = resources.getStringArray(R.array.settings_theme_options)
        val tvValue = findViewById<TextView>(R.id.tvThemeValue)
        tvValue.text = labels[settings.themeMode]

        findViewById<LinearLayout>(R.id.rowTheme).setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.settings_theme_title)
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
        val name = authManager.displayName ?: getString(R.string.default_user_name)
        findViewById<TextView>(R.id.tvProfileName).text = name
        findViewById<TextView>(R.id.tvProfileEmail).text = authManager.email
        findViewById<TextView>(R.id.tvAvatar).text = name.first().uppercase()
    }

    private fun setupReminders() {
        val switch = findViewById<MaterialSwitch>(R.id.switchReminders)
        switchReminders = switch
        val rowHour = findViewById<LinearLayout>(R.id.rowReminderHour)
        val tvHour = findViewById<TextView>(R.id.tvReminderHour)

        switch.isChecked = settings.remindersEnabled
        updateHourRowState(rowHour, settings.remindersEnabled)
        tvHour.text = formatTime(settings.reminderHour, settings.reminderMinute)

        switch.setOnCheckedChangeListener { _, isChecked ->
            settings.remindersEnabled = isChecked
            updateHourRowState(rowHour, isChecked)
            // Apply immediately (replace=true so a re-enable picks up the current time)
            if (isChecked) {
                if (hasNotificationPermission()) {
                    ReminderScheduler.schedule(this, settings.reminderHour, settings.reminderMinute, replace = true)
                } else {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            } else {
                ReminderScheduler.cancel(this)
            }
        }

        rowHour.setOnClickListener {
            if (settings.remindersEnabled) showReminderTimePicker()
        }
        // After a rotation the picker is restored by the system: listen to it again
        (supportFragmentManager.findFragmentByTag(TIME_PICKER_TAG) as? MaterialTimePicker)
            ?.let(::listenToTimePicker)
    }

    /** Material 3 clock picker, in the app's colours and always 24 h. */
    private fun showReminderTimePicker() {
        val picker = MaterialTimePicker.Builder()
            .setTimeFormat(TimeFormat.CLOCK_24H)
            .setHour(settings.reminderHour)
            .setMinute(settings.reminderMinute)
            .setTitleText(R.string.settings_reminder_hour_label)
            .setInputMode(MaterialTimePicker.INPUT_MODE_CLOCK)
            .build()
        listenToTimePicker(picker)
        picker.show(supportFragmentManager, TIME_PICKER_TAG)
    }

    private fun listenToTimePicker(picker: MaterialTimePicker) {
        picker.addOnPositiveButtonClickListener {
            settings.reminderHour = picker.hour
            settings.reminderMinute = picker.minute
            findViewById<TextView>(R.id.tvReminderHour).text = formatTime(picker.hour, picker.minute)
            // Reschedule with the new time
            ReminderScheduler.schedule(this, picker.hour, picker.minute, replace = true)
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
            title = getString(R.string.settings_notifications_blocked_title),
            message = getString(R.string.settings_notifications_blocked_message),
            confirmText = getString(R.string.settings_notifications_blocked_confirm)
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

    private fun setupDeleteAccount() {
        findViewById<LinearLayout>(R.id.rowDeleteAccount).setOnClickListener {
            AuthUiHelper.showConfirm(
                context = this,
                title = getString(R.string.delete_account_title),
                message = getString(R.string.delete_account_message),
                confirmText = getString(R.string.delete_account_confirm)
            ) { deleteAccount() }
        }
    }

    private fun deleteAccount() {
        val loading = AuthUiHelper.showLoading(
            this,
            getString(R.string.delete_account_progress_title),
            getString(R.string.delete_account_progress_message)
        )
        lifecycleScope.launch {
            val result = accountDeletionManager.deleteAccount()
            loading.dismiss()
            when (result) {
                AccountDeletionManager.Result.DELETED -> {
                    ReminderScheduler.cancel(this@SettingsActivity)
                    AuthUiHelper.showInfo(
                        this@SettingsActivity,
                        getString(R.string.delete_account_done_title),
                        getString(R.string.delete_account_done_message)
                    ) { goToLogin() }
                }
                AccountDeletionManager.Result.DATA_DELETED_ACCOUNT_PENDING -> {
                    ReminderScheduler.cancel(this@SettingsActivity)
                    AuthUiHelper.showInfo(
                        this@SettingsActivity,
                        getString(R.string.delete_account_pending_title),
                        getString(R.string.delete_account_pending_message)
                    ) { goToLogin() }
                }
                AccountDeletionManager.Result.NEEDS_RECENT_LOGIN -> AuthUiHelper.showInfo(
                    this@SettingsActivity,
                    getString(R.string.delete_account_relogin_title),
                    getString(R.string.delete_account_relogin_message)
                )
                AccountDeletionManager.Result.FAILED -> AuthUiHelper.showInfo(
                    this@SettingsActivity,
                    getString(R.string.delete_account_failed_title),
                    getString(R.string.delete_account_failed_message)
                )
            }
        }
    }

    private fun goToLogin() {
        startActivity(
            Intent(this, LoginActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    private fun updateHourRowState(row: View, enabled: Boolean) {
        row.alpha = if (enabled) 1f else DISABLED_ROW_ALPHA
        row.isClickable = enabled
    }

    private fun formatTime(hour: Int, minute: Int): String =
        getString(R.string.time_hh_mm, hour, minute)

    private companion object {
        const val TIME_PICKER_TAG = "reminder_time_picker"

        /** The reminder time row looks disabled while reminders are off. */
        const val DISABLED_ROW_ALPHA = 0.4f
    }
}
