package com.clubcanino.app.seller.data

data class ShopOrderLine(
    val productId: String,
    val productName: String,
    val quantity: Int,
    val unitPrice: Double
) {
    val lineTotal: Double
        get() = unitPrice * quantity
}

data class ShopOrder(
    val id: String,
    val buyerName: String,
    val dateLabel: String,
    var status: String,
    val lines: List<ShopOrderLine>
) {
    val itemCount: Int
        get() = lines.sumOf { it.quantity }

    val total: Double
        get() = lines.sumOf { it.lineTotal }

    val isHistory: Boolean
        get() = ShopOrderStatus.isHistory(status)
}

object ShopOrderStatus {
    const val PENDING = "Pendiente"
    const val PREPARING = "En preparación"
    const val SHIPPING = "En camino"
    const val DELIVERED = "Entregado"
    const val CANCELLED = "Cancelado"

    val flow = listOf(PENDING, PREPARING, SHIPPING, DELIVERED)

    fun isHistory(status: String): Boolean = status == DELIVERED || status == CANCELLED

    fun next(status: String): String? {
        val index = flow.indexOf(status)
        if (index < 0 || index >= flow.lastIndex) return null
        return flow[index + 1]
    }
}
