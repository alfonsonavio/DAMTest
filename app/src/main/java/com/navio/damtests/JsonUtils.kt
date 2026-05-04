package com.navio.damtests

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.navio.damtests.data.local.entity.Question

object JsonUtils {

    private data class QuestionRaw(
        val subjectId: String,
        val topicId: String, // CAMBIADO: de Int a String
        val text: String,
        val options: List<String>,
        val correctOptionIndex: Int
    )

    fun loadQuestionsFromAsset(context: Context, fileName: String): List<Question> {
        val jsonString = context.assets.open(fileName).bufferedReader().use { it.readText() }

        val listType = object : TypeToken<List<QuestionRaw>>() {}.type
        val rawList: List<QuestionRaw> = Gson().fromJson(jsonString, listType)

        return rawList.map { raw ->
            Question(
                subjectId = raw.subjectId,
                topicId = raw.topicId, // Ahora esto ya es String
                text = raw.text,
                optionA = raw.options.getOrNull(0) ?: "",
                optionB = raw.options.getOrNull(1) ?: "",
                optionC = raw.options.getOrNull(2) ?: "",
                optionD = raw.options.getOrNull(3) ?: "",
                correctOptionIndex = raw.correctOptionIndex
            )
        }
    }
}