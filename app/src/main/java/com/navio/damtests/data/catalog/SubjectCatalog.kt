package com.navio.damtests.data.catalog

import com.navio.damtests.R
import com.navio.damtests.core.UiText
import com.navio.damtests.data.local.entity.Subject
import com.navio.damtests.data.favorites.Favorites

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
        firstYear("programacion", R.string.subject_programacion, R.drawable.ic_terminal, R.color.subject_prog_bg),
        firstYear("base_de_datos", R.string.subject_base_de_datos, R.drawable.ic_storage, R.color.subject_db_bg),
        firstYear("sistemas", R.string.subject_sistemas, R.drawable.ic_memory, R.color.subject_sistemas_bg),
        firstYear("marcas", R.string.subject_marcas, R.drawable.ic_description, R.color.subject_marcas_bg),
        firstYear("entornos", R.string.subject_entornos, R.drawable.ic_code, R.color.subject_entornos_bg),
        firstYear("digitalizacion", R.string.subject_digitalizacion,
            R.drawable.ic_computer, R.color.subject_digital_bg),
        firstYear("ipe", R.string.subject_ipe, R.drawable.ic_assessment, R.color.subject_ipe_bg),
        firstYear("sostenibilidad", R.string.subject_sostenibilidad,
            R.drawable.ic_eco, R.color.subject_sostenibilidad_bg),

        // --- 2º ---
        secondYear("acceso_datos", R.string.subject_acceso_datos, R.drawable.ic_import_export, R.color.subject_db_bg),
        secondYear("ciberseguridad", R.string.subject_ciberseguridad,
            R.drawable.ic_security, R.color.subject_sistemas_bg),
        secondYear("interfaces", R.string.subject_interfaces, R.drawable.ic_dashboard, R.color.subject_marcas_bg),
        secondYear("ingles", R.string.subject_ingles, R.drawable.ic_translate, R.color.subject_digital_bg),
        secondYear("ipe2", R.string.subject_ipe2, R.drawable.ic_work, R.color.subject_ipe_bg),
        secondYear("servicios", R.string.subject_servicios, R.drawable.ic_dns, R.color.subject_prog_bg),
        secondYear("multimedia", R.string.subject_multimedia, R.drawable.ic_smartphone, R.color.subject_entornos_bg),
        secondYear("gestion", R.string.subject_gestion, R.drawable.ic_business, R.color.subject_sostenibilidad_bg)
    )

    private fun firstYear(id: String, name: Int, icon: Int, color: Int) = Subject(id, name, icon, color, course = 1)

    private fun secondYear(id: String, name: Int, icon: Int, color: Int) = Subject(id, name, icon, color, course = 2)

    /** Display name for a subject id, falling back to the id itself. */
    fun nameFor(subjectId: String): UiText = when (subjectId) {
        Favorites.ALL_SUBJECTS -> UiText.Res(R.string.subject_all)
        else -> all.firstOrNull { it.id == subjectId }?.let { UiText.Res(it.nameRes) } ?: UiText.Raw(subjectId)
    }
}