package com.navio.damtests.notifications

import com.navio.damtests.data.streak.StreakInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unit tests for the reminder text chosen from the study streak. */
class ReminderMessageTest {

    @Test
    fun `active streak not studied today warns about losing it`() {
        val (title, body) = ReminderWorker.messageFor(StreakInfo(days = 5, studiedToday = false))
        assertEquals("🔥 ¡No pierdas tu racha!", title)
        assertTrue(body.contains("5 días de racha"))
    }

    @Test
    fun `one-day streak uses the singular`() {
        val (_, body) = ReminderWorker.messageFor(StreakInfo(days = 1, studiedToday = false))
        assertTrue(body.contains("1 día de racha"))
    }

    @Test
    fun `already studied today gets a normal reminder`() {
        val (title, _) = ReminderWorker.messageFor(StreakInfo(days = 5, studiedToday = true))
        assertFalse(title.contains("racha"))
    }

    @Test
    fun `no streak gets a normal reminder`() {
        val (title, _) = ReminderWorker.messageFor(StreakInfo(days = 0, studiedToday = false))
        assertFalse(title.contains("racha"))
    }
}