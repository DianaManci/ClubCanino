package com.clubcanino.app.seller.data

import android.content.Context
import com.clubcanino.app.admin.data.ShopUserStore
import com.clubcanino.app.buyer.data.BuyerDemoData
import com.clubcanino.app.buyer.data.BuyerSession

/**
 * Orders shared by the buyer checkout, the seller and the admin report.
 * The list is stored on the device and is not cleared when the user leaves the session.
 */
object ShopOrders {

    private val ordersInternal = mutableListOf<ShopOrder>()
    private var loaded = false
    private var appContext: Context? = null

    fun bind(context: Context) {
        appContext = context.applicationContext
        ensureLoaded()
    }

    fun orders(): List<ShopOrder> {
        ensureLoaded()
        return ordersInternal.toList()
    }

    fun orderById(id: String): ShopOrder? {
        ensureLoaded()
        return ordersInternal.find { it.id == id }
    }

    fun activeOrders(): List<ShopOrder> {
        ensureLoaded()
        return ordersInternal.filter { !it.isHistory }
    }

    fun historyOrders(): List<ShopOrder> {
        ensureLoaded()
        return ordersInternal.filter { it.isHistory }
    }

    fun byStatus(status: String?): List<ShopOrder> {
        ensureLoaded()
        if (status.isNullOrBlank() || status == FILTER_ALL) return ordersInternal.toList()
        return ordersInternal.filter { it.status == status }
    }

    fun deliveredRevenue(): Double {
        ensureLoaded()
        return ordersInternal
            .filter { it.status == ShopOrderStatus.DELIVERED }
            .sumOf { it.total }
    }

    fun countByStatus(status: String): Int {
        ensureLoaded()
        return ordersInternal.count { it.status == status }
    }

    fun forBuyer(email: String): List<ShopOrder> {
        ensureLoaded()
        val key = BuyerSession.normalizeEmail(email)
        if (key.isEmpty()) return emptyList()
        return ordersInternal.filter { it.buyerEmail.equals(key, ignoreCase = true) }
    }

    fun recent(limit: Int): List<ShopOrder> {
        ensureLoaded()
        val active = ordersInternal.filter { !it.isHistory }
        val history = ordersInternal.filter { it.isHistory }
        return (active + history).take(limit)
    }

    fun advance(id: String): Boolean {
        ensureLoaded()
        val order = ordersInternal.find { it.id == id } ?: return false
        val next = ShopOrderStatus.next(order.status) ?: return false
        order.status = next
        persist()
        return true
    }

    fun cancel(id: String): Boolean {
        ensureLoaded()
        val order = ordersInternal.find { it.id == id } ?: return false
        if (order.isHistory) return false
        order.status = ShopOrderStatus.CANCELLED
        persist()
        return true
    }

    /**
     * Registers a simulated purchase at the front of the shared list.
     * The initial status is pending, so the seller sees it under orders to manage
     * and the admin report includes it in the full list and the pending filter.
     */
    fun place(
        buyerName: String,
        lines: List<ShopOrderLine>,
        dateLabel: String,
        buyerEmail: String
    ): ShopOrder? {
        ensureLoaded()
        if (buyerName.isBlank() || buyerEmail.isBlank() || lines.isEmpty()) return null
        var nextNumber = ordersInternal
            .mapNotNull { it.id.removePrefix("ORD-").toIntOrNull() }
            .maxOrNull()
            ?.plus(1)
            ?: 3000
        while (ordersInternal.any { it.id == "ORD-$nextNumber" }) {
            nextNumber++
        }
        val order = ShopOrder(
            id = "ORD-$nextNumber",
            buyerName = buyerName.trim(),
            dateLabel = dateLabel,
            status = ShopOrderStatus.PENDING,
            lines = lines,
            buyerEmail = BuyerSession.normalizeEmail(buyerEmail)
        )
        if (ordersInternal.any { it.id == order.id }) return ordersInternal.first { it.id == order.id }
        ordersInternal.add(0, order)
        persist()
        return order
    }

    private fun ensureLoaded() {
        if (loaded) return
        val context = appContext ?: return
        loaded = true
        val store = ShopOrderStore(context)
        if (store.hasSavedOrders()) {
            ordersInternal.clear()
            ordersInternal.addAll(store.load())
            var addedSeed = false
            demoOrders().forEach { seed ->
                if (ordersInternal.none { it.id == seed.id }) {
                    ordersInternal.add(seed)
                    addedSeed = true
                }
            }
            if (addedSeed) persist()
        } else {
            ordersInternal.clear()
            ordersInternal.addAll(demoOrders())
            persist()
        }
        repairIncompleteLines()
        linkPurchasesMissingEmail()
    }

    /**
     * Purchases saved before the buyer email existed keep their name.
     * Demo orders stay without an email, so they are not listed as a buyer's own purchases.
     */
    private fun linkPurchasesMissingEmail() {
        val context = appContext ?: return
        ShopUserStore.bind(context)
        val buyers = ShopUserStore.users().filter { it.role == ShopUserStore.ROLE_BUYER }
        var changed = false
        val linked = ordersInternal.map { order ->
            if (order.buyerEmail.isNotBlank() || order.id in DEMO_ORDER_IDS) return@map order
            val matches = buyers.filter { it.fullName.equals(order.buyerName, ignoreCase = true) }
            if (matches.size != 1) return@map order
            changed = true
            order.copy(buyerEmail = BuyerSession.normalizeEmail(matches.first().email))
        }
        if (!changed) return
        ordersInternal.clear()
        ordersInternal.addAll(linked)
        persist()
    }

    private fun persist() {
        val context = appContext ?: return
        ShopOrderStore(context).save(ordersInternal.toList())
    }

    private fun demoOrders(): List<ShopOrder> = listOf(
        order(
            id = "ORD-2048",
            buyer = "Ana Gómez",
            date = "31 oct 2026",
            status = ShopOrderStatus.PENDING,
            lines = listOf(line("p1", 1), line("p3", 2))
        ),
        order(
            id = "ORD-2044",
            buyer = "Luis Herrera",
            date = "29 oct 2026",
            status = ShopOrderStatus.PREPARING,
            lines = listOf(line("p5", 3))
        ),
        order(
            id = "ORD-2039",
            buyer = "María Peña",
            date = "26 oct 2026",
            status = ShopOrderStatus.SHIPPING,
            lines = listOf(line("p7", 1), line("p2", 1))
        ),
        order(
            id = "ORD-2010",
            buyer = "Ana Gómez",
            date = "18 oct 2026",
            status = ShopOrderStatus.DELIVERED,
            lines = listOf(line("p4", 1), line("p6", 1))
        ),
        order(
            id = "ORD-1994",
            buyer = "Camilo Díaz",
            date = "05 oct 2026",
            status = ShopOrderStatus.DELIVERED,
            lines = listOf(line("p10", 2))
        ),
        order(
            id = "ORD-1977",
            buyer = "Laura Soto",
            date = "22 sep 2026",
            status = ShopOrderStatus.CANCELLED,
            lines = listOf(line("p9", 1))
        )
    )

    private fun line(productId: String, quantity: Int): ShopOrderLine {
        val product = BuyerDemoData.products.find { it.id.equals(productId, ignoreCase = true) }
        return ShopOrderLine(
            productId = productId,
            productName = product?.name ?: productId,
            quantity = quantity,
            unitPrice = product?.price ?: 0.0
        )
    }

    /**
     * Fills a stored line from the catalog only when the name is still the product
     * code or the price was never saved. Lines that already have a name and a price stay as they are.
     */
    private fun repairIncompleteLines() {
        val context = appContext ?: return
        ShopCatalog.bind(context)
        var changed = false
        val repaired = ordersInternal.map { order ->
            val lines = order.lines.map { stored ->
                val nameMissing = stored.productName.isBlank() ||
                    stored.productName.equals(stored.productId, ignoreCase = true)
                val priceMissing = stored.unitPrice == 0.0
                if (!nameMissing && !priceMissing) return@map stored
                val known = knownProduct(stored.productId) ?: return@map stored
                val name = if (nameMissing) known.first else stored.productName
                val price = if (priceMissing && known.second > 0.0) known.second else stored.unitPrice
                if (name == stored.productName && price == stored.unitPrice) return@map stored
                changed = true
                stored.copy(productName = name, unitPrice = price)
            }
            order.copy(lines = lines)
        }
        if (!changed) return
        ordersInternal.clear()
        ordersInternal.addAll(repaired)
        persist()
    }

    private fun knownProduct(productId: String): Pair<String, Double>? {
        val shop = ShopCatalog.products().find { it.id.equals(productId, ignoreCase = true) }
        if (shop != null && shop.name.isNotBlank() && shop.price > 0.0) {
            return shop.name to shop.price
        }
        val demo = BuyerDemoData.products.find { it.id.equals(productId, ignoreCase = true) }
        if (demo != null && demo.name.isNotBlank() && demo.price > 0.0) {
            return demo.name to demo.price
        }
        return null
    }

    private fun order(
        id: String,
        buyer: String,
        date: String,
        status: String,
        lines: List<ShopOrderLine>
    ): ShopOrder = ShopOrder(
        id = id,
        buyerName = buyer,
        dateLabel = date,
        status = status,
        lines = lines
    )

    const val FILTER_ALL = "Todos"

    private val DEMO_ORDER_IDS = setOf(
        "ORD-2048",
        "ORD-2044",
        "ORD-2039",
        "ORD-2010",
        "ORD-1994",
        "ORD-1977"
    )
}
