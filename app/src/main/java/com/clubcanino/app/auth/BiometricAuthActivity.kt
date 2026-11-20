package com.clubcanino.app.auth

import android.os.Bundle
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.os.Handler
import android.os.Looper
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.clubcanino.app.R
import com.clubcanino.app.admin.data.ShopUserStore
import com.clubcanino.app.buyer.data.BuyerSession
import com.clubcanino.app.navigation.RoleNavigator
import com.google.android.material.button.MaterialButton
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher

/** Ingreso con huella. Descifra el secreto dentro del callback y deja el inicio de Firebase para después, en segundo plano. */
class BiometricAuthActivity : AppCompatActivity() {

    private lateinit var textStatus: TextView
    private lateinit var buttonAuthenticate: MaterialButton
    private val mainHandler = Handler(Looper.getMainLooper())
    private var attempt = 0
    private var busy = false
    private var pendingTimeout: Runnable? = null

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

    override fun onDestroy() {
        attempt++
        cancelTimeout()
        super.onDestroy()
    }

    private fun updateAvailability() {
        if (biometricStatus() == BiometricManager.BIOMETRIC_SUCCESS) {
            showIdle(R.string.msg_biometric_ready, enabled = true)
        } else {
            showIdle(R.string.msg_biometric_unavailable, enabled = false)
        }
    }

    private fun biometricStatus(): Int {
        return BiometricManager.from(this)
            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
    }

    private fun startBiometricPrompt() {
        if (busy) return
        if (biometricStatus() != BiometricManager.BIOMETRIC_SUCCESS) {
            showIdle(R.string.msg_biometric_unavailable, enabled = false)
            return
        }
        val token = ++attempt
        busy = true
        buttonAuthenticate.isEnabled = false
        scheduleTimeout(token, PREPARE_TIMEOUT_MS, R.string.msg_biometric_timeout)
        BiometricCredentials.enqueue {
            val prepared = BiometricCredentials.prepareUnlock(applicationContext)
            runOnUiThread {
                if (token != attempt || !alive()) return@runOnUiThread
                cancelTimeout()
                when (prepared) {
                    BiometricCredentials.Unlock.Missing,
                    BiometricCredentials.Unlock.Invalid -> {
                        busy = false
                        showIdle(R.string.msg_biometric_needs_login, enabled = true)
                    }
                    BiometricCredentials.Unlock.Error -> {
                        busy = false
                        showIdle(R.string.msg_biometric_error, enabled = true)
                    }
                    is BiometricCredentials.Unlock.Ready -> presentPrompt(prepared.cipher)
                }
            }
        }
    }

    private fun presentPrompt(cipher: Cipher) {
        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    val unlocked = result.cryptoObject?.cipher
                    val read = readSecret(unlocked)
                    window.decorView.post {
                        if (!alive()) return@post
                        when (read) {
                            is SecretRead.Ready -> signInWith(read.email, read.password)
                            SecretRead.Invalid -> {
                                val token = ++attempt
                                giveUpStoredSecret(token)
                            }
                            SecretRead.Retry -> {
                                busy = false
                                BiometricCredentials.markRefreshNeeded(applicationContext)
                                showIdle(R.string.msg_biometric_error, enabled = true)
                            }
                        }
                    }
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    window.decorView.post {
                        if (!alive()) return@post
                        busy = false
                        val message = when (errorCode) {
                            BiometricPrompt.ERROR_USER_CANCELED,
                            BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                            BiometricPrompt.ERROR_CANCELED -> R.string.msg_biometric_cancelled
                            BiometricPrompt.ERROR_LOCKOUT,
                            BiometricPrompt.ERROR_LOCKOUT_PERMANENT -> R.string.msg_biometric_lockout
                            else -> R.string.msg_biometric_error
                        }
                        showIdle(message, enabled = true)
                    }
                }
            }
        )
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.biometric_prompt_title))
            .setSubtitle(getString(R.string.biometric_prompt_subtitle))
            .setNegativeButtonText(getString(R.string.biometric_prompt_negative))
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build()
        try {
            prompt.authenticate(promptInfo, BiometricPrompt.CryptoObject(cipher))
        } catch (_: Exception) {
            busy = false
            showIdle(R.string.msg_biometric_error, enabled = true)
        }
    }

    private fun readSecret(cipher: Cipher?): SecretRead {
        if (cipher == null) return SecretRead.Retry
        return try {
            val saved = BiometricCredentials.decrypt(applicationContext, cipher)
            if (saved == null) SecretRead.Invalid else SecretRead.Ready(saved.first, saved.second)
        } catch (_: KeyPermanentlyInvalidatedException) {
            SecretRead.Invalid
        } catch (_: AEADBadTagException) {
            SecretRead.Invalid
        } catch (_: Exception) {
            SecretRead.Retry
        }
    }

    private fun signInWith(email: String, password: String) {
        val token = ++attempt
        busy = true
        textStatus.setText(R.string.msg_biometric_signing_in)
        buttonAuthenticate.isEnabled = false
        scheduleTimeout(token, SIGN_IN_TIMEOUT_MS, R.string.msg_biometric_timeout)
        BiometricCredentials.enqueue {
            ShopUserStore.signIn(applicationContext, email, password) { account ->
                runOnUiThread {
                    if (token != attempt || !alive()) return@runOnUiThread
                    cancelTimeout()
                    busy = false
                    if (account == null) {
                        BiometricCredentials.markRefreshNeeded(applicationContext)
                        showIdle(R.string.msg_biometric_login_failed, enabled = true)
                        return@runOnUiThread
                    }
                    BiometricCredentials.clearRefresh(applicationContext)
                    BuyerSession.start(this, account.email, account.role)
                    textStatus.setText(R.string.msg_biometric_success)
                    RoleNavigator.openRoleHome(this, RoleNavigator.roleOf(account.role))
                    finish()
                }
            }
        }
    }

    private fun scheduleTimeout(token: Int, delayMs: Long, message: Int) {
        cancelTimeout()
        val runnable = Runnable {
            if (token != attempt) return@Runnable
            attempt++
            busy = false
            showIdle(message, enabled = true)
        }
        pendingTimeout = runnable
        mainHandler.postDelayed(runnable, delayMs)
    }

    private fun cancelTimeout() {
        pendingTimeout?.let { mainHandler.removeCallbacks(it) }
        pendingTimeout = null
    }

    private fun giveUpStoredSecret(token: Int) {
        if (token != attempt || !alive()) return
        cancelTimeout()
        busy = false
        BiometricCredentials.clear(applicationContext)
        showIdle(R.string.msg_biometric_needs_login, enabled = true)
    }

    private fun showIdle(message: Int, enabled: Boolean) {
        textStatus.setText(message)
        buttonAuthenticate.isEnabled = enabled
    }

    private fun alive(): Boolean = !isFinishing && !isDestroyed

    private sealed class SecretRead {
        data class Ready(val email: String, val password: String) : SecretRead()
        data object Invalid : SecretRead()
        data object Retry : SecretRead()
    }

    companion object {
        private const val PREPARE_TIMEOUT_MS = 8_000L
        private const val SIGN_IN_TIMEOUT_MS = 25_000L
    }
}
