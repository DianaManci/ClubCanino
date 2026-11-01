package com.clubcanino.app.auth

import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.clubcanino.app.R
import com.clubcanino.app.buyer.data.BuyerSession
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class RegisterActivity : AppCompatActivity() {

    private lateinit var inputLayoutName: TextInputLayout
    private lateinit var inputLayoutEmail: TextInputLayout
    private lateinit var inputLayoutPassword: TextInputLayout
    private lateinit var inputLayoutConfirmPassword: TextInputLayout
    private lateinit var editName: TextInputEditText
    private lateinit var editEmail: TextInputEditText
    private lateinit var editPassword: TextInputEditText
    private lateinit var editConfirmPassword: TextInputEditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_register)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.registerRoot)) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(
                view.paddingLeft + bars.left,
                view.paddingTop + bars.top,
                view.paddingRight + bars.right,
                view.paddingBottom + bars.bottom
            )
            insets
        }

        inputLayoutName = findViewById(R.id.inputLayoutName)
        inputLayoutEmail = findViewById(R.id.inputLayoutEmail)
        inputLayoutPassword = findViewById(R.id.inputLayoutPassword)
        inputLayoutConfirmPassword = findViewById(R.id.inputLayoutConfirmPassword)
        editName = findViewById(R.id.editName)
        editEmail = findViewById(R.id.editEmail)
        editPassword = findViewById(R.id.editPassword)
        editConfirmPassword = findViewById(R.id.editConfirmPassword)

        findViewById<MaterialButton>(R.id.buttonRegister).setOnClickListener { attemptRegister() }
        findViewById<MaterialButton>(R.id.buttonGoLogin).setOnClickListener {
            finish()
        }
    }

    private fun attemptRegister() {
        clearErrors()

        val name = editName.text?.toString()?.trim().orEmpty()
        val email = editEmail.text?.toString()?.trim().orEmpty()
        val password = editPassword.text?.toString().orEmpty()
        val confirmPassword = editConfirmPassword.text?.toString().orEmpty()
        var isValid = true

        if (AuthValidator.isBlank(name)) {
            inputLayoutName.error = getString(R.string.error_field_required)
            isValid = false
        }

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

        if (AuthValidator.isBlank(confirmPassword)) {
            inputLayoutConfirmPassword.error = getString(R.string.error_field_required)
            isValid = false
        } else if (!AuthValidator.passwordsMatch(password, confirmPassword)) {
            inputLayoutConfirmPassword.error = getString(R.string.error_password_mismatch)
            isValid = false
        }

        if (!isValid) return

        // Local-only: associate future buyer pet data with this demo account email.
        BuyerSession.setBuyerEmail(this, email)
        Toast.makeText(this, R.string.msg_register_pending, Toast.LENGTH_LONG).show()
    }

    private fun clearErrors() {
        inputLayoutName.error = null
        inputLayoutEmail.error = null
        inputLayoutPassword.error = null
        inputLayoutConfirmPassword.error = null
    }
}
