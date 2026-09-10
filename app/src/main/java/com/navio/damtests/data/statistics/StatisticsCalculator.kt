package com.navio.damtests.data.statistics

import com.navio.damtests.data.local.entity.QuestionStats
import com.navio.damtests.data.local.entity.TestAttempt

/**
 * Pure calculator that turns raw attempt history and per-question stats into the
 * [StatisticsData] shown on the Statistics screen. No Android, no Room — fully
 * unit-testable.
 *
 * All inputs are expected to already be filtered to a single course (the caller
 * passes only that course's subjects' data).
 */
class StatisticsCalculator {

    companion object {
        private const val MAX_EVOLUTION_POINTS = 10
        private const val MAX_WEAK_TOPICS = 5
        private const val WEAK_TOPIC_MIN_SEEN = 3   // ignore barely-seen topics
        private const val WEAK_TOPIC_MIN_FAILURE = 40 // only show topics failed ≥40%
    }

    fun calculate(
        attempts: List<TestAttempt>,
        stats: List<QuestionStats>
    ): StatisticsData {
        return StatisticsData(
            globalAccuracy = globalAccuracy(stats),
            totalTests     = attempts.size,
            evolution      = evolution(attempts),
            bySubject      = bySubject(stats),
            weakestTopics  = weakestTopics(stats)
        )
    }

    /** % correct across every answered question. */
    private fun globalAccuracy(stats: List<QuestionStats>): Int {
        val seen    = stats.sumOf { it.timesSeen }
        val correct = stats.sumOf { it.timesCorrect }
        return if (seen > 0) (correct * 100) / seen else 0
    }

    /** The last [MAX_EVOLUTION_POINTS] attempts as score-% points, chronological. */
    private fun evolution(attempts: List<TestAttempt>): List<EvolutionPoint> {
        return attempts
            .sortedBy { it.timestamp }
            .takeLast(MAX_EVOLUTION_POINTS)
            .map { EvolutionPoint(it.timestamp, it.percentage) }
    }

    /** Accuracy per subject, sorted best-first. */
    private fun bySubject(stats: List<QuestionStats>): List<SubjectAccuracy> {
        return stats
            .groupBy { it.subjectId }
            .map { (subjectId, list) ->
                val seen    = list.sumOf { it.timesSeen }
                val correct = list.sumOf { it.timesCorrect }
                SubjectAccuracy(
                    subjectId = subjectId,
                    accuracy  = if (seen > 0) (correct * 100) / seen else 0
                )
            }
            .sortedByDescending { it.accuracy }
    }

    /**
     * Weakest topics: those failed the most. A topic here is a (subject, topic)
     * pair derived from the stableId "{subjectId}_{topicId}_{firebaseKey}".
     * Only topics seen enough times and failed above the threshold are shown.
     */
    private fun weakestTopics(stats: List<QuestionStats>): List<WeakTopic> {
        // Group question stats by their topic (extracted from stableId)
        val byTopic = stats.groupBy { topicKeyOf(it) }

        return byTopic.mapNotNull { (key, list) ->
            val (subjectId, topicId) = key
            val seen  = list.sumOf { it.timesSeen }
            val wrong = list.sumOf { it.timesWrong }
            if (seen < WEAK_TOPIC_MIN_SEEN) return@mapNotNull null
            val failurePercent = (wrong * 100) / seen
            if (failurePercent < WEAK_TOPIC_MIN_FAILURE) return@mapNotNull null
            WeakTopic(subjectId, topicId, failurePercent)
        }
            .sortedByDescending { it.failurePercent }
            .take(MAX_WEAK_TOPICS)
    }

    /**
     * Extracts (subjectId, topicId) from a QuestionStats. The stableId is
     * "{subjectId}_{topicId}_{firebaseKey}", e.g. "digitalizacion_tema_1_p1"
     * → ("digitalizacion", "tema_1"). Falls back to the stored subjectId.
     */
    private fun topicKeyOf(stat: QuestionStats): Pair<String, String> {
        // stableId = subjectId + "_" + topicId + "_" + firebaseKey
        // topicId itself is like "tema_1", so we can't just split by "_".
        // Strategy: strip the known subjectId prefix, then the trailing _pN key.
        val remainder = stat.stableId.removePrefix("${stat.subjectId}_")
        val topicId = remainder.substringBeforeLast("_", missingDelimiterValue = remainder)
        return stat.subjectId to topicId
    }
}