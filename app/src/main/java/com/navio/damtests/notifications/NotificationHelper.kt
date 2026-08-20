package com.navio.damtests.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import com.navio.damtests.LoginActivity
import com.navio.damtests.R

/**
 * Builds and shows study-reminder notifications, and owns the notification channel.
 *
 * Kept separate from the WorkManager worker so the notification logic can be
 * reused and reasoned about on its own.
 */
class NotificationHelper(private val context: Context) {

    private val notificationManager = context.getSystemService<NotificationManager>()

    /**
     * Creates the reminders channel. Safe to call repeatedly — creating a channel
     * that already exists is a no-op. Only does anything on API 26+.
     */
    fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Recordatorios de estudio",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Recordatorios para que no dejes de practicar"
        }
        notificationManager?.createNotificationChannel(channel)
    }

    /** Shows a study-reminder notification with the given title and message. */
    fun showReminder(title: String, message: String) {
        ensureChannel()

        // Tapping the notification opens the app (LoginActivity routes onward).
        val intent = Intent(context, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)  // dismiss when tapped
            .build()

        notificationManager?.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        const val CHANNEL_ID = "study_reminders"
        private const val NOTIFICATION_ID = 1001
    }
}