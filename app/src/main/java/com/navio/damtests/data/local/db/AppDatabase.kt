package com.navio.damtests.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.navio.damtests.data.local.entity.Question
import com.navio.damtests.data.local.entity.QuestionsDao
import com.navio.damtests.data.local.entity.TopicProgress

/**
 * Room database. Provided as a singleton by Hilt (see di/AppModule).
 *
 * Version 2: added Question.stableId. Uses destructive migration — the local
 * question cache is safely rebuilt from Firebase on the next sync, and user
 * progress lives in Firestore, so nothing irreplaceable is lost.
 */
@Database(entities = [Question::class, TopicProgress::class], version = 2)
abstract class AppDatabase : RoomDatabase() {
    abstract fun questionsDao(): QuestionsDao
}