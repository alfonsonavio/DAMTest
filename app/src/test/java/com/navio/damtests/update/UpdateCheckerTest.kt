package com.navio.damtests.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {

    @Test
    fun `newer published version is an update`() {
        assertTrue(UpdateChecker.isUpdateAvailable(latestCode = 5, currentCode = 4))
    }

    @Test
    fun `same or older published version is not an update`() {
        assertFalse(UpdateChecker.isUpdateAvailable(latestCode = 4, currentCode = 4))
        assertFalse(UpdateChecker.isUpdateAvailable(latestCode = 3, currentCode = 4))
    }

    @Test
    fun `missing Remote Config value never prompts`() {
        // getLong returns 0 when latest_version_code isn't set
        assertFalse(UpdateChecker.isUpdateAvailable(latestCode = 0, currentCode = 4))
    }
}
