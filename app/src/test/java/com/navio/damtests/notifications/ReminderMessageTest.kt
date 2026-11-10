package com.navio.damtests.notifications

import com.navio.damtests.core.UiText
import com.navio.damtests.data.streak.StreakInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Unit tests for the reminder text chosen from the study streak. */
class ReminderMessageTest {

    private val streakTitles = ReminderWorker.STREAK_MESSAGES.map { it.first }
    private val streakBodies = ReminderWorker.STREAK_MESSAGES.map { it.second }

    @Test
    fun `active streak not studied today warns about losing it`() {
        val message = ReminderWorker.messageFor(StreakInfo(days = 5, studiedToday = false))

        assertTrue((message.title as UiText.Res).id in streakTitles)
        val body = message.body as UiText.Plural
        assertTrue(body.id in streakBodies)
        assertEquals(5, body.count)
    }

    @Test
    fun `streak warnings vary`() {
        val random = Random(seed = 1)
        val titles = (1..50).map {
            (ReminderWorker.messageFor(StreakInfo(days = 3, studiedToday = false), random).title as UiText.Res).id
        }.toSet()

        assertEquals(streakTitles.toSet(), titles)
    }

    @Test
    fun `already studied today gets a normal reminder`() {
        val message = ReminderWorker.messageFor(StreakInfo(days = 5, studiedToday = true))

        assertTrue((message.title as UiText.Res).id !in streakTitles)
        assertTrue(message.body is UiText.Res)
    }

    @Test
    fun `no streak gets a normal reminder`() {
        val message = ReminderWorker.messageFor(StreakInfo(days = 0, studiedToday = false))

        assertTrue((message.title as UiText.Res).id !in streakTitles)
    }
}
