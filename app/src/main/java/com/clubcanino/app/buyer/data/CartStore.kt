package com.clubcanino.app.buyer.data

import android.content.Context
import com.clubcanino.app.R
import com.clubcanino.app.seller.data.ShopCatalog
import org.json.JSONArray
import org.json.JSONObject

/**
 * One cart per buyer email. The list is stored on the device and is not cleared
 * when another buyer signs in.
 */
object CartStore {

    private val carts = mutableMapOf<String, LinkedHashMap<String, CartItem>>()
    private val listeners = mutableListOf<() -> Unit>()
    private var activeEmail: String = ""
    private var loaded = false
    private var appContext: Context? = null

    fun bind(context: Context) {
        appContext = context.applicationContext
        ensureLoaded()
        refreshOwner(notify = true)
    }

    fun items(): List<CartItem> = activeItems().values.toList()

    fun itemCount(): Int = activeItems().values.sumOf { it.quantity }

    fun subtotal(): Double = activeItems().values.sumOf { it.lineTotal }

    fun add(product: Product, quantity: Int = 1) {
        val items = activeItems()
        if (activeEmail.isEmpty()) return
        val safeQty = quantity.coerceAtLeast(1)
        val existing = items[product.id]
        if (existing == null) {
            items[product.id] = CartItem(product, safeQty)
        } else {
            existing.quantity += safeQty
        }
        persist()
        notifyListeners()
    }

    fun setQuantity(productId: String, quantity: Int) {
        val items = activeItems()
        if (activeEmail.isEmpty()) return
        val item = items[productId] ?: return
        if (quantity <= 0) {
            items.remove(productId)
        } else {
            item.quantity = quantity
        }
        persist()
        notifyListeners()
    }

    fun remove(productId: String) {
        val items = activeItems()
        if (activeEmail.isEmpty()) return
        if (items.remove(productId) == null) return
        persist()
        notifyListeners()
    }

    fun clear() {
        val items = activeItems()
        if (activeEmail.isEmpty()) return
        val hadItems = items.isNotEmpty()
        items.clear()
        persist()
        if (hadItems) notifyListeners()
    }

    fun addListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    private fun activeItems(): LinkedHashMap<String, CartItem> {
        refreshOwner(notify = false)
        if (activeEmail.isEmpty()) return NO_OWNER
        return carts.getOrPut(activeEmail) { linkedMapOf() }
    }

    private fun refreshOwner(notify: Boolean) {
        ensureLoaded()
        val context = appContext ?: return
        val email = BuyerSession.currentEmail(context)?.let(BuyerSession::normalizeEmail).orEmpty()
        if (email == activeEmail) return
        activeEmail = email
        if (email.isNotEmpty()) {
            carts.getOrPut(email) { linkedMapOf() }
        }
        if (notify) notifyListeners()
    }

    private fun ensureLoaded() {
        if (loaded) return
        val context = appContext ?: return
        loaded = true
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_CARTS, null) ?: return
        read(raw)
    }

    private fun persist() {
        val context = appContext ?: return
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CARTS, write())
            .commit()
    }

    private fun write(): String {
        val root = JSONObject()
        carts.forEach { (email, items) ->
            if (email.isEmpty()) return@forEach
            if (items.isEmpty() && email != activeEmail) return@forEach
            val array = JSONArray()
            items.values.forEach { item ->
                array.put(
                    JSONObject().apply {
                        put(JSON_ID, item.product.id)
                        put(JSON_NAME, item.product.name)
                        put(JSON_PRICE, item.product.price)
                        put(JSON_QUANTITY, item.quantity)
                    }
                )
            }
            root.put(email, array)
        }
        return root.toString()
    }

    private fun read(raw: String) {
        carts.clear()
        try {
            val root = JSONObject(raw)
            val keys = root.keys()
            while (keys.hasNext()) {
                val rawKey = keys.next()
                val email = BuyerSession.normalizeEmail(rawKey)
                val array = root.optJSONArray(rawKey) ?: continue
                if (email.isEmpty() || carts.containsKey(email)) continue
                val items = linkedMapOf<String, CartItem>()
                for (index in 0 until array.length()) {
                    val obj = array.optJSONObject(index) ?: continue
                    val id = obj.optString(JSON_ID).trim()
                    val name = obj.optString(JSON_NAME).trim()
                    val quantity = obj.optInt(JSON_QUANTITY, 0)
                    if (id.isEmpty() || name.isEmpty() || quantity <= 0) continue
                    if (!obj.has(JSON_PRICE)) continue
                    val price = obj.optDouble(JSON_PRICE, 0.0)
                    val existing = items[id]
                    if (existing == null) {
                        items[id] = CartItem(lineProduct(id, name, price), quantity)
                    } else {
                        existing.quantity += quantity
                    }
                }
                carts[email] = items
            }
        } catch (_: Exception) {
            carts.clear()
        }
    }

    private fun lineProduct(id: String, name: String, price: Double): Product {
        val catalog = ShopCatalog.productById(id)
        return Product(
            id = id,
            name = name,
            description = catalog?.description.orEmpty(),
            price = price,
            category = catalog?.category.orEmpty(),
            petType = catalog?.petType.orEmpty(),
            featured = catalog?.featured ?: false,
            onPromotion = catalog?.onPromotion ?: false,
            imageColorRes = catalog?.imageColorRes ?: R.color.green_container,
            imageIconRes = catalog?.imageIconRes ?: R.drawable.ic_pet_dog,
            stock = catalog?.stock ?: 1,
            available = true
        )
    }

    private fun notifyListeners() {
        listeners.toList().forEach { it.invoke() }
    }

    private val NO_OWNER = linkedMapOf<String, CartItem>()

    private const val PREFS_NAME = "club_canino_carts"
    private const val KEY_CARTS = "buyer_carts"
    private const val JSON_ID = "productId"
    private const val JSON_NAME = "name"
    private const val JSON_PRICE = "price"
    private const val JSON_QUANTITY = "quantity"
}
