package com.navio.damtests

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL

/**
 * Fetches the list of PDF assets from the GitHub Release and derives
 * which topicIds have a PDF available for a given subject.
 *
 * Uses the public GitHub API — no authentication needed.
 */
object PdfReleaseRepository {

    private const val TAG = "PdfReleaseRepository"
    private const val API_URL =
        "https://api.github.com/repos/alfonsonavio/DAMTest/releases/tags/v1.0.0-resources"

    // In-memory cache so we only fetch once per app session
    private var cachedAssetNames: Set<String>? = null

    /**
     * Returns the set of topicIds that have a PDF for [subjectId].
     *
     * PDF naming convention: {subjectId}_{topicNumber}.pdf
     * Example: base_de_datos_3.pdf → topicId = "tema_3"
     */
    suspend fun getTopicIdsWithPdf(subjectId: String): Set<String> =
        withContext(Dispatchers.IO) {
            try {
                val assetNames = cachedAssetNames ?: fetchAssetNames().also { cachedAssetNames = it }
                val prefix = "${subjectId}_"

                assetNames
                    .filter { it.startsWith(prefix) && it.endsWith(".pdf") }
                    .mapNotNull { fileName ->
                        // base_de_datos_3.pdf → remove prefix "base_de_datos_" → "3.pdf" → remove ".pdf" → "3"
                        val numberPart = fileName.removePrefix(prefix).removeSuffix(".pdf")
                        // Only accept pure numbers (tema_X) for now
                        if (numberPart.all { it.isDigit() }) "tema_$numberPart" else null
                    }
                    .toSet()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch PDF list: ${e.message}")
                emptySet()
            }
        }

    private fun fetchAssetNames(): Set<String> {
        val connection = URL(API_URL).openConnection()
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        connection.setRequestProperty("User-Agent", "DAMTest-Android")
        val json = connection.getInputStream().bufferedReader().readText()

        val root   = JSONObject(json)
        val assets = root.getJSONArray("assets")
        val names  = mutableSetOf<String>()
        for (i in 0 until assets.length()) {
            names.add(assets.getJSONObject(i).getString("name"))
        }
        Log.d(TAG, "Fetched ${names.size} release assets")
        return names
    }
}
