package com.navio.damtests.ui.auth

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.navio.damtests.R
import com.navio.damtests.auth.AuthManager
import com.navio.damtests.data.local.dao.QuestionsDao
import com.navio.damtests.data.sync.UserProgressRepository
import com.navio.damtests.ui.common.AuthError
import com.navio.damtests.ui.common.AuthUiHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class RegisterActivity : AppCompatActivity() {

    @Inject lateinit var authManager: AuthManager
    @Inject lateinit var userProgressRepository: UserProgressRepository
    @Inject lateinit var questionsDao: QuestionsDao

    private lateinit var tilName: TextInputLayout
    private lateinit var tilEmail: TextInputLayout
    private lateinit var tilPassword: TextInputLayout
    private lateinit var tilPasswordConfirm: TextInputLayout
    private lateinit var etName: TextInputEditText
    private lateinit var etEmail: TextInputEditText
    private lateinit var etPassword: TextInputEditText
    private lateinit var etPasswordConfirm: TextInputEditText
    private lateinit var btnRegister: MaterialButton
    private lateinit var tvLogin: TextView
    private lateinit var progressOverlay: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        tilName            = findViewById(R.id.tilName)
        tilEmail           = findViewById(R.id.tilEmail)
        tilPassword        = findViewById(R.id.tilPassword)
        tilPasswordConfirm = findViewById(R.id.tilPasswordConfirm)
        etName             = findViewById(R.id.etName)
        etEmail            = findViewById(R.id.etEmail)
        etPassword         = findViewById(R.id.etPassword)
        etPasswordConfirm  = findViewById(R.id.etPasswordConfirm)
        btnRegister        = findViewById(R.id.btnRegister)
        tvLogin            = findViewById(R.id.tvLogin)
        progressOverlay    = findViewById(R.id.progressOverlay)

        // Permanent helper text so the user knows the password rules upfront
        tilPassword.helperText = getString(R.string.register_password_rules, MIN_PASSWORD_LENGTH)

        // Clear each field's error as soon as the user edits it
        etName.doAfterTextChanged { tilName.error = null }
        etEmail.doAfterTextChanged { tilEmail.error = null }
        etPassword.doAfterTextChanged { tilPassword.error = null }
        etPasswordConfirm.doAfterTextChanged { tilPasswordConfirm.error = null }

        btnRegister.setOnClickListener { attemptRegister() }
        tvLogin.setOnClickListener { finish() }
    }

    private fun attemptRegister() {
        val name     = etName.text?.toString()?.trim() ?: ""
        val email    = etEmail.text?.toString()?.trim() ?: ""
        val password = etPassword.text?.toString() ?: ""
        val confirm  = etPasswordConfirm.text?.toString() ?: ""

        // Inline validation — errors shown under each specific field
        if (!validateInline(name, email, password, confirm)) return

        lifecycleScope.launch {
            setLoading(true)
            val result = authManager.register(email, password, name)
            result.fold(
                onSuccess = { onRegisterSuccess() },
                onFailure = {
                    setLoading(false)
                    // Server-side errors (email taken, no connection) → field or dialog
                    val error = AuthError.from(it)
                    if (error.affectsEmail) {
                        tilEmail.error = getString(error.messageRes)
                    } else {
                        AuthUiHelper.showInfo(
                            this@RegisterActivity, getString(R.string.error_title), getString(error.messageRes)
                        )
                    }
                }
            )
        }
    }

    /**
     * Validates each field and sets inline errors.
     * Returns true if everything is valid, false otherwise (focusing the first error).
     */
    private fun validateInline(name: String, email: String, password: String, confirm: String): Boolean {
        var valid = true
        var firstErrorField: View? = null

        if (name.isBlank()) {
            tilName.error = getString(R.string.register_error_name)
            firstErrorField = firstErrorField ?: etName
            valid = false
        }

        if (email.isBlank()) {
            tilEmail.error = getString(R.string.error_missing_email)
            firstErrorField = firstErrorField ?: etEmail
            valid = false
        } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            tilEmail.error = getString(R.string.auth_error_invalid_email)
            firstErrorField = firstErrorField ?: etEmail
            valid = false
        }

        val passwordError = when {
            password.isBlank()                    -> getString(R.string.register_error_password_empty)
            password.length < MIN_PASSWORD_LENGTH -> getString(R.string.register_error_password_short, MIN_PASSWORD_LENGTH)
            !password.any { it.isDigit() }        -> getString(R.string.register_error_password_digit)
            !password.any { it.isLetter() }       -> getString(R.string.register_error_password_letter)
            else -> null
        }
        if (passwordError != null) {
            tilPassword.error = passwordError
            firstErrorField = firstErrorField ?: etPassword
            valid = false
        }

        if (confirm != password || confirm.isBlank()) {
            tilPasswordConfirm.error = getString(R.string.error_passwords_no_match)
            firstErrorField = firstErrorField ?: etPasswordConfirm
            valid = false
        }

        firstErrorField?.requestFocus()
        return valid
    }

    private suspend fun onRegisterSuccess() {
        val uid = authManager.currentUid
        if (uid != null) {
            try {
                val localProgress = questionsDao.getAllProgressOnce()
                userProgressRepository.uploadAllProgress(uid, localProgress)
            } catch (_: Exception) { }
        }

        authManager.signOut()
        setResult(RESULT_OK)
        finish()
    }

    private fun setLoading(loading: Boolean) {
        progressOverlay.visibility = if (loading) View.VISIBLE else View.GONE
        btnRegister.isEnabled = !loading
    }

    private companion object {
        const val MIN_PASSWORD_LENGTH = 8
    }
}
