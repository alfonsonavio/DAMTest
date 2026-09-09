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
import com.google.android.material.tabs.TabLayout
import com.navio.damtests.QuizRepository
import com.navio.damtests.R
import com.navio.damtests.TopicSelectionActivity
import com.navio.damtests.data.local.entity.Subject
import com.navio.damtests.data.local.entity.TopicProgress
import com.navio.damtests.ui.SubjectAdapter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Subjects grid with 1º/2º course tabs. The course is an in-app property of each
 * Subject (see Subject.course); switching tabs just filters the grid. Second-year
 * subjects are placeholders until their questions are uploaded to Firebase.
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

        val tabs = view.findViewById<TabLayout>(R.id.tabsCourse)
        tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                selectedCourse = tab.position + 1  // tab 0 → 1º, tab 1 → 2º
                renderSubjects()
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                repository.getAllProgress().collect { progressList ->
                    latestProgress = progressList
                    renderSubjects()
                }
            }
        }
    }

    /** Renders the subjects of the currently selected course. */
    private fun renderSubjects() {
        val subjects = allSubjects().filter { it.course == selectedCourse }
        rv.adapter = SubjectAdapter(subjects, latestProgress) { subject ->
            startActivity(
                Intent(requireContext(), TopicSelectionActivity::class.java)
                    .putExtra("SUBJECT_ID", subject.id)
            )
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