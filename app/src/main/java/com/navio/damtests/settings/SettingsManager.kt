package com.navio.damtests.settings

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * User preferences, persisted with SharedPreferences.
 *
 * Central place for the settings screen's toggles so the rest of the app reads
 * from one source: reminders on/off, reminder hour, and haptics on/off.
 */
@Singleton
class SettingsManager @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var remindersEnabled: Boolean
        get() = prefs.getBoolean(KEY_REMINDERS, true)
        set(value) = prefs.edit { putBoolean(KEY_REMINDERS, value) }

    /** Hour of day (0-23) for the daily reminder. */
    var reminderHour: Int
        get() = prefs.getInt(KEY_REMINDER_HOUR, DEFAULT_HOUR)
        set(value) = prefs.edit { putInt(KEY_REMINDER_HOUR, value) }

    var hapticsEnabled: Boolean
        get() = prefs.getBoolean(KEY_HAPTICS, true)
        set(value) = prefs.edit { putBoolean(KEY_HAPTICS, value) }

    companion object {
        private const val PREFS_NAME = "damtest_settings"
        private const val KEY_REMINDERS = "reminders_enabled"
        private const val KEY_REMINDER_HOUR = "reminder_hour"
        private const val KEY_HAPTICS = "haptics_enabled"
        const val DEFAULT_HOUR = 18
    }
}