package com.clubcanino.app.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BiometricSecretFormatTest {

    @Test
    fun roundTripKeepsEmailAndPassword() {
        val packed = BiometricSecretFormat.pack("Ana.Gomez@clubcanino.demo", "clave-prueba")
        val unpacked = BiometricSecretFormat.unpack(packed!!)
        assertEquals("Ana.Gomez@clubcanino.demo", unpacked!!.first)
        assertEquals("clave-prueba", unpacked.second)
    }

    @Test
    fun rejectsBlankValues() {
        assertNull(BiometricSecretFormat.pack("   ", "clave-prueba"))
        assertNull(BiometricSecretFormat.pack("ana@clubcanino.demo", ""))
    }

    @Test
    fun passwordKeepsAnyCharacter() {
        val password = "a\u0000b\nc"
        val unpacked = BiometricSecretFormat.unpack(
            BiometricSecretFormat.pack("ana@clubcanino.demo", password)!!
        )
        assertEquals(password, unpacked!!.second)
    }

    @Test
    fun rejectsTruncatedPayload() {
        assertNull(BiometricSecretFormat.unpack(byteArrayOf(0, 5, 1, 2)))
        assertNull(BiometricSecretFormat.unpack(byteArrayOf()))
    }
}
