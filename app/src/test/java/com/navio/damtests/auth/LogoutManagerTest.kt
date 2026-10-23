package com.navio.damtests.auth

import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.FirebaseFirestore
import com.navio.damtests.data.local.entity.QuestionsDao
import com.navio.damtests.data.session.QuizSessionStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for LogoutManager: local data must only be wiped (and the user
 * signed out) once the cloud backup is fully confirmed.
 */
class LogoutManagerTest {

    private lateinit var authManager: AuthManager
    private lateinit var firestore: FirebaseFirestore
    private lateinit var dao: QuestionsDao
    private lateinit var sessionStore: QuizSessionStore
    private lateinit var progressRepo: UserProgressRepository
    private lateinit var statsRepo: StatsSyncRepository
    private lateinit var sessionBackup: SessionBackupRepository
    private lateinit var logoutManager: LogoutManager

    /** An already-completed Task, so `await()` returns (or throws) immediately. */
    private fun completedTask(error: Exception? = null): Task<Void> = mockk {
        every { isComplete } returns true
        every { isCanceled } returns false
        every { exception } returns error
        every { result } returns null
    }

    @Before
    fun setUp() {
        // android.util.Log is a stub in local unit tests; LogoutManager logs on every path.
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0

        authManager = mockk(relaxed = true)
        firestore = mockk(relaxed = true)
        dao = mockk(relaxed = true)
        sessionStore = mockk(relaxed = true)
        progressRepo = mockk(relaxed = true)
        statsRepo = mockk(relaxed = true)
        sessionBackup = mockk(relaxed = true)
        logoutManager = LogoutManager(authManager, firestore, dao, sessionStore, progressRepo, statsRepo, sessionBackup)

        every { authManager.currentUid } returns "user123"
        every { firestore.waitForPendingWrites() } returns completedTask()
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    @Test
    fun `confirmed backup wipes local user data and signs out`() = runTest {
        val signedOut = logoutManager.backupAndSignOut()

        assertTrue(signedOut)
        coVerifyOrder {
            progressRepo.pushAllProgress("user123", any())
            statsRepo.pushAll("user123")
            sessionBackup.backup("user123")
            firestore.waitForPendingWrites()
            dao.clearUserData()
            authManager.signOut()
        }
        verify { sessionStore.clear() }
        verify { statsRepo.forgetSyncState("user123") }
    }

    @Test
    fun `failed progress backup keeps everything and stays signed in`() = runTest {
        coEvery { progressRepo.pushAllProgress(any(), any()) } throws RuntimeException("offline")

        val signedOut = logoutManager.backupAndSignOut()

        assertFalse(signedOut)
        coVerify(exactly = 0) { dao.clearUserData() }
        verify(exactly = 0) { sessionStore.clear() }
        verify(exactly = 0) { authManager.signOut() }
    }

    @Test
    fun `failed statistics backup keeps everything and stays signed in`() = runTest {
        coEvery { statsRepo.pushAll(any()) } throws RuntimeException("server error")

        assertFalse(logoutManager.backupAndSignOut())
        coVerify(exactly = 0) { dao.clearUserData() }
        verify(exactly = 0) { authManager.signOut() }
    }

    @Test
    fun `unconfirmed pending writes keep everything and stay signed in`() = runTest {
        every { firestore.waitForPendingWrites() } returns completedTask(RuntimeException("not confirmed"))

        assertFalse(logoutManager.backupAndSignOut())
        coVerify(exactly = 0) { dao.clearUserData() }
        verify(exactly = 0) { authManager.signOut() }
    }
}