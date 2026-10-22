package com.navio.damtests.data

import com.navio.damtests.R
import com.navio.damtests.data.local.entity.Subject

/**
 * Single source of truth for the DAM subjects shown in the app.
 *
 * The course (1º/2º) is an in-app property of each Subject; Firebase only knows
 * subject ids. Second-year subjects are placeholders until their questions are
 * uploaded — screens detect that at runtime via QuizRepository.subjectHasContent.
 */
object SubjectCatalog {

    val all: List<Subject> = listOf(
        // --- 1º ---
        Subject("programacion",   "Programación",   R.drawable.ic_terminal,    R.color.subject_prog_bg,           course = 1),
        Subject("base_de_datos",  "Base de Datos",  R.drawable.ic_storage,     R.color.subject_db_bg,             course = 1),
        Subject("sistemas",       "Sistemas",        R.drawable.ic_memory,      R.color.subject_sistemas_bg,       course = 1),
        Subject("marcas",         "Leng. Marcas",    R.drawable.ic_description, R.color.subject_marcas_bg,         course = 1),
        Subject("entornos",       "Entornos",        R.drawable.ic_code,        R.color.subject_entornos_bg,       course = 1),
        Subject("digitalizacion", "Digitalización",  R.drawable.ic_computer,    R.color.subject_digital_bg,        course = 1),
        Subject("ipe",            "IPE",             R.drawable.ic_assessment,  R.color.subject_ipe_bg,            course = 1),
        Subject("sostenibilidad", "Sostenibilidad",  R.drawable.ic_eco,         R.color.subject_sostenibilidad_bg, course = 1),

        // --- 2º (placeholder: questions not yet in Firebase) ---
        Subject("acceso_datos",   "Acceso a Datos",  R.drawable.ic_storage,     R.color.subject_db_bg,             course = 2),
        Subject("interfaces",     "Desarrollo de Interfaces", R.drawable.ic_computer, R.color.subject_digital_bg,  course = 2),
        Subject("multimedia",     "Prog. Multimedia y Móviles", R.drawable.ic_code, R.color.subject_entornos_bg,   course = 2),
        Subject("servicios",      "Prog. Servicios y Procesos", R.drawable.ic_memory, R.color.subject_sistemas_bg, course = 2),
        Subject("gestion",        "Sist. Gestión Empresarial", R.drawable.ic_terminal, R.color.subject_prog_bg,    course = 2),
        Subject("eie",            "EIE",             R.drawable.ic_assessment,  R.color.subject_ipe_bg,            course = 2)
    )

    /** Display name for a subject id, falling back to the id itself. */
    fun nameFor(subjectId: String): String =
        all.firstOrNull { it.id == subjectId }?.name ?: subjectId
}