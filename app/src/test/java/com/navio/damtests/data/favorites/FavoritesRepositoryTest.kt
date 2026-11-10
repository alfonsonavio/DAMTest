package com.navio.damtests.data.favorites

import com.navio.damtests.auth.AuthManager
import com.navio.damtests.data.local.dao.FavoriteQuestionDao
import com.navio.damtests.data.local.entity.Question
import com.navio.damtests.data.sync.StatsSyncRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/** Unit tests for FavoritesRepository, with a mocked DAO and cloud repository. */
class FavoritesRepositoryTest {

    private lateinit var favoriteDao: FavoriteQuestionDao
    private lateinit var authManager: AuthManager
    private lateinit var statsSyncRepository: StatsSyncRepository
    private lateinit var repository: FavoritesRepository

    private fun question(id: Int, topic: String = "tema_1") = Question(
        id = id,
        subjectId = "programacion",
        topicId = topic,
        text = "Pregunta $id",
        optionA = "A", optionB = "B", optionC = "C", optionD = "D",
        correctOptionIndex = 0
    )

    @Before
    fun setUp() {
        favoriteDao = mockk(relaxed = true)
        authManager = mockk(relaxed = true)
        statsSyncRepository = mockk(relaxed = true)
        repository = FavoritesRepository(favoriteDao, authManager, statsSyncRepository)
    }

    @Test
    fun `setFavorite saves locally and uploads when user is logged in`() = runTest {
        every { authManager.currentUid } returns "user123"
        val q = question(1).copy(stableId = "programacion_tema_1_p1")

        repository.setFavorite(q, favorite = true, now = 5000)

        coVerify(exactly = 1) {
            favoriteDao.saveFavorite(
                match { it.stableId == "programacion_tema_1_p1" && it.isFavorite && it.updatedAt == 5000L }
            )
        }
        coVerify(exactly = 1) { statsSyncRepository.saveFavorite("user123", match { it.isFavorite }) }
    }

    @Test
    fun `setFavorite ignores questions without a stable id`() = runTest {
        every { authManager.currentUid } returns "user123"

        repository.setFavorite(question(1).copy(stableId = ""), favorite = true)

        coVerify(exactly = 0) { favoriteDao.saveFavorite(any()) }
        coVerify(exactly = 0) { statsSyncRepository.saveFavorite(any(), any()) }
    }

    @Test
    fun `favourites test of all subjects queries every subject and respects the limit`() = runTest {
        coEvery { favoriteDao.getFavoriteQuestions(null) } returns (1..30).map { question(it).copy(stableId = "q$it") }

        val result = repository.getFavoriteQuestions("all", limit = 20)

        assertEquals(20, result.size)
        coVerify(exactly = 1) { favoriteDao.getFavoriteQuestions(null) }
    }
}
