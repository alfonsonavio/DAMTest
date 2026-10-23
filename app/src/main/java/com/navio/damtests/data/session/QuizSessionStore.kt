package com.navio.damtests.data.session

import android.content.Context
import androidx.core.content.edit
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists the single test in progress (only one is kept: starting to answer a
 * different test replaces it). Stored as JSON in SharedPreferences, so it
 * survives the app being closed or killed by the system.
 */
@Singleton
class QuizSessionStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    fun load(): QuizSession? {
        val json = prefs.getString(KEY_SESSION, null) ?: return null
        return try {
            gson.fromJson(json, QuizSession::class.java)
        } catch (e: Exception) {
            clear() // corrupt or from an incompatible version: drop it
            null
        }
    }

    fun save(session: QuizSession) = prefs.edit { putString(KEY_SESSION, gson.toJson(session)) }

    fun clear() = prefs.edit { remove(KEY_SESSION) }

    /** Raw saved test, for the cloud backup made on logout. */
    fun exportJson(): String? = prefs.getString(KEY_SESSION, null)

    /** Restores a saved test from the cloud backup made on logout. */
    fun importJson(json: String) = prefs.edit { putString(KEY_SESSION, json) }

    private companion object {
        const val PREFS_NAME = "quiz_session"
        const val KEY_SESSION = "current"
    }
}