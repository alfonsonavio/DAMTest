package com.navio.damtests

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Fetches the list of PDF assets from the GitHub Release and derives
 * which topicIds have a PDF available for a given subject.
 *
 * Uses the public GitHub API, which allows only 60 requests per hour per IP
 * without authentication: a classroom sharing one Wi-Fi would hit that limit
 * quickly. So the list is kept on disk and refreshed at most once a day, and
 * if a refresh fails (limit, no connection, timeout) the last known list is
 * used instead of hiding every PDF.
 */
object PdfReleaseRepository {

    private const val TAG = "PdfReleaseRepository"
    private const val API_URL =
        "https://api.github.com/repos/alfonsonavio/DAMTest/releases/tags/v1.0.0-resources"

    private const val PREFS_NAME = "pdf_release_cache"
    private const val KEY_NAMES = "asset_names"
    private const val KEY_FETCHED_AT = "fetched_at"
    private const val MAX_AGE_MS = 24 * 60 * 60 * 1000L
    private const val TIMEOUT_MS = 8_000

    // In-memory copy so the disk is read once per app session
    @Volatile private var cachedAssetNames: Set<String>? = null

    /**
     * Returns the set of topicIds that have a PDF for [subjectId].
     *
     * PDF naming convention: {subjectId}_{topicNumber}.pdf
     * Example: base_de_datos_3.pdf → topicId = "tema_3"
     */
    suspend fun getTopicIdsWithPdf(context: Context, subjectId: String): Set<String> =
        withContext(Dispatchers.IO) {
            val prefix = "${subjectId}_"
            assetNames(context.applicationContext)
                .filter { it.startsWith(prefix) && it.endsWith(".pdf") }
                .mapNotNull { fileName ->
                    // base_de_datos_3.pdf → remove prefix "base_de_datos_" → "3.pdf" → remove ".pdf" → "3"
                    val numberPart = fileName.removePrefix(prefix).removeSuffix(".pdf")
                    // Only accept pure numbers (tema_X) for now
                    if (numberPart.isNotEmpty() && numberPart.all { it.isDigit() }) "tema_$numberPart" else null
                }
                .toSet()
        }

    private fun assetNames(context: Context): Set<String> {
        cachedAssetNames?.let { return it }

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val stored = prefs.getStringSet(KEY_NAMES, null)?.toSet()
        val fetchedAt = prefs.getLong(KEY_FETCHED_AT, 0L)
        val isFresh = System.currentTimeMillis() - fetchedAt < MAX_AGE_MS

        if (stored != null && isFresh) {
            cachedAssetNames = stored
            return stored
        }

        return try {
            val names = fetchAssetNames()
            prefs.edit {
                putStringSet(KEY_NAMES, names)
                putLong(KEY_FETCHED_AT, System.currentTimeMillis())
            }
            cachedAssetNames = names
            names
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch PDF list, using the last known one: ${e.message}")
            stored ?: emptySet()
        }
    }

    private fun fetchAssetNames(): Set<String> {
        val connection = URL(API_URL).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("User-Agent", "DAMTest-Android")

            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) error("GitHub API answered $code")

            val json = connection.inputStream.bufferedReader().use { it.readText() }
            val assets = JSONObject(json).getJSONArray("assets")
            val names = mutableSetOf<String>()
            for (i in 0 until assets.length()) {
                names.add(assets.getJSONObject(i).getString("name"))
            }
            Log.d(TAG, "Fetched ${names.size} release assets")
            return names
        } finally {
            connection.disconnect()
        }
    }
}
