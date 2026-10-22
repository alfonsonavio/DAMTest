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

    // --- failures first ---

    @Test
    fun `all failed questions are included when they fit in the limit`() {
        val failed = (1..8).map { question("f$it") }
        val unseen = (1..182).map { question("n$it") } // never seen: max novelty
        val statsById = failed.associate { it.stableId to stats(it.stableId, seen = 3, wrong = 1) }

        val result = selector.select(failed + unseen, statsById, limit = 20, now = 0L)

        assertEquals(20, result.size)
        assertTrue(
            "Every failed question must be in the review",
            result.map { it.stableId }.containsAll(failed.map { it.stableId })
        )
    }

    @Test
    fun `only failed questions are picked when there are more than the limit`() {
        val failed = (1..35).map { question("f$it") }
        val others = (1..155).map { question("n$it") }
        val statsById = failed.associate { it.stableId to stats(it.stableId, seen = 2, wrong = 1) }

        val result = selector.select(failed + others, statsById, limit = 20, now = 0L)

        assertEquals(20, result.size)
        assertTrue("All picks must be failed questions", result.all { it.stableId.startsWith("f") })
    }

    @Test
    fun `most failed questions are favoured among many failed ones`() {
        val hard = (1..10).map { question("hard$it") } // always failed
        val soft = (1..30).map { question("soft$it") } // failed once in ten
        val statsById = buildMap {
            hard.forEach { put(it.stableId, stats(it.stableId, seen = 10, wrong = 10)) }
            soft.forEach { put(it.stableId, stats(it.stableId, seen = 10, wrong = 1)) }
        }

        val sel = SmartReviewSelector(Random(7))
        var hardPicks = 0
        repeat(200) {
            hardPicks += sel.select(hard + soft, statsById, limit = 10, now = 0L)
                .count { it.stableId.startsWith("hard") }
        }

        // Uniform sampling would give ~2.5 hard per run (500 total); weighting pushes it up.
        assertTrue("Hard questions should dominate (was $hardPicks)", hardPicks > 800)
    }

    @Test
    fun `isFailed is true only with at least one wrong answer`() {
        assertTrue(!selector.isFailed(null))
        assertTrue(!selector.isFailed(stats("q", seen = 5, wrong = 0)))
        assertTrue(selector.isFailed(stats("q", seen = 5, wrong = 1)))
    }
}
