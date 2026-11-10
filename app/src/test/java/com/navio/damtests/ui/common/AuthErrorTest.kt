package com.navio.damtests.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthErrorTest {

    @Test
    fun `firebase messages are recognised`() {
        assertEquals(
            AuthError.EMAIL_IN_USE,
            AuthError.from(Exception("The email address is already in use by another account."))
        )
        assertEquals(
            AuthError.WRONG_CREDENTIALS,
            AuthError.from(Exception("An internal error has occurred. [ INVALID_LOGIN_CREDENTIALS ]"))
        )
        assertEquals(AuthError.NETWORK, AuthError.from(Exception("A network error (such as timeout) has occurred.")))
    }

    @Test
    fun `unknown or missing messages fall back to a generic error`() {
        assertEquals(AuthError.UNKNOWN, AuthError.from(Exception("Something new")))
        assertEquals(AuthError.UNKNOWN, AuthError.from(null))
    }

    @Test
    fun `only email problems are shown under the email field`() {
        assertTrue(AuthError.EMAIL_IN_USE.affectsEmail)
        assertTrue(AuthError.INVALID_EMAIL.affectsEmail)
        assertFalse(AuthError.NETWORK.affectsEmail)
        assertFalse(AuthError.WRONG_CREDENTIALS.affectsEmail)
    }
}
