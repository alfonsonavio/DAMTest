package com.navio.damtests.data.statistics

import com.navio.damtests.data.local.entity.QuestionStats
import com.navio.damtests.data.local.entity.TestAttempt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for StatisticsCalculator — pure logic, no Android or Room.
 */
class StatisticsCalculatorTest {

    private lateinit var calc: StatisticsCalculator

    @Before
    fun setUp() { calc = StatisticsCalculator() }

    private fun stat(subject: String, topic: String, key: String, seen: Int, correct: Int) =
        QuestionStats(
            stableId = "${subject}_${topic}_$key",
            subjectId = subject,
            timesSeen = seen,
            timesCorrect = correct,
            timesWrong = seen - correct,
            lastSeenTimestamp = 0L
        )

    private fun attempt(subject: String, topic: String, score: Int, total: Int, ts: Long) =
        TestAttempt(subjectId = subject, topicId = topic, score = score, totalQuestions = total, timestamp = ts)

    @Test
    fun `global accuracy is correct over all questions`() {
        val stats = listOf(
            stat("prog", "tema_1", "p1", seen = 10, correct = 8),
            stat("prog", "tema_1", "p2", seen = 10, correct = 2)
        )
        val result = calc.calculate(emptyList(), stats)
        // 10 correct out of 20 seen = 50%
        assertEquals(50, result.globalAccuracy)
    }

    @Test
    fun `global accuracy is zero when nothing seen`() {
        val result = calc.calculate(emptyList(), emptyList())
        assertEquals(0, result.globalAccuracy)
    }

    @Test
    fun `total tests equals number of attempts`() {
        val attempts = listOf(
            attempt("prog", "tema_1", 8, 10, 100L),
            attempt("prog", "tema_2", 5, 10, 200L)
        )
        val result = calc.calculate(attempts, emptyList())
        assertEquals(2, result.totalTests)
    }

    @Test
    fun `evolution is chronological and capped at 10 points`() {
        val attempts = (1..15).map { attempt("prog", "tema_1", it, 20, it.toLong()) }
        val result = calc.calculate(attempts, emptyList())
        assertEquals(10, result.evolution.size)
        // must be sorted ascending by timestamp
        val timestamps = result.evolution.map { it.timestamp }
        assertEquals(timestamps.sorted(), timestamps)
        // last point is the newest attempt (ts=15)
        assertEquals(15L, result.evolution.last().timestamp)
    }

    @Test
    fun `evolution percentage is score over total`() {
        val attempts = listOf(attempt("prog", "tema_1", 15, 20, 100L))
        val result = calc.calculate(attempts, emptyList())
        assertEquals(75, result.evolution.first().percentage) // 15/20 = 75%
    }

    @Test
    fun `bySubject computes accuracy per subject sorted best first`() {
        val stats = listOf(
            stat("prog", "tema_1", "p1", seen = 10, correct = 9), // 90%
            stat("db",   "tema_1", "p1", seen = 10, correct = 4)  // 40%
        )
        val result = calc.calculate(emptyList(), stats)
        assertEquals(2, result.bySubject.size)
        assertEquals("prog", result.bySubject.first().subjectId) // best first
        assertEquals(90, result.bySubject.first().accuracy)
        assertEquals(40, result.bySubject.last().accuracy)
    }

    @Test
    fun `weakest topics only includes topics failed above threshold`() {
        val stats = listOf(
            // tema_1: 70% wrong, seen 10 → should appear
            stat("prog", "tema_1", "p1", seen = 10, correct = 3),
            // tema_2: 10% wrong → below threshold, excluded
            stat("prog", "tema_2", "p1", seen = 10, correct = 9)
        )
        val result = calc.calculate(emptyList(), stats)
        assertEquals(1, result.weakestTopics.size)
        assertEquals("tema_1", result.weakestTopics.first().topicId)
        assertEquals(70, result.weakestTopics.first().failurePercent)
    }

    @Test
    fun `weakest topics ignores barely-seen topics`() {
        val stats = listOf(
            // seen only twice → below WEAK_TOPIC_MIN_SEEN, excluded even if failed
            stat("prog", "tema_1", "p1", seen = 2, correct = 0)
        )
        val result = calc.calculate(emptyList(), stats)
        assertTrue(result.weakestTopics.isEmpty())
    }

    @Test
    fun `topic key is extracted correctly from stableId`() {
        // stableId "digitalizacion_tema_1_p1" → subject digitalizacion, topic tema_1
        val stats = listOf(
            QuestionStats("digitalizacion_tema_1_p1", "digitalizacion", 10, 2, 8, 0L)
        )
        val result = calc.calculate(emptyList(), stats)
        assertEquals("tema_1", result.weakestTopics.first().topicId)
        assertEquals("digitalizacion", result.weakestTopics.first().subjectId)
    }

    @Test
    fun `empty data produces empty statistics`() {
        val result = calc.calculate(emptyList(), emptyList())
        assertTrue(result.isEmpty)
    }
}