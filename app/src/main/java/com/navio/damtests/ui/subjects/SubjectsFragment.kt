package com.navio.damtests.ui.subjects

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButtonToggleGroup
import com.navio.damtests.FirebaseSyncManager
import com.navio.damtests.QuizRepository
import com.navio.damtests.PdfReleaseRepository
import com.navio.damtests.R
import com.navio.damtests.TopicSelectionActivity
import com.navio.damtests.auth.AuthUiHelper
import com.navio.damtests.data.SubjectCatalog
import com.navio.damtests.data.local.entity.Subject
import com.navio.damtests.data.local.entity.TopicProgress
import com.navio.damtests.ui.SubjectAdapter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/**
 * Subjects grid with a 1º/2º segmented control. Course is an in-app property of
 * each Subject (see Subject.course); switching segment filters the grid.
 *
 * Subjects with no downloaded content yet (e.g. second-year placeholders whose
 * questions aren't in Firebase) show a "coming soon" dialog instead of opening
 * an empty topic screen. Availability is detected automatically from the local
 * cache — there is no manual flag to maintain.
 */
@AndroidEntryPoint
class SubjectsFragment : Fragment(R.layout.fragment_subjects) {

    @Inject lateinit var repository: QuizRepository

    private lateinit var rv: RecyclerView
    private var selectedCourse = 1
    private var latestProgress: List<TopicProgress> = emptyList()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        rv = view.findViewById(R.id.rvSubjects)
        rv.layoutManager = GridLayoutManager(requireContext(), 2)

        val toggle = view.findViewById<MaterialButtonToggleGroup>(R.id.toggleCourse)
        toggle.check(R.id.btnCourse1)
        toggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                selectedCourse = if (checkedId == R.id.btnCourse2) 2 else 1
                renderSubjects()
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                repository.getAllProgress().collect { progressList ->
                    latestProgress = progressList
                    renderSubjects()
                }
            }
        }
    }

    private fun renderSubjects() {
        val subjects = SubjectCatalog.all.filter { it.course == selectedCourse }
        rv.adapter = SubjectAdapter(subjects, latestProgress) { subject ->
            openSubjectIfAvailable(subject)
        }
    }

    /**
     * Opens the subject's topics if it has questions or at least one PDF;
     * otherwise shows a "coming soon" dialog. Questions are checked against the
     * local cache and PDFs against the resources release.
     */
    private fun openSubjectIfAvailable(subject: Subject) {
        viewLifecycleOwner.lifecycleScope.launch {
            var hasContent = subjectHasContent(subject)

            // First launch: the questions may still be downloading. Wait for
            // the download instead of wrongly saying the subject is coming soon.
            if (!hasContent && FirebaseSyncManager.syncing.value) {
                val loading = AuthUiHelper.showLoading(
                    requireContext(),
                    "Descargando preguntas",
                    "Es la primera vez que abres la app: estamos descargando las preguntas. Solo tarda unos segundos."
                )
                withTimeoutOrNull(SYNC_WAIT_MS) { FirebaseSyncManager.syncing.first { !it } }
                loading.dismiss()
                hasContent = subjectHasContent(subject)
            }

            if (hasContent) {
                startActivity(
                    Intent(requireContext(), TopicSelectionActivity::class.java)
                        .putExtra("SUBJECT_ID", subject.id)
                )
            } else {
                AuthUiHelper.showInfo(
                    requireContext(),
                    "Próximamente",
                    "El contenido de ${subject.name} estará disponible muy pronto."
                )
            }
        }
    }

    private suspend fun subjectHasContent(subject: Subject): Boolean =
        repository.subjectHasContent(subject.id) ||
            PdfReleaseRepository.getTopicIdsWithPdf(requireContext(), subject.id).isNotEmpty()

    private companion object {
        const val SYNC_WAIT_MS = 60_000L
    }
}
