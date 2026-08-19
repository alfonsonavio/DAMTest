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
 * Fires once a day around [REMINDER_HOUR]. The hour is fixed for now, but is
 * centralised here so it can be made user-configurable once a settings screen
 * exists (planned with the UI redesign).
 */
object ReminderScheduler {

    private const val WORK_NAME = "daily_study_reminder"
    private const val REMINDER_HOUR = 18  // 18:00, fixed for now

    /**
     * Schedules the daily reminder. Uses KEEP so an existing schedule isn't reset
     * every time the app starts (the reminder keeps its original timing).
     */
    fun schedule(context: Context) {
        val initialDelay = computeInitialDelayMillis()

        val request = PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    /** Cancels the daily reminder (for a future settings toggle). */
    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    /** Millis from now until the next REMINDER_HOUR:00. */
    private fun computeInitialDelayMillis(): Long {
        val now = Calendar.getInstance()
        val next = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, REMINDER_HOUR)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            if (before(now)) add(Calendar.DAY_OF_MONTH, 1) // already past today → tomorrow
        }
        return next.timeInMillis - now.timeInMillis
    }
}