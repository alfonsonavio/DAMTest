package com.navio.damtests.notifications

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Duration
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * Schedules the daily study reminder via WorkManager.
 *
 * Fires once a day at the given time. The time comes from the user's settings
 * (see SettingsManager); this object only knows how to schedule and cancel.
 */
object ReminderScheduler {

    private const val WORK_NAME = "daily_study_reminder"
    private const val REPEAT_DAYS = 1L

    /**
     * Schedules the daily reminder at [hour]:[minute].
     *
     * @param replace when true, an existing schedule is replaced so a changed
     *   time takes effect immediately (used when the user updates the setting).
     *   When false (app startup) an existing schedule is kept, so its timing
     *   isn't reset on every launch.
     */
    fun schedule(context: Context, hour: Int, minute: Int, replace: Boolean = false) {
        val initialDelay = initialDelay(ZonedDateTime.now(), hour, minute)

        val request = PeriodicWorkRequestBuilder<ReminderWorker>(REPEAT_DAYS, TimeUnit.DAYS)
            .setInitialDelay(initialDelay.toMillis(), TimeUnit.MILLISECONDS)
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

    /**
     * Time from [now] until the next [hour]:[minute]: today if it is still
     * ahead, otherwise tomorrow. Uses the zone of [now], so daylight saving
     * changes are taken into account.
     */
    internal fun initialDelay(now: ZonedDateTime, hour: Int, minute: Int): Duration {
        val today = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
        val next = if (today.isAfter(now)) today else today.plusDays(1)
        return Duration.between(now, next)
    }
}