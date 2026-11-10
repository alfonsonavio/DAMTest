package com.navio.damtests.di

import android.content.Context
import androidx.room.Room
import com.navio.damtests.data.local.dao.FavoriteQuestionDao
import com.navio.damtests.data.local.dao.QuestionStatsDao
import com.navio.damtests.data.local.dao.QuestionsDao
import com.navio.damtests.data.local.dao.TestAttemptDao
import com.navio.damtests.data.local.dao.TopicProgressDao
import com.navio.damtests.data.local.dao.UserDataDao
import com.navio.damtests.data.local.db.AppDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module that provides application-wide singletons:
 * the Room database and its DAOs (one per table, plus the logout cleanup).
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "quiz_database"
        )
            .addMigrations(AppDatabase.MIGRATION_4_5)
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    @Singleton
    fun provideQuestionsDao(database: AppDatabase): QuestionsDao = database.questionsDao()

    @Provides
    @Singleton
    fun provideTopicProgressDao(database: AppDatabase): TopicProgressDao = database.topicProgressDao()

    @Provides
    @Singleton
    fun provideQuestionStatsDao(database: AppDatabase): QuestionStatsDao = database.questionStatsDao()

    @Provides
    @Singleton
    fun provideTestAttemptDao(database: AppDatabase): TestAttemptDao = database.testAttemptDao()

    @Provides
    @Singleton
    fun provideFavoriteQuestionDao(database: AppDatabase): FavoriteQuestionDao = database.favoriteQuestionDao()

    @Provides
    @Singleton
    fun provideUserDataDao(database: AppDatabase): UserDataDao = database.userDataDao()
}
