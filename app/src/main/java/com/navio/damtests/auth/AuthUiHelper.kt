package com.navio.damtests.auth

import android.content.Context
import android.view.LayoutInflater
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.navio.damtests.R

/**
 * Helpers to show the app's custom-styled dialogs (matching dialog_results style)
 * instead of the default grey Snackbars/AlertDialogs, and to translate Firebase
 * Auth error messages to Spanish.
 */
object AuthUiHelper {

    /** Shows a styled info/success dialog with a single "Entendido" button. */
    fun showInfo(context: Context, title: String, message: String, onOk: (() -> Unit)? = null) {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_info, null)
        val dialog = AlertDialog.Builder(context).setView(view).setCancelable(false).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        view.findViewById<TextView>(R.id.tvInfoTitle).text = title
        view.findViewById<TextView>(R.id.tvInfoMessage).text = message
        view.findViewById<MaterialButton>(R.id.btnInfoOk).setOnClickListener {
            dialog.dismiss()
            onOk?.invoke()
        }
        dialog.show()
    }

    /** Shows a styled confirmation dialog (used for logout). */
    fun showConfirm(
        context: Context,
        title: String,
        message: String,
        confirmText: String,
        onConfirm: () -> Unit
    ) {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_confirm, null)
        val dialog = AlertDialog.Builder(context).setView(view).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        view.findViewById<TextView>(R.id.tvConfirmTitle).text = title
        view.findViewById<TextView>(R.id.tvConfirmMessage).text = message
        view.findViewById<MaterialButton>(R.id.btnConfirmYes).apply {
            text = confirmText
            setOnClickListener { dialog.dismiss(); onConfirm() }
        }
        view.findViewById<MaterialButton>(R.id.btnConfirmNo).setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    /** Shows the styled "forgot password" dialog with its own email field. */
    fun showForgotPassword(
        context: Context,
        prefillEmail: String,
        onSend: (email: String, dialog: AlertDialog) -> Unit
    ) {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_forgot_password, null)
        val dialog = AlertDialog.Builder(context).setView(view).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val etEmail = view.findViewById<TextInputEditText>(R.id.etResetEmail)
        etEmail.setText(prefillEmail)

        view.findViewById<MaterialButton>(R.id.btnSendReset).setOnClickListener {
            val email = etEmail.text?.toString()?.trim() ?: ""
            onSend(email, dialog)
        }
        view.findViewById<MaterialButton>(R.id.btnCancelReset).setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    /**
     * Translates common Firebase Auth exception messages to Spanish.
     */
    fun translateError(context: Context, throwable: Throwable?): String {
        val msg = throwable?.message ?: ""
        return when {
            msg.contains("email address is already in use", true) ||
                    msg.contains("already in use", true) ->
                "Ese correo ya está registrado"
            msg.contains("badly formatted", true) ||
                    msg.contains("invalid email", true) ->
                "El correo no tiene un formato válido"
            msg.contains("password is invalid", true) ||
                    msg.contains("INVALID_LOGIN_CREDENTIALS", true) ||
                    msg.contains("supplied auth credential", true) ->
                "Correo o contraseña incorrectos"
            msg.contains("no user record", true) ||
                    msg.contains("there is no user", true) ->
                "No existe ninguna cuenta con ese correo"
            msg.contains("network error", true) ->
                "Error de red. Comprueba tu conexión"
            msg.contains("blocked all requests", true) ||
                    msg.contains("unusual activity", true) ->
                "Demasiados intentos. Espera un momento e inténtalo de nuevo"
            msg.contains("Password should be at least", true) ->
                "La contraseña debe tener al menos 6 caracteres"
            else -> "Algo salió mal. Inténtalo de nuevo"
        }
    }
}