package com.navio.damtests

import android.util.Log
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import kotlinx.coroutines.tasks.await

object RemoteConfigManager {

    private const val TAG = "RemoteConfigManager"

    const val KEY_GEMINI = "gemini_api_key"
    const val KEY_GROQ   = "groq_api_key"

    suspend fun fetchAndActivate() {
        try {
            val config = FirebaseRemoteConfig.getInstance()

            // Await the settings — this was the bug: setConfigSettingsAsync
            // was not awaited, so the interval change wasn't applied before fetch.
            config.setConfigSettingsAsync(
                FirebaseRemoteConfigSettings.Builder()
                    .setMinimumFetchIntervalInSeconds(3600)
                    .build()
            ).await()

            // Fetch and activate separately for maximum reliability
            config.fetch(3600).await()         // force fresh fetch, ignore cache
            val activated = config.activate().await()

            Log.d(TAG, "fetch+activate done. activated=$activated")
            Log.d(TAG, "groq_api_key present: ${config.getString(KEY_GROQ).isNotEmpty()}")
            Log.d(TAG, "gemini_api_key present: ${config.getString(KEY_GEMINI).isNotEmpty()}")
        } catch (e: Exception) {
            Log.e(TAG, "Remote Config fetch failed: ${e.message}")
        }
    }

    fun getString(key: String): String =
        FirebaseRemoteConfig.getInstance().getString(key)
}