package com.clubcanino.app.buyer.data

import android.content.Context

/**
 * Local session for the active buyer account (device-only; not a server session).
 */
object BuyerSession {

    private const val PREFS_NAME = "club_canino_session"
    private const val KEY_BUYER_EMAIL = "current_buyer_email"
    private const val KEY_ROLE = "current_role"

    const val DEMO_BUYER_EMAIL = "ana.gomez@clubcanino.demo"

    fun setBuyerEmail(context: Context, email: String) {
        val normalized = normalizeEmail(email)
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_BUYER_EMAIL, normalized)
            .apply()
    }

    fun start(context: Context, email: String, role: String) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_BUYER_EMAIL, normalizeEmail(email))
            .putString(KEY_ROLE, role)
            .commit()
    }

    fun currentRole(context: Context): String? {
        return context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_ROLE, null)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }

    fun clear(context: Context) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_BUYER_EMAIL)
            .remove(KEY_ROLE)
            .commit()
    }

    fun currentEmail(context: Context): String? {
        return context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_BUYER_EMAIL, null)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }

    fun currentBuyerId(context: Context): String = currentEmail(context) ?: DEMO_BUYER_EMAIL

    fun normalizeEmail(email: String): String = email.trim().lowercase()
}
