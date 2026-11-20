package com.clubcanino.app.buyer.ui

import android.content.Context
import com.clubcanino.app.BuildConfig
import com.clubcanino.app.auth.FirebaseGate
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Habla con el backend de Club Canino. La aplicación no conoce la llave privada
 * de ePayco ni la cuenta de servicio. Si el backend no está configurado, no hay pago.
 */
object ClubBackend {

    data class Session(val sessionId: String, val test: Boolean)
    data class Verification(val approved: Boolean, val reference: String)

    fun isConfigured(): Boolean {
        val url = baseUrl()
        return url.startsWith("https://") ||
            url.startsWith("http://10.0.2.2") ||
            url.startsWith("http://127.0.0.1") ||
            url.startsWith("http://localhost")
    }

    fun createSession(context: Context, orderId: String, onResult: (Session?, String) -> Unit) {
        authorized(context) { token ->
            if (token == null) {
                onResult(null, "unauthorized")
                return@authorized
            }
            post("/v1/payments/session", token, JSONObject().put("orderId", orderId)) { code, body ->
                if (code !in 200..299 || body == null) {
                    val error = body?.optString("error").orEmpty().ifBlank {
                        if (code == 0) "network" else "epayco"
                    }
                    onResult(null, error)
                    return@post
                }
                val sessionId = body.optString("sessionId")
                if (sessionId.isBlank()) onResult(null, "epayco")
                else onResult(Session(sessionId, body.optBoolean("test", true)), "")
            }
        }
    }

    fun verifyPayment(
        context: Context,
        orderId: String,
        reference: String,
        onResult: (Verification) -> Unit
    ) {
        if (reference.isBlank()) {
            onResult(Verification(false, ""))
            return
        }
        authorized(context) { token ->
            if (token == null) {
                onResult(Verification(false, ""))
                return@authorized
            }
            val payload = JSONObject().put("orderId", orderId).put("reference", reference)
            post("/v1/payments/verify", token, payload) { _, body ->
                val approved = body?.optBoolean("approved", false) == true
                val ref = body?.optString("reference").orEmpty()
                onResult(Verification(approved && ref.isNotBlank(), ref))
            }
        }
    }

    fun createAccount(
        context: Context,
        fullName: String,
        email: String,
        phone: String,
        role: String,
        password: String,
        onResult: (Int, String, String) -> Unit
    ) {
        if (!isConfigured()) {
            onResult(0, "not_configured", "")
            return
        }
        val payload = JSONObject()
            .put("fullName", fullName)
            .put("email", email)
            .put("phone", phone)
            .put("role", role)
            .put("password", password)
        authorized(context) { token ->
            if (token == null) {
                onResult(0, "unauthorized", "")
                return@authorized
            }
            post("/v1/admin/users/create", token, payload) { code, body ->
                onResult(code, body?.optString("error").orEmpty(), body?.optString("uid").orEmpty())
            }
        }
    }

    fun deleteAccount(context: Context, uid: String, onResult: (Int, String) -> Unit) {
        admin(context, "/v1/admin/users/delete", JSONObject().put("uid", uid), onResult)
    }

    fun updateAccount(
        context: Context,
        uid: String,
        fullName: String,
        email: String,
        phone: String,
        role: String,
        storeName: String,
        password: String,
        onResult: (Int, String) -> Unit
    ) {
        val payload = JSONObject()
            .put("uid", uid)
            .put("fullName", fullName)
            .put("email", email)
            .put("phone", phone)
            .put("role", role)
            .put("storeName", storeName)
            .put("password", password)
        admin(context, "/v1/admin/users/update", payload, onResult)
    }

    private fun admin(context: Context, path: String, payload: JSONObject, onResult: (Int, String) -> Unit) {
        if (!isConfigured()) {
            onResult(0, "not_configured")
            return
        }
        authorized(context) { token ->
            if (token == null) {
                onResult(0, "unauthorized")
                return@authorized
            }
            post(path, token, payload) { code, body ->
                onResult(code, body?.optString("error").orEmpty())
            }
        }
    }

    private fun authorized(context: Context, onToken: (String?) -> Unit) {
        val user = FirebaseGate.auth(context)?.currentUser
        if (!isConfigured() || user == null) {
            onToken(null)
            return
        }
        user.getIdToken(false)
            .addOnSuccessListener { result -> onToken(result.token) }
            .addOnFailureListener { onToken(null) }
    }

    private fun post(path: String, token: String, payload: JSONObject, onResult: (Int, JSONObject?) -> Unit) {
        Thread {
            val result = try {
                val connection = URL(baseUrl() + path).openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.connectTimeout = 20000
                connection.readTimeout = 20000
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("Authorization", "Bearer $token")
                connection.outputStream.use { it.write(payload.toString().toByteArray()) }
                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                val json = if (text.isBlank()) null else JSONObject(text)
                code to json
            } catch (_: Exception) {
                0 to null
            }
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                onResult(result.first, result.second)
            }
        }.start()
    }

    private fun baseUrl(): String = BuildConfig.BACKEND_BASE_URL.trim().trimEnd('/')
}
