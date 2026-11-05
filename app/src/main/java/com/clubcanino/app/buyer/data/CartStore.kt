package com.clubcanino.app.buyer.data

import android.content.Context
import com.clubcanino.app.R
import com.clubcanino.app.auth.FirebaseGate
import com.google.firebase.firestore.SetOptions

/**
 * One cart per signed-in customer. Items from several stores stay in the same cart.
 */
object CartStore {

    private val carts = mutableMapOf<String, LinkedHashMap<String, CartItem>>()
    private val listeners = mutableListOf<() -> Unit>()
    private var activeUid: String = ""
    private var appContext: Context? = null

    fun bind(context: Context) {
        appContext = context.applicationContext
        val uid = FirebaseGate.auth(context)?.currentUser?.uid.orEmpty()
        if (uid == activeUid && uid.isNotEmpty() && carts.containsKey(uid)) {
            return
        }
        activeUid = uid
        if (uid.isEmpty()) {
            notifyListeners()
            return
        }
        carts.getOrPut(uid) { linkedMapOf() }
        val db = FirebaseGate.db(context) ?: return
        db.collection(CARTS).document(uid).collection(LINES).get()
            .addOnSuccessListener { query ->
                if (uid != activeUid) return@addOnSuccessListener
                val items = linkedMapOf<String, CartItem>()
                query.documents.forEach { doc ->
                    val name = doc.getString("name").orEmpty().trim()
                    val quantity = (doc.getLong("quantity") ?: 0L).toInt()
                    if (name.isEmpty() || quantity <= 0) return@forEach
                    val product = Product(
                        id = doc.id,
                        name = name,
                        description = "",
                        price = doc.getDouble("price") ?: 0.0,
                        category = "",
                        petType = "",
                        imageColorRes = R.color.green_container,
                        imageIconRes = R.drawable.ic_pet_dog,
                        sellerId = doc.getString("sellerId").orEmpty(),
                        storeName = doc.getString("storeName").orEmpty(),
                        photoUrl = doc.getString("photoUrl").orEmpty()
                    )
                    items[doc.id] = CartItem(product, quantity)
                }
                carts[uid] = items
                notifyListeners()
            }
    }

    fun items(): List<CartItem> = activeItems().values.toList()

    fun itemCount(): Int = activeItems().values.sumOf { it.quantity }

    fun subtotal(): Double = activeItems().values.sumOf { it.lineTotal }

    fun totalsByStore(): List<Pair<String, Double>> {
        return activeItems().values
            .groupBy { it.product.storeName.ifBlank { it.product.sellerId.ifBlank { "Tienda" } } }
            .map { (store, lines) -> store to lines.sumOf { it.lineTotal } }
    }

    fun add(product: Product, quantity: Int = 1) {
        val items = activeItems()
        if (activeUid.isEmpty()) return
        val safeQty = quantity.coerceAtLeast(1)
        val existing = items[product.id]
        if (existing == null) {
            items[product.id] = CartItem(product, safeQty)
        } else {
            existing.quantity += safeQty
        }
        persist(product.id)
        notifyListeners()
    }

    fun setQuantity(productId: String, quantity: Int) {
        val items = activeItems()
        if (activeUid.isEmpty()) return
        val item = items[productId] ?: return
        if (quantity <= 0) {
            items.remove(productId)
            delete(productId)
        } else {
            item.quantity = quantity
            persist(productId)
        }
        notifyListeners()
    }

    fun remove(productId: String) {
        val items = activeItems()
        if (activeUid.isEmpty()) return
        if (items.remove(productId) == null) return
        delete(productId)
        notifyListeners()
    }

    fun clear() {
        val items = activeItems()
        if (activeUid.isEmpty()) return
        val ids = items.keys.toList()
        val hadItems = items.isNotEmpty()
        items.clear()
        ids.forEach { delete(it) }
        if (hadItems) notifyListeners()
    }

    fun addListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    private fun activeItems(): LinkedHashMap<String, CartItem> {
        if (activeUid.isEmpty()) return NO_OWNER
        return carts.getOrPut(activeUid) { linkedMapOf() }
    }

    private fun persist(productId: String) {
        val context = appContext ?: return
        val item = activeItems()[productId] ?: return
        val db = FirebaseGate.db(context) ?: return
        val data = mapOf(
            "productId" to item.product.id,
            "sellerId" to item.product.sellerId,
            "storeName" to item.product.storeName,
            "name" to item.product.name,
            "price" to item.product.price,
            "quantity" to item.quantity,
            "photoUrl" to item.product.photoUrl
        )
        db.collection(CARTS).document(activeUid).collection(LINES).document(productId)
            .set(data, SetOptions.merge())
    }

    private fun delete(productId: String) {
        val context = appContext ?: return
        val db = FirebaseGate.db(context) ?: return
        if (activeUid.isEmpty()) return
        db.collection(CARTS).document(activeUid).collection(LINES).document(productId).delete()
    }

    private fun notifyListeners() {
        listeners.toList().forEach { it.invoke() }
    }

    private val NO_OWNER = linkedMapOf<String, CartItem>()

    private const val CARTS = "carts"
    private const val LINES = "lines"
}
