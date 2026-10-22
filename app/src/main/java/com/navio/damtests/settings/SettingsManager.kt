package com.navio.damtests.settings

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
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

    /** Theme choice: [THEME_SYSTEM], [THEME_LIGHT] or [THEME_DARK]. */
    var themeMode: Int
        get() = prefs.getInt(KEY_THEME_MODE, THEME_SYSTEM)
        set(value) = prefs.edit { putInt(KEY_THEME_MODE, value) }

    /** Maps [themeMode] to the AppCompatDelegate night-mode flag. */
    fun nightModeFlag(): Int = when (themeMode) {
        THEME_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
        THEME_DARK  -> AppCompatDelegate.MODE_NIGHT_YES
        else        -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
    }

    companion object {
        private const val PREFS_NAME = "damtest_settings"
        private const val KEY_REMINDERS = "reminders_enabled"
        private const val KEY_REMINDER_HOUR = "reminder_hour"
        private const val KEY_HAPTICS = "haptics_enabled"
        private const val KEY_THEME_MODE = "theme_mode"
        const val DEFAULT_HOUR = 18

        const val THEME_SYSTEM = 0
        const val THEME_LIGHT = 1
        const val THEME_DARK = 2
    }
}