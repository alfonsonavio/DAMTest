package com.navio.damtests.auth

import com.google.firebase.firestore.FirebaseFirestore
import com.navio.damtests.data.local.entity.TopicProgress
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for UserProgressRepository.mergeProgress().
 *
 * mergeProgress is a pure function (no Firebase, no coroutines), so it's the
 * easiest thing in the app to test — and one of the most important, since a bug
 * here means users lose progress when switching devices.
 *
 * We still mock FirebaseFirestore just to construct the class, but these tests
 * never touch it — they only exercise the merge logic.
 */
class UserProgressRepositoryTest {

    private lateinit var repository: UserProgressRepository

    private fun progress(topic: String, score: Int, timestamp: Long) = TopicProgress(
        subjectId = "programacion",
        topicId = topic,
        lastScore = score,
        totalQuestions = 10,
        attemptsCount = 1,
        lastAttemptTimestamp = timestamp
    )

    @Before
    fun setUp() {
        val firestore = mockk<FirebaseFirestore>(relaxed = true)
        repository = UserProgressRepository(firestore)
    }

    @Test
    fun `merge keeps the more recent record for the same topic`() {
        val local = listOf(progress("tema_1", score = 5, timestamp = 100L))
        val cloud = listOf(progress("tema_1", score = 9, timestamp = 200L)) // newer

        val merged = repository.mergeProgress(local, cloud)

        assertEquals(1, merged.size)
        assertEquals(9, merged.first().lastScore) // the newer (cloud) record wins
    }

    @Test
    fun `merge keeps local when local is more recent`() {
        val local = listOf(progress("tema_1", score = 7, timestamp = 300L)) // newer
        val cloud = listOf(progress("tema_1", score = 4, timestamp = 100L))

        val merged = repository.mergeProgress(local, cloud)

        assertEquals(7, merged.first().lastScore)
    }

    @Test
    fun `merge combines different topics from both sources`() {
        val local = listOf(progress("tema_1", 5, 100L))
        val cloud = listOf(progress("tema_2", 8, 100L))

        val merged = repository.mergeProgress(local, cloud)

        assertEquals(2, merged.size)
        val topics = merged.map { it.topicId }.toSet()
        assertEquals(setOf("tema_1", "tema_2"), topics)
    }

    @Test
    fun `merge of two empty lists is empty`() {
        val merged = repository.mergeProgress(emptyList(), emptyList())
        assertEquals(0, merged.size)
    }

    @Test
    fun `merge with empty cloud returns all local records`() {
        val local = listOf(progress("tema_1", 5, 100L), progress("tema_2", 6, 100L))

        val merged = repository.mergeProgress(local, emptyList())

        assertEquals(2, merged.size)
    }
}