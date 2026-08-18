package com.navio.damtests

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.navio.damtests.ui.TopicAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

@AndroidEntryPoint
class TopicSelectionActivity : AppCompatActivity() {

    @Inject lateinit var repository: QuizRepository
    private lateinit var adapter: TopicAdapter
    private val httpClient = OkHttpClient()

    // Topics with PDF available, populated once from GitHub Release API
    private var pdfTopicIds: Set<String> = emptySet()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_topic_selection)

        val subjectId = intent.getStringExtra("SUBJECT_ID") ?: "programacion"


        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar_topics)
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title     = subjectId.replace("_", " ").uppercase()
            elevation = 0f
        }

        window.statusBarColor = getColor(R.color.colorPrimary)

        val rvTopics = findViewById<RecyclerView>(R.id.rvTopics)
        rvTopics.layoutManager = LinearLayoutManager(this)

        lifecycleScope.launch {
            // Fetch PDF list and DB topics in parallel via both sources
            pdfTopicIds = PdfReleaseRepository.getTopicIdsWithPdf(subjectId)

            val sortedTopics = repository
                .getUniqueTopicsForSubject(subjectId, pdfTopicIds)
                .sortedWith(compareBy(
                    { topic ->
                        when {
                            topic.id.startsWith("tema_")         -> 0
                            topic.id.startsWith("caso_")         -> 1
                            topic.id.startsWith("repaso_")       -> 2
                            topic.id in listOf("-2", "-3", "-1") -> 3
                            else                                 -> 4
                        }
                    },
                    { topic ->
                        if (topic.id.startsWith("-")) {
                            when (topic.id) { "-2" -> 1; "-3" -> 2; else -> 3 }
                        } else {
                            topic.id.filter { it.isDigit() }.toIntOrNull() ?: Int.MAX_VALUE
                        }
                    }
                ))

            repeatOnLifecycle(Lifecycle.State.STARTED) {
                repository.getProgressFlow(subjectId).collect { progressList ->
                    adapter = TopicAdapter(
                        topics       = sortedTopics,
                        progressList = progressList,
                        pdfTopicIds  = pdfTopicIds,
                        onTopicClick = { topic -> onTopicSelected(subjectId, topic.id) },
                        onPdfClick   = { topic -> openPdf(subjectId, topic.id) }
                    )
                    rvTopics.adapter = adapter
                }
            }
        }
    }

    // --- Topic selection ---

    private fun onTopicSelected(subjectId: String, topicId: String) {
        lifecycleScope.launch {
            val isGeneralTest = topicId.startsWith("-")
            val hasQuestions  = isGeneralTest || repository.hasQuestions(subjectId, topicId)

            if (hasQuestions) {
                startActivity(
                    Intent(this@TopicSelectionActivity, QuizActivity::class.java)
                        .putExtra("SUBJECT_ID", subjectId)
                        .putExtra("TOPIC_ID", topicId)
                )
            } else {
                AlertDialog.Builder(this@TopicSelectionActivity)
                    .setTitle(R.string.no_questions_title)
                    .setMessage(R.string.no_questions_yet)
                    .setPositiveButton(R.string.close, null)
                    .show()
            }
        }
    }

    // --- PDF handling ---

    private fun openPdf(subjectId: String, topicId: String) {
        if (topicId.startsWith("-")) {
            Toast.makeText(this, R.string.no_pdf_general_test, Toast.LENGTH_SHORT).show()
            return
        }

        val cleanId   = topicId.removePrefix("tema_")
        val fileName  = "${subjectId}_${cleanId}.pdf"
        val localFile = File(cacheDir, fileName)

        if (localFile.exists()) showPdf(localFile) else downloadAndOpenPdf(fileName, localFile)
    }

    private fun downloadAndOpenPdf(fileName: String, destination: File) {
        Toast.makeText(this, R.string.downloading_pdf, Toast.LENGTH_SHORT).show()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val url      = "${Constants.PDF_BASE_URL}$fileName"
                val response = httpClient.newCall(Request.Builder().url(url).build()).execute()
                if (response.isSuccessful) {
                    response.body?.bytes()?.let { bytes ->
                        FileOutputStream(destination).use { it.write(bytes) }
                        withContext(Dispatchers.Main) { showPdf(destination) }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@TopicSelectionActivity,
                            R.string.pdf_not_found, Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@TopicSelectionActivity,
                        R.string.network_error, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showPdf(file: File) {
        try {
            val uri = FileProvider.getUriForFile(this, "${packageName}.provider", file)
            startActivity(
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/pdf")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            )
        } catch (e: Exception) {
            Toast.makeText(this, R.string.pdf_open_error, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}