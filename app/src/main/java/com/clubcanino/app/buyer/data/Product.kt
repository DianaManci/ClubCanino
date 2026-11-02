package com.clubcanino.app.buyer.data

data class Product(
    val id: String,
    val name: String,
    val description: String,
    val price: Double,
    val category: String,
    val petType: String,
    val featured: Boolean = false,
    val onPromotion: Boolean = false,
    val imageColorRes: Int,
    val imageIconRes: Int,
    val stock: Int = 1,
    val available: Boolean = true
)
