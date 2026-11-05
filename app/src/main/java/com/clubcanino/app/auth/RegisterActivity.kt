package com.clubcanino.app.auth

import android.graphics.Rect
import android.os.Bundle
import android.view.View
import android.widget.RadioButton
import android.widget.ScrollView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.clubcanino.app.R
import com.clubcanino.app.admin.data.ShopUserStore
import com.clubcanino.app.admin.data.ShopUserWrite
import com.clubcanino.app.buyer.data.BuyerSession
import com.clubcanino.app.navigation.RoleNavigator
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
    private lateinit var inputLayoutStoreName: TextInputLayout
    private lateinit var editStoreName: TextInputEditText
    private lateinit var radioSeller: RadioButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_register)
        keepFocusedFieldVisible(findViewById(R.id.registerScroll))

        inputLayoutName = findViewById(R.id.inputLayoutName)
        inputLayoutEmail = findViewById(R.id.inputLayoutEmail)
        inputLayoutPassword = findViewById(R.id.inputLayoutPassword)
        inputLayoutConfirmPassword = findViewById(R.id.inputLayoutConfirmPassword)
        editName = findViewById(R.id.editName)
        editEmail = findViewById(R.id.editEmail)
        editPassword = findViewById(R.id.editPassword)
        editConfirmPassword = findViewById(R.id.editConfirmPassword)
        inputLayoutStoreName = findViewById(R.id.inputLayoutStoreName)
        editStoreName = findViewById(R.id.editStoreName)
        radioSeller = findViewById(R.id.radioSeller)
        findViewById<RadioButton>(R.id.radioBuyer).setOnClickListener { updateStoreField() }
        radioSeller.setOnClickListener { updateStoreField() }

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
        val seller = radioSeller.isChecked
        val storeName = editStoreName.text?.toString()?.trim().orEmpty()
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

        if (seller && storeName.isEmpty()) {
            inputLayoutStoreName.error = getString(R.string.error_field_required)
            isValid = false
        }

        if (!isValid) return
        if (!FirebaseGate.isReady(this)) {
            Toast.makeText(this, R.string.msg_firebase_missing, Toast.LENGTH_LONG).show()
            return
        }

        val role = if (seller) ShopUserStore.ROLE_SELLER else ShopUserStore.ROLE_BUYER
        findViewById<MaterialButton>(R.id.buttonRegister).isEnabled = false
        ShopUserStore.register(this, name, email, password, role, storeName) { result ->
            findViewById<MaterialButton>(R.id.buttonRegister).isEnabled = true
            when (result) {
                ShopUserWrite.SAVED -> {
                    BuyerSession.start(this, email, role)
                    RoleNavigator.openRoleHome(this, RoleNavigator.roleOf(role))
                    finish()
                }
                ShopUserWrite.DUPLICATE_EMAIL -> {
                    inputLayoutEmail.error = getString(R.string.msg_user_duplicate_email)
                }
                ShopUserWrite.NOT_CONFIGURED -> {
                    Toast.makeText(this, R.string.msg_firebase_missing, Toast.LENGTH_LONG).show()
                }
                else -> {
                    Toast.makeText(this, R.string.msg_error_generic, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun updateStoreField() {
        inputLayoutStoreName.visibility = if (radioSeller.isChecked) View.VISIBLE else View.GONE
        if (!radioSeller.isChecked) inputLayoutStoreName.error = null
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
        inputLayoutName.error = null
        inputLayoutEmail.error = null
        inputLayoutPassword.error = null
        inputLayoutConfirmPassword.error = null
        inputLayoutStoreName.error = null
    }
}
