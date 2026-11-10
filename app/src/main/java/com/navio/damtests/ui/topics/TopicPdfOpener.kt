package com.navio.damtests.ui.topics

import android.content.ActivityNotFoundException
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.navio.damtests.R
import com.navio.damtests.core.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Opens a topic's PDF notes: from the cache when already downloaded, or
 * downloaded from the GitHub Release first.
 */
class TopicPdfOpener(private val activity: AppCompatActivity) {

    private val httpClient = OkHttpClient()

    fun open(subjectId: String, topicId: String) {
        val fileName = "${subjectId}_${topicId.removePrefix(TEMA_PREFIX)}.pdf"
        val localFile = File(activity.cacheDir, fileName)

        if (localFile.exists()) show(localFile) else downloadAndShow(fileName, localFile)
    }

    private fun downloadAndShow(fileName: String, destination: File) {
        toast(R.string.downloading_pdf)

        activity.lifecycleScope.launch(Dispatchers.IO) {
            // Download to a temporary file and rename it only when complete, so
            // an interrupted download is never kept (and reopened) as the PDF.
            val partial = File(destination.parentFile, "${destination.name}$PARTIAL_SUFFIX")
            try {
                val request = Request.Builder().url("${Constants.PDF_BASE_URL}$fileName").build()
                httpClient.newCall(request).execute().use { response ->
                    val body = response.body
                    if (response.isSuccessful && body != null) {
                        body.byteStream().use { input ->
                            FileOutputStream(partial).use { output -> input.copyTo(output) }
                        }
                        if (!partial.renameTo(destination)) throw IOException("Could not save $fileName")
                        withContext(Dispatchers.Main) { show(destination) }
                    } else {
                        withContext(Dispatchers.Main) { toast(R.string.pdf_not_found) }
                    }
                }
            } catch (e: IOException) {
                Log.w(TAG, "Could not download $fileName: ${e.message}")
                partial.delete()
                withContext(Dispatchers.Main) { toast(R.string.network_error) }
            }
        }
    }

    private fun show(file: File) {
        try {
            val uri = FileProvider.getUriForFile(activity, "${activity.packageName}$PROVIDER_SUFFIX", file)
            activity.startActivity(
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, PDF_MIME_TYPE)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            )
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "No app installed to open PDFs: ${e.message}")
            toast(R.string.pdf_open_error)
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "The PDF is outside the shared folders: ${e.message}")
            toast(R.string.pdf_open_error)
        }
    }

    private fun toast(message: Int) = Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()

    private companion object {
        const val TAG = "TopicPdfOpener"
        const val TEMA_PREFIX = "tema_"
        const val PARTIAL_SUFFIX = ".part"
        const val PROVIDER_SUFFIX = ".provider"
        const val PDF_MIME_TYPE = "application/pdf"
    }
}
