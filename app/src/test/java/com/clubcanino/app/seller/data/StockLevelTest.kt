package com.clubcanino.app.seller.data

import org.junit.Assert.assertEquals
import org.junit.Test

class StockLevelTest {

    @Test
    fun dropsWhenAProductIsLowAndRisesWhenAnotherIsAdded() {
        val seven = List(4) { product(2) } + List(3) { product(10) }
        assertEquals(42, MarketCatalog.stockLevelPercent(seven))

        val eight = seven + product(12)
        assertEquals(50, MarketCatalog.stockLevelPercent(eight))

        val oneMoreLow = eight.dropLast(1) + product(1)
        assertEquals(37, MarketCatalog.stockLevelPercent(oneMoreLow))
    }

    @Test
    fun ignoresInactiveProductsAndIsEmptyAtZero() {
        val products = listOf(product(1, active = false), product(8))
        assertEquals(100, MarketCatalog.stockLevelPercent(products))
        assertEquals(0, MarketCatalog.stockLevelPercent(emptyList()))
    }

    private fun product(stock: Int, active: Boolean = true) = ShopProduct(
        id = "p$stock$active",
        name = "Producto",
        description = "",
        price = 1000.0,
        category = "Alimento",
        petType = "Perro",
        stock = stock,
        active = active,
        imageColorRes = 0,
        imageIconRes = 0
    )
}
