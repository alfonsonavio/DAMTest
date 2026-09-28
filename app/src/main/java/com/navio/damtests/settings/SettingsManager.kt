package com.navio.damtests.settings

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists the user's app preferences in SharedPreferences.
 *
 * Injectable @Singleton so any screen or manager can read and write the same
 * values without passing a Context around. Defaults preserve today's behaviour:
 * daily reminders on at 18:00, and haptics on.
 */
@Singleton
class SettingsManager @Inject constructor(
    @ApplicationContext context: Context
) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var remindersEnabled: Boolean
        get() = prefs.getBoolean(KEY_REMINDERS_ENABLED, true)
        set(value) = prefs.edit { putBoolean(KEY_REMINDERS_ENABLED, value) }

    /** Hour of day (0–23) the daily reminder fires. */
    var reminderHour: Int
        get() = prefs.getInt(KEY_REMINDER_HOUR, DEFAULT_HOUR)
        set(value) = prefs.edit { putInt(KEY_REMINDER_HOUR, value) }

    var hapticsEnabled: Boolean
        get() = prefs.getBoolean(KEY_HAPTICS_ENABLED, true)
        set(value) = prefs.edit { putBoolean(KEY_HAPTICS_ENABLED, value) }

    companion object {
        const val DEFAULT_HOUR = 18

        private const val PREFS_NAME = "damtest_settings"
        private const val KEY_REMINDERS_ENABLED = "reminders_enabled"
        private const val KEY_REMINDER_HOUR = "reminder_hour"
        private const val KEY_HAPTICS_ENABLED = "haptics_enabled"
    }
}