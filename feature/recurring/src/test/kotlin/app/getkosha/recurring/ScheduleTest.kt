package app.getkosha.recurring

import app.getkosha.model.KoshaJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class ScheduleTest {
    private fun d(s: String) = LocalDate.parse(s)

    @Test fun monthStepsKeepTheDayClampedToShortMonths() {
        assertEquals(d("2026-02-28"), Schedule.occurrence(d("2026-01-31"), 1, "month", 1))
        assertEquals(d("2026-03-31"), Schedule.occurrence(d("2026-01-31"), 1, "month", 2))
        assertEquals(d("2028-02-29"), Schedule.occurrence(d("2024-02-29"), 4, "year", 1))
        assertEquals(d("2026-01-15"), Schedule.occurrence(d("2026-01-01"), 2, "week", 1))
    }

    @Test fun backfillCountsFromTheStartUntilToday() {
        // 10 Aug, 10 Sep, 10 Oct: "Kosha will record payments from 10 Aug until today: 3 payments."
        assertEquals(3, Schedule.backfill(d("2026-08-10"), 1, "month", null, d("2026-11-07")))
        assertEquals(4, Schedule.backfill(d("2026-08-07"), 1, "month", null, d("2026-11-07")))
        assertEquals(0, Schedule.backfill(d("2026-11-07"), 1, "month", null, d("2026-11-07")))
        assertEquals(2, Schedule.backfill(d("2026-08-10"), 1, "month", d("2026-09-30"), d("2026-11-07")))
    }

    @Test fun nextStopsAtTheEnd() {
        assertEquals(d("2026-12-01"), Schedule.next(d("2025-01-01"), 1, "month", null, d("2026-11-07")))
        assertNull(Schedule.next(d("2025-01-01"), 1, "month", d("2026-10-31"), d("2026-11-07")))
        assertEquals(d("2027-03-03"), Schedule.next(d("2024-03-03"), 1, "year", null, d("2026-11-07")))
    }

    @Test fun archiveNamesNumberClashes() {
        assertEquals(
            listOf("2026-10 bill.pdf", "2026-10 bill (2).pdf", "2026-09 a-b.pdf"),
            Schedule.archiveNames(listOf("2026-10-20" to "bill.pdf", "2026-10-21" to "bill.pdf", "2026-09-01" to "a/b.pdf")),
        )
    }

    @Test fun readsTheWebShape() {
        val json = """{"subscriptions":[{"id":"s1","name":"Netflix","wallet_id":"a1","category_id":"fun","amount":-1299,
            "every":1,"unit":"year","start_day":"2025-01-15","end_day":null,"auto_pay":1,"kind":"subscription",
            "to_wallet_id":null,"variable":0,"currency":"eur","ended":false,"monthly":-108.25,"created_at":1,
            "instances":[{"due_day":"2026-01-15","day":"2026-01-15","amount":-1299,"note":"","status":"upcoming","edited":false,"tx_id":null}],
            "next":null,"history":[{"id":"x","due_day":"2025-01-15","day":"2025-01-15","date":1,"amount":-1299,"paid":1,"wallet_id":"a1"}]}],
            "income":[{"currency":"eur","total":388800}]}"""
        val data = KoshaJson.decodeFromString(SubscriptionsData.serializer(), json)
        val s = data.subscriptions.single()
        assertEquals(-108L, s.perMonth)
        assertEquals(Group.Subscriptions, s.group())
        assertEquals(listOf(d("2026-01-15")), Schedule.inMonth(s, YearMonth.of(2026, 1)))
        assertEquals(emptyList<LocalDate>(), Schedule.inMonth(s, YearMonth.of(2026, 2)))
    }
}
