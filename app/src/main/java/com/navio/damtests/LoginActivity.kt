package com.navio.damtests

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.navio.damtests.auth.AuthManager
import com.navio.damtests.auth.AuthUiHelper
import com.navio.damtests.auth.UserProgressRepository
import com.navio.damtests.data.local.entity.QuestionsDao
import kotlinx.coroutines.launch

@AndroidEntryPoint
class LoginActivity : AppCompatActivity() {

    @Inject lateinit var authManager: AuthManager
    @Inject lateinit var userProgressRepository: UserProgressRepository
    @Inject lateinit var questionsDao: QuestionsDao

    private lateinit var etEmail: TextInputEditText
    private lateinit var etPassword: TextInputEditText
    private lateinit var btnLogin: MaterialButton
    private lateinit var btnGoogle: MaterialButton
    private lateinit var tvForgotPassword: TextView
    private lateinit var tvRegister: TextView
    private lateinit var progressOverlay: View

    private val googleSignInClient by lazy {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        GoogleSignIn.getClient(this, gso)
    }

    private val googleSignInLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            lifecycleScope.launch {
                setLoading(true)
                val authResult = authManager.signInWithGoogle(account)
                authResult.fold(
                    onSuccess = { onLoginSuccess() },
                    onFailure = {
                        setLoading(false)
                        AuthUiHelper.showInfo(this@LoginActivity, "Error",
                            AuthUiHelper.translateError(this@LoginActivity, it))
                    }
                )
            }
        } catch (e: ApiException) {
            AuthUiHelper.showInfo(this, "Error", getString(R.string.error_google_signin))
        }
    }

    private val registerLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            AuthUiHelper.showInfo(this, "¡Cuenta creada!",
                "Ya puedes iniciar sesión con tus credenciales")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (authManager.isLoggedIn) {
            goToMain()
            return
        }

        setContentView(R.layout.activity_login)

        etEmail          = findViewById(R.id.etEmail)
        etPassword       = findViewById(R.id.etPassword)
        btnLogin         = findViewById(R.id.btnLogin)
        btnGoogle        = findViewById(R.id.btnGoogle)
        tvForgotPassword = findViewById(R.id.tvForgotPassword)
        tvRegister       = findViewById(R.id.tvRegister)
        progressOverlay  = findViewById(R.id.progressOverlay)

        btnLogin.setOnClickListener { attemptEmailLogin() }
        btnGoogle.setOnClickListener { startGoogleSignIn() }
        tvForgotPassword.setOnClickListener { showForgotPasswordDialog() }
        tvRegister.setOnClickListener {
            registerLauncher.launch(Intent(this, RegisterActivity::class.java))
        }
    }

    private fun attemptEmailLogin() {
        val email    = etEmail.text?.toString()?.trim() ?: ""
        val password = etPassword.text?.toString() ?: ""

        if (email.isBlank() || password.isBlank()) {
            AuthUiHelper.showInfo(this, "Faltan datos", getString(R.string.error_fields_required))
            return
        }

        lifecycleScope.launch {
            setLoading(true)
            val result = authManager.signInWithEmail(email, password)
            result.fold(
                onSuccess = { onLoginSuccess() },
                onFailure = {
                    setLoading(false)
                    AuthUiHelper.showInfo(this@LoginActivity, "Error",
                        AuthUiHelper.translateError(this@LoginActivity, it))
                }
            )
        }
    }

    private fun startGoogleSignIn() {
        googleSignInClient.signOut().addOnCompleteListener {
            googleSignInLauncher.launch(googleSignInClient.signInIntent)
        }
    }

    private fun showForgotPasswordDialog() {
        val prefill = etEmail.text?.toString()?.trim() ?: ""
        AuthUiHelper.showForgotPassword(this, prefill) { email, dialog ->
            if (email.isBlank()) {
                AuthUiHelper.showInfo(this, "Falta el correo",
                    "Introduce tu correo electrónico")
                return@showForgotPassword
            }
            lifecycleScope.launch {
                val result = authManager.sendPasswordResetEmail(email)
                dialog.dismiss()
                result.fold(
                    onSuccess = {
                        AuthUiHelper.showInfo(this@LoginActivity, "Correo enviado",
                            "Revisa tu bandeja de entrada (y la carpeta de spam) para restablecer tu contraseña")
                    },
                    onFailure = {
                        AuthUiHelper.showInfo(this@LoginActivity, "Error",
                            AuthUiHelper.translateError(this@LoginActivity, it))
                    }
                )
            }
        }
    }

    private suspend fun onLoginSuccess() {
        val uid = authManager.currentUid
        if (uid != null) {
            // Merge cloud + local progress in the background — don't block the UI
            try {
                val localProgress = questionsDao.getAllProgressOnce()
                val cloudProgress = userProgressRepository.downloadAllProgress(uid)
                val merged        = userProgressRepository.mergeProgress(localProgress, cloudProgress)
                merged.forEach { questionsDao.saveProgress(it) }
            } catch (_: Exception) {
                // Sync failure shouldn't block login
            }
        }
        goToMain()
    }

    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun setLoading(loading: Boolean) {
        progressOverlay.visibility = if (loading) View.VISIBLE else View.GONE
        btnLogin.isEnabled  = !loading
        btnGoogle.isEnabled = !loading
    }
}