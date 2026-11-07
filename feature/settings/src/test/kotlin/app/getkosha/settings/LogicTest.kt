package app.getkosha.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class LogicTest {
    @Test
    fun securityStepsCountBackupCodesOnlyWithTwoFactor() {
        val none = AccountSummary(hasPassword = true, passkeys = 0, twoFactor = false)
        assertEquals(listOf(true, false, false, false), securitySteps(none, backupCodesSaved = true).map { it.second })
        val all = AccountSummary(hasPassword = true, passkeys = 2, twoFactor = true)
        assertEquals(0, securitySteps(all, backupCodesSaved = true).count { !it.second })
        assertEquals(1, securitySteps(all, backupCodesSaved = false).count { !it.second })
    }

    @Test
    fun searchNeedsEveryWord() {
        assertTrue(matchesAll("time zo", "Time zone", "Months and days"))
        assertFalse(matchesAll("time money", "Time zone"))
        assertFalse(matchesAll("  ", "anything"))
    }

    @Test
    fun currencyOrderKeepsDefaultFirstAndAddsAccountCurrencies() {
        assertEquals(listOf("eur", "inr", "usd", "gbp"), currencyOrder("eur", listOf("inr", "eur", "usd"), listOf("usd", "gbp")))
    }

    @Test
    fun movedReorders() {
        assertEquals(listOf("b", "c", "a"), listOf("a", "b", "c").moved(0, 2))
        assertEquals(listOf("c", "a", "b"), listOf("a", "b", "c").moved(2, 0))
        assertEquals(listOf("a", "b"), listOf("a", "b").moved(0, 5))
    }

    @Test
    fun ratesAreUnitsPerDefault() {
        val perUsd = mapOf("usd" to 1.0, "eur" to 0.86, "inr" to 81.36)
        assertEquals(94.6, rate("eur", "inr", perUsd)!!, 0.01)
        assertNull(rate("eur", "xyz", perUsd))
        assertEquals("94.6012", fmtRate(94.6012))
        assertEquals("123.46", fmtRate(123.456))
        assertEquals("1.1623", fmtRate(1.16234))
        assertEquals("0.01057", fmtRate(0.010571))
    }

    @Test
    fun zoneLabels() {
        assertEquals("New York", zoneCity("America/New_York"))
        val july = Instant.parse("2026-07-01T12:00:00Z")
        assertEquals("UTC+5:30", zoneOffset("Asia/Kolkata", july))
        assertEquals("UTC−4", zoneOffset("America/New_York", july))
        assertEquals("UTC+0", zoneOffset("UTC", july))
    }

    @Test
    fun payeeCodeMatchesTheWeb() {
        // Values from core.ts payeeCode, run in Node.
        assertEquals("payee_11uj3ko", payeeCode("u1", "Pizza Hut"))
        assertEquals("payee_p1vjvp", payeeCode("demo", " Bonanza Coffee "))
    }

    @Test
    fun samplePayloadFollowsTheShares() {
        val tx = SampleTx("Pizza Hut", -2957, "eur", "2026-11-04", "Delivery", "Visa Card")
        val open = samplePayload(tx, mapOf("amount" to true, "payee" to true, "date" to true, "note" to false, "account" to false), "u1")
        assertEquals(Triple("payee", "\"Pizza Hut\"", Shown.On), open[0])
        assertEquals(Triple("amount", "-29.57", Shown.On), open[1])
        assertEquals(Triple("currency", "\"EUR\"", Shown.On), open[2])
        assertEquals(Triple("date", "\"2026-11-04\"", Shown.On), open[3])
        assertEquals(Shown.Off, open[4].third)
        val masked = samplePayload(tx, mapOf("amount" to false, "payee" to false, "date" to false), "u1")
        assertEquals(Triple("payee", "\"payee_11uj3ko\"", Shown.Masked), masked[0])
        assertEquals(Triple("amount", "null", Shown.Masked), masked[1])
        assertEquals(Triple("month", "\"2026-11\"", Shown.Masked), masked[3])
    }
}
