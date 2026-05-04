package com.navio.damtests.data.local.entity

import androidx.room.Entity

@Entity(
    tableName = "topic_progress",
    primaryKeys = ["subjectId", "topicId"]
)
data class TopicProgress(
    val subjectId: String,
    val topicId: String,
    val lastScore: Int,
    val totalQuestions: Int = 20,
    val attemptsCount: Int = 0,
    val lastAttemptTimestamp: Long = System.currentTimeMillis()
)