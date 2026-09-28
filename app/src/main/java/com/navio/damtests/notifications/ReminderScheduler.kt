package com.navio.damtests.notifications

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Schedules the daily study reminder via WorkManager.
 *
 * Fires once a day at the given hour. The hour comes from the user's settings
 * (see SettingsManager); this object only knows how to schedule and cancel.
 */
object ReminderScheduler {

    private const val WORK_NAME = "daily_study_reminder"

    /**
     * Schedules the daily reminder at [hour]:00.
     *
     * @param replace when true, an existing schedule is replaced so a changed
     *   hour takes effect immediately (used when the user updates the setting).
     *   When false (app startup) an existing schedule is kept, so its timing
     *   isn't reset on every launch.
     */
    fun schedule(context: Context, hour: Int, replace: Boolean = false) {
        val initialDelay = computeInitialDelayMillis(hour)

        val request = PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
            .build()

        val policy = if (replace) {
            ExistingPeriodicWorkPolicy.REPLACE
        } else {
            ExistingPeriodicWorkPolicy.KEEP
        }

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, policy, request)
    }

    /** Cancels the daily reminder (used when the user turns reminders off). */
    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    /** Millis from now until the next [hour]:00. */
    private fun computeInitialDelayMillis(hour: Int): Long {
        val now = Calendar.getInstance()
        val next = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            if (before(now)) add(Calendar.DAY_OF_MONTH, 1) // already past today → tomorrow
        }
        return next.timeInMillis - now.timeInMillis
    }
}