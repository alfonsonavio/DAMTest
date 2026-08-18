package com.navio.damtests.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.navio.damtests.data.local.entity.Question
import com.navio.damtests.data.local.entity.QuestionStats
import com.navio.damtests.data.local.entity.QuestionsDao
import com.navio.damtests.data.local.entity.TopicProgress

/**
 * Room database. Provided as a singleton by Hilt (see di/AppModule).
 *
 * Version 3: added QuestionStats (per-question stats for smart review).
 * Destructive migration is safe — questions rebuild from Firebase and progress
 * lives in Firestore. Stats reset on migration, which is acceptable since they
 * are a local convenience that repopulates as the user answers questions.
 */
@Database(
    entities = [Question::class, TopicProgress::class, QuestionStats::class],
    version = 3
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun questionsDao(): QuestionsDao
}