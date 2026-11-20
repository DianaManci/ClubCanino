package com.clubcanino.app.auth

/**
 * Empaqueta correo y contraseña antes de cifrarlos. La longitud va al inicio
 * para que una contraseña con cualquier carácter se recupere igual.
 */
internal object BiometricSecretFormat {

    fun pack(email: String, password: String): ByteArray? {
        val normalized = email.trim()
        if (normalized.isEmpty() || password.isEmpty()) return null
        val emailBytes = normalized.toByteArray(Charsets.UTF_8)
        val passwordBytes = password.toByteArray(Charsets.UTF_8)
        if (emailBytes.isEmpty() || emailBytes.size > 0xFFFF || passwordBytes.isEmpty()) return null
        val out = ByteArray(2 + emailBytes.size + passwordBytes.size)
        out[0] = (emailBytes.size ushr 8).toByte()
        out[1] = emailBytes.size.toByte()
        System.arraycopy(emailBytes, 0, out, 2, emailBytes.size)
        System.arraycopy(passwordBytes, 0, out, 2 + emailBytes.size, passwordBytes.size)
        return out
    }

    fun unpack(payload: ByteArray): Pair<String, String>? {
        if (payload.size < 3) return null
        val emailLength = ((payload[0].toInt() and 0xFF) shl 8) or (payload[1].toInt() and 0xFF)
        if (emailLength <= 0 || payload.size < 2 + emailLength + 1) return null
        val email = payload.copyOfRange(2, 2 + emailLength).toString(Charsets.UTF_8).trim()
        val password = payload.copyOfRange(2 + emailLength, payload.size).toString(Charsets.UTF_8)
        if (email.isEmpty() || password.isEmpty()) return null
        return email to password
    }
}
