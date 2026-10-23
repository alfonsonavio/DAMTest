package com.navio.damtests.auth

import com.navio.damtests.data.local.entity.QuestionsDao
import com.navio.damtests.data.session.QuizSessionStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/** Unit tests for AccountDeletionManager. */
class AccountDeletionManagerTest {

    private lateinit var authManager: AuthManager
    private lateinit var cloudUserData: CloudUserDataRepository
    private lateinit var dao: QuestionsDao
    private lateinit var sessionStore: QuizSessionStore
    private lateinit var statsRepo: StatsSyncRepository
    private lateinit var manager: AccountDeletionManager

    private val now = 10_000_000L

    @Before
    fun setUp() {
        authManager = mockk(relaxed = true)
        cloudUserData = mockk(relaxed = true)
        dao = mockk(relaxed = true)
        sessionStore = mockk(relaxed = true)
        statsRepo = mockk(relaxed = true)
        manager = AccountDeletionManager(authManager, cloudUserData, dao, sessionStore, statsRepo)

        every { authManager.currentUid } returns "user123"
        every { authManager.lastSignInAt } returns now - 60_000L // signed in a minute ago
    }

    @Test
    fun `recent sign-in deletes cloud data, account and local data`() = runTest {
        assertEquals(AccountDeletionManager.Result.DELETED, manager.deleteAccount(now))

        coVerifyOrder {
            cloudUserData.deleteAll("user123")
            authManager.deleteCurrentUser()
            dao.clearUserData()
            authManager.signOut()
        }
        verify { sessionStore.clear() }
        verify { statsRepo.forgetSyncState("user123") }
    }

    @Test
    fun `old sign-in deletes nothing and asks to sign in again`() = runTest {
        every { authManager.lastSignInAt } returns now - 60 * 60 * 1000L // an hour ago

        assertEquals(AccountDeletionManager.Result.NEEDS_RECENT_LOGIN, manager.deleteAccount(now))

        coVerify(exactly = 0) { cloudUserData.deleteAll(any()) }
        coVerify(exactly = 0) { authManager.deleteCurrentUser() }
        coVerify(exactly = 0) { dao.clearUserData() }
    }

    @Test
    fun `failed cloud deletion deletes nothing`() = runTest {
        coEvery { cloudUserData.deleteAll(any()) } throws RuntimeException("offline")

        assertEquals(AccountDeletionManager.Result.FAILED, manager.deleteAccount(now))

        coVerify(exactly = 0) { authManager.deleteCurrentUser() }
        coVerify(exactly = 0) { dao.clearUserData() }
        verify(exactly = 0) { authManager.signOut() }
    }

    @Test
    fun `failed account deletion after data deletion still wipes local data`() = runTest {
        coEvery { authManager.deleteCurrentUser() } throws RuntimeException("recent login required")

        assertEquals(
            AccountDeletionManager.Result.DATA_DELETED_ACCOUNT_PENDING,
            manager.deleteAccount(now)
        )
        coVerify { dao.clearUserData() }
        verify { authManager.signOut() }
    }
}
