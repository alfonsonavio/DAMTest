package com.navio.damtests.data.streak

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Current study streak and whether the user has already studied today. */
data class StreakInfo(val days: Int, val studiedToday: Boolean)

/**
 * Pure study-streak maths. No Android, no database — fully unit-testable.
 *
 * A study day is a calendar day (in the device's time zone) with at least one
 * finished test, smart review included. The streak is the number of
 * consecutive study days ending today, or ending yesterday if the user hasn't
 * studied yet today: the streak is only lost once a whole day goes by without
 * a test.
 */
object StreakCalculator {

    fun calculate(
        timestamps: List<Long>,
        now: Long,
        zone: ZoneId = ZoneId.systemDefault()
    ): StreakInfo {
        val studyDays = timestamps.mapTo(HashSet()) { it.toLocalDate(zone) }
        val today = now.toLocalDate(zone)
        val studiedToday = today in studyDays

        var day: LocalDate = when {
            studiedToday                     -> today
            today.minusDays(1) in studyDays  -> today.minusDays(1)
            else                             -> return StreakInfo(0, false)
        }

        var days = 0
        while (day in studyDays) {
            days++
            day = day.minusDays(1)
        }
        return StreakInfo(days, studiedToday)
    }

    private fun Long.toLocalDate(zone: ZoneId): LocalDate =
        Instant.ofEpochMilli(this).atZone(zone).toLocalDate()
}