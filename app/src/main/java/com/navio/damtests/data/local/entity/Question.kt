package com.navio.damtests.data.local.entity

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize

/**
 * Room entity representing a single quiz question.
 * Implements [Parcelable] so it can be passed between Activities via Intent.
 *
 * [stableId] is a Firebase-derived identifier ("{subjectId}_{topicId}_{firebaseKey}",
 * e.g. "digitalizacion_tema_1_p1"). Unlike the auto-generated Room [id] — which
 * changes every time a topic is re-synced — [stableId] stays constant across
 * syncs, so it can be used to track per-question statistics reliably.
 */
@Parcelize
@Entity(tableName = "questions")
data class Question(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val stableId: String = "",
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