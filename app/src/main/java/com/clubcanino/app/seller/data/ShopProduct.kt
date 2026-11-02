package com.clubcanino.app.seller.data

data class ShopProduct(
    val id: String,
    val name: String,
    val description: String,
    val price: Double,
    val category: String,
    val petType: String,
    val stock: Int,
    val active: Boolean,
    val imageColorRes: Int,
    val imageIconRes: Int,
    val featured: Boolean = false,
    val onPromotion: Boolean = false
)
