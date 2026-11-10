package com.navio.damtests.data.favorites

import com.navio.damtests.auth.AuthManager
import com.navio.damtests.data.local.dao.FavoriteQuestionDao
import com.navio.damtests.data.local.entity.FavoriteQuestion
import com.navio.damtests.data.local.entity.Question
import com.navio.damtests.data.sync.StatsSyncRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Questions the user marked as favourite: the star on every question, the
 * favourites screen and the favourites test. Changes are saved in Room and,
 * if a user is signed in, uploaded to Firestore right away.
 */
class FavoritesRepository @Inject constructor(
    private val favoriteQuestionDao: FavoriteQuestionDao,
    private val authManager: AuthManager,
    private val statsSyncRepository: StatsSyncRepository
) {

    /** Stable ids of the questions marked as favourite, updated live. */
    fun getFavoriteIds(): Flow<Set<String>> =
        favoriteQuestionDao.getFavoriteIdsFlow().map { it.toSet() }

    /** All favourite questions, newest first, updated live (favourites screen). */
    fun getFavoriteQuestionsFlow(): Flow<List<Question>> =
        favoriteQuestionDao.getFavoriteQuestionsFlow().map { list -> list.distinctBy { it.stableId } }

    /** Favourite questions of the subject, updated live (favourites card). */
    fun getFavoriteCountFlow(subjectId: String): Flow<Int> =
        favoriteQuestionDao.countFavoritesFlow(subjectId)

    /**
     * Marks or unmarks [question] as favourite, locally and in the cloud.
     * Questions without a stable id can't be favourited (they couldn't be
     * found again after the next question sync).
     */
    suspend fun setFavorite(question: Question, favorite: Boolean, now: Long = System.currentTimeMillis()) {
        if (question.stableId.isBlank()) return
        val record = FavoriteQuestion(question.stableId, question.subjectId, favorite, now)
        favoriteQuestionDao.saveFavorite(record)
        authManager.currentUid?.let { uid -> statsSyncRepository.saveFavorite(uid, record) }
    }

    /**
     * Up to [limit] favourite questions in random order, for the favourites
     * test of [subjectId] (or of every subject, for [Favorites.ALL_SUBJECTS]).
     */
    suspend fun getFavoriteQuestions(subjectId: String, limit: Int): List<Question> =
        favoriteQuestionDao.getFavoriteQuestions(subjectId.takeUnless { it == Favorites.ALL_SUBJECTS })
            .distinctBy { it.stableId }
            .shuffled()
            .take(limit)
}
