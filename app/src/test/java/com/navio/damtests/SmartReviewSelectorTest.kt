package com.navio.damtests.data

import com.navio.damtests.data.local.entity.Question
import com.navio.damtests.data.local.entity.QuestionStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Unit tests for SmartReviewSelector — the weighted selection algorithm behind
 * smart review. Pure logic, so no Android or Room needed.
 */
class SmartReviewSelectorTest {

    private val selector = SmartReviewSelector(Random(42)) // fixed seed = deterministic

    private fun question(stableId: String) = Question(
        stableId = stableId,
        subjectId = "programacion",
        topicId = "tema_1",
        text = "Q $stableId",
        optionA = "A", optionB = "B", optionC = "C", optionD = "D",
        correctOptionIndex = 0
    )

    private fun stats(stableId: String, seen: Int, wrong: Int, lastSeen: Long = 0L) =
        QuestionStats(
            stableId = stableId,
            subjectId = "programacion",
            timesSeen = seen,
            timesCorrect = seen - wrong,
            timesWrong = wrong,
            lastSeenTimestamp = lastSeen
        )

    // --- weightFor ---

    @Test
    fun `never seen question gets base plus novelty weight`() {
        val w = selector.weightFor(null, now = 1000L)
        assertEquals(3.0, w, 0.001) // BASE (1.0) + NOVELTY (2.0)
    }

    @Test
    fun `always failed question weighs more than always correct`() {
        val failed  = stats("q1", seen = 10, wrong = 10, lastSeen = 1000L)
        val correct = stats("q2", seen = 10, wrong = 0,  lastSeen = 1000L)

        val wFailed  = selector.weightFor(failed,  now = 1000L)
        val wCorrect = selector.weightFor(correct, now = 1000L)

        assertTrue("Failed question must weigh more", wFailed > wCorrect)
    }

    @Test
    fun `staleness increases weight for old questions`() {
        val stats = stats("q1", seen = 4, wrong = 0, lastSeen = 0L)
        val recent = selector.weightFor(stats, now = 0L)                     // just seen
        val old    = selector.weightFor(stats, now = 8L * 24 * 60 * 60 * 1000) // 8 days later

        assertTrue("Older question must weigh more", old > recent)
    }

    // --- select ---

    @Test
    fun `select returns all questions when count is below limit`() {
        val questions = listOf(question("q1"), question("q2"))
        val result = selector.select(questions, emptyMap(), limit = 20)
        assertEquals(2, result.size)
    }

    @Test
    fun `select returns exactly the limit when there are more questions`() {
        val questions = (1..50).map { question("q$it") }
        val result = selector.select(questions, emptyMap(), limit = 20)
        assertEquals(20, result.size)
    }

    @Test
    fun `select returns no duplicates`() {
        val questions = (1..50).map { question("q$it") }
        val result = selector.select(questions, emptyMap(), limit = 20)
        assertEquals(result.size, result.map { it.stableId }.toSet().size)
    }

    @Test
    fun `select returns empty for empty input`() {
        val result = selector.select(emptyList(), emptyMap(), limit = 20)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `heavily failed questions appear more often across many runs`() {
        // 1 always-failed question + 19 always-correct ones, pick 10 each run.
        val failed = question("failed")
        val others = (1..19).map { question("ok$it") }
        val all = listOf(failed) + others

        val statsById = buildMap {
            put("failed", stats("failed", seen = 20, wrong = 20, lastSeen = 0L))
            others.forEach { put(it.stableId, stats(it.stableId, seen = 20, wrong = 0, lastSeen = 0L)) }
        }

        // Run selection many times, count how often the failed question is picked
        var failedPicks = 0
        val runs = 200
        val sel = SmartReviewSelector(Random(1))
        repeat(runs) {
            val picked = sel.select(all, statsById, limit = 10, now = 0L)
            if (picked.any { it.stableId == "failed" }) failedPicks++
        }

        // With 10/20 slots it'd appear ~50% by chance; weighting should push it higher.
        assertTrue(
            "Failed question should appear in most runs (was $failedPicks/$runs)",
            failedPicks > runs * 0.6
        )
    }
}