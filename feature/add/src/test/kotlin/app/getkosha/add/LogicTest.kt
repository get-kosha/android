package app.getkosha.add

import app.getkosha.model.Event
import app.getkosha.model.Wallet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

class LogicTest {
    private fun w(id: String) = Wallet(id = id, name = id, currency = "eur")

    @Test
    fun startAccountPrefersTheDefaultThenTheLastUsed() {
        val active = listOf(w("a"), w("b"), w("c"))
        assertEquals("b", startAccount("b", active, "c"))
        assertEquals("c", startAccount("last", active, "c"))
        // A default or last account that was archived or deleted falls through.
        assertEquals("c", startAccount("gone", active, "c"))
        assertEquals("a", startAccount("last", active, "gone"))
        assertNull(startAccount("last", emptyList(), null))
    }

    @Test
    fun eventForPicksTheLatestStartThatCoversTheDay() {
        val events = listOf(
            Event("trip", "Trip", startDay = "2026-11-01", endDay = "2026-11-10"),
            Event("weekend", "Weekend", startDay = "2026-11-04", endDay = "2026-11-06"),
            Event("open", "Still going", startDay = "2026-12-01"),
            Event("undated", "Undated"),
        )
        assertEquals("weekend", eventFor("2026-11-05", events))
        assertEquals("trip", eventFor("2026-11-08", events))
        assertEquals("open", eventFor("2027-03-01", events))
        assertNull(eventFor("2026-10-31", events))
    }

    @Test
    fun keypadInputDropsTrailingZeros() {
        assertEquals("29.57", keypadInput(-2957, 2))
        assertEquals("12", keypadInput(1200, 2))
        assertEquals("12.5", keypadInput(1250, 2))
        assertEquals("1200", keypadInput(120000, 0))
        assertEquals("", keypadInput(0, 2))
    }

    @Test
    fun exchangeMatchesTheFeeSheet() {
        // €500.00 with a €4.10 fee becomes ₹46,850.00 at a market rate of 94.60: €4.76 (0.95%) against the market.
        val x = exchange(50000, 410, 4685000, 94.60)
        assertEquals(49590, x.converted)
        assertEquals(476, Math.round(x.totalCost))
        assertEquals("0.95", "%.2f".format(java.util.Locale.ROOT, x.costPct))
        // Same currency: the cost is the fee.
        assertEquals(410.0, exchange(50000, 410, 49590, null).totalCost, 0.0)
    }

    @Test
    fun transferSidesFollowWhatWasTyped() {
        val rates = mapOf("usd" to 1.0, "eur" to 0.92, "inr" to 87.032)
        val market = transferSides(50000, FeeInput(fee = "4.1"), "eur", "inr", 2, 2, rates)
        assertEquals(410, market.fee)
        assertEquals(49590, market.converted)
        assertEquals(Math.round(49590 * (87.032 / 0.92)), market.received)
        val received = transferSides(50000, FeeInput(fee = "4.1", received = "46850", typed = FeeInput.Typed.Received), "eur", "inr", 2, 2, rates)
        assertEquals(4685000, received.received)
        val rate = transferSides(50000, FeeInput(rate = "94.5", typed = FeeInput.Typed.Rate), "eur", "inr", 2, 2, rates)
        assertEquals(4725000, rate.received)
        // Same currency: what arrives is what was sent less the fee.
        assertEquals(49590, transferSides(50000, FeeInput(fee = "4.10"), "eur", "eur", 2, 2, rates).received)
    }

    @Test
    fun fmtRateKeepsTheWebsPrecision() {
        assertEquals(BigDecimal("160.53"), fmtRate(160.5312))
        assertEquals(BigDecimal("94.4712"), fmtRate(94.47123))
        assertEquals(BigDecimal("1.0870"), fmtRate(1.08702))
        assertEquals(BigDecimal("0.006231"), fmtRate(0.0062311))
    }
}
