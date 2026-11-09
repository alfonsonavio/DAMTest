package com.navio.damtests.data.exam

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.roundToLong

/** Outcome of a submitted exam. Blank questions neither add nor subtract. */
data class ExamGrade(
    val correct: Int,
    val wrong: Int,
    val blank: Int,
    val penalized: Boolean,
    /** Mark out of 10, rounded to two decimals, never below 0. */
    val grade: Double
) {
    val total: Int get() = correct + wrong + blank
    val passed: Boolean get() = grade >= 5.0
}

object ExamGrader {

    /** With penalty, each wrong answer takes away a third of a correct one. */
    const val WRONG_ANSWER_PENALTY = 1.0 / 3.0

    fun grade(correct: Int, wrong: Int, blank: Int, penalized: Boolean): ExamGrade {
        val total = correct + wrong + blank
        val points = correct - if (penalized) wrong * WRONG_ANSWER_PENALTY else 0.0
        val mark = if (total == 0) 0.0 else points.coerceAtLeast(0.0) / total * 10
        return ExamGrade(correct, wrong, blank, penalized, (mark * 100).roundToLong() / 100.0)
    }

    /** Spanish format without useless decimals: "7,25", "6,5", "10". */
    fun format(grade: Double): String =
        DecimalFormat("0.##", DecimalFormatSymbols(Locale.forLanguageTag("es-ES"))).format(grade)
}