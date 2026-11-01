package com.clubcanino.app.buyer.data

data class CartItem(
    val product: Product,
    var quantity: Int
) {
    val lineTotal: Double
        get() = product.price * quantity
}
