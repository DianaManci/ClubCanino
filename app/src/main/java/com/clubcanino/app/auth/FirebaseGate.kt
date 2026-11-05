package com.clubcanino.app.auth

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage

object FirebaseGate {

    fun isReady(context: Context): Boolean {
        return try {
            val appContext = context.applicationContext
            if (FirebaseApp.getApps(appContext).isEmpty()) {
                FirebaseApp.initializeApp(appContext)
            }
            FirebaseApp.getApps(appContext).isNotEmpty()
        } catch (_: Exception) {
            false
        }
    }

    fun auth(context: Context): FirebaseAuth? =
        if (isReady(context)) FirebaseAuth.getInstance() else null

    fun db(context: Context): FirebaseFirestore? =
        if (isReady(context)) FirebaseFirestore.getInstance() else null

    fun storage(context: Context): FirebaseStorage? =
        if (isReady(context)) FirebaseStorage.getInstance() else null

    fun accountAuth(context: Context): FirebaseAuth? {
        if (!isReady(context)) return null
        val appContext = context.applicationContext
        val existing = FirebaseApp.getApps(appContext).firstOrNull { it.name == ACCOUNT_APP }
        val app = existing ?: FirebaseApp.initializeApp(
            appContext,
            FirebaseApp.getInstance().options,
            ACCOUNT_APP
        )
        return FirebaseAuth.getInstance(app)
    }

    private const val ACCOUNT_APP = "clubcanino-accounts"
}
