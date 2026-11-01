package com.clubcanino.app.buyer.data

data class OrderSummary(
    val id: String,
    val dateLabel: String,
    val status: String,
    val total: Double,
    val itemCount: Int
)
