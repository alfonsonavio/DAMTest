package com.navio.damtests

import com.navio.damtests.data.local.entity.Question

data class ShuffledQuestion(
    val originalQuestion: Question,
    val shuffledOptions: List<String>,
    val newCorrectIndex: Int
)

fun Question.shuffle(): ShuffledQuestion {
    // Creamos una lista de pares
    val options = listOf(
        optionA to (correctOptionIndex == 0),
        optionB to (correctOptionIndex == 1),
        optionC to (correctOptionIndex == 2),
        optionD to (correctOptionIndex == 3)
    ).shuffled()

    return ShuffledQuestion(
        originalQuestion = this,
        shuffledOptions = options.map { it.first },
        newCorrectIndex = options.indexOfFirst { it.second }
    )
}