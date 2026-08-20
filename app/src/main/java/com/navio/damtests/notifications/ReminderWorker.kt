package com.navio.damtests.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * WorkManager worker that shows a study-reminder notification.
 *
 * Scheduled to run roughly once a day by [ReminderScheduler]. Picks a random
 * motivational message each time so the reminder doesn't feel repetitive.
 */
class ReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val (title, message) = REMINDER_MESSAGES.random()
        NotificationHelper(applicationContext).showReminder(title, message)
        return Result.success()
    }

    companion object {
        /** Rotating reminder messages (title to body). */
        private val REMINDER_MESSAGES = listOf(
            "¿Un test rápido?" to "Dedica 5 minutos a repasar y mantén el ritmo.",
            "Hora de practicar" to "Un test al día te acerca a aprobar tu examen de DAM.",
            "No pierdas la racha" to "Vuelve a DAMTest y sigue mejorando tus notas.",
            "Repaso pendiente" to "Tus preguntas falladas te esperan en el repaso inteligente.",
            "¡A por ello!" to "Un poco de práctica hoy es mucho menos estudio mañana."
        )
    }
}