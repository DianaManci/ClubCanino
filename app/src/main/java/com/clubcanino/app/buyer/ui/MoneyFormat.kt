package com.clubcanino.app.buyer.ui

import java.text.NumberFormat
import java.util.Locale

object MoneyFormat {
    private val format = NumberFormat.getCurrencyInstance(Locale("es", "CO"))

    fun format(amount: Double): String = format.format(amount)
}
