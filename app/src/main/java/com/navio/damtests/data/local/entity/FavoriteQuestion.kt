package com.navio.damtests.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A question the user marked (or unmarked) as a favourite.
 *
 * Unmarking keeps the row with [isFavorite] = false instead of deleting it, so
 * the change syncs to other devices: when merging, the copy with the most
 * recent [updatedAt] wins, and a deletion can't be undone by an older copy.
 */
@Entity(tableName = "favorite_questions")
data class FavoriteQuestion(
    @PrimaryKey val stableId: String,
    val subjectId: String,
    val isFavorite: Boolean,
    val updatedAt: Long = System.currentTimeMillis()
)