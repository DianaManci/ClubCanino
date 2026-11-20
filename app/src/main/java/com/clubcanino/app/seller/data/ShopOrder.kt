package com.clubcanino.app.seller.data

data class ShopOrderLine(
    val productId: String,
    val productName: String,
    val quantity: Int,
    val unitPrice: Double,
    val sellerId: String = ""
) {
    val lineTotal: Double
        get() = unitPrice * quantity
}

data class ShopOrder(
    val id: String,
    val buyerName: String,
    val dateLabel: String,
    var status: String,
    val lines: List<ShopOrderLine>,
    val buyerEmail: String = "",
    val buyerId: String = "",
    val address: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val paymentMethod: String = "",
    val paymentRef: String = "",
    val paymentStatus: String = ShopOrderStatus.PAYMENT_PENDING,
    val clientRequestId: String = "",
    val createdAt: Long = 0L
) {
    val itemCount: Int
        get() = lines.sumOf { it.quantity }

    val total: Double
        get() = lines.sumOf { it.lineTotal }

    val isPaid: Boolean
        get() = paymentStatus == ShopOrderStatus.PAYMENT_APPROVED

    val displayStatus: String
        get() = if (isPaid || status == ShopOrderStatus.CANCELLED) status else paymentStatus

    val isHistory: Boolean
        get() = ShopOrderStatus.isHistory(status) ||
            paymentStatus == ShopOrderStatus.PAYMENT_REJECTED ||
            paymentStatus == ShopOrderStatus.PAYMENT_CANCELLED

    fun buyerSection(): BuyerOrderSection {
        if (
            status == ShopOrderStatus.CANCELLED ||
            paymentStatus == ShopOrderStatus.PAYMENT_REJECTED ||
            paymentStatus == ShopOrderStatus.PAYMENT_CANCELLED
        ) {
            return BuyerOrderSection.CANCELLED
        }
        if (!isPaid || status == ShopOrderStatus.PENDING) return BuyerOrderSection.PENDING
        if (status == ShopOrderStatus.DELIVERED) return BuyerOrderSection.DELIVERED
        return BuyerOrderSection.IN_PROGRESS
    }
}

enum class BuyerOrderSection {
    PENDING,
    IN_PROGRESS,
    DELIVERED,
    CANCELLED
}

object ShopOrderStatus {
    const val PAYMENT_PENDING = "Pendiente de pago"
    const val PAYMENT_APPROVED = "Aprobado"
    const val PAYMENT_REJECTED = "Rechazado"
    const val PAYMENT_CANCELLED = "Cancelado"
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
