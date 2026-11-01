package com.clubcanino.app.buyer.data

import android.content.Context

/**
 * Local session for the active buyer account (device-only; not a server session).
 */
object BuyerSession {

    private const val PREFS_NAME = "club_canino_session"
    private const val KEY_BUYER_EMAIL = "current_buyer_email"

    const val DEMO_BUYER_EMAIL = "ana.gomez@clubcanino.demo"

    fun setBuyerEmail(context: Context, email: String) {
        val normalized = normalizeEmail(email)
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_BUYER_EMAIL, normalized)
            .apply()
    }

    fun currentBuyerId(context: Context): String {
        val stored = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_BUYER_EMAIL, null)
            ?.trim()
            .orEmpty()
        return if (stored.isEmpty()) DEMO_BUYER_EMAIL else stored
    }

    fun normalizeEmail(email: String): String = email.trim().lowercase()
}
