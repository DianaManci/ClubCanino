package com.clubcanino.app.auth

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Stores a salted SHA-256 digest. The password itself is never written to disk.
 */
object PasswordHasher {

    fun create(password: String): StoredPassword {
        val salt = ByteArray(SALT_BYTES)
        SecureRandom().nextBytes(salt)
        val saltText = Base64.encodeToString(salt, Base64.NO_WRAP)
        return StoredPassword(saltText, digest(password, saltText))
    }

    fun matches(password: String, salt: String, expectedHash: String): Boolean {
        val actual = digest(password, salt)
        return MessageDigest.isEqual(
            actual.toByteArray(Charsets.UTF_8),
            expectedHash.toByteArray(Charsets.UTF_8)
        )
    }

    private fun digest(password: String, salt: String): String {
        val message = MessageDigest.getInstance("SHA-256")
        message.update(Base64.decode(salt, Base64.NO_WRAP))
        val hash = message.digest(password.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(hash, Base64.NO_WRAP)
    }

    data class StoredPassword(val salt: String, val hash: String)

    private const val SALT_BYTES = 16
}
