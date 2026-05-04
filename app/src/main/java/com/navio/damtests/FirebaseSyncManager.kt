package com.navio.damtests

import android.content.Context
import android.util.Log
import com.google.firebase.database.FirebaseDatabase
import com.navio.damtests.data.local.entity.Question
import kotlinx.coroutines.tasks.await

class FirebaseSyncManager(private val context: Context, private val repository: QuizRepository) {

    private val prefs = context.getSharedPreferences("sync_prefs", Context.MODE_PRIVATE)
    private val database = FirebaseDatabase.getInstance("https://damtests-5ec43-default-rtdb.firebaseio.com/").reference

    suspend fun syncQuestions() {
        try {
            Log.d("SYNC", "Accediendo a versiones...")
            val versionesSnapshot = database.child("versiones").get().await()

            for (subjectSnapshot in versionesSnapshot.children) {
                val subjectId = subjectSnapshot.key ?: continue
                for (topicSnapshot in subjectSnapshot.children) {
                    val topicId = topicSnapshot.key ?: continue // Esto leerá "tema_1"
                    val remoteVersion = topicSnapshot.getValue(Int::class.java) ?: 0

                    val prefKey = "version_${subjectId}_$topicId"
                    val localVersion = prefs.getInt(prefKey, 0)

                    Log.d("SYNC", "Asignatura: $subjectId, Tema: $topicId, Versión remota: $remoteVersion, Local: $localVersion")

                    if (remoteVersion > localVersion) {
                        downloadTopic(subjectId, topicId, remoteVersion)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("SYNC", "Error crítico: ${e.message}")
        }
    }

    private suspend fun downloadTopic(subjectId: String, topicId: String, newVersion: Int) {
        try {
            val questionsSnapshot = database.child("preguntas")
                .child(subjectId)
                .child(topicId)
                .get().await()

            val questionsList = mutableListOf<Question>()

            for (qSnap in questionsSnapshot.children) {
                // qSnap representa a "p1", "p2", etc.
                // .getValue() extraerá el contenido del objeto dentro de p1
                val question = qSnap.getValue(Question::class.java)
                question?.let {
                    questionsList.add(it.copy(subjectId = subjectId, topicId = topicId))
                }
            }

            if (questionsList.isNotEmpty()) {
                // 3. Guardar en Room (borra lo viejo de ese tema y mete lo nuevo)
                repository.updateTopicQuestions(subjectId, topicId, questionsList)

                // 4. Actualizar versión local en SharedPreferences
                prefs.edit().putInt("version_${subjectId}_$topicId", newVersion).apply()
            }
        } catch (e: Exception) {
            Log.e("SYNC", "Error descargando tema $topicId: ${e.message}")
        }
    }
}