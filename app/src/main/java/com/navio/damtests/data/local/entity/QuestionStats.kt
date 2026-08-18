package com.navio.damtests.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Per-question answer statistics, used by the smart-review mode to prioritise
 * the questions a user struggles with most.
 *
 * Keyed by [stableId] (the Firebase-derived id that survives re-syncs), NOT by
 * the auto-generated Room question id, so stats stay attached to the right
 * question across syncs.
 *
 * These stats also feed future per-subject statistics screens.
 */
@Entity(tableName = "question_stats")
data class QuestionStats(
    @PrimaryKey val stableId: String,
    val subjectId: String,
    val timesSeen: Int = 0,
    val timesCorrect: Int = 0,
    val timesWrong: Int = 0,
    val lastSeenTimestamp: Long = 0L
) {
    /** Failure ratio in [0.0, 1.0]. Returns 0 for never-seen questions. */
    val failureRate: Double
        get() = if (timesSeen == 0) 0.0 else timesWrong.toDouble() / timesSeen
}