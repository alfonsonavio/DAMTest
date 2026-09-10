package com.navio.damtests.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.navio.damtests.data.local.entity.Question
import com.navio.damtests.data.local.entity.QuestionStats
import com.navio.damtests.data.local.entity.QuestionsDao
import com.navio.damtests.data.local.entity.TestAttempt
import com.navio.damtests.data.local.entity.TopicProgress

/**
 * Room database. Provided as a singleton by Hilt (see di/AppModule).
 *
 * Version 4: added TestAttempt (immutable per-test history for statistics).
 * Destructive migration is safe — questions rebuild from Firebase and progress
 * lives in Firestore. The attempt history resets on migration, which is
 * acceptable; it rebuilds as the user takes tests.
 */
@Database(
    entities = [Question::class, TopicProgress::class, QuestionStats::class, TestAttempt::class],
    version = 4
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun questionsDao(): QuestionsDao
}