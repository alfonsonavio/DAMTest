package com.navio.damtests.di

import android.content.Context
import androidx.room.Room
import com.navio.damtests.QuizRepository
import com.navio.damtests.data.local.db.AppDatabase
import com.navio.damtests.data.local.entity.QuestionsDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module that provides application-wide singletons:
 * the Room database, its DAO, and the QuizRepository.
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
        ).build()

    @Provides
    @Singleton
    fun provideQuestionsDao(database: AppDatabase): QuestionsDao =
        database.questionsDao()
}