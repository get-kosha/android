package app.getkosha.activity

import app.getkosha.model.Transaction
import app.getkosha.model.TransactionList
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.YearMonth

class ActivityModelTest {
    private fun tx(id: String, day: String, amount: Long, category: String = "groceries", currency: String = "eur", fee: Long? = null) =
        Transaction(
            id = id, day = day, payee = id, amount = amount, currency = currency, accountId = "a", categoryId = category,
            transfer = fee?.let { Transaction.TransferLink("t$id", "b", -amount, it) },
        )

    private val rates = mapOf("eur" to 0.5, "usd" to 1.0)

    @Test fun groupsByDayAndLeavesNeutralOutOfDayTotals() {
        val list = listOf(
            tx("rewe", "2026-11-07", -1840),
            tx("bvg", "2026-11-06", -350),
            tx("move", "2026-11-06", -20000, category = "transfer", fee = 120),
            tx("fix", "2026-11-06", 500, category = "0"),
            tx("pay", "2026-11-06", 1000, currency = "usd"),
        )
        val days = list.byDay()
        assertEquals(listOf("2026-11-07", "2026-11-06"), days.map { it.first })
        assertEquals(-350L + 500L, days[1].second.dayTotal("eur", rates)) // $10 is €5 at these rates
        assertEquals(20120L, list[2].sent)
    }

    @Test fun summaryConvertsTheServerTotals() {
        val s = listOf(
            TransactionList.Totals("eur", spent = -131240, received = 324000, transferred = 20000),
            TransactionList.Totals("usd", spent = -2000, received = 0, transferred = 0),
        ).summary("eur", rates)
        assertEquals(-132240L, s.out)
        assertEquals(324000L, s.inn)
        assertEquals(191760L, s.net)
        assertEquals(20000L, s.moved)
    }

    @Test fun paramsLeaveOutWhatIsNotSet() {
        val p = Filters(query = "  rewe ", type = "expense", minAmount = 2000).params(YearMonth.of(2026, 11))
        assertEquals("2026-11", p["month"])
        assertEquals("rewe", p["query"])
        assertEquals(2000L, p["minAmount"])
        assertEquals(null, p["includeCorrections"])
        assertEquals(1, Filters(minAmount = 1).count)
    }

    @Test fun exchangeCostAsOnTheWeb() {
        // core.test.ts: send €1,000 via Wise, €5 fee, €995 converted at 106.00 (market 106.50)
        val fx = exchange(100000, 500, 10547000, 106.5)
        assertEquals(99500L, fx.converted)
        assertEquals(467L, Math.round(fx.rateCost!!))
        assertEquals(967L, Math.round(fx.totalCost))
        assertEquals("0.97", String.format(java.util.Locale.ROOT, "%.2f", fx.costPct))
        assertEquals(0.0, exchange(100000, 0, 100000, null).totalCost, 0.0)
    }

    @Test fun byHand() {
        val t = tx("tap", "2026-09-09", -41200)
        assertEquals(true, t.outsideEvent("2026-10-12", "2026-10-18"))
        assertEquals(false, t.outsideEvent(null, null))
    }
}
