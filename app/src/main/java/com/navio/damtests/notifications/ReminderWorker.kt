package com.navio.damtests.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.navio.damtests.R
import com.navio.damtests.core.UiText
import com.navio.damtests.data.local.dao.QuestionsDao
import com.navio.damtests.data.streak.StreakCalculator
import com.navio.damtests.data.streak.StreakInfo
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlin.random.Random

/**
 * WorkManager worker that shows a study-reminder notification.
 *
 * Scheduled to run roughly once a day by [ReminderScheduler]. If the user has
 * a study streak and hasn't studied yet today, the reminder warns that the
 * streak is about to be lost; otherwise it is a motivational message. Both
 * are picked at random from several texts.
 */
class ReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    /** Hilt access to the database from a worker WorkManager creates itself. */
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ReminderEntryPoint {
        fun questionsDao(): QuestionsDao
    }

    override suspend fun doWork(): Result {
        val streak = try {
            val dao = EntryPointAccessors
                .fromApplication(applicationContext, ReminderEntryPoint::class.java)
                .questionsDao()
            StreakCalculator.calculate(dao.getAttemptTimestamps(), System.currentTimeMillis())
        } catch (e: Exception) {
            StreakInfo(0, false) // never skip the reminder because of the streak
        }

        val message = messageFor(streak)
        NotificationHelper(applicationContext).showReminder(
            message.title.asString(applicationContext),
            message.body.asString(applicationContext)
        )
        return Result.success()
    }

    /** Title and body of a reminder, resolved to text when it is shown. */
    data class ReminderMessage(val title: UiText, val body: UiText)

    companion object {
        /** Everyday reminders, one picked at random (title to body). */
        private val REMINDER_MESSAGES = listOf(
            R.string.reminder_quick_test_title to R.string.reminder_quick_test_body,
            R.string.reminder_practice_title to R.string.reminder_practice_body,
            R.string.reminder_keep_going_title to R.string.reminder_keep_going_body,
            R.string.reminder_review_title to R.string.reminder_review_body,
            R.string.reminder_go_title to R.string.reminder_go_body
        )

        /** Streak warnings, one picked at random; the bodies are plurals of the streak days. */
        val STREAK_MESSAGES = listOf(
            R.string.reminder_streak_title to R.plurals.reminder_streak_body,
            R.string.reminder_streak_waiting_title to R.plurals.reminder_streak_waiting_body,
            R.string.reminder_streak_danger_title to R.plurals.reminder_streak_danger_body,
            R.string.reminder_streak_one_more_title to R.plurals.reminder_streak_one_more_body
        )

        /**
         * Pure choice of the reminder text (unit-tested): a streak warning when
         * there is a streak and the user hasn't studied yet today, otherwise
         * an everyday reminder. Both vary so the reminder doesn't feel repetitive.
         */
        fun messageFor(streak: StreakInfo, random: Random = Random.Default): ReminderMessage =
            if (streak.days > 0 && !streak.studiedToday) {
                val (title, body) = STREAK_MESSAGES.random(random)
                ReminderMessage(UiText.Res(title), UiText.Plural(body, streak.days))
            } else {
                val (title, body) = REMINDER_MESSAGES.random(random)
                ReminderMessage(UiText.Res(title), UiText.Res(body))
            }
    }
}
