package com.navio.damtests.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.navio.damtests.data.local.entity.QuestionsDao
import com.navio.damtests.data.streak.StreakCalculator
import com.navio.damtests.data.streak.StreakInfo
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * WorkManager worker that shows a study-reminder notification.
 *
 * Scheduled to run roughly once a day by [ReminderScheduler]. If the user has
 * a study streak and hasn't studied yet today, the reminder warns that the
 * streak is about to be lost; otherwise it picks a random motivational
 * message so the reminder doesn't feel repetitive.
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

        val (title, message) = messageFor(streak)
        NotificationHelper(applicationContext).showReminder(title, message)
        return Result.success()
    }

    companion object {
        /** Rotating reminder messages (title to body). */
        private val REMINDER_MESSAGES = listOf(
            "¿Un test rápido?" to "Dedica 5 minutos a repasar y mantén el ritmo.",
            "Hora de practicar" to "Un test al día te acerca a aprobar tu examen de DAM.",
            "Sigue con el ritmo" to "Vuelve a DAMTest y sigue mejorando tus notas.",
            "Repaso pendiente" to "Tus preguntas falladas te esperan en el repaso inteligente.",
            "¡A por ello!" to "Un poco de práctica hoy es mucho menos estudio mañana."
        )

        /** Pure choice of the reminder text (unit-tested). */
        fun messageFor(streak: StreakInfo): Pair<String, String> =
            if (streak.days > 0 && !streak.studiedToday) {
                val days = if (streak.days == 1) "1 día" else "${streak.days} días"
                "🔥 ¡No pierdas tu racha!" to
                        "Llevas $days de racha. Haz un test hoy para no perderla."
            } else {
                REMINDER_MESSAGES.random()
            }
    }
}