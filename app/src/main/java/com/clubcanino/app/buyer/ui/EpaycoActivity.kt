package com.clubcanino.app.buyer.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.clubcanino.app.R
import org.json.JSONObject

/**
 * Abre el checkout de ePayco con una sesión creada por el backend.
 * Una respuesta incompleta, un error o una referencia vacía no aprueban el pago.
 */
class EpaycoActivity : AppCompatActivity() {

    private var finished = false
    @Volatile
    private var responseReceived = false
    private val timeout = Handler(Looper.getMainLooper())

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val orderId = intent.getStringExtra(EXTRA_ORDER_ID).orEmpty()
        if (orderId.isBlank() || !ClubBackend.isConfigured()) {
            Toast.makeText(this, R.string.payment_backend_missing, Toast.LENGTH_LONG).show()
            close(false, "")
            return
        }
        val webView = WebView(this)
        setContentView(webView)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.javaScriptCanOpenWindowsAutomatically = true
        val cookies = CookieManager.getInstance()
        cookies.setAcceptCookie(true)
        cookies.setAcceptThirdPartyCookies(webView, true)
        webView.webViewClient = object : WebViewClient() {
            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError
            ) {
                if (request.isForMainFrame) runIfOpen { close(false, "") }
            }
        }
        webView.addJavascriptInterface(Bridge(orderId), "ClubCanino")
        armTimeout(SESSION_WAIT_MS)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                close(false, "")
            }
        })
        ClubBackend.createSession(this, orderId) { session, error ->
            if (isFinishing || isDestroyed) return@createSession
            if (session == null || session.sessionId.isBlank()) {
                Toast.makeText(this, messageForCheckoutError(error), Toast.LENGTH_LONG).show()
                close(false, "", error.ifBlank { "epayco" })
            } else if (!session.test) {
                Toast.makeText(this, R.string.payment_epayco_test_only, Toast.LENGTH_LONG).show()
                close(false, "")
            } else {
                armTimeout(CHECKOUT_WAIT_MS)
                webView.loadDataWithBaseURL(
                    "https://checkout.epayco.co/",
                    checkoutPage(session.sessionId),
                    "text/html",
                    "UTF-8",
                    null
                )
            }
        }
    }

    private fun armTimeout(delayMs: Long) {
        timeout.removeCallbacksAndMessages(null)
        timeout.postDelayed({ close(false, "") }, delayMs)
    }

    override fun onDestroy() {
        timeout.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun messageForCheckoutError(error: String): Int = when (error) {
        "backend_not_configured" -> R.string.payment_epayco_error
        "network", "unauthorized" -> R.string.payment_order_network
        else -> R.string.payment_epayco_error
    }

    private fun close(approved: Boolean, reference: String, error: String = "") {
        if (finished) return
        finished = true
        timeout.removeCallbacksAndMessages(null)
        if (approved && reference.isNotBlank()) {
            setResult(RESULT_OK, Intent().putExtra(EXTRA_ACCEPTED, true).putExtra(EXTRA_REF, reference))
        } else {
            setResult(RESULT_CANCELED, Intent().putExtra(EXTRA_ERROR, error))
        }
        finish()
    }

    private inner class Bridge(private val orderId: String) {
        @JavascriptInterface
        fun onCheckoutResponse(payload: String) {
            responseReceived = true
            val reference = referenceOf(payload)
            if (reference.isBlank()) {
                runIfOpen { close(false, "") }
                return
            }
            ClubBackend.verifyPayment(this@EpaycoActivity, orderId, reference) { verification ->
                runIfOpen { close(verification.approved, verification.reference) }
            }
        }

        @JavascriptInterface
        fun onCheckoutFailed() {
            runIfOpen { close(false, "") }
        }

        @JavascriptInterface
        fun onCheckoutClosed() {
            timeout.postDelayed({
                if (!responseReceived) runIfOpen { close(false, "") }
            }, 1500)
        }
    }

    private fun runIfOpen(block: () -> Unit) {
        runOnUiThread {
            if (!isFinishing && !isDestroyed) block()
        }
    }

    companion object {
        const val EXTRA_ORDER_ID = "orderId"
        const val EXTRA_ACCEPTED = "accepted"
        const val EXTRA_REF = "reference"
        const val EXTRA_ERROR = "error"

        private const val SESSION_WAIT_MS = 45_000L
        private const val CHECKOUT_WAIT_MS = 8 * 60_000L

        fun createIntent(context: Context, orderId: String): Intent =
            Intent(context, EpaycoActivity::class.java).putExtra(EXTRA_ORDER_ID, orderId)

        private fun referenceOf(payload: String): String {
            return try {
                val json = JSONObject(payload)
                textReference(json) ?: textReference(json.optJSONObject("data")) ?: ""
            } catch (_: Exception) {
                ""
            }
        }

        private fun textReference(json: JSONObject?): String? {
            if (json == null) return null
            return listOf("x_ref_payco", "ref_payco", "reference")
                .firstNotNullOfOrNull { key -> json.optString(key).takeIf { it.isNotBlank() } }
        }

        private fun jsString(value: String): String {
            return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "")
                .replace("\n", "")
                .replace("</", "<\\/")
        }

        private fun checkoutPage(sessionId: String): String = """
            <!DOCTYPE html>
            <html lang="es">
            <head>
              <meta charset="UTF-8" />
              <meta name="viewport" content="width=device-width, initial-scale=1" />
              <script src="https://checkout.epayco.co/checkout-v2.js"></script>
            </head>
            <body>
              <script>
                const checkout = ePayco.checkout.configure({
                  sessionId: "${jsString(sessionId)}",
                  type: "onpage",
                  test: true
                });
                checkout.setHooks({
                  onResponse: (response) => ClubCanino.onCheckoutResponse(JSON.stringify(response || {})),
                  onErrors: () => ClubCanino.onCheckoutFailed(),
                  onClosed: () => ClubCanino.onCheckoutClosed()
                });
                checkout.open();
              </script>
            </body>
            </html>
        """.trimIndent()
    }
}
