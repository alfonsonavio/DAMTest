package com.navio.damtests.data.local.entity

/**
 * A DAM subject shown in the Asignaturas grid.
 *
 * [course] is inferred in-app (1 or 2), NOT stored in Firebase: both first- and
 * second-year subjects hang directly off `preguntas/<id>/` in the Realtime
 * Database. The course only drives which 1º/2º tab a subject appears under.
 */
data class Subject(
    val id: String,
    val name: String,
    val iconRes: Int,
    val colorRes: Int,
    val course: Int = 1
)