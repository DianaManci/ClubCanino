package com.clubcanino.app.auth

import android.util.Patterns

object AuthValidator {

    fun isBlank(value: String?): Boolean = value.isNullOrBlank()

    fun isValidEmail(email: String): Boolean =
        email.isNotBlank() && Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()

    fun isValidPassword(password: String): Boolean = password.length >= 6

    fun passwordsMatch(password: String, confirmation: String): Boolean =
        password == confirmation
}
