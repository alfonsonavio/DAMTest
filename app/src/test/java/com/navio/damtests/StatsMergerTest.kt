package com.navio.damtests

import com.navio.damtests.data.local.entity.QuestionStats
import com.navio.damtests.data.local.entity.TestAttempt
import com.navio.damtests.data.sync.StatsMerger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unit tests for the pure statistics sync merge logic. */
class StatsMergerTest {

    private fun attempt(topic: String, timestamp: Long, id: Long = 0) = TestAttempt(
        id = id, subjectId = "programacion", topicId = topic,
        score = 5, totalQuestions = 10, timestamp = timestamp
    )

    private fun stats(id: String, seen: Int, lastSeen: Long) = QuestionStats(
        stableId = id, subjectId = "programacion",
        timesSeen = seen, timesCorrect = seen, timesWrong = 0, lastSeenTimestamp = lastSeen
    )

    // --- Attempts ---

    @Test
    fun `attempts only in the cloud are restored locally with a fresh id`() {
        val plan = StatsMerger.mergeAttempts(
            local = emptyList(),
            cloud = listOf(attempt("tema_1", 1000, id = 42)),
            uploadLocalOnly = true
        )
        assertEquals(1, plan.toLocal.size)
        assertEquals(0L, plan.toLocal.first().id)
        assertTrue(plan.toCloud.isEmpty())
    }

    @Test
    fun `attempts present on both sides are not duplicated`() {
        val plan = StatsMerger.mergeAttempts(
            local = listOf(attempt("tema_1", 1000, id = 1)),
            cloud = listOf(attempt("tema_1", 1000)),
            uploadLocalOnly = true
        )
        assertTrue(plan.toLocal.isEmpty())
        assertTrue(plan.toCloud.isEmpty())
    }

    @Test
    fun `first sync uploads attempts the cloud does not have`() {
        val plan = StatsMerger.mergeAttempts(
            local = listOf(attempt("tema_1", 1000), attempt("tema_2", 2000)),
            cloud = listOf(attempt("tema_1", 1000)),
            uploadLocalOnly = true
        )
        assertEquals(listOf("tema_2"), plan.toCloud.map { it.topicId })
    }

    @Test
    fun `incremental sync never uploads local attempts`() {
        val plan = StatsMerger.mergeAttempts(
            local = listOf(attempt("tema_2", 2000)),
            cloud = emptyList(),
            uploadLocalOnly = false
        )
        assertTrue(plan.toCloud.isEmpty())
    }

    // --- Question stats ---

    @Test
    fun `newer cloud stats replace older local stats`() {
        val plan = StatsMerger.mergeQuestionStats(
            local = listOf(stats("q1", seen = 2, lastSeen = 1000)),
            cloud = listOf(stats("q1", seen = 5, lastSeen = 2000)),
            uploadLocalNewer = true
        )
        assertEquals(5, plan.toLocal.single().timesSeen)
        assertTrue(plan.toCloud.isEmpty())
    }

    @Test
    fun `newer local stats are uploaded on first sync and kept locally`() {
        val plan = StatsMerger.mergeQuestionStats(
            local = listOf(stats("q1", seen = 5, lastSeen = 2000), stats("q2", seen = 1, lastSeen = 500)),
            cloud = listOf(stats("q1", seen = 2, lastSeen = 1000)),
            uploadLocalNewer = true
        )
        assertTrue(plan.toLocal.isEmpty())
        assertEquals(setOf("q1", "q2"), plan.toCloud.map { it.stableId }.toSet())
    }

    @Test
    fun `incremental sync only downloads stats`() {
        val plan = StatsMerger.mergeQuestionStats(
            local = listOf(stats("q1", seen = 5, lastSeen = 2000)),
            cloud = listOf(stats("q2", seen = 1, lastSeen = 3000)),
            uploadLocalNewer = false
        )
        assertEquals(listOf("q2"), plan.toLocal.map { it.stableId })
        assertTrue(plan.toCloud.isEmpty())
    }
}