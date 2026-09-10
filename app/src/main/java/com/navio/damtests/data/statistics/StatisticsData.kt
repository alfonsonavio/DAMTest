package com.navio.damtests.data.statistics

/**
 * All the statistics shown on the Statistics screen, for one course.
 * Produced by [StatisticsCalculator] from raw attempts and question stats.
 */
data class StatisticsData(
    val globalAccuracy: Int,              // % correct across all answered questions
    val totalTests: Int,                  // number of completed tests
    val evolution: List<EvolutionPoint>,  // score % per test over time (chronological)
    val bySubject: List<SubjectAccuracy>, // accuracy per subject
    val weakestTopics: List<WeakTopic>    // topics with the highest failure rate
) {
    val isEmpty: Boolean get() = totalTests == 0 && bySubject.isEmpty()
}

/** A single point in the evolution chart: a test's score %, with its timestamp. */
data class EvolutionPoint(
    val timestamp: Long,
    val percentage: Int
)

/** Accuracy for one subject. */
data class SubjectAccuracy(
    val subjectId: String,
    val accuracy: Int   // 0..100
)

/** A weak topic to review. */
data class WeakTopic(
    val subjectId: String,
    val topicId: String,
    val failurePercent: Int  // 0..100
)