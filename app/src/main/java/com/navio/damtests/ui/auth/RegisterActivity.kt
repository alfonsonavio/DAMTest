package com.navio.damtests.ui.auth

import android.os.Bundle
import android.util.Log
import android.util.Patterns
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
import com.navio.damtests.core.suspendRunCatching
import com.navio.damtests.data.local.dao.QuestionsDao
import com.navio.damtests.data.sync.UserProgressRepository
import com.navio.damtests.ui.common.AuthError
import com.navio.damtests.ui.common.AuthUiHelper
import com.navio.damtests.ui.common.setupEdgeToEdge
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
        setupEdgeToEdge(darkTop = false)
        setContentView(R.layout.activity_register)

        tilName = findViewById(R.id.tilName)
        tilEmail = findViewById(R.id.tilEmail)
        tilPassword = findViewById(R.id.tilPassword)
        tilPasswordConfirm = findViewById(R.id.tilPasswordConfirm)
        etName = findViewById(R.id.etName)
        etEmail = findViewById(R.id.etEmail)
        etPassword = findViewById(R.id.etPassword)
        etPasswordConfirm = findViewById(R.id.etPasswordConfirm)
        btnRegister = findViewById(R.id.btnRegister)
        tvLogin = findViewById(R.id.tvLogin)
        progressOverlay = findViewById(R.id.progressOverlay)

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
        val name = etName.text?.toString()?.trim() ?: ""
        val email = etEmail.text?.toString()?.trim() ?: ""
        val password = etPassword.text?.toString() ?: ""
        val confirm = etPasswordConfirm.text?.toString() ?: ""

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
                            this@RegisterActivity,
                            getString(R.string.error_title),
                            getString(error.messageRes)
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
        val errors = listOf(
            tilName to nameError(name),
            tilEmail to emailError(email),
            tilPassword to passwordError(password),
            tilPasswordConfirm to confirmError(password, confirm)
        )
        errors.forEach { (field, error) -> if (error != null) field.error = error }
        errors.firstOrNull { (_, error) -> error != null }?.first?.editText?.requestFocus()
        return errors.all { (_, error) -> error == null }
    }

    private fun nameError(name: String): String? =
        if (name.isBlank()) getString(R.string.register_error_name) else null

    private fun emailError(email: String): String? = when {
        email.isBlank() -> getString(R.string.error_missing_email)
        !Patterns.EMAIL_ADDRESS.matcher(email).matches() -> getString(R.string.auth_error_invalid_email)
        else -> null
    }

    private fun passwordError(password: String): String? = when {
        password.isBlank() -> getString(R.string.register_error_password_empty)
        password.length < MIN_PASSWORD_LENGTH -> getString(R.string.register_error_password_short, MIN_PASSWORD_LENGTH)
        !password.any { it.isDigit() } -> getString(R.string.register_error_password_digit)
        !password.any { it.isLetter() } -> getString(R.string.register_error_password_letter)
        else -> null
    }

    private fun confirmError(password: String, confirm: String): String? =
        if (confirm != password || confirm.isBlank()) getString(R.string.error_passwords_no_match) else null

    private suspend fun onRegisterSuccess() {
        val uid = authManager.currentUid
        if (uid != null) {
            // Best effort: the account is already created, so this must not stop registering
            suspendRunCatching { userProgressRepository.uploadAllProgress(uid, questionsDao.getAllProgressOnce()) }
                .onFailure { Log.w(TAG, "Could not upload the local progress: ${it.message}") }
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
        const val TAG = "RegisterActivity"
        const val MIN_PASSWORD_LENGTH = 8
    }
}
