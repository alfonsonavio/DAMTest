package com.navio.damtests.data.topics

/**
 * The study topics of a subject ("tema_N", "caso_N", "repaso_N") and the
 * order they are listed in: topics by number, then practical cases, then the
 * final review.
 */
object StudyTopics {

    private const val TEMA = "tema_"
    private const val CASO = "caso_"
    private const val REPASO = "repaso_"

    /** Id of topic number [number], e.g. "tema_3". */
    fun temaId(number: Int): String = "$TEMA$number"

    fun temaNumber(topicId: String): Int? =
        topicId.takeIf { it.startsWith(TEMA) }?.removePrefix(TEMA)?.toIntOrNull()

    /** Lists topics, cases and the final review in that order, each by its number. */
    val ORDER: Comparator<String> = compareBy<String>(
        { id ->
            when {
                id.startsWith(TEMA) -> 0
                id.startsWith(CASO) -> 1
                id.startsWith(REPASO) -> 2
                else -> 3
            }
        },
        { id -> id.substringAfter('_').toIntOrNull() ?: Int.MAX_VALUE },
        { id -> id }
    )
}
