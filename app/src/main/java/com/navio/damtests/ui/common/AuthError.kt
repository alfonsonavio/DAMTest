package com.navio.damtests.ui.common

import androidx.annotation.StringRes
import com.navio.damtests.R

/**
 * Firebase Auth failures the app explains to the user. Firebase reports them
 * only as English messages, so they are recognised by their text.
 *
 * @property affectsEmail Shown under the email field rather than in a dialog.
 */
enum class AuthError(@StringRes val messageRes: Int, val affectsEmail: Boolean = false) {
    EMAIL_IN_USE(R.string.auth_error_email_in_use, affectsEmail = true),
    INVALID_EMAIL(R.string.auth_error_invalid_email, affectsEmail = true),
    WRONG_CREDENTIALS(R.string.auth_error_wrong_credentials),
    NO_ACCOUNT(R.string.auth_error_no_account, affectsEmail = true),
    NETWORK(R.string.auth_error_network),
    TOO_MANY_ATTEMPTS(R.string.auth_error_too_many_attempts),
    WEAK_PASSWORD(R.string.auth_error_weak_password),
    UNKNOWN(R.string.auth_error_unknown);

    companion object {
        private val PATTERNS = listOf(
            EMAIL_IN_USE to listOf("already in use"),
            INVALID_EMAIL to listOf("badly formatted", "invalid email"),
            WRONG_CREDENTIALS to listOf("password is invalid", "INVALID_LOGIN_CREDENTIALS", "supplied auth credential"),
            NO_ACCOUNT to listOf("no user record", "there is no user"),
            NETWORK to listOf("network error"),
            TOO_MANY_ATTEMPTS to listOf("blocked all requests", "unusual activity"),
            WEAK_PASSWORD to listOf("Password should be at least")
        )

        fun from(throwable: Throwable?): AuthError {
            val message = throwable?.message.orEmpty()
            return PATTERNS.firstOrNull { (_, texts) -> texts.any { message.contains(it, ignoreCase = true) } }
                ?.first ?: UNKNOWN
        }
    }
}
