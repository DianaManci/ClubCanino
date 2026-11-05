package com.clubcanino.app.auth

import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.widget.ScrollView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.clubcanino.app.R
import com.clubcanino.app.admin.data.ShopUserStore
import com.clubcanino.app.buyer.data.BuyerSession
import com.clubcanino.app.navigation.RoleNavigator
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
        keepFocusedFieldVisible(findViewById(R.id.loginScroll))

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

        if (!FirebaseGate.isReady(this)) {
            inputLayoutEmail.error = getString(R.string.msg_firebase_missing)
            return
        }

        findViewById<MaterialButton>(R.id.buttonLogin).isEnabled = false
        ShopUserStore.signIn(this, email, password) { account ->
            findViewById<MaterialButton>(R.id.buttonLogin).isEnabled = true
            if (account == null) {
                inputLayoutPassword.error = getString(R.string.error_login_invalid)
                return@signIn
            }
            BuyerSession.start(this, account.email, account.role)
            RoleNavigator.openRoleHome(this, RoleNavigator.roleOf(account.role))
        }
    }

    private fun keepFocusedFieldVisible(scroll: ScrollView) {
        ViewCompat.setOnApplyWindowInsetsListener(scroll) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, ime.bottom))
            if (insets.isVisible(WindowInsetsCompat.Type.ime())) {
                currentFocus?.let { focused ->
                    view.post {
                        val field = Rect(0, 0, focused.width, focused.height)
                        scroll.requestChildRectangleOnScreen(focused, field, true)
                    }
                }
            }
            insets
        }
    }

    private fun clearErrors() {
        inputLayoutEmail.error = null
        inputLayoutPassword.error = null
    }
}
