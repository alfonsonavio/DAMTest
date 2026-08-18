package com.navio.damtests.data

import com.navio.damtests.data.local.entity.Question
import com.navio.damtests.data.local.entity.QuestionStats
import kotlin.random.Random

/**
 * Selects questions for the smart-review mode using weighted random sampling.
 *
 * Each question gets a weight based on:
 *  - a base weight, so every question keeps a chance (spaced repetition),
 *  - its failure rate (questions you get wrong more often weigh more),
 *  - novelty (never-seen questions get a solid weight so review still teaches),
 *  - staleness (the longer since you last saw it, the slightly higher the weight).
 *
 * Then it samples [limit] distinct questions without replacement, where higher
 * weight means higher probability. A [Random] can be injected for deterministic
 * tests.
 *
 * This class is pure (no Android, no Room) so it is fully unit-testable.
 */
class SmartReviewSelector(private val random: Random = Random.Default) {

    companion object {
        private const val BASE_WEIGHT = 1.0
        private const val FAILURE_MULTIPLIER = 4.0   // how much failing boosts a question
        private const val NOVELTY_WEIGHT = 2.0       // weight added for never-seen questions
        private const val MAX_STALENESS_BONUS = 1.5  // max extra weight for old questions
        private const val STALENESS_WINDOW_MS = 7L * 24 * 60 * 60 * 1000 // 7 days
    }

    /**
     * Computes the weight of a single question given its stats (may be null if
     * never answered) and the current time.
     */
    fun weightFor(stats: QuestionStats?, now: Long): Double {
        if (stats == null || stats.timesSeen == 0) {
            // Never seen: base + novelty
            return BASE_WEIGHT + NOVELTY_WEIGHT
        }
        val failureComponent = stats.failureRate * FAILURE_MULTIPLIER
        val elapsed = (now - stats.lastSeenTimestamp).coerceAtLeast(0)
        val staleness = (elapsed.toDouble() / STALENESS_WINDOW_MS).coerceIn(0.0, 1.0)
        val stalenessComponent = staleness * MAX_STALENESS_BONUS
        return BASE_WEIGHT + failureComponent + stalenessComponent
    }

    /**
     * Selects up to [limit] distinct questions by weighted random sampling.
     *
     * @param questions all candidate questions for the subject
     * @param statsById map from stableId to its stats (missing = never seen)
     * @param limit how many questions to return
     * @param now current time in millis (injectable for tests)
     */
    fun select(
        questions: List<Question>,
        statsById: Map<String, QuestionStats>,
        limit: Int,
        now: Long = System.currentTimeMillis()
    ): List<Question> {
        if (questions.isEmpty()) return emptyList()
        if (questions.size <= limit) return questions.shuffled(random)

        // Mutable pools we draw from
        val pool = questions.toMutableList()
        val weights = pool.map { weightFor(statsById[it.stableId], now) }.toMutableList()
        val selected = mutableListOf<Question>()

        repeat(limit) {
            val totalWeight = weights.sum()
            if (totalWeight <= 0.0) return@repeat

            // Weighted pick
            var r = random.nextDouble(totalWeight)
            var chosenIndex = 0
            for (i in pool.indices) {
                r -= weights[i]
                if (r <= 0.0) { chosenIndex = i; break }
            }

            selected.add(pool[chosenIndex])
            // Remove chosen so we don't pick it again (sampling without replacement)
            pool.removeAt(chosenIndex)
            weights.removeAt(chosenIndex)
        }

        return selected
    }
}