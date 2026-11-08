package app.getkosha.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RefundTest {
    private fun tx(extra: String) = KoshaJson.decodeFromString(
        Transaction.serializer(),
        """{"id":"t1","day":"2026-11-04","payee":"Shop","amount":-5000,"currency":"eur","accountId":"a1","categoryId":"shopping"$extra}""",
    )

    @Test fun anOlderServerCantRefund() {
        val t = tx("")
        assertNull(t.refunds)
        assertFalse(t.canRefund)
        assertFalse(t.refundedInFull)
    }

    @Test fun partThenFull() {
        val none = tx(""","refunds":[],"refunded":0""")
        assertTrue(none.canRefund)
        assertFalse(none.refundedInFull)
        assertEquals(5000, none.refundLeft)

        val part = tx(""","refunds":[{"id":"r1","accountId":"a2","amount":2000,"currency":"eur","day":"2026-11-06"}],"refunded":2000""")
        assertFalse(part.refundedInFull)
        assertEquals(3000, part.refundLeft)

        val full = part.copy(refunded = 5000)
        assertTrue(full.refundedInFull)
        assertEquals(0, full.refundLeft)
    }

    @Test fun onlyPaidSpending() {
        val base = tx(""","refunds":[],"refunded":0""")
        assertFalse(base.copy(amount = 5000).canRefund)
        assertFalse(base.copy(status = "upcoming").canRefund)
        assertFalse(base.copy(categoryId = Categories.CORRECTION).canRefund)
        assertFalse(base.copy(refundOf = "t0").canRefund)
        assertFalse(base.copy(transfer = Transaction.TransferLink("x")).canRefund)
    }
}
