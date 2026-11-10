package com.navio.damtests.data.quiz

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TestModeTest {

    @Test
    fun `stored ids never change`() {
        // These ids are saved in Room and Firestore: changing one would orphan
        // the history, progress and saved tests of every user.
        assertEquals(
            listOf("-1", "-2", "-3", "-4", "-5", "-6", "-7", "-8"),
            TestMode.entries.map { it.topicId }
        )
    }

    @Test
    fun `every mode is found from its topic id and regular topics are not modes`() {
        TestMode.entries.forEach { assertEquals(it, TestMode.fromTopicId(it.topicId)) }
        assertNull(TestMode.fromTopicId("tema_1"))
        assertNull(TestMode.fromTopicId("caso_2"))
    }

    @Test
    fun `only smart review and favourites are practice`() {
        assertEquals(
            setOf(TestMode.SMART_REVIEW, TestMode.FAVORITES),
            TestMode.entries.filter { it.isPractice }.toSet()
        )
        assertFalse(TestMode.GENERAL_ALL.isPractice)
    }

    @Test
    fun `the three exam ranges are exams`() {
        assertTrue(TestMode.EXAM_FIRST_HALF.isExam)
        assertEquals(3, TestMode.entries.count { it.isExam })
    }
}
