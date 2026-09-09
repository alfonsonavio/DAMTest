package com.navio.damtests.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A single completed test, stored as an immutable history record (unlike
 * [TopicProgress], which overwrites the last score per topic). This history
 * powers the statistics screen's evolution chart and time-based metrics.
 *
 * Smart-review tests (topicId "-4") are practice-only and are NOT recorded here,
 * so they don't distort the score evolution.
 */
@Entity(tableName = "test_attempts")
data class TestAttempt(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val subjectId: String,
    val topicId: String,
    val score: Int,
    val totalQuestions: Int,
    val timestamp: Long = System.currentTimeMillis()
) {
    /** Score as a percentage in [0, 100]. */
    val percentage: Int
        get() = if (totalQuestions > 0) (score * 100) / totalQuestions else 0
}