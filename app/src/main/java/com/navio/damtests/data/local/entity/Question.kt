package com.navio.damtests.data.local.entity

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize

/**
 * Room entity representing a single quiz question.
 * Implements [Parcelable] so it can be passed between Activities via Intent.
 */
@Parcelize
@Entity(tableName = "questions")
data class Question(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val subjectId: String = "",
    val topicId: String = "",
    val text: String = "",
    val contextText: String? = null,
    val optionA: String = "",
    val optionB: String = "",
    val optionC: String = "",
    val optionD: String = "",
    val correctOptionIndex: Int = 0
) : Parcelable
