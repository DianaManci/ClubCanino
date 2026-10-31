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
import com.google.android.material.button.MaterialButton

class SplashActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private var navigated = false

    private val autoContinue = Runnable { goToLogin() }

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

        handler.postDelayed(autoContinue, AUTO_CONTINUE_DELAY_MS)
    }

    private fun goToLogin() {
        if (navigated) return
        navigated = true
        handler.removeCallbacks(autoContinue)
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }

    override fun onDestroy() {
        handler.removeCallbacks(autoContinue)
        super.onDestroy()
    }

    companion object {
        private const val AUTO_CONTINUE_DELAY_MS = 2500L
    }
}
