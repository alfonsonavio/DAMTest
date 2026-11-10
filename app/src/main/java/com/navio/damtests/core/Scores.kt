package com.navio.damtests.core

/** Highest mark of the Spanish 0-10 scale. */
const val MAX_MARK = 10

private const val PERCENT = 100

/** [part] of [total] as a whole percentage, rounded down (0 when [total] is 0). */
fun percentOf(part: Int, total: Int): Int = if (total > 0) part * PERCENT / total else 0

/** [score] out of [total] on the 0-10 scale (0 when [total] is 0). */
fun markOf(score: Int, total: Int): Double = if (total > 0) score.toDouble() / total * MAX_MARK else 0.0
