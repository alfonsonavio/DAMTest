package com.navio.damtests.ui.favorites

import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.navio.damtests.R
import com.navio.damtests.data.favorites.Favorites
import com.navio.damtests.data.quiz.QuizRepository
import com.navio.damtests.data.quiz.TestMode
import com.navio.damtests.data.session.QuizSessionStore
import com.navio.damtests.ui.quiz.QuizLauncher
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Lists every favourite question (with its correct answer), lets the user
 * remove them and starts a favourites test mixing every subject.
 */
@AndroidEntryPoint
class FavoritesActivity : AppCompatActivity() {

    @Inject lateinit var repository: QuizRepository
    @Inject lateinit var sessionStore: QuizSessionStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_favorites)

        val rv          = findViewById<RecyclerView>(R.id.rvFavorites)
        val emptyState  = findViewById<View>(R.id.emptyFavorites)
        val btnPractice = findViewById<MaterialButton>(R.id.btnPracticeFavorites)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        val adapter = FavoritesAdapter(onRemoveClick = { question ->
            lifecycleScope.launch {
                repository.setFavorite(question, favorite = false)
                // Removing is one tap away from a mistake: offer to undo it
                Snackbar.make(rv, "Quitada de favoritas", Snackbar.LENGTH_LONG)
                    .setAction("Deshacer") {
                        lifecycleScope.launch { repository.setFavorite(question, favorite = true) }
                    }
                    .show()
            }
        })
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter

        btnPractice.setOnClickListener {
            QuizLauncher.open(this, sessionStore, Favorites.ALL_SUBJECTS, TestMode.FAVORITES.topicId)
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                repository.getFavoriteQuestionsFlow().collect { favorites ->
                    adapter.submitList(favorites)
                    val empty = favorites.isEmpty()
                    rv.visibility = if (empty) View.GONE else View.VISIBLE
                    emptyState.visibility = if (empty) View.VISIBLE else View.GONE
                    btnPractice.isEnabled = !empty
                    btnPractice.text = if (empty) "PRACTICAR FAVORITAS"
                    else "PRACTICAR FAVORITAS (${favorites.size})"
                }
            }
        }
    }
}