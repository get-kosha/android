package app.getkosha.model

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Days and months as Kosha stores them: "YYYY-MM-DD" and "YYYY-MM" in the user's time zone from settings,
 * never the phone's (core.ts dayOf, monthOf).
 */
object Dates {
    private fun zone(tz: String): ZoneId = runCatching { ZoneId.of(tz) }.getOrDefault(ZoneId.of("UTC"))

    fun today(tz: String, now: Instant = Instant.now()): LocalDate = now.atZone(zone(tz)).toLocalDate()

    fun now(tz: String, now: Instant = Instant.now()): LocalTime = now.atZone(zone(tz)).toLocalTime().withSecond(0).withNano(0)

    fun month(tz: String, now: Instant = Instant.now()): YearMonth = YearMonth.from(today(tz, now))

    // Named apart from today(tz) and month(tz): a String overload of month() took "UTC" for "YYYY-MM".
    fun parseDay(text: String): LocalDate = LocalDate.parse(text)

    fun parseMonth(text: String): YearMonth = YearMonth.parse(text)

    fun text(day: LocalDate): String = day.toString()

    fun text(month: YearMonth): String = month.toString()

    /** "HH:MM", 24-hour, as the API writes times. */
    fun text(time: LocalTime): String = time.format(DateTimeFormatter.ofPattern("HH:mm"))
}
