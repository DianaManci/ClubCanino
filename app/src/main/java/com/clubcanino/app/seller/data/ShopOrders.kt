package com.clubcanino.app.seller.data

import android.content.Context
import com.clubcanino.app.admin.data.ShopUserStore
import com.clubcanino.app.auth.FirebaseGate
import com.clubcanino.app.buyer.data.BuyerDemoData
import com.clubcanino.app.buyer.data.BuyerSession
import com.clubcanino.app.buyer.data.PurchaseAttempt
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.ListenerRegistration

/**
 * Pedidos compartidos por el comprador, el vendedor y el administrador.
 * Con Firebase activo, la lista llega de Firestore. El pago solo pasa a
 * aprobado cuando el backend lo confirma.
 */
data class PendingPlacement(val orderId: String? = null, val failure: String? = null)

object ShopOrders {

    private val ordersInternal = mutableListOf<ShopOrder>()
    private val uiListeners = mutableListOf<() -> Unit>()
    private val errorListeners = mutableListOf<(String) -> Unit>()
    private var loaded = false
    private var cloud = false
    private var registration: ListenerRegistration? = null
    private var appContext: Context? = null

    fun addListener(listener: () -> Unit) {
        if (!uiListeners.contains(listener)) uiListeners.add(listener)
    }

    fun removeListener(listener: () -> Unit) {
        uiListeners.remove(listener)
    }

    fun addErrorListener(listener: (String) -> Unit) {
        if (!errorListeners.contains(listener)) errorListeners.add(listener)
    }

    fun removeErrorListener(listener: (String) -> Unit) {
        errorListeners.remove(listener)
    }

    /** Cierra la escucha de Firestore al salir de la sesión. */
    fun stop() {
        registration?.remove()
        registration = null
        cloud = false
        loaded = false
        ordersInternal.clear()
    }

    fun bind(context: Context) {
        appContext = context.applicationContext
        if (FirebaseGate.db(context) != null) {
            startCloud(context.applicationContext)
            return
        }
        ensureLoaded()
    }

    /** Pedidos activos del vendedor en sesión. El administrador ve la lista completa. */
    fun visibleActive(context: Context): List<ShopOrder> {
        val uid = sellerUid(context) ?: return activeOrders()
        return forSeller(uid).filter { !it.isHistory }
    }

    fun deliveredRevenueFor(context: Context): Double {
        val uid = sellerUid(context) ?: return deliveredRevenue()
        return forSeller(uid).filter { it.isPaid && it.status == ShopOrderStatus.DELIVERED }.sumOf { it.total }
    }

    fun forSeller(sellerId: String): List<ShopOrder> {
        ensureLoaded()
        if (sellerId.isBlank()) return emptyList()
        return ordersInternal.filter { order -> order.lines.any { it.sellerId == sellerId } }
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
            .filter { it.isPaid && it.status == ShopOrderStatus.DELIVERED }
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

    fun advance(id: String, onResult: (Boolean) -> Unit) {
        ensureLoaded()
        val order = ordersInternal.find { it.id == id } ?: return onResult(false)
        if (!order.isPaid) return onResult(false)
        val next = ShopOrderStatus.next(order.status) ?: return onResult(false)
        val context = appContext
        val db = context?.let { FirebaseGate.db(it) }
        if (db == null) {
            order.status = next
            persist()
            onResult(true)
            return
        }
        db.collection(ORDERS).document(id).update("status", next)
            .addOnSuccessListener { onResult(true) }
            .addOnFailureListener { onResult(false) }
    }

    fun cancel(id: String, onResult: (Boolean) -> Unit) {
        ensureLoaded()
        val order = ordersInternal.find { it.id == id } ?: return onResult(false)
        if (order.isHistory) return onResult(false)
        val context = appContext
        val db = context?.let { FirebaseGate.db(it) }
        if (db == null) {
            order.status = ShopOrderStatus.CANCELLED
            persist()
            onResult(true)
            return
        }
        val updates = hashMapOf<String, Any>(
            "status" to ShopOrderStatus.CANCELLED
        )
        db.collection(ORDERS).document(id).update(updates)
            .addOnSuccessListener { onResult(true) }
            .addOnFailureListener { onResult(false) }
    }

    /**
     * Crea un pedido pendiente de pago. Los precios quedan como declaración del
     * cliente: el backend los compara con Firestore y no cobra el total enviado
     * si no coincide. Solo el backend puede pasar el pago a aprobado.
     */
    fun placePending(
        context: Context,
        requestId: String,
        buyerName: String,
        buyerEmail: String,
        lines: List<ShopOrderLine>,
        dateLabel: String,
        address: String,
        latitude: Double,
        longitude: Double,
        paymentMethod: String,
        onResult: (PendingPlacement) -> Unit
    ) {
        val db = FirebaseGate.db(context) ?: return onResult(PendingPlacement(failure = "data"))
        val buyerId = FirebaseGate.auth(context)?.currentUser?.uid ?: return onResult(PendingPlacement(failure = "permission"))
        if (buyerName.isBlank() || buyerEmail.isBlank() || lines.isEmpty() || requestId.isBlank()) {
            onResult(PendingPlacement(failure = "data"))
            return
        }
        db.collection(ORDERS)
            .whereEqualTo("buyerId", buyerId)
            .whereEqualTo("clientRequestId", requestId)
            .limit(1)
            .get()
            .addOnSuccessListener { existing ->
                val previous = existing.documents.firstOrNull()
                if (previous != null) {
                    onResult(PendingPlacement(orderId = previous.id))
                    return@addOnSuccessListener
                }
                val id = "ORD-${System.currentTimeMillis()}"
                val sellerIds = lines.map { it.sellerId }.filter { it.isNotBlank() }.distinct()
                val (subtotal, total) = PurchaseAttempt.orderTotals(lines)
                val data = mapOf(
                    "buyerId" to buyerId,
                    "buyerName" to buyerName.trim(),
                    "buyerEmail" to BuyerSession.normalizeEmail(buyerEmail),
                    "dateLabel" to dateLabel,
                    "createdAt" to System.currentTimeMillis(),
                    "status" to ShopOrderStatus.PENDING,
                    "paymentStatus" to ShopOrderStatus.PAYMENT_PENDING,
                    "paymentRef" to "",
                    "address" to address,
                    "latitude" to latitude,
                    "longitude" to longitude,
                    "paymentMethod" to paymentMethod,
                    "currency" to "COP",
                    "clientRequestId" to requestId,
                    "sellerIds" to sellerIds,
                    "subtotal" to subtotal,
                    "total" to total,
                    "lines" to lines.map { line ->
                        mapOf(
                            "productId" to line.productId,
                            "productName" to line.productName,
                            "quantity" to line.quantity,
                            "unitPrice" to line.unitPrice,
                            "lineTotal" to line.lineTotal,
                            "sellerId" to line.sellerId
                        )
                    }
                )
                db.collection(ORDERS).document(id).set(data)
                    .addOnSuccessListener { onResult(PendingPlacement(orderId = id)) }
                    .addOnFailureListener { error -> onResult(PendingPlacement(failure = placementFailure(error))) }
            }
            .addOnFailureListener { error -> onResult(PendingPlacement(failure = placementFailure(error))) }
    }

    private fun placementFailure(error: Exception): String {
        val code = (error as? FirebaseFirestoreException)?.code?.name
        return PurchaseAttempt.checkoutFailure(code)
    }

    fun markPaymentClosed(context: Context, orderId: String, status: String, onResult: (Boolean) -> Unit) {
        onResult(false)
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

    private fun startCloud(context: Context) {
        if (cloud) return
        val db = FirebaseGate.db(context) ?: return
        val uid = FirebaseGate.auth(context)?.currentUser?.uid
        if (uid.isNullOrBlank()) return
        cloud = true
        loaded = true
        val role = BuyerSession.currentRole(context)
        val query = when (role) {
            ShopUserStore.ROLE_ADMIN -> db.collection(ORDERS)
            ShopUserStore.ROLE_SELLER -> db.collection(ORDERS).whereArrayContains("sellerIds", uid)
            else -> db.collection(ORDERS).whereEqualTo("buyerId", uid)
        }
        registration = query.addSnapshotListener { snap, error ->
            if (error != null) {
                errorListeners.toList().forEach { it(error.localizedMessage ?: "Firestore") }
                return@addSnapshotListener
            }
            if (snap == null) return@addSnapshotListener
            val parsed = snap.documents.mapNotNull { it.toOrder() }
            ordersInternal.clear()
            ordersInternal.addAll(parsed.sortedByDescending { it.createdAt })
            uiListeners.toList().forEach { it() }
        }
    }

    private fun sellerUid(context: Context): String? {
        if (BuyerSession.currentRole(context) != ShopUserStore.ROLE_SELLER) return null
        return FirebaseGate.auth(context)?.currentUser?.uid
    }

    private fun DocumentSnapshot.toOrder(): ShopOrder? {
        val name = getString("buyerName").orEmpty().trim()
        if (name.isEmpty()) return null
        val rawLines = get("lines") as? List<*> ?: emptyList<Any>()
        val lines = rawLines.mapNotNull { item ->
            val row = item as? Map<*, *> ?: return@mapNotNull null
            val productName = row["productName"]?.toString().orEmpty()
            val quantity = (row["quantity"] as? Number)?.toInt() ?: 0
            if (productName.isBlank() || quantity <= 0) return@mapNotNull null
            ShopOrderLine(
                productId = row["productId"]?.toString().orEmpty(),
                productName = productName,
                quantity = quantity,
                unitPrice = (row["unitPrice"] as? Number)?.toDouble() ?: 0.0,
                sellerId = row["sellerId"]?.toString().orEmpty()
            )
        }
        return ShopOrder(
            id = id,
            buyerName = name,
            dateLabel = getString("dateLabel").orEmpty(),
            status = getString("status").orEmpty().ifBlank { ShopOrderStatus.PENDING },
            lines = lines,
            buyerEmail = getString("buyerEmail").orEmpty(),
            buyerId = getString("buyerId").orEmpty(),
            address = getString("address").orEmpty(),
            latitude = getDouble("latitude") ?: 0.0,
            longitude = getDouble("longitude") ?: 0.0,
            paymentMethod = getString("paymentMethod").orEmpty(),
            paymentRef = getString("paymentRef").orEmpty(),
            paymentStatus = getString("paymentStatus").orEmpty().ifBlank { ShopOrderStatus.PAYMENT_PENDING },
            clientRequestId = getString("clientRequestId").orEmpty(),
            createdAt = getLong("createdAt") ?: 0L
        )
    }

    private fun ensureLoaded() {
        if (cloud || loaded) return
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
    private const val ORDERS = "orders"

    private val DEMO_ORDER_IDS = setOf(
        "ORD-2048",
        "ORD-2044",
        "ORD-2039",
        "ORD-2010",
        "ORD-1994",
        "ORD-1977"
    )
}
