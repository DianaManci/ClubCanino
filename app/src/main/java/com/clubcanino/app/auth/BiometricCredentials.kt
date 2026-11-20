package com.clubcanino.app.auth

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.clubcanino.app.R
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Guarda el correo y la contraseña cifrados con una llave de Android Keystore
 * que solo se puede usar después de una huella fuerte. El cifrado no sustituye
 * a Firebase: la primera vez hace falta entrar con correo y contraseña.
 *
 * La lectura y el cifrado ocurren fuera del callback biométrico. Ahí solo se
 * recibe el [Cipher] ya autorizado, para que el diálogo pueda cerrarse.
 */
object BiometricCredentials {

    sealed class Unlock {
        data class Ready(val cipher: Cipher) : Unlock()
        data object Missing : Unlock()
        data object Invalid : Unlock()
        data object Error : Unlock()
    }

    fun enqueue(block: () -> Unit) {
        cryptoHandler.post(block)
    }

    fun canUseStrongBiometric(context: Context): Boolean {
        return BiometricManager.from(context)
            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
            BiometricManager.BIOMETRIC_SUCCESS
    }

    fun shouldOfferEnroll(context: Context, email: String): Boolean {
        if (!canUseStrongBiometric(context)) return false
        val stored = prefs(context)
        val bound = stored.getString(KEY_EMAIL, null).orEmpty()
        val secret = stored.getString(KEY_SECRET, null)
        if (secret.isNullOrBlank() || bound.isBlank() || stored.getBoolean(KEY_REFRESH, false)) {
            return true
        }
        return !bound.equals(email.trim(), ignoreCase = true)
    }

    fun prepareUnlock(context: Context): Unlock {
        discardLegacy(context)
        val stored = prefs(context)
        val secret = stored.getString(KEY_SECRET, null)
        val iv = stored.getString(KEY_IV, null)
        if (secret.isNullOrBlank() || iv.isNullOrBlank()) return Unlock.Missing
        return try {
            val key = loadKey() ?: return Unlock.Invalid.also { clear(context) }
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                key,
                GCMParameterSpec(GCM_TAG_BITS, Base64.decode(iv, Base64.NO_WRAP))
            )
            Unlock.Ready(cipher)
        } catch (_: KeyPermanentlyInvalidatedException) {
            clear(context)
            Unlock.Invalid
        } catch (_: Exception) {
            Unlock.Error
        }
    }

    fun decrypt(context: Context, cipher: Cipher): Pair<String, String>? {
        val secret = prefs(context).getString(KEY_SECRET, null) ?: return null
        val plain = cipher.doFinal(Base64.decode(secret, Base64.NO_WRAP))
        return BiometricSecretFormat.unpack(plain)
    }

    fun markRefreshNeeded(context: Context) {
        prefs(context).edit().putBoolean(KEY_REFRESH, true).commit()
    }

    fun clearRefresh(context: Context) {
        prefs(context).edit().putBoolean(KEY_REFRESH, false).commit()
    }

    fun clear(context: Context) {
        prefs(context).edit().clear().commit()
        try {
            val keyStore = keyStore()
            if (keyStore.containsAlias(KEY_ALIAS)) keyStore.deleteEntry(KEY_ALIAS)
        } catch (_: Exception) {
            // Si la llave ya no está, el secreto cifrado tampoco se conserva.
        }
    }

    /**
     * Pide la huella para cifrar las credenciales del login con contraseña.
     * Si la persona cancela o el cifrado falla, [onFinished] recibe false y
     * el ingreso con contraseña puede continuar.
     */
    fun enroll(
        activity: FragmentActivity,
        email: String,
        password: String,
        onFinished: (Boolean) -> Unit
    ) {
        var finished = false
        val main = Handler(Looper.getMainLooper())
        fun finish(saved: Boolean) {
            if (finished) return
            finished = true
            if (!activity.isFinishing && !activity.isDestroyed) onFinished(saved)
        }
        val prepareWatch = Runnable { finish(false) }
        main.postDelayed(prepareWatch, PREPARE_TIMEOUT_MS)
        enqueue {
            discardLegacy(activity)
            val cipher = try {
                prepareEncryptCipher()
            } catch (_: Exception) {
                null
            }
            activity.runOnUiThread {
                main.removeCallbacks(prepareWatch)
                if (finished || activity.isFinishing || activity.isDestroyed) return@runOnUiThread
                if (cipher == null) {
                    finish(false)
                    return@runOnUiThread
                }
                val prompt = BiometricPrompt(
                    activity,
                    ContextCompat.getMainExecutor(activity),
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                            val unlocked = result.cryptoObject?.cipher
                            val sealed = if (unlocked == null) null else encrypt(unlocked, email, password)
                            activity.window.decorView.post {
                                if (finished) return@post
                                if (sealed == null) {
                                    finish(false)
                                    return@post
                                }
                                val saveWatch = Runnable { finish(false) }
                                main.postDelayed(saveWatch, PREPARE_TIMEOUT_MS)
                                enqueue {
                                    val saved = persist(activity.applicationContext, sealed, email)
                                    activity.runOnUiThread {
                                        main.removeCallbacks(saveWatch)
                                        finish(saved)
                                    }
                                }
                            }
                        }

                        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                            activity.window.decorView.post { finish(false) }
                        }
                    }
                )
                val info = BiometricPrompt.PromptInfo.Builder()
                    .setTitle(activity.getString(R.string.biometric_enroll_title))
                    .setSubtitle(activity.getString(R.string.biometric_enroll_subtitle))
                    .setNegativeButtonText(activity.getString(R.string.biometric_enroll_negative))
                    .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                    .build()
                try {
                    prompt.authenticate(info, BiometricPrompt.CryptoObject(cipher))
                } catch (_: Exception) {
                    finish(false)
                }
            }
        }
    }

    data class SealedSecret(val secret: String, val iv: String)

    fun encrypt(cipher: Cipher, email: String, password: String): SealedSecret? {
        val payload = BiometricSecretFormat.pack(email, password) ?: return null
        return try {
            val encrypted = cipher.doFinal(payload)
            SealedSecret(
                Base64.encodeToString(encrypted, Base64.NO_WRAP),
                Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
            )
        } catch (_: Exception) {
            null
        }
    }

    fun persist(context: Context, sealed: SealedSecret, email: String): Boolean {
        return prefs(context).edit()
            .putString(KEY_SECRET, sealed.secret)
            .putString(KEY_IV, sealed.iv)
            .putString(KEY_EMAIL, email.trim().lowercase())
            .putBoolean(KEY_REFRESH, false)
            .commit()
    }

    private fun prepareEncryptCipher(): Cipher {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, keyForEncrypt())
        return cipher
    }

    private fun keyForEncrypt(): SecretKey {
        try {
            loadKey()?.let { return it }
        } catch (_: KeyPermanentlyInvalidatedException) {
            try {
                keyStore().deleteEntry(KEY_ALIAS)
            } catch (_: Exception) {
                // La llave invalidada se reemplaza al generarla de nuevo.
            }
        }
        return createKey()
    }

    private fun loadKey(): SecretKey? {
        val keyStore = keyStore()
        if (!keyStore.containsAlias(KEY_ALIAS)) return null
        return keyStore.getKey(KEY_ALIAS, null) as? SecretKey
    }

    private fun createKey(): SecretKey {
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val builder = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
        } else {
            @Suppress("DEPRECATION")
            builder.setUserAuthenticationValidityDurationSeconds(-1)
        }
        generator.init(builder.build())
        return generator.generateKey()
    }

    private fun keyStore(): KeyStore {
        return KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    }

    private fun discardLegacy(context: Context) {
        try {
            context.applicationContext.deleteSharedPreferences(LEGACY_PREFS)
        } catch (_: Exception) {
            // El almacén anterior no estaba ligado a la huella. Si no se puede borrar, no se lee.
        }
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val cryptoThread = android.os.HandlerThread("club-canino-biometric").apply { start() }
    private val cryptoHandler = Handler(cryptoThread.looper)

    private const val PREFS = "club_canino_biometric_gate"
    private const val LEGACY_PREFS = "club_canino_biometric"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "club_canino_biometric_login"
    private const val KEY_SECRET = "secret"
    private const val KEY_IV = "iv"
    private const val KEY_EMAIL = "bound_email"
    private const val KEY_REFRESH = "needs_refresh"
    private const val GCM_TAG_BITS = 128
    private const val PREPARE_TIMEOUT_MS = 8_000L
    private const val TRANSFORMATION =
        "${KeyProperties.KEY_ALGORITHM_AES}/" +
            "${KeyProperties.BLOCK_MODE_GCM}/" +
            KeyProperties.ENCRYPTION_PADDING_NONE
}
