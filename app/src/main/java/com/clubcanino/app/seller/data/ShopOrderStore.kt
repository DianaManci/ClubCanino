package com.clubcanino.app.seller.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Device-local order list. Shared by buyer checkout, seller and admin.
 * Logout does not touch this file.
 */
class ShopOrderStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun hasSavedOrders(): Boolean = prefs.contains(KEY_ORDERS)

    fun load(): List<ShopOrder> {
        val raw = prefs.getString(KEY_ORDERS, null) ?: return emptyList()
        return decode(raw)
    }

    fun save(orders: List<ShopOrder>) {
        prefs.edit()
            .putString(KEY_ORDERS, encode(orders))
            .commit()
    }

    private fun encode(orders: List<ShopOrder>): String {
        val array = JSONArray()
        orders.forEach { order ->
            val lines = JSONArray()
            order.lines.forEach { line ->
                lines.put(
                    JSONObject().apply {
                        put(JSON_PRODUCT_ID, line.productId)
                        put(JSON_PRODUCT_NAME, line.productName)
                        put(JSON_QUANTITY, line.quantity)
                        put(JSON_UNIT_PRICE, line.unitPrice)
                    }
                )
            }
            array.put(
                JSONObject().apply {
                    put(JSON_ID, order.id)
                    put(JSON_BUYER, order.buyerName)
                    put(JSON_DATE, order.dateLabel)
                    put(JSON_STATUS, order.status)
                    put(JSON_LINES, lines)
                }
            )
        }
        return array.toString()
    }

    private fun decode(raw: String): List<ShopOrder> {
        return try {
            val array = JSONArray(raw)
            val seen = HashSet<String>()
            buildList {
                for (index in 0 until array.length()) {
                    val obj = array.optJSONObject(index) ?: continue
                    val id = obj.optString(JSON_ID).trim()
                    val buyer = obj.optString(JSON_BUYER).trim()
                    if (id.isEmpty() || buyer.isEmpty() || !seen.add(id)) continue
                    val lines = decodeLines(obj.optJSONArray(JSON_LINES))
                    if (lines.isEmpty()) continue
                    add(
                        ShopOrder(
                            id = id,
                            buyerName = buyer,
                            dateLabel = obj.optString(JSON_DATE).ifBlank { "Fecha no indicada" },
                            status = obj.optString(JSON_STATUS).ifBlank { ShopOrderStatus.PENDING },
                            lines = lines
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun decodeLines(array: JSONArray?): List<ShopOrderLine> {
        if (array == null) return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val obj = array.optJSONObject(index) ?: continue
                val productId = obj.optString(JSON_PRODUCT_ID).trim()
                val productName = obj.optString(JSON_PRODUCT_NAME).trim()
                val quantity = obj.optInt(JSON_QUANTITY, 0)
                if (productId.isEmpty() || productName.isEmpty() || quantity <= 0) continue
                add(
                    ShopOrderLine(
                        productId = productId,
                        productName = productName,
                        quantity = quantity,
                        unitPrice = obj.optDouble(JSON_UNIT_PRICE, 0.0)
                    )
                )
            }
        }
    }

    companion object {
        private const val PREFS_NAME = "club_canino_orders"
        private const val KEY_ORDERS = "shop_orders"
        private const val JSON_ID = "id"
        private const val JSON_BUYER = "buyerName"
        private const val JSON_DATE = "dateLabel"
        private const val JSON_STATUS = "status"
        private const val JSON_LINES = "lines"
        private const val JSON_PRODUCT_ID = "productId"
        private const val JSON_PRODUCT_NAME = "productName"
        private const val JSON_QUANTITY = "quantity"
        private const val JSON_UNIT_PRICE = "unitPrice"
    }
}
