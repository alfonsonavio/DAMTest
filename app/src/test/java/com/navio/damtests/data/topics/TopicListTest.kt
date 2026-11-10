package com.navio.damtests.data.topics

import com.navio.damtests.data.local.entity.Topic
import com.navio.damtests.data.local.entity.TopicProgress
import com.navio.damtests.ui.topics.TopicRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TopicListTest {

    // --- Practice options ---

    @Test
    fun `a subject with only pdfs offers no practice`() {
        assertEquals(PracticeOptions.NONE, PracticeOptions.from(emptyList()))
    }

    @Test
    fun `up to 10 topics offer the general test without halves`() {
        val options = PracticeOptions.from(listOf("tema_1", "tema_2", "tema_10"))

        assertTrue(options.hasGeneralTest)
        assertFalse(options.hasHalves)
    }

    @Test
    fun `topics on both halves offer the 1-10 and 11-20 split`() {
        assertTrue(PracticeOptions.from(listOf("tema_1", "tema_12")).hasHalves)
    }

    @Test
    fun `only practical cases offer review and favourites but no general test`() {
        val options = PracticeOptions.from(listOf("caso_1"))

        assertTrue(options.hasQuestions)
        assertFalse(options.hasGeneralTest)
    }

    // --- Order ---

    @Test
    fun `topics go by number, then cases, then the final review`() {
        val sorted = listOf("repaso_1", "tema_10", "caso_2", "tema_2", "caso_1", "tema_1")
            .sortedWith(StudyTopics.ORDER)

        assertEquals(listOf("tema_1", "tema_2", "tema_10", "caso_1", "caso_2", "repaso_1"), sorted)
    }

    // --- Rows ---

    @Test
    fun `rows show the last score as a percentage and whether there is a pdf`() {
        val topics = listOf(Topic("tema_1", "Tema 1", "programacion"), Topic("tema_2", "Tema 2", "programacion"))
        val progress = listOf(TopicProgress("programacion", "tema_1", lastScore = 15, totalQuestions = 20))

        val rows = TopicRow.build(topics, progress, pdfTopicIds = setOf("tema_2"))

        assertEquals(75, rows[0].lastPercent)
        assertFalse(rows[0].hasPdf)
        assertNull(rows[1].lastPercent)
        assertTrue(rows[1].hasPdf)
    }

    @Test
    fun `a progress without questions never divides by zero`() {
        val rows = TopicRow.build(
            listOf(Topic("tema_1", "Tema 1", "programacion")),
            listOf(TopicProgress("programacion", "tema_1", lastScore = 0, totalQuestions = 0)),
            emptySet()
        )

        assertNull(rows.single().lastPercent)
    }
}