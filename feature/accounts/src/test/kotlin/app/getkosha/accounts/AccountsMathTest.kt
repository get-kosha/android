package app.getkosha.accounts

import app.getkosha.model.AccountType
import app.getkosha.model.KoshaJson
import app.getkosha.model.Wallet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

class AccountsMathTest {
    @Test fun exchangeCostAgainstTheMarket() {
        // €1,000 sent with a €1.67 fee, ₹93,693.27 received, market 94.60: about €9.58 cost.
        val fx = AccountsMath.exchange(100000, 167, 9369327, 94.60)
        assertEquals(99833, fx.converted)
        assertEquals(93.85, fx.rate, 0.01)
        assertEquals(958.0, fx.totalCost, 1.0)
        // Beating the market costs less than nothing.
        assertEquals(true, AccountsMath.exchange(20000, 0, 17280, 0.86).totalCost < 0)
        // Without a market rate, only the fee counts.
        val noMarket = AccountsMath.exchange(5000, 120, 4000, null)
        assertNull(noMarket.rateCost)
        assertEquals(120.0, noMarket.totalCost, 0.0)
    }

    @Test fun ratesKeepUsefulPrecision() {
        assertEquals("125.40", AccountsMath.rate(125.4012, Locale.UK))
        assertEquals("93.8512", AccountsMath.rate(93.8512, Locale.UK))
        assertEquals("1.1623", AccountsMath.rate(1.16231, Locale.UK))
        assertEquals("0.008640", AccountsMath.rate(0.00864, Locale.UK))
    }

    @Test fun worthFollowsTheAccountType() {
        val card = Wallet("c", "Visa", "eur", balance = 131643, type = AccountType.Credit, creditLimit = 200000)
        assertEquals(-68357, AccountsMath.shownWorth(card))
        assertEquals(-68357, AccountsMath.worth(card))
        val fund = Wallet("i", "TR", "eur", balance = 1890000, type = AccountType.Investment, gain = 217703)
        assertEquals(2107703, AccountsMath.worth(fund))
        // A card paid off beyond its limit owes nothing in lists.
        assertEquals(0, AccountsMath.shownWorth(card.copy(balance = 210000)))
    }

    @Test fun stacksCardDebtBelowZero() {
        val layers = listOf(Layer("a", listOf(100.0, 120.0)), Layer("card", listOf(-30.0, -10.0)), Layer("b", listOf(50.0, 0.0)))
        val s = AccountsMath.stack(layers)
        assertEquals(listOf(0.0 to 100.0, 0.0 to 120.0), s[0])
        assertEquals(listOf(-30.0 to 0.0, -10.0 to 0.0), s[1])
        assertEquals(listOf(100.0 to 150.0, 120.0 to 120.0), s[2])
    }

    @Test fun readsTheOverview() {
        val json = """{"currency":"eur","month":"2026-11","today":"2026-11-07","ratesUpdated":1,
            "series":[{"month":"2026-10","netWorth":1000.5,"gains":0,"byAccount":{"a":1000.5}},{"month":"2026-11","netWorth":900,"gains":12,"byAccount":{"a":950,"c":-62}}],
            "days":[],"last6":[],"accounts":{"a":{"months":[1,2],"days":[],"lastEntry":"2026-11-07","in":324000,"out":-108620,"moved":20000}},"recent":[]}"""
        val o = KoshaJson.decodeFromString(AccountsOverview.serializer(), json)
        assertEquals(324000.0, o.accounts["a"]!!.inflow, 0.0)
        val layers = AccountsMath.layers(o.series, listOf("c", "a"))
        assertEquals(listOf("c", "a", null), layers.map { it.id })
        assertEquals(1, AccountsMath.range(o.series, 1).size)
    }
}
