package com.navio.damtests.data.quiz

import com.navio.damtests.data.local.entity.Question

/** The four options in their original order (a, b, c, d). */
val Question.options: List<String> get() = listOf(optionA, optionB, optionC, optionD)

/** Text of the right option. Compare answers by text: options are shown shuffled. */
val Question.correctOptionText: String get() = options.getOrElse(correctOptionIndex) { optionD }
