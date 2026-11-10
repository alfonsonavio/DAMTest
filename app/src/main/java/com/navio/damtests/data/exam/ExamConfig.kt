package com.navio.damtests.data.exam

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/** What the user chose before an exam; also used to repeat it from the review. */
@Parcelize
data class ExamConfig(
    val subjectId: String,
    val range: ExamRange,
    val size: Int,
    val penalized: Boolean
) : Parcelable
