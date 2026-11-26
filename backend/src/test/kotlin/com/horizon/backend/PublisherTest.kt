package com.horizon.backend

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PublisherTest {
    @Test
    fun contractRendersWithoutPlaceholders() {
        val text = Contract.render("Studio Kasai", "Jean Kasai", "jean@studio.cd", "RDC", 30.0)
        assertFalse(text.contains("{{"))
        assertTrue(text.contains("Studio Kasai"))
        assertTrue(text.contains("30 %"))
        assertTrue(text.contains("70 %"))
        assertEquals(Security.sha256Hex(text), Security.sha256Hex(Contract.render("Studio Kasai", "Jean Kasai", "jean@studio.cd", "RDC", 30.0)))
    }

    @Test
    fun percentFormatting() {
        assertEquals("30", Contract.formatPercent(30.0))
        assertEquals("27,5", Contract.formatPercent(27.5))
        assertEquals("72,5", Contract.formatPercent(72.5))
    }

    @Test
    fun revenueIgnoresTestSalesAndSubtractsPayouts() {
        val lines = listOf(
            SalesLine("a", "A", "USD", false, 3, 1500),
            SalesLine("a", "A", "USD", true, 10, 9900),
            SalesLine("b", "B", "USD", false, 1, 500)
        )
        val payouts = listOf(PayoutDto("p1", 700, "USD", null, "2026-01-01T00:00:00Z"))
        val b = Revenue.balances(lines, payouts, 3000).single()
        assertEquals(2000, b.grossCents)
        assertEquals(600, b.commissionCents)
        assertEquals(1400, b.dueCents)
        assertEquals(700, b.paidCents)
        assertEquals(700, b.balanceCents)
    }
}
