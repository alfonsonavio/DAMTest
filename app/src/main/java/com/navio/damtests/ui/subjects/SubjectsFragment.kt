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
import com.navio.damtests.QuizRepository
import com.navio.damtests.R
import com.navio.damtests.TopicSelectionActivity
import com.navio.damtests.auth.AuthUiHelper
import com.navio.damtests.data.local.entity.Subject
import com.navio.damtests.data.local.entity.TopicProgress
import com.navio.damtests.ui.SubjectAdapter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
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
        val subjects = allSubjects().filter { it.course == selectedCourse }
        rv.adapter = SubjectAdapter(subjects, latestProgress) { subject ->
            openSubjectIfAvailable(subject)
        }
    }

    /**
     * Opens the subject's topics if it has content; otherwise shows a
     * "coming soon" dialog. Content is checked live against the local cache.
     */
    private fun openSubjectIfAvailable(subject: Subject) {
        viewLifecycleOwner.lifecycleScope.launch {
            val hasContent = repository.subjectHasContent(subject.id)
            if (hasContent) {
                startActivity(
                    Intent(requireContext(), TopicSelectionActivity::class.java)
                        .putExtra("SUBJECT_ID", subject.id)
                )
            } else {
                AuthUiHelper.showInfo(
                    requireContext(),
                    "Próximamente",
                    "Las preguntas de ${subject.name} estarán disponibles muy pronto."
                )
            }
        }
    }

    private fun allSubjects(): List<Subject> = listOf(
        // --- 1º ---
        Subject("programacion",   "Programación",   R.drawable.ic_terminal,    R.color.bg_prog,           course = 1),
        Subject("base_de_datos",  "Base de Datos",  R.drawable.ic_storage,     R.color.bg_db,             course = 1),
        Subject("sistemas",       "Sistemas",        R.drawable.ic_memory,      R.color.bg_sistemas,       course = 1),
        Subject("marcas",         "Leng. Marcas",    R.drawable.ic_description, R.color.bg_marcas,         course = 1),
        Subject("entornos",       "Entornos",        R.drawable.ic_code,        R.color.bg_entornos,       course = 1),
        Subject("digitalizacion", "Digitalización",  R.drawable.ic_computer,    R.color.bg_digital,        course = 1),
        Subject("ipe",            "IPE",             R.drawable.ic_assessment,  R.color.bg_ipe,            course = 1),
        Subject("sostenibilidad", "Sostenibilidad",  R.drawable.ic_eco,         R.color.bg_sostenibilidad, course = 1),

        // --- 2º (placeholder: questions not yet in Firebase) ---
        Subject("acceso_datos",   "Acceso a Datos",  R.drawable.ic_storage,     R.color.bg_db,             course = 2),
        Subject("interfaces",     "Desarrollo de Interfaces", R.drawable.ic_computer, R.color.bg_digital,  course = 2),
        Subject("multimedia",     "Prog. Multimedia y Móviles", R.drawable.ic_code, R.color.bg_entornos,   course = 2),
        Subject("servicios",      "Prog. Servicios y Procesos", R.drawable.ic_memory, R.color.bg_sistemas, course = 2),
        Subject("gestion",        "Sist. Gestión Empresarial", R.drawable.ic_terminal, R.color.bg_prog,    course = 2),
        Subject("eie",            "EIE",             R.drawable.ic_assessment,  R.color.bg_ipe,            course = 2)
    )
}