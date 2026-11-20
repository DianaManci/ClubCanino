package com.clubcanino.app.seller.data

import org.junit.Assert.assertEquals
import org.junit.Test

class BuyerOrderSectionTest {

    @Test
    fun groupsUnpaidAndNotStartedOrdersAsPending() {
        assertEquals(BuyerOrderSection.PENDING, order(ShopOrderStatus.PENDING, ShopOrderStatus.PAYMENT_PENDING).buyerSection())
        assertEquals(BuyerOrderSection.PENDING, order(ShopOrderStatus.PENDING, ShopOrderStatus.PAYMENT_APPROVED).buyerSection())
    }

    @Test
    fun groupsPreparationAndShippingAsInProgress() {
        assertEquals(BuyerOrderSection.IN_PROGRESS, order(ShopOrderStatus.PREPARING, ShopOrderStatus.PAYMENT_APPROVED).buyerSection())
        assertEquals(BuyerOrderSection.IN_PROGRESS, order(ShopOrderStatus.SHIPPING, ShopOrderStatus.PAYMENT_APPROVED).buyerSection())
    }

    @Test
    fun groupsDeliveredApartFromCancelled() {
        assertEquals(BuyerOrderSection.DELIVERED, order(ShopOrderStatus.DELIVERED, ShopOrderStatus.PAYMENT_APPROVED).buyerSection())
        assertEquals(BuyerOrderSection.CANCELLED, order(ShopOrderStatus.CANCELLED, ShopOrderStatus.PAYMENT_PENDING).buyerSection())
        assertEquals(BuyerOrderSection.CANCELLED, order(ShopOrderStatus.PENDING, ShopOrderStatus.PAYMENT_REJECTED).buyerSection())
    }

    private fun order(status: String, payment: String) = ShopOrder(
        id = "ORD-1",
        buyerName = "Ana",
        dateLabel = "20 nov 2026",
        status = status,
        lines = emptyList(),
        paymentStatus = payment
    )
}
