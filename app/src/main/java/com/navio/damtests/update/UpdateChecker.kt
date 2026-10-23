package com.navio.damtests.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.edit
import com.navio.damtests.BuildConfig
import com.navio.damtests.RemoteConfigManager
import com.navio.damtests.auth.AuthUiHelper
import java.util.concurrent.TimeUnit

/**
 * Tells the user when a newer version of the app has been published.
 *
 * The app is distributed as an APK from GitHub Releases, not from Google Play,
 * so nothing else would ever let users know an update exists. On each
 * release, set `latest_version_code` (and `latest_version_name`) in Remote
 * Config; copies older than that show a dialog, at most once a day.
 */
object UpdateChecker {

    const val DOWNLOAD_URL = "https://github.com/alfonsonavio/DAMTest/releases/latest"

    private const val PREFS_NAME = "update_checker"
    private const val KEY_LAST_PROMPT = "last_prompt_at"
    private val PROMPT_INTERVAL_MS = TimeUnit.HOURS.toMillis(24)

    /** Pure decision, unit-tested: is [latestCode] newer than [currentCode]? */
    fun isUpdateAvailable(latestCode: Long, currentCode: Int): Boolean =
        latestCode > currentCode

    /** Shows the update dialog if an update exists and it wasn't shown in the last day. */
    fun maybePrompt(context: Context) {
        val latestCode = RemoteConfigManager.getLong(RemoteConfigManager.KEY_LATEST_VERSION_CODE)
        if (!isUpdateAvailable(latestCode, BuildConfig.VERSION_CODE)) return

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (now - prefs.getLong(KEY_LAST_PROMPT, 0L) < PROMPT_INTERVAL_MS) return
        prefs.edit { putLong(KEY_LAST_PROMPT, now) }

        val versionName = RemoteConfigManager.getString(RemoteConfigManager.KEY_LATEST_VERSION_NAME)
        val versionText = if (versionName.isNotBlank()) "DAMTest $versionName" else "una nueva versión de DAMTest"

        AuthUiHelper.showConfirm(
            context = context,
            title = "Nueva versión disponible",
            message = "Ya puedes descargar $versionText, con mejoras y correcciones. " +
                "Instálala encima de la actual: no perderás tu progreso.",
            confirmText = "Descargar"
        ) {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(DOWNLOAD_URL)))
        }
    }
}
