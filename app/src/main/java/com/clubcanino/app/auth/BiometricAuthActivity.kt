package com.clubcanino.app.auth

import android.os.Bundle
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.clubcanino.app.R
import com.google.android.material.button.MaterialButton

class BiometricAuthActivity : AppCompatActivity() {

    private lateinit var textStatus: TextView
    private lateinit var buttonAuthenticate: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_biometric)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.biometricRoot)) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        textStatus = findViewById(R.id.textStatus)
        buttonAuthenticate = findViewById(R.id.buttonAuthenticate)

        findViewById<MaterialButton>(R.id.buttonBackToLogin).setOnClickListener {
            finish()
        }

        updateAvailability()
        buttonAuthenticate.setOnClickListener { startBiometricPrompt() }
    }

    private fun updateAvailability() {
        when (biometricStatus()) {
            BiometricManager.BIOMETRIC_SUCCESS -> {
                textStatus.setText(R.string.msg_biometric_ready)
                buttonAuthenticate.isEnabled = true
            }
            else -> {
                textStatus.setText(R.string.msg_biometric_unavailable)
                buttonAuthenticate.isEnabled = false
            }
        }
    }

    private fun biometricStatus(): Int {
        val biometricManager = BiometricManager.from(this)
        return biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK)
    }

    private fun startBiometricPrompt() {
        if (biometricStatus() != BiometricManager.BIOMETRIC_SUCCESS) {
            textStatus.setText(R.string.msg_biometric_unavailable)
            buttonAuthenticate.isEnabled = false
            return
        }

        val executor = ContextCompat.getMainExecutor(this)
        val prompt = BiometricPrompt(
            this,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    textStatus.setText(R.string.msg_biometric_success_local)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    if (errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                        errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                        errorCode == BiometricPrompt.ERROR_CANCELED
                    ) {
                        textStatus.setText(R.string.msg_biometric_cancelled)
                    } else {
                        textStatus.text = getString(R.string.msg_biometric_error)
                    }
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    textStatus.setText(R.string.msg_biometric_error)
                }
            }
        )

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.biometric_prompt_title))
            .setSubtitle(getString(R.string.biometric_prompt_subtitle))
            .setNegativeButtonText(getString(R.string.biometric_prompt_negative))
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
            .build()

        prompt.authenticate(promptInfo)
    }
}
