package com.clubcanino.app.buyer.data

object CartStore {

    private val itemsById = linkedMapOf<String, CartItem>()
    private val listeners = mutableListOf<() -> Unit>()

    fun items(): List<CartItem> = itemsById.values.toList()

    fun itemCount(): Int = itemsById.values.sumOf { it.quantity }

    fun subtotal(): Double = itemsById.values.sumOf { it.lineTotal }

    fun add(product: Product, quantity: Int = 1) {
        val safeQty = quantity.coerceAtLeast(1)
        val existing = itemsById[product.id]
        if (existing == null) {
            itemsById[product.id] = CartItem(product, safeQty)
        } else {
            existing.quantity += safeQty
        }
        notifyListeners()
    }

    fun setQuantity(productId: String, quantity: Int) {
        val item = itemsById[productId] ?: return
        if (quantity <= 0) {
            itemsById.remove(productId)
        } else {
            item.quantity = quantity
        }
        notifyListeners()
    }

    fun remove(productId: String) {
        if (itemsById.remove(productId) != null) {
            notifyListeners()
        }
    }

    fun clear() {
        if (itemsById.isNotEmpty()) {
            itemsById.clear()
            notifyListeners()
        }
    }

    fun addListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    private fun notifyListeners() {
        listeners.toList().forEach { it.invoke() }
    }
}
