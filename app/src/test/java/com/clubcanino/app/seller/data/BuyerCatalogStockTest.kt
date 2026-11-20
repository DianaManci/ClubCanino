package com.clubcanino.app.seller.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BuyerCatalogStockTest {

    @Test
    fun showsSoldOutProductsAndKeepsThemUnavailable() {
        val products = listOf(
            product("food", stock = 0),
            product("toy", stock = 4),
            product("hidden", stock = 2, active = false)
        )
        val visible = MarketCatalog.visibleToBuyer(products, "Todos", "")
        assertEquals(listOf("food", "toy"), visible.map { it.id })
        assertFalse(visible.first { it.id == "food" }.available)
        assertTrue(visible.first { it.id == "toy" }.available)
    }

    private fun product(id: String, stock: Int, active: Boolean = true) = ShopProduct(
        id = id,
        name = id,
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
