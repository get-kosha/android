package app.getkosha.overview

import app.getkosha.model.AccountType
import app.getkosha.model.Wallet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DashboardTest {
    private val usual = List(30) { (it + 1) * 8000.0 } // €80 a day, €2,400 a month
    private val d = Dashboard(
        primary = "eur",
        month = "2026-11",
        current = Dashboard.Flows(income = 324000, expense = 131240),
        pace = Dashboard.Pace(daysInMonth = 30, today = 7, current = List(7) { 131240L }, usual = usual),
        categories = listOf(
            Dashboard.DashCategory("rent", 95000, 1, fixed = true, usualToDate = 95000.0),
            Dashboard.DashCategory("dining", 8620, 4, usualToDate = 6950.0),
            Dashboard.DashCategory("groceries", 14230, 6, usualToDate = 15500.0),
            Dashboard.DashCategory("shopping", 3990, 1, usualToDate = 300.0),
        ),
        upcoming = listOf(
            Dashboard.Upcoming("o2|2026-11-05", "O2", "2026-11-05", -5200, "eur", "repetitive", "o2"),
            Dashboard.Upcoming("sp|2026-11-10", "Spotify", "2026-11-10", -1099, "eur", "subscription", "sp"),
            Dashboard.Upcoming("hd|2026-11-30", "HDFC", "2026-11-30", -50000, "eur", "transfer", "hd", toWalletId = "hdfc"),
            Dashboard.Upcoming("x|2026-12-01", "Later", "2026-12-01", -1000, "usd", "subscription", "x"),
        ),
    )
    private val rates = mapOf("eur" to 0.9, "usd" to 1.0)

    @Test fun pace() {
        assertEquals(56000L, d.usualBy())
        assertEquals(240000L, d.usualMonth())
        // spent + (usual month − usual by now)
        assertEquals(131240L + 184000L, d.projected())
        assertEquals(56000L - 131240L, d.underUsual())
    }

    @Test fun stillToPayLeavesOutTransfersAndNextMonth() {
        assertEquals(5200L + 1099L, d.stillToPay("2026-11", rates))
        assertEquals(2, d.billsDue("2026-11", rates))
        assertEquals(0L, d.stillToPay("2026-12", rates))
    }

    @Test fun pills() {
        assertEquals(VsUsual.Fixed, vsUsual(95000, 95000.0, fixed = true))
        assertEquals(VsUsual.New, vsUsual(3990, 300.0))
        val up = vsUsual(8620, 6950.0) as VsUsual.Change
        assertTrue(up.warn)
        assertTrue((vsUsual(14230, 15500.0) as VsUsual.Change).good)
    }

    @Test fun needsAttention() {
        val wallets = listOf(
            Wallet("visa", "Visa Card", "eur", balance = 131643, type = AccountType.Credit, creditLimit = 200000),
            Wallet("tr", "Trade Republic", "eur", balance = 1890000, type = AccountType.Investment, gainDay = "2026-08-27"),
            Wallet("old", "Old ETF", "eur", archived = 1, type = AccountType.Investment),
        )
        val items = d.attention(LocalDate.parse("2026-11-07"), wallets)
        assertEquals(listOf("o2|2026-11-05", "visa", "dining", "tr"), items.map { it.key })
        assertEquals(68357L, (items[1] as Attention.CardOwed).owed)
        assertEquals(72L, (items[3] as Attention.Stale).age)
    }
}
