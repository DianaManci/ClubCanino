package com.clubcanino.app.buyer.data

import com.clubcanino.app.seller.data.ShopOrderLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PurchaseAttemptTest {

    private val lines = listOf(
        ShopOrderLine("food", "Croquetas", 2, 15000.0, "seller-1"),
        ShopOrderLine("toy", "Pelota", 1, 8000.0, "seller-2")
    )

    @Test
    fun reusesThePendingOrderWhenTheCartDoesNotChange() {
        val signature = PurchaseAttempt.signature(lines, "Calle 10", "Tarjeta")
        val first = PurchaseAttempt.nextAttempt(null, signature, "req-1")
        val retry = PurchaseAttempt.nextAttempt(first, signature, "req-2")
        assertEquals("req-1", retry.requestId)
        assertEquals(first, retry)
    }

    @Test
    fun startsAnotherOrderWhenQuantityOrAddressChanges() {
        val original = PurchaseAttempt.signature(lines, "Calle 10", "Tarjeta")
        val current = PurchaseAttempt.Attempt("req-1", original)
        val moreItems = lines.map { if (it.productId == "food") it.copy(quantity = 3) else it }
        val changedCart = PurchaseAttempt.signature(moreItems, "Calle 10", "Tarjeta")
        val changedAddress = PurchaseAttempt.signature(lines, "Carrera 5", "Tarjeta")
        assertNotEquals("req-1", PurchaseAttempt.nextAttempt(current, changedCart, "req-3").requestId)
        assertNotEquals("req-1", PurchaseAttempt.nextAttempt(current, changedAddress, "req-4").requestId)
    }

    @Test
    fun keepsSubtotalAndTotalEqualToTheLines() {
        val (subtotal, total) = PurchaseAttempt.orderTotals(lines)
        assertEquals(38000.0, subtotal, 0.0)
        assertEquals(subtotal, total, 0.0)
    }

    @Test
    fun namesTheFirestoreFailureWithoutTreatingItAsAPayment() {
        assertEquals("index", PurchaseAttempt.checkoutFailure("FAILED_PRECONDITION"))
        assertEquals("permission", PurchaseAttempt.checkoutFailure("PERMISSION_DENIED"))
        assertEquals("network", PurchaseAttempt.checkoutFailure("UNAVAILABLE"))
        assertEquals("data", PurchaseAttempt.checkoutFailure(null))
    }

    @Test
    fun keepsTheRequestIdWhenTheActivityIsRecreated() {
        val memory = MapAttemptMemory()
        val signature = PurchaseAttempt.signature(lines, "Calle 10", "Tarjeta")
        var field: PurchaseAttempt.Attempt? = PurchaseAttempt.resume(memory, null, signature, "req-1")
        field = null
        val restored = PurchaseAttempt.resume(memory, field, signature, "req-9")
        assertEquals("req-1", restored.requestId)
    }

    @Test
    fun keepsTheRequestIdWhenTheAppRestarts() {
        val firstProcess = MapAttemptMemory()
        val signature = PurchaseAttempt.signature(lines, "Calle 10", "Tarjeta")
        PurchaseAttempt.resume(firstProcess, null, signature, "req-1")
        val secondProcess = MapAttemptMemory(firstProcess.snapshot())
        val restored = PurchaseAttempt.resume(secondProcess, null, signature, "req-9")
        assertEquals("req-1", restored.requestId)
    }

    @Test
    fun storesANewIdWhenQuantityAddressOrMethodChangesAndKeepsThePreviousOne() {
        val memory = MapAttemptMemory()
        val original = PurchaseAttempt.signature(lines, "Calle 10", "Tarjeta")
        PurchaseAttempt.resume(memory, null, original, "req-1")
        val moreItems = lines.map { if (it.productId == "food") it.copy(quantity = 3) else it }
        val changedCart = PurchaseAttempt.signature(moreItems, "Calle 10", "Tarjeta")
        val changedAddress = PurchaseAttempt.signature(lines, "Carrera 5", "Tarjeta")
        val changedMethod = PurchaseAttempt.signature(lines, "Calle 10", "Efectivo")
        assertEquals("req-3", PurchaseAttempt.resume(memory, null, changedCart, "req-3").requestId)
        assertEquals("req-4", PurchaseAttempt.resume(memory, null, changedAddress, "req-4").requestId)
        assertEquals("req-5", PurchaseAttempt.resume(memory, null, changedMethod, "req-5").requestId)
        assertEquals("req-1", memory.requestIdFor(original))
        assertEquals("req-1", PurchaseAttempt.resume(memory, null, original, "req-9").requestId)
    }

    @Test
    fun keepsTheRequestIdAfterANetworkErrorOrCancellation() {
        val memory = MapAttemptMemory()
        val signature = PurchaseAttempt.signature(lines, "Calle 10", "Tarjeta")
        PurchaseAttempt.resume(memory, null, signature, "req-1")
        val afterFailure = PurchaseAttempt.resume(memory, null, signature, "req-2")
        assertEquals("req-1", afterFailure.requestId)
        assertEquals("req-1", memory.requestIdFor(signature))
    }

    @Test
    fun repeatsTheSameAttemptInsteadOfOpeningAnotherPendingOrder() {
        val memory = MapAttemptMemory()
        val signature = PurchaseAttempt.signature(lines, "Calle 10", "Tarjeta")
        val first = PurchaseAttempt.resume(memory, null, signature, "req-1")
        val second = PurchaseAttempt.resume(memory, null, signature, "req-2")
        assertEquals(first.requestId, second.requestId)
    }

    @Test
    fun forgetsTheAttemptOnlyAfterThePurchaseIsConfirmed() {
        val memory = MapAttemptMemory()
        val paid = PurchaseAttempt.signature(lines, "Calle 10", "Tarjeta")
        val stillPending = PurchaseAttempt.signature(lines, "Carrera 5", "Tarjeta")
        PurchaseAttempt.resume(memory, null, paid, "req-paid")
        PurchaseAttempt.resume(memory, null, stillPending, "req-pending")
        memory.forget(paid)
        assertNull(memory.requestIdFor(paid))
        assertEquals("req-pending", memory.requestIdFor(stillPending))
        assertEquals("req-new", PurchaseAttempt.resume(memory, null, paid, "req-new").requestId)
    }
}

private class MapAttemptMemory(
    initial: Map<String, String> = emptyMap()
) : PurchaseAttempt.AttemptMemory {
    private val values = initial.toMutableMap()
    private var checkout: String? = null

    fun snapshot(): Map<String, String> = values.toMap()

    override fun requestIdFor(signature: String): String? = values[signature]

    override fun save(attempt: PurchaseAttempt.Attempt) {
        values[attempt.signature] = attempt.requestId
        checkout = attempt.signature
    }

    override fun forget(signature: String) {
        values.remove(signature)
        if (checkout == signature) checkout = null
    }

    override fun checkoutSignature(): String? = checkout
}
