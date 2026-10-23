package com.navio.damtests

import android.util.Log
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import kotlinx.coroutines.tasks.await

object RemoteConfigManager {

    private const val TAG = "RemoteConfigManager"

    const val KEY_GEMINI = "gemini_api_key"
    const val KEY_GROQ   = "groq_api_key"

    // AI model names — editable from the Firebase console, no app update needed.
    // Defaults are used on first launch or if the fetch fails.
    const val KEY_GROQ_MODEL   = "groq_model"
    const val KEY_GEMINI_MODEL = "gemini_model"

    // Latest published version, set by hand in the Firebase console on each
    // release, so installed copies can tell the user an update exists.
    const val KEY_LATEST_VERSION_CODE = "latest_version_code"
    const val KEY_LATEST_VERSION_NAME = "latest_version_name"

    const val DEFAULT_GROQ_MODEL   = "openai/gpt-oss-120b"
    const val DEFAULT_GEMINI_MODEL = "gemini-3.5-flash"

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
            Log.d(TAG, "groq_model: ${getStringOr(KEY_GROQ_MODEL, DEFAULT_GROQ_MODEL)}")
            Log.d(TAG, "gemini_model: ${getStringOr(KEY_GEMINI_MODEL, DEFAULT_GEMINI_MODEL)}")
        } catch (e: Exception) {
            Log.e(TAG, "Remote Config fetch failed: ${e.message}")
        }
    }

    fun getString(key: String): String =
        FirebaseRemoteConfig.getInstance().getString(key)

    /** Numeric Remote Config value; 0 when missing or not a number. */
    fun getLong(key: String): Long =
        FirebaseRemoteConfig.getInstance().getLong(key)

    /** Returns the Remote Config value for [key], or [default] if it is missing or blank. */
    fun getStringOr(key: String, default: String): String =
        FirebaseRemoteConfig.getInstance().getString(key).ifBlank { default }
}