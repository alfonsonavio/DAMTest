package com.navio.damtests.data

import android.content.Context
import android.util.Log
import com.google.firebase.database.FirebaseDatabase
import com.navio.damtests.QuizRepository
import com.navio.damtests.data.local.entity.Question
import kotlinx.coroutines.tasks.await

class FirebaseManager(private val context: Context, private val repository: QuizRepository) {

    private val db = FirebaseDatabase.getInstance().reference
    private val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

    suspend fun checkAndUpdateQuestions(subjectId: String) {
        // 1. Buscamos en 'versiones/asignatura' qué temas han cambiado
        val versionsSnapshot = db.child("versiones").child(subjectId).get().await()

        versionsSnapshot.children.forEach { topicSnap ->
            val topicId = topicSnap.key ?: return@forEach
            val remoteVersion = topicSnap.getValue(Int::class.java) ?: 0
            val localVersion = prefs.getInt("version_${subjectId}_$topicId", 0)

            if (remoteVersion > localVersion) {
                // 2. Si el tema ha cambiado, bajamos solo sus preguntas
                val questionsSnap = db.child("preguntas").child(subjectId).child(topicId).get().await()
                val questionsList = mutableListOf<Question>()

                questionsSnap.children.forEach { qSnap ->
                    val q = qSnap.getValue(Question::class.java)
                    q?.let { questionsList.add(it) }
                }

                // 3. Guardamos en Room y actualizamos versión local
                // En FirebaseManager.kt, dentro del bloque donde detectas nueva versión:

// En lugar de solo insert, hacemos una "limpieza y carga"
                repository.deleteQuestionsByTopic(subjectId, topicId) // Borra lo viejo de este tema
                repository.insertQuestions(questionsList)            // Inserta lo nuevo
                prefs.edit().putInt("version_${subjectId}_$topicId", remoteVersion).apply()
            }
        }
    }
}