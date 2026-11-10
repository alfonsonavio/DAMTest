package com.navio.damtests.data.quiz

import com.navio.damtests.data.local.entity.Question

data class ShuffledQuestion(
    val originalQuestion: Question,
    val shuffledOptions: List<String>,
    val newCorrectIndex: Int
)

fun Question.shuffle(): ShuffledQuestion {
    // Each option with whether it is the right one, then mixed
    val marked = options
        .mapIndexed { index, text -> text to (index == correctOptionIndex) }
        .shuffled()

    return ShuffledQuestion(
        originalQuestion = this,
        shuffledOptions = marked.map { it.first },
        newCorrectIndex = marked.indexOfFirst { it.second }
    )
}
