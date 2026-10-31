package com.clubcanino.app.auth

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.clubcanino.app.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class LoginActivity : AppCompatActivity() {

    private lateinit var inputLayoutEmail: TextInputLayout
    private lateinit var inputLayoutPassword: TextInputLayout
    private lateinit var editEmail: TextInputEditText
    private lateinit var editPassword: TextInputEditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_login)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.loginRoot)) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(
                view.paddingLeft + bars.left,
                view.paddingTop + bars.top,
                view.paddingRight + bars.right,
                view.paddingBottom + bars.bottom
            )
            insets
        }

        inputLayoutEmail = findViewById(R.id.inputLayoutEmail)
        inputLayoutPassword = findViewById(R.id.inputLayoutPassword)
        editEmail = findViewById(R.id.editEmail)
        editPassword = findViewById(R.id.editPassword)

        findViewById<MaterialButton>(R.id.buttonLogin).setOnClickListener { attemptLogin() }
        findViewById<MaterialButton>(R.id.buttonGoRegister).setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
        findViewById<MaterialButton>(R.id.buttonForgotPassword).setOnClickListener {
            startActivity(Intent(this, PasswordRecoveryActivity::class.java))
        }
        findViewById<MaterialButton>(R.id.buttonBiometric).setOnClickListener {
            startActivity(Intent(this, BiometricAuthActivity::class.java))
        }
    }

    private fun attemptLogin() {
        clearErrors()

        val email = editEmail.text?.toString()?.trim().orEmpty()
        val password = editPassword.text?.toString().orEmpty()
        var isValid = true

        if (AuthValidator.isBlank(email)) {
            inputLayoutEmail.error = getString(R.string.error_field_required)
            isValid = false
        } else if (!AuthValidator.isValidEmail(email)) {
            inputLayoutEmail.error = getString(R.string.error_email_invalid)
            isValid = false
        }

        if (AuthValidator.isBlank(password)) {
            inputLayoutPassword.error = getString(R.string.error_field_required)
            isValid = false
        } else if (!AuthValidator.isValidPassword(password)) {
            inputLayoutPassword.error = getString(R.string.error_password_short)
            isValid = false
        }

        if (!isValid) return

        Toast.makeText(this, R.string.msg_login_pending, Toast.LENGTH_LONG).show()
    }

    private fun clearErrors() {
        inputLayoutEmail.error = null
        inputLayoutPassword.error = null
    }
}
