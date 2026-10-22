package com.navio.damtests

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.navio.damtests.notifications.NotificationHelper
import com.navio.damtests.settings.SettingsManager
import dagger.hilt.android.HiltAndroidApp

/**
 * Application class required by Hilt to generate the dependency container.
 * Registered in AndroidManifest via android:name=".DamTestApp".
 *
 * Ensures the notification channel exists on startup. The daily reminder itself
 * is scheduled only once the user is logged in (see MainActivity), so we don't
 * remind people who aren't using the app.
 */
@HiltAndroidApp
class DamTestApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // Apply the saved theme (light / dark / system) before any Activity is shown.
        AppCompatDelegate.setDefaultNightMode(SettingsManager(this).nightModeFlag())
        NotificationHelper(this).ensureChannel()
    }
}