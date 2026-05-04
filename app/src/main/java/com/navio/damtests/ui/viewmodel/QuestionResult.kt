package com.navio.damtests.ui.viewmodel

import com.navio.damtests.data.local.entity.Question

data class QuestionResult(
    val question: Question,
    val userSelectedIndex: Int,
    val shuffledOptions: List<String>
)