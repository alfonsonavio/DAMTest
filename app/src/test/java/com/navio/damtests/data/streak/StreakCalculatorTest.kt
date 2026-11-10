package com.navio.damtests.data.streak

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

/** Unit tests for the study-streak maths. */
class StreakCalculatorTest {

    private val zone = ZoneId.of("Europe/Madrid")

    /** Epoch ms for a local date-time in [zone]. */
    private fun at(day: Int, hour: Int = 12, month: Int = 11) =
        LocalDateTime.of(2026, month, day, hour, 0).atZone(zone).toInstant().toEpochMilli()

    private fun streak(vararg tests: Long, now: Long) =
        StreakCalculator.calculate(tests.toList(), now, zone)

    @Test
    fun `no tests means no streak`() {
        assertEquals(StreakInfo(0, false), streak(now = at(9)))
    }

    @Test
    fun `consecutive days ending today are counted`() {
        assertEquals(StreakInfo(3, true), streak(at(7), at(8), at(9), now = at(9, 20)))
    }

    @Test
    fun `several tests on the same day count once`() {
        assertEquals(StreakInfo(2, true), streak(at(8, 9), at(8, 18), at(9, 10), at(9, 11), now = at(9, 20)))
    }

    @Test
    fun `streak is kept until the end of today if yesterday was studied`() {
        assertEquals(StreakInfo(2, false), streak(at(7), at(8), now = at(9, 23)))
    }

    @Test
    fun `a whole day without tests loses the streak`() {
        assertEquals(StreakInfo(0, false), streak(at(6), at(7), now = at(9, 8)))
    }

    @Test
    fun `a gap before the latest run starts a new streak`() {
        assertEquals(StreakInfo(2, true), streak(at(5), at(6), at(8), at(9), now = at(9, 20)))
    }

    @Test
    fun `days are local calendar days, not 24-hour windows`() {
        // 23:30 one day and 00:30 the next are two different study days
        assertEquals(StreakInfo(2, true), streak(at(8, 23), at(9, 0), now = at(9, 1)))
    }

    @Test
    fun `streak crosses month boundaries`() {
        assertEquals(
            StreakInfo(3, true),
            streak(at(30, month = 10), at(31, month = 10), at(1), now = at(1, 20))
        )
    }
}
