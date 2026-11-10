package com.navio.damtests.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.navio.damtests.R
import com.navio.damtests.auth.AuthManager
import com.navio.damtests.auth.GoogleCredentialClient
import com.navio.damtests.data.local.dao.TopicProgressDao
import com.navio.damtests.data.sync.SessionBackupRepository
import com.navio.damtests.data.sync.StatsSyncRepository
import com.navio.damtests.data.sync.UserProgressRepository
import com.navio.damtests.ui.common.AuthUiHelper
import com.navio.damtests.ui.common.setupEdgeToEdge
import com.navio.damtests.ui.main.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class LoginActivity : AppCompatActivity() {

    @Inject lateinit var authManager: AuthManager

    @Inject lateinit var userProgressRepository: UserProgressRepository

    @Inject lateinit var topicProgressDao: TopicProgressDao

    @Inject lateinit var statsSyncRepository: StatsSyncRepository

    @Inject lateinit var sessionBackupRepository: SessionBackupRepository

    @Inject lateinit var googleCredentialClient: GoogleCredentialClient

    private lateinit var etEmail: TextInputEditText
    private lateinit var etPassword: TextInputEditText
    private lateinit var btnLogin: MaterialButton
    private lateinit var btnGoogle: MaterialButton
    private lateinit var tvForgotPassword: TextView
    private lateinit var tvRegister: TextView
    private lateinit var progressOverlay: View

    private val registerLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            AuthUiHelper.showInfo(
                this,
                getString(R.string.register_success_title),
                getString(R.string.register_success_message)
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupEdgeToEdge(darkTop = false)

        if (authManager.isLoggedIn) {
            goToMain()
            return
        }

        setContentView(R.layout.activity_login)

        etEmail = findViewById(R.id.etEmail)
        etPassword = findViewById(R.id.etPassword)
        btnLogin = findViewById(R.id.btnLogin)
        btnGoogle = findViewById(R.id.btnGoogle)
        tvForgotPassword = findViewById(R.id.tvForgotPassword)
        tvRegister = findViewById(R.id.tvRegister)
        progressOverlay = findViewById(R.id.progressOverlay)

        btnLogin.setOnClickListener { attemptEmailLogin() }
        btnGoogle.setOnClickListener { startGoogleSignIn() }
        tvForgotPassword.setOnClickListener { showForgotPasswordDialog() }
        tvRegister.setOnClickListener {
            registerLauncher.launch(Intent(this, RegisterActivity::class.java))
        }
    }

    private fun attemptEmailLogin() {
        val email = etEmail.text?.toString()?.trim() ?: ""
        val password = etPassword.text?.toString() ?: ""

        if (email.isBlank() || password.isBlank()) {
            AuthUiHelper.showInfo(
                this,
                getString(R.string.error_missing_data_title),
                getString(R.string.error_fields_required)
            )
            return
        }

        lifecycleScope.launch {
            setLoading(true)
            val result = authManager.signInWithEmail(email, password)
            result.fold(
                onSuccess = { onLoginSuccess() },
                onFailure = {
                    setLoading(false)
                    showError(it)
                }
            )
        }
    }

    private fun startGoogleSignIn() {
        lifecycleScope.launch {
            when (val result = googleCredentialClient.getGoogleIdToken(this@LoginActivity)) {
                is GoogleCredentialClient.Result.Success -> {
                    setLoading(true)
                    authManager.signInWithGoogleIdToken(result.idToken).fold(
                        onSuccess = { onLoginSuccess() },
                        onFailure = {
                            setLoading(false)
                            showError(it)
                        }
                    )
                }
                GoogleCredentialClient.Result.Cancelled -> Unit // user closed the picker
                GoogleCredentialClient.Result.NoAccount -> AuthUiHelper.showInfo(
                    this@LoginActivity,
                    getString(R.string.error_google_no_account_title),
                    getString(R.string.error_google_no_account)
                )
                is GoogleCredentialClient.Result.Error -> AuthUiHelper.showInfo(
                    this@LoginActivity,
                    getString(R.string.error_title),
                    getString(R.string.error_google_signin)
                )
            }
        }
    }

    private fun showError(error: Throwable) {
        AuthUiHelper.showInfo(this, getString(R.string.error_title), AuthUiHelper.translateError(this, error))
    }

    private fun showForgotPasswordDialog() {
        val prefill = etEmail.text?.toString()?.trim() ?: ""
        AuthUiHelper.showForgotPassword(this, prefill) { email, dialog ->
            if (email.isBlank()) {
                AuthUiHelper.showInfo(
                    this,
                    getString(R.string.error_missing_email_title),
                    getString(R.string.error_missing_email)
                )
                return@showForgotPassword
            }
            lifecycleScope.launch {
                val result = authManager.sendPasswordResetEmail(email)
                dialog.dismiss()
                result.fold(
                    onSuccess = {
                        AuthUiHelper.showInfo(
                            this@LoginActivity,
                            getString(R.string.reset_email_sent_title),
                            getString(R.string.reset_email_sent_message)
                        )
                    },
                    onFailure = {
                        showError(it)
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
                val localProgress = topicProgressDao.getAllProgressOnce()
                val cloudProgress = userProgressRepository.downloadAllProgress(uid)
                val merged = userProgressRepository.mergeProgress(localProgress, cloudProgress)
                merged.forEach { topicProgressDao.saveProgress(it) }
            } catch (_: Exception) {
                // Sync failure shouldn't block login
            }
            // Restore statistics and the test in progress before opening the app,
            // so everything is already there on the first screen. Both handle
            // their own errors; statistics retry on the next app start.
            statsSyncRepository.sync(uid)
            sessionBackupRepository.restore(uid)
        }
        goToMain()
    }

    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun setLoading(loading: Boolean) {
        progressOverlay.visibility = if (loading) View.VISIBLE else View.GONE
        btnLogin.isEnabled = !loading
        btnGoogle.isEnabled = !loading
    }
}
