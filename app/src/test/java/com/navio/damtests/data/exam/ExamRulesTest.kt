package com.navio.damtests.data.exam

import com.navio.damtests.data.quiz.TestMode
import com.navio.damtests.ui.exam.ExamSetupDialog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExamRulesTest {

    // --- Sizes and time ---

    @Test
    fun `only the sizes the topics can fill are offered`() {
        assertEquals(listOf(10, 20), Exam.availableSizes(25))
        assertEquals(listOf(10, 20, 30, 40), Exam.availableSizes(300))
    }

    @Test
    fun `with fewer questions than the smallest size the exam uses all of them`() {
        assertEquals(listOf(7), Exam.availableSizes(7))
        assertTrue(Exam.availableSizes(0).isEmpty())
    }

    @Test
    fun `each question gives a minute and a half`() {
        assertEquals(30 * 60L, Exam.durationSeconds(20))
        assertEquals("45 min", Exam.durationLabel(30))
        assertEquals("10 min 30 s", Exam.durationLabel(7))
    }

    @Test
    fun `each exam range is found from its attempt topic id`() {
        ExamRange.entries.forEach { assertEquals(it, ExamRange.fromTopicId(it.mode.topicId)) }
        assertNull(ExamRange.fromTopicId(TestMode.SMART_REVIEW.topicId))
        assertNull(ExamRange.fromTopicId("tema_1"))
    }

    // --- Setup ranges ---

    @Test
    fun `halves are offered only when both have questions`() {
        val both = ExamSetupDialog.availableRanges(
            mapOf(ExamRange.ALL to 50, ExamRange.FIRST_HALF to 30, ExamRange.SECOND_HALF to 20)
        )
        assertEquals(ExamRange.entries.toList(), both.keys.toList())

        val oneHalf = ExamSetupDialog.availableRanges(
            mapOf(ExamRange.ALL to 30, ExamRange.FIRST_HALF to 30, ExamRange.SECOND_HALF to 0)
        )
        assertEquals(listOf(ExamRange.ALL), oneHalf.keys.toList())
    }

    @Test
    fun `a subject without questions offers no exam`() {
        assertTrue(ExamSetupDialog.availableRanges(mapOf(ExamRange.ALL to 0)).isEmpty())
    }

    // --- Grading ---

    @Test
    fun `without penalty the mark is the share of correct answers`() {
        val grade = ExamGrader.grade(correct = 15, wrong = 3, blank = 2, penalized = false)
        assertEquals(7.5, grade.grade, 0.0)
        assertEquals(20, grade.total)
        assertTrue(grade.passed)
    }

    @Test
    fun `with penalty each wrong answer takes a third of a correct one`() {
        // 15 - 3/3 = 14 points out of 20 → 7
        val grade = ExamGrader.grade(correct = 15, wrong = 3, blank = 2, penalized = true)
        assertEquals(7.0, grade.grade, 0.0)
    }

    @Test
    fun `blank answers never subtract`() {
        val grade = ExamGrader.grade(correct = 10, wrong = 0, blank = 10, penalized = true)
        assertEquals(5.0, grade.grade, 0.0)
    }

    @Test
    fun `the mark never goes below zero and is rounded to two decimals`() {
        assertEquals(0.0, ExamGrader.grade(correct = 1, wrong = 9, blank = 0, penalized = true).grade, 0.0)
        // 2 - 1/3 = 1.666… out of 3 → 5.555… → 5.56
        assertEquals(5.56, ExamGrader.grade(correct = 2, wrong = 1, blank = 0, penalized = true).grade, 0.0)
    }

    @Test
    fun `marks are shown in Spanish format without useless decimals`() {
        assertEquals("7,25", ExamGrader.format(7.25))
        assertEquals("6,5", ExamGrader.format(6.5))
        assertEquals("10", ExamGrader.format(10.0))
    }
}