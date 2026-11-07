package app.getkosha.statements

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// The asserts of the web's src/lib/core.test.ts ("statements", "splits and bank corrections"), for the parts the
// phone runs itself; parsing CSV and OFX stays on the server.
class StatementTest {
    private fun line(day: String, amount: Long, description: String, ref: String? = null, replaces: String? = null) =
        Line(day, amount, description, null, ref, replaces)

    private fun entry(id: String, day: String, amount: Long, name: String, ref: String? = null) = Entry(id, day, amount, name, ref)

    @Test
    fun dates() {
        assertEquals("2026-11-03", parseDay("03/11/2026", DMY))
        assertEquals("2026-11-03", parseDay("11/03/2026", MDY))
        assertEquals("2026-11-03", parseDay("2026-11-03T10:22:00", YMD))
        assertEquals("2026-11-03", parseDay("3 Nov 2026", TEXT))
        assertEquals("2026-11-03", parseDay("Nov 3, 2026", TEXT))
        assertEquals(null, parseDay("31/02/2026", DMY))
        // A day over 12 decides; otherwise date order does.
        assertEquals(DMY, guessDateFormat(listOf("13/10/2026", "01/11/2026")))
        assertEquals(MDY, guessDateFormat(listOf("10/13/2026", "11/01/2026")))
        assertEquals(DMY, guessDateFormat(listOf("01/10/2026", "05/10/2026", "02/11/2026")))
        assertEquals(YMD, guessDateFormat(listOf("2026-10-01")))
    }

    @Test
    fun payees() {
        assertEquals("tesco stores", descriptorKey("CARD PAYMENT TO TESCO STORES 3211 ON 03 NOV"))
        assertEquals("spotify", descriptorKey("PAYPAL *SPOTIFY P2B3C4"))
        assertEquals("Tesco", payeeFor("CARD PAYMENT TO TESCO STORES 3211", listOf("Tesco", "Uber"), emptyMap()))
        assertEquals("Blue Bottle Coffee", payeeFor("SQ *BLUE BOTTLE COFFEE", emptyList(), emptyMap()))
        assertEquals("Thames Water", payeeFor("DD THAMES WATER 99", emptyList(), mapOf("thames water" to "Thames Water")))
        assertEquals(1.0, similarity("TESCO STORES 3211", "Tesco"), 0.0)
        assertEquals(0.0, similarity("UBER TRIP", "Tesco"), 0.0)
    }

    @Test
    fun matching() {
        val m = matchLines(
            listOf(
                line("2026-11-03", -1250, "TESCO STORES", "ofx:A1"),
                line("2026-11-05", -2000, "UBER TRIP"),
                line("2026-11-06", -2315, "BLUE BOTTLE"),
                line("2026-11-06", -999, "NETFLIX"),
            ),
            listOf(
                entry("t1", "2026-11-01", -1250, "Tesco", "ofx:A1"),
                entry("u1", "2026-11-02", -2000, "Uber"),
                entry("b1", "2026-11-05", -2015, "Blue Bottle"),
                entry("x1", "2026-10-01", -2000, "Uber"),
            ),
        )
        assertEquals(listOf(Matched(0, "t1", Kind.Ref), Matched(1, "u1", Kind.Exact), Matched(2, "b1", Kind.Close)), m.pairs)
        assertEquals(listOf(3), m.missing)
        assertEquals(listOf("x1"), m.extra)
    }

    private val words = object : HintWords {
        override fun anEntry() = "an entry"
        override fun exactUnticked(name: String, amount: String) = "$name ($amount) is exactly the difference. Is it on the statement?"
        override fun exactTicked(name: String, amount: String) = "$name ($amount) is exactly the difference. Is it really on the statement?"
        override fun wrongWayIn(name: String) = "$name is half the difference. Is it the wrong way round, money in rather than out?"
        override fun wrongWayOut(name: String) = "$name is half the difference. Is it the wrong way round, money out rather than in?"
        override fun swapped(name: String, meant: String, amount: String) = "Was $name $meant rather than $amount? Swapped digits are a common slip."
        override fun decimal(name: String, meant: String, amount: String) =
            "Was $name $meant rather than $amount? The decimal point may be in the wrong place."
        override fun pair(first: String, second: String) = "$first and $second together make the difference."
        override fun duplicate(name: String) = "$name appears twice. Is one a duplicate?"
        override fun late() = "Entries from the last days of the period can show on the next statement instead."
        override fun lookOut(amount: String) = "Look on the statement for a fee, a charge or interest of $amount that isn't in Kosha yet."
        override fun lookIn(amount: String) = "Look on the statement for interest, a refund or a payment in of $amount that isn't in Kosha yet."
    }

    private fun money(c: Long) = String.format(java.util.Locale.ROOT, "%.2f", c / 100.0)

    private fun hint(diff: Long, ticked: List<Pair<String, Long>>, unticked: List<Pair<String, Long>> = emptyList()) = differenceHints(
        diff,
        ticked.map { Item(it.first, it.second, "2026-11-10", it.first) },
        unticked.map { Item(it.first, it.second, "2026-11-10", it.first) },
        "2026-11-30",
        ::money,
        words,
    )

    @Test
    fun hints() {
        assertEquals(emptyList<Hint>(), hint(0, emptyList()))
        assertEquals(listOf("a"), hint(-1250, emptyList(), listOf("a" to -1250L))[0].ids)
        assertTrue(hint(-2000, listOf("a" to 1000L))[0].text.contains("wrong way round, money out rather than in"))
        // Typed 54.00 for 45.00: off by 9.00, divisible by 9.
        assertTrue(hint(900, listOf("a" to -5400L))[0].text.contains("Was a -45.00 rather than -54.00"))
        assertEquals(listOf("a", "b"), hint(-3000, emptyList(), listOf("a" to -1000L, "b" to -2000L))[0].ids)
        assertTrue(hint(-777, emptyList())[0].text.contains("a fee, a charge or interest of 7.77"))
        // Typed 164.67 for 1,646.70: a slid decimal point.
        assertTrue(hint(-148203, listOf("a" to -16467L))[0].text.contains("Was a -1646.70 rather than -164.67? The decimal point"))
        assertTrue(hint(148203, listOf("a" to -164670L))[0].text.contains("Was a -164.67 rather than -1646.70"))
    }

    @Test
    fun splits() {
        // One bank charge of 50.00 entered as 30 + 20: the one way to make it, named alike, so it matches.
        val split = matchLines(
            listOf(line("2026-11-05", -5000, "TESCO STORES 3211"), line("2026-11-06", -999, "NETFLIX")),
            listOf(entry("s1", "2026-11-04", -3000, "Tesco"), entry("s2", "2026-11-04", -2000, "Tesco")),
        )
        assertEquals(listOf(Matched(0, "s1", Kind.Split), Matched(0, "s2", Kind.Split)), split.pairs)
        assertEquals(listOf(1), split.missing)
        assertEquals(emptyList<String>(), split.extra)
        assertEquals(emptyList<Split>(), split.splits)
        // The other way round: two bank lines that Kosha has as one entry.
        val halves = matchLines(
            listOf(line("2026-11-05", -1500, "IKEA"), line("2026-11-07", -2500, "IKEA")),
            listOf(entry("k1", "2026-11-05", -4000, "Ikea")),
        )
        assertEquals(listOf(Matched(0, "k1", Kind.Split), Matched(1, "k1", Kind.Split)), halves.pairs)
        // Two ways to make 50.00: nothing is matched, and both come back to suggest.
        val twoWays = matchLines(
            listOf(line("2026-11-05", -5000, "TESCO")),
            listOf(entry("a", "2026-11-04", -3000, "Tesco"), entry("b", "2026-11-04", -2000, "Tesco"), entry("c", "2026-11-05", -2000, "Tesco")),
        )
        assertEquals(emptyList<Matched>(), twoWays.pairs)
        assertEquals(listOf(listOf("a", "c"), listOf("a", "b")), twoWays.splits.map { it.entries.sorted() })
        // One way, but the names don't agree: suggested, not matched. Out of the date window: neither.
        val unalike = matchLines(
            listOf(line("2026-11-05", -5000, "TESCO")),
            listOf(entry("a", "2026-11-04", -3000, "Uber"), entry("b", "2026-11-04", -2000, "Tesco")),
        )
        assertEquals(emptyList<Matched>(), unalike.pairs)
        assertEquals(listOf(Split(listOf(0), listOf("a", "b"))), unalike.splits)
        val far = matchLines(
            listOf(line("2026-11-20", -5000, "TESCO")),
            listOf(entry("a", "2026-11-04", -3000, "Tesco"), entry("b", "2026-11-04", -2000, "Tesco")),
        )
        assertEquals(emptyList<Split>(), far.splits)
        assertEquals(listOf(0), far.missing)
        // An exact match wins over a split.
        val exactFirst = matchLines(
            listOf(line("2026-11-05", -5000, "TESCO")),
            listOf(entry("a", "2026-11-04", -3000, "Tesco"), entry("b", "2026-11-04", -2000, "Tesco"), entry("x", "2026-11-05", -5000, "Tesco")),
        )
        assertEquals(listOf(Matched(0, "x", Kind.Exact)), exactFirst.pairs)
        // A reconciliation ticks every entry of a split.
        val plan = planReconciliation(
            lines = listOf(line("2026-11-05", -5000, "TESCO STORES")),
            open = listOf(entry("s1", "2026-11-04", -3000, "Tesco"), entry("s2", "2026-11-04", -2000, "Tesco")),
            done = emptyList(),
            statementDay = "2026-11-30",
            lastDay = null,
        )
        assertEquals(setOf("s1", "s2"), plan.matched)
        assertEquals(emptyList<Int>(), plan.missing)
        assertTrue(plan.ticks(Entry("s1", "2026-11-04", 0, "", null)) && plan.ticks(Entry("s2", "2026-11-04", 0, "", null)))
    }

    @Test
    fun corrections() {
        // What parseOfx gives for a file that replaces A1 and withdraws B0 (core.test.ts).
        val lines = listOf(line("2026-11-03", -1500, "TESCO", "ofx:A1b", "ofx:A1"), line("2026-11-05", -400, "KIOSK", "ofx:C1"))
        val fix = planReconciliation(
            lines = lines,
            open = listOf(
                entry("t1", "2026-11-03", -1250, "Tesco", "ofx:A1"),
                entry("n1", "2026-11-04", -999, "Netflix", "ofx:B0"),
                entry("k1", "2026-11-05", -400, "Kiosk", "ofx:C1"),
            ),
            done = emptyList(),
            statementDay = "2026-11-30",
            lastDay = null,
            withdrawn = listOf("ofx:B0"),
        )
        assertEquals(listOf(Matched(0, "t1", Kind.Corrected)), fix.close)
        assertEquals(setOf("k1"), fix.matched)
        assertEquals(emptyList<Int>(), fix.missing)
        assertEquals(listOf("n1"), fix.withdrawn)
        // The withdrawn entry stays, unticked and in the period, even dated before the statement's first line.
        assertFalse(fix.ticks(Entry("n1", "2026-11-04", 0, "", null)))
        assertFalse(fix.ticks(Entry("t1", "2026-11-03", 0, "", null)))
        val same = matchLines(listOf(lines[0].copy(amount = -1250)), listOf(entry("t1", "2026-11-03", -1250, "Tesco", "ofx:A1")))
        assertEquals(listOf(Matched(0, "t1", Kind.Ref)), same.pairs)
    }

    @Test
    fun importing() {
        assertEquals("groceries", starterCategory("Lidl LIDL SAGT DANKE", false, setOf("groceries")))
        assertEquals(null, starterCategory("Lidl", true, setOf("groceries")))
        // Out of one account and into another within three days, closest first, each line once.
        val pairs = pairTransfers(
            listOf(
                Leg("a1", "a", "2026-11-01", -5000),
                Leg("b1", "b", "2026-11-03", 5000),
                Leg("b2", "b", "2026-11-01", 5000),
                Leg("a2", "a", "2026-11-02", 5000),
            ),
        )
        assertEquals(listOf("a1" to "b2"), pairs)
        // Overlapping statements share lines; within one statement two equal lines are two payments.
        val merged = mergeStatements(
            listOf(
                StatementLines(listOf(line("2026-10-30", -500, "X"), line("2026-10-30", -500, "X"), line("2026-10-31", -100, "Y"))),
                StatementLines(listOf(line("2026-10-30", -500, "X"), line("2026-11-02", -700, "Z"))),
            ),
        )
        assertEquals(listOf(-500L, -500L, -100L, -700L), merged.map { it.amount })
        val gaps = statementGaps(
            listOf(Span("oct", "2026-10-01", "2026-10-31", 0, 1000), Span("nov", "2026-11-01", "2026-11-30", 900, 0), Span("jan", "2027-01-01", "2027-01-31", null, null)),
        )
        assertEquals(listOf(Gap.Opening("nov", 900, "oct", 1000), Gap.Uncovered("2026-12-01", "2026-12-31")), gaps)
    }
}
