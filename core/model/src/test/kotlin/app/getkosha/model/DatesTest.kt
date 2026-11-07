package app.getkosha.model

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

class DatesTest {
    // 23:30 UTC on 31 Oct is already 1 Nov in Berlin: days follow the user's zone, not the phone's.
    private val now = Instant.parse("2026-10-31T23:30:00Z")

    @Test fun inTheUsersZone() {
        assertEquals(YearMonth.of(2026, 10), Dates.month("UTC", now))
        assertEquals(YearMonth.of(2026, 11), Dates.month("Europe/Berlin", now))
        assertEquals(LocalDate.of(2026, 11, 1), Dates.today("Europe/Berlin", now))
        assertEquals(YearMonth.of(2026, 10), Dates.month("Not/AZone", now))
    }

    @Test fun parses() {
        assertEquals(YearMonth.of(2026, 11), Dates.parseMonth("2026-11"))
        assertEquals(LocalDate.of(2026, 11, 7), Dates.parseDay("2026-11-07"))
    }
}
