package com.navio.damtests

import android.app.Application
import com.navio.damtests.notifications.NotificationHelper
import com.navio.damtests.notifications.ReminderScheduler
import dagger.hilt.android.HiltAndroidApp

/**
 * Application class required by Hilt to generate the dependency container.
 * Registered in AndroidManifest via android:name=".DamTestApp".
 *
 * On startup it ensures the notification channel exists and schedules the daily
 * study reminder (WorkManager keeps any existing schedule, so this is safe to
 * call on every launch).
 */
@HiltAndroidApp
class DamTestApp : Application() {

    override fun onCreate() {
        super.onCreate()
        NotificationHelper(this).ensureChannel()
        ReminderScheduler.schedule(this)
    }
}