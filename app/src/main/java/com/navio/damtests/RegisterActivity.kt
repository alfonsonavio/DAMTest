package com.navio.damtests

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.navio.damtests.auth.AuthManager
import com.navio.damtests.auth.AuthUiHelper
import com.navio.damtests.auth.UserProgressRepository
import com.navio.damtests.data.local.entity.QuestionsDao
import kotlinx.coroutines.launch

@AndroidEntryPoint
class RegisterActivity : AppCompatActivity() {

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
        tilPassword.helperText = "Mínimo 8 caracteres, con al menos una letra y un número"

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
            val result = AuthManager.register(email, password, name)
            result.fold(
                onSuccess = { onRegisterSuccess() },
                onFailure = {
                    setLoading(false)
                    // Server-side errors (email taken, no connection) → field or dialog
                    val msg = AuthUiHelper.translateError(this@RegisterActivity, it)
                    if (msg.contains("correo", true)) {
                        tilEmail.error = msg
                    } else {
                        AuthUiHelper.showInfo(this@RegisterActivity, "Error", msg)
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
            tilName.error = "Introduce tu nombre"
            firstErrorField = firstErrorField ?: etName
            valid = false
        }

        if (email.isBlank()) {
            tilEmail.error = "Introduce tu correo"
            firstErrorField = firstErrorField ?: etEmail
            valid = false
        } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            tilEmail.error = "El correo no tiene un formato válido"
            firstErrorField = firstErrorField ?: etEmail
            valid = false
        }

        val passwordError = when {
            password.isBlank()            -> "Introduce una contraseña"
            password.length < 8           -> "Debe tener al menos 8 caracteres"
            !password.any { it.isDigit() } -> "Debe incluir al menos un número"
            !password.any { it.isLetter() } -> "Debe incluir al menos una letra"
            else -> null
        }
        if (passwordError != null) {
            tilPassword.error = passwordError
            firstErrorField = firstErrorField ?: etPassword
            valid = false
        }

        if (confirm != password || confirm.isBlank()) {
            tilPasswordConfirm.error = "Las contraseñas no coinciden"
            firstErrorField = firstErrorField ?: etPasswordConfirm
            valid = false
        }

        firstErrorField?.requestFocus()
        return valid
    }

    private suspend fun onRegisterSuccess() {
        val uid = AuthManager.currentUid
        if (uid != null) {
            try {
                val localProgress = questionsDao.getAllProgressOnce()
                UserProgressRepository.uploadAllProgress(uid, localProgress)
            } catch (_: Exception) { }
        }

        AuthManager.signOut()
        setResult(RESULT_OK)
        finish()
    }

    private fun setLoading(loading: Boolean) {
        progressOverlay.visibility = if (loading) View.VISIBLE else View.GONE
        btnRegister.isEnabled = !loading
    }
}