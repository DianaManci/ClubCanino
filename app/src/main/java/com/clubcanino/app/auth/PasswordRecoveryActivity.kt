package com.clubcanino.app.auth

import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.clubcanino.app.R
import com.clubcanino.app.admin.data.ShopUserStore
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class PasswordRecoveryActivity : AppCompatActivity() {

    private lateinit var inputLayoutEmail: TextInputLayout
    private lateinit var editEmail: TextInputEditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_password_recovery)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.recoveryRoot)) { view, insets ->
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
        editEmail = findViewById(R.id.editEmail)

        findViewById<MaterialButton>(R.id.buttonSendRecovery).setOnClickListener {
            attemptRecovery()
        }
        findViewById<MaterialButton>(R.id.buttonBackToLogin).setOnClickListener {
            finish()
        }
    }

    private fun attemptRecovery() {
        inputLayoutEmail.error = null

        val email = editEmail.text?.toString()?.trim().orEmpty()

        if (AuthValidator.isBlank(email)) {
            inputLayoutEmail.error = getString(R.string.error_field_required)
            return
        }

        if (!AuthValidator.isValidEmail(email)) {
            inputLayoutEmail.error = getString(R.string.error_email_invalid)
            return
        }

        if (!FirebaseGate.isReady(this)) {
            Toast.makeText(this, R.string.msg_firebase_missing, Toast.LENGTH_LONG).show()
            return
        }
        ShopUserStore.sendPasswordReset(this, email) { sent ->
            val message = if (sent) R.string.msg_recovery_sent else R.string.msg_error_generic
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            if (sent) finish()
        }
    }
}
