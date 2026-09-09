package com.navio.damtests.ui.subjects

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.navio.damtests.QuizRepository
import com.navio.damtests.R
import com.navio.damtests.TopicSelectionActivity
import com.navio.damtests.data.local.entity.Subject
import com.navio.damtests.ui.SubjectAdapter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Subjects grid. Migrated from the old MainActivity. Later (second-year branch)
 * this gains 1º/2º tabs; for now it shows the first-year subjects.
 */
@AndroidEntryPoint
class SubjectsFragment : Fragment(R.layout.fragment_subjects) {

    @Inject lateinit var repository: QuizRepository

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val rv = view.findViewById<RecyclerView>(R.id.rvSubjects)
        rv.layoutManager = GridLayoutManager(requireContext(), 2)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                repository.getAllProgress().collect { progressList ->
                    rv.adapter = SubjectAdapter(getSubjectsList(), progressList) { subject ->
                        startActivity(
                            Intent(requireContext(), TopicSelectionActivity::class.java)
                                .putExtra("SUBJECT_ID", subject.id)
                        )
                    }
                }
            }
        }
    }

    private fun getSubjectsList(): List<Subject> = listOf(
        Subject("programacion",   "Programación",   R.drawable.ic_terminal,    R.color.bg_prog),
        Subject("base_de_datos",  "Base de Datos",  R.drawable.ic_storage,     R.color.bg_db),
        Subject("sistemas",       "Sistemas",        R.drawable.ic_memory,      R.color.bg_sistemas),
        Subject("marcas",         "Leng. Marcas",    R.drawable.ic_description, R.color.bg_marcas),
        Subject("entornos",       "Entornos",        R.drawable.ic_code,        R.color.bg_entornos),
        Subject("digitalizacion", "Digitalización",  R.drawable.ic_computer,    R.color.bg_digital),
        Subject("ipe",            "IPE",             R.drawable.ic_assessment,  R.color.bg_ipe),
        Subject("sostenibilidad", "Sostenibilidad",  R.drawable.ic_eco,         R.color.bg_sostenibilidad)
    )
}