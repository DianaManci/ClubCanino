package com.clubcanino.app.auth

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.clubcanino.app.R
import com.clubcanino.app.admin.data.ShopUserStore
import com.clubcanino.app.buyer.data.BuyerSession
import com.clubcanino.app.navigation.RoleNavigator
import com.google.android.material.button.MaterialButton

/** Pantalla inicial. Si Firebase conserva un usuario, abre su panel con el rol guardado en Firestore. */
class SplashActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private var navigated = false

    private val autoContinue = Runnable { goToLogin() }
    private val restoreTimeout = Runnable { goToLogin() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_splash)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.splashRoot)) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        findViewById<MaterialButton>(R.id.buttonContinue).setOnClickListener {
            goToLogin()
        }

        if (!restoreSession()) {
            handler.postDelayed(autoContinue, AUTO_CONTINUE_DELAY_MS)
        }
    }

    private fun restoreSession(): Boolean {
        val user = FirebaseGate.auth(this)?.currentUser ?: return false
        val db = FirebaseGate.db(this) ?: return false
        handler.postDelayed(restoreTimeout, RESTORE_TIMEOUT_MS)
        db.collection("users").document(user.uid).get()
            .addOnSuccessListener { snapshot ->
                if (navigated) return@addOnSuccessListener
                handler.removeCallbacks(restoreTimeout)
                val role = snapshot.getString("role").orEmpty()
                val email = snapshot.getString("email").orEmpty().ifBlank { user.email.orEmpty() }
                val knownRole = role == ShopUserStore.ROLE_BUYER ||
                    role == ShopUserStore.ROLE_SELLER ||
                    role == ShopUserStore.ROLE_ADMIN
                if (!knownRole || email.isBlank()) {
                    goToLogin()
                    return@addOnSuccessListener
                }
                BuyerSession.start(this, email, role)
                navigated = true
                RoleNavigator.openRoleHome(this, RoleNavigator.roleOf(role))
                finish()
            }
            .addOnFailureListener {
                handler.removeCallbacks(restoreTimeout)
                goToLogin()
            }
        return true
    }

    private fun goToLogin() {
        if (navigated) return
        navigated = true
        handler.removeCallbacks(autoContinue)
        handler.removeCallbacks(restoreTimeout)
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }

    override fun onDestroy() {
        handler.removeCallbacks(autoContinue)
        handler.removeCallbacks(restoreTimeout)
        super.onDestroy()
    }

    companion object {
        private const val AUTO_CONTINUE_DELAY_MS = 2500L
        private const val RESTORE_TIMEOUT_MS = 8_000L
    }
}
