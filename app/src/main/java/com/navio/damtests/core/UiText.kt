package com.navio.damtests.core

import android.content.Context
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes

/**
 * Text decided outside the UI (catalogs, rules, workers) without a Context:
 * a string resource with its arguments, resolved when it reaches the screen.
 * Keeps every user-facing text in strings.xml and the deciding code testable.
 */
sealed interface UiText {

    data class Res(@StringRes val id: Int, val args: List<Any> = emptyList()) : UiText

    data class Plural(@PluralsRes val id: Int, val count: Int, val args: List<Any> = listOf(count)) : UiText

    /** Text that is data, not copy (e.g. an unknown subject id shown as is). */
    data class Raw(val text: String) : UiText

    fun asString(context: Context): String = when (this) {
        is Res -> context.getString(id, *args.toTypedArray())
        is Plural -> context.resources.getQuantityString(id, count, *args.toTypedArray())
        is Raw -> text
    }
}
