package com.clubcanino.app.buyer.data

import android.content.Context
import com.clubcanino.app.seller.data.ShopOrderLine

/**
 * Identidad de un intento de compra. El mismo carrito, dirección y método
 * reutilizan el pedido pendiente. El identificador se guarda en el dispositivo
 * y solo se olvida cuando el pago queda confirmado.
 */
object PurchaseAttempt {

    data class Attempt(val requestId: String, val signature: String)

    interface AttemptMemory {
        fun requestIdFor(signature: String): String?
        fun save(attempt: Attempt)
        fun forget(signature: String)
        fun checkoutSignature(): String?
    }

    /**
     * SharedPreferences del dispositivo. Sobrevive a recrear la actividad
     * y a cerrar el proceso. Cada firma conserva su propio identificador.
     */
    class PreferenceAttemptMemory(context: Context) : AttemptMemory {
        private val prefs = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        override fun requestIdFor(signature: String): String? {
            if (signature.isBlank()) return null
            return prefs.getString(ENTRY_PREFIX + signature, null)?.takeIf { it.isNotBlank() }
        }

        override fun save(attempt: Attempt) {
            if (attempt.signature.isBlank() || attempt.requestId.isBlank()) return
            prefs.edit()
                .putString(ENTRY_PREFIX + attempt.signature, attempt.requestId)
                .putString(CHECKOUT_KEY, attempt.signature)
                .commit()
        }

        override fun forget(signature: String) {
            if (signature.isBlank()) return
            val editor = prefs.edit().remove(ENTRY_PREFIX + signature)
            if (prefs.getString(CHECKOUT_KEY, null) == signature) editor.remove(CHECKOUT_KEY)
            editor.commit()
        }

        override fun checkoutSignature(): String? =
            prefs.getString(CHECKOUT_KEY, null)?.takeIf { it.isNotBlank() }

        private companion object {
            const val PREFS = "club_canino_purchase_attempt"
            const val ENTRY_PREFIX = "id\u0000"
            const val CHECKOUT_KEY = "checkout"
        }
    }

    fun signature(lines: List<ShopOrderLine>, address: String, method: String): String {
        val body = lines
            .sortedWith(compareBy({ it.productId }, { it.sellerId }))
            .joinToString(";") { line ->
                "${line.productId}|${line.quantity}|${line.unitPrice}|${line.lineTotal}|${line.sellerId}"
            }
        return "$body#${address.trim()}#${method.trim()}"
    }

    fun nextAttempt(current: Attempt?, signature: String, newId: String): Attempt {
        if (current != null && current.requestId.isNotBlank() && current.signature == signature) {
            return current
        }
        return Attempt(newId, signature)
    }

    /**
     * Recupera el identificador guardado para esta firma. Si no existe, continúa
     * el intento que aún está en memoria o abre uno nuevo y lo guarda.
     */
    fun resume(memory: AttemptMemory, current: Attempt?, signature: String, newId: String): Attempt {
        val savedId = memory.requestIdFor(signature)?.takeIf { it.isNotBlank() }
        val attempt = if (savedId != null) {
            Attempt(savedId, signature)
        } else {
            nextAttempt(current, signature, newId)
        }
        memory.save(attempt)
        return attempt
    }

    fun orderTotals(lines: List<ShopOrderLine>): Pair<Double, Double> {
        val total = lines.sumOf { it.lineTotal }
        return total to total
    }

    fun checkoutFailure(codeName: String?): String = when (codeName) {
        "PERMISSION_DENIED" -> "permission"
        "FAILED_PRECONDITION" -> "index"
        "UNAVAILABLE", "DEADLINE_EXCEEDED" -> "network"
        else -> "data"
    }
}
