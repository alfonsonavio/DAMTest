package com.navio.damtests.data.local.entity

import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes

/**
 * A DAM subject shown in the Asignaturas grid.
 *
 * [course] is inferred in-app (1 or 2), NOT stored in Firebase: both first- and
 * second-year subjects hang directly off `preguntas/<id>/` in the Realtime
 * Database. The course only drives which 1º/2º tab a subject appears under.
 */
data class Subject(
    val id: String,
    @StringRes val nameRes: Int,
    @DrawableRes val iconRes: Int,
    @ColorRes val colorRes: Int,
    val course: Int = 1
)