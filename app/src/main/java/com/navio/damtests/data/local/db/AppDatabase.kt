package com.navio.damtests.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.navio.damtests.data.local.entity.Question
import com.navio.damtests.data.local.entity.QuestionsDao
import com.navio.damtests.data.local.entity.TopicProgress

/**
 * Room database. The instance is provided as a singleton by Hilt
 * (see [com.navio.damtests.di.AppModule]), so no manual companion-object
 * singleton is needed here anymore.
 */
@Database(entities = [Question::class, TopicProgress::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun questionsDao(): QuestionsDao
}