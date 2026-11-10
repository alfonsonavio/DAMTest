package com.navio.damtests.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.navio.damtests.data.local.dao.FavoriteQuestionDao
import com.navio.damtests.data.local.dao.QuestionStatsDao
import com.navio.damtests.data.local.dao.QuestionsDao
import com.navio.damtests.data.local.dao.TestAttemptDao
import com.navio.damtests.data.local.dao.TopicProgressDao
import com.navio.damtests.data.local.dao.UserDataDao
import com.navio.damtests.data.local.entity.FavoriteQuestion
import com.navio.damtests.data.local.entity.Question
import com.navio.damtests.data.local.entity.QuestionStats
import com.navio.damtests.data.local.entity.TestAttempt
import com.navio.damtests.data.local.entity.TopicProgress

/**
 * Room database. Provided as a singleton by Hilt (see di/AppModule).
 *
 * Version 4: added TestAttempt (immutable per-test history for statistics).
 * Version 5: added FavoriteQuestion.
 *
 * Since 1.1.2 the statistics sync only downloads what changed since the last
 * sync, so a destructive migration would now lose local history for good:
 * every schema change from version 4 on needs a real [Migration].
 */
@Database(
    entities = [
        Question::class, TopicProgress::class, QuestionStats::class,
        TestAttempt::class, FavoriteQuestion::class
    ],
    version = 5
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun questionsDao(): QuestionsDao
    abstract fun topicProgressDao(): TopicProgressDao
    abstract fun questionStatsDao(): QuestionStatsDao
    abstract fun testAttemptDao(): TestAttemptDao
    abstract fun favoriteQuestionDao(): FavoriteQuestionDao
    abstract fun userDataDao(): UserDataDao

    companion object {
        /** 4 → 5: new favorite_questions table; existing data is kept. */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `favorite_questions` (" +
                        "`stableId` TEXT NOT NULL, " +
                        "`subjectId` TEXT NOT NULL, " +
                        "`isFavorite` INTEGER NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`stableId`))"
                )
            }
        }
    }
}
