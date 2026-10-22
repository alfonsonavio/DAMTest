package com.navio.damtests.data

import com.navio.damtests.R
import com.navio.damtests.data.local.entity.Subject

/**
 * Single source of truth for the DAM subjects shown in the app.
 *
 * The course (1º/2º) is an in-app property of each Subject; Firebase only knows
 * subject ids. The id is also the prefix of the subject's PDFs in the resources
 * release ({id}_{topic}.pdf) and its node under "preguntas" in Firebase.
 * Content arrives over time, so screens check at runtime whether a subject has
 * questions or PDFs yet.
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

        // --- 2º ---
        Subject("acceso_datos",   "Acceso a Datos",       R.drawable.ic_import_export, R.color.subject_db_bg,             course = 2),
        Subject("ciberseguridad", "Ciberseguridad",       R.drawable.ic_security,      R.color.subject_sistemas_bg,       course = 2),
        Subject("interfaces",     "Interfaces",           R.drawable.ic_dashboard,     R.color.subject_marcas_bg,         course = 2),
        Subject("ingles",         "Inglés",               R.drawable.ic_translate,     R.color.subject_digital_bg,        course = 2),
        Subject("ipe2",           "IPE II",               R.drawable.ic_work,          R.color.subject_ipe_bg,            course = 2),
        Subject("servicios",      "Servicios",            R.drawable.ic_dns,           R.color.subject_prog_bg,           course = 2),
        Subject("multimedia",     "Multimedia",           R.drawable.ic_smartphone,    R.color.subject_entornos_bg,       course = 2),
        Subject("gestion",        "Gestión",              R.drawable.ic_business,      R.color.subject_sostenibilidad_bg, course = 2)
    )

    /** Display name for a subject id, falling back to the id itself. */
    fun nameFor(subjectId: String): String =
        all.firstOrNull { it.id == subjectId }?.name ?: subjectId
}
