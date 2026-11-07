package app.getkosha.welcome

import app.getkosha.model.AccountType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OpeningTest {
    @Test
    fun figuresAreMinorUnitsOrNothing() {
        assertNull(figure(""))
        assertEquals(241830L, figure("2418.3"))
        assertEquals(150000L, figure("1200+300"))
        assertEquals(0L, figure("0"))
    }

    @Test
    fun aCardKeepsTheCreditLeft() {
        assertEquals(Opening(131643, 200000, null), openingFigures(AccountType.Credit, 68357, 200000))
        // Without a limit it is minus what is owed, so its worth still counts the debt.
        assertEquals(Opening(-68357, null, null), openingFigures(AccountType.Credit, 68357, null))
        assertEquals(-68357L, accountWorth(AccountType.Credit, 131643, 200000, null))
    }

    @Test
    fun anInvestmentKeepsMoneyInAndItsGain() {
        assertEquals(Opening(1890000, null, 217703), openingFigures(AccountType.Investment, 1890000, 2107703))
        // Only a value today: it all counts as money put in.
        assertEquals(Opening(2107703, null, 0), openingFigures(AccountType.Investment, null, 2107703))
        assertEquals(2107703L, accountWorth(AccountType.Investment, 1890000, null, 217703))
    }

    @Test
    fun netWorthPreviewMatchesTheDesign() {
        // 2,418.30 in the bank, 683.57 owed of 2,000.00, 18,900.00 put in worth 21,077.03 today: €22,811.76.
        val rows = listOf(
            NewAccount("1", "N26 Main", AccountType.Bank, "eur", amount = "2418.30"),
            NewAccount("2", "Visa Card", AccountType.Credit, "eur", amount = "683.57", extra = "2000"),
            NewAccount("3", "Trade Republic", AccountType.Investment, "eur", amount = "18900", extra = "21077.03"),
        )
        assertEquals(2281176L, previewNetWorth(rows, "eur", emptyMap()))
    }

    @Test
    fun netWorthPreviewConvertsAtTheServersRates() {
        val rows = listOf(NewAccount("1", "HDFC", AccountType.Bank, "inr", amount = "9460"))
        assertEquals(10000L, previewNetWorth(rows, "eur", mapOf("eur" to 1.0, "inr" to 94.6)))
    }
}
