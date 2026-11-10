package com.navio.damtests.notifications

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime

class ReminderSchedulerTest {

    private val madrid = ZoneId.of("Europe/Madrid")

    private fun at(day: Int, month: Int, hour: Int, minute: Int) =
        ZonedDateTime.of(2026, month, day, hour, minute, 0, 0, madrid)

    @Test
    fun `minutes are kept when the reminder is later today`() {
        val delay = ReminderScheduler.initialDelay(at(10, 11, 18, 0), hour = 18, minute = 45)

        assertEquals(Duration.ofMinutes(45), delay)
    }

    @Test
    fun `a time already past today moves to tomorrow`() {
        val delay = ReminderScheduler.initialDelay(at(10, 11, 18, 46), hour = 18, minute = 45)

        assertEquals(Duration.ofHours(24).minusMinutes(1), delay)
    }

    @Test
    fun `the exact current minute waits a whole day`() {
        val delay = ReminderScheduler.initialDelay(at(10, 11, 18, 45), hour = 18, minute = 45)

        assertEquals(Duration.ofDays(1), delay)
    }

    @Test
    fun `daylight saving change is taken into account`() {
        // 25 Oct 2026: clocks go back one hour at 03:00 in Madrid, so that day has 25 hours
        val delay = ReminderScheduler.initialDelay(at(24, 10, 20, 0), hour = 19, minute = 30)

        assertEquals(Duration.ofHours(24).plusMinutes(30), delay)
    }
}
