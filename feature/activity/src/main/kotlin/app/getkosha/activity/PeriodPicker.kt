package app.getkosha.activity

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.getkosha.design.Button
import app.getkosha.design.Card
import app.getkosha.design.Hairline
import app.getkosha.design.KText
import app.getkosha.design.KoshaTheme
import app.getkosha.design.MonthGrid
import app.getkosha.design.PhIcon
import app.getkosha.design.R
import app.getkosha.design.Radio
import app.getkosha.design.Sheet
import app.getkosha.model.Dates
import app.getkosha.ui.HeaderPeriod
import app.getkosha.ui.MonthSheet
import app.getkosha.ui.rememberKosha
import app.getkosha.ui.shortDay
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle

/** Activity's dates in the header: the label opens the picker, and a month or a year steps back and on. */
@Composable
internal fun periodHeader(shown: Period, current: YearMonth, onOpen: () -> Unit, onPick: (Period) -> Unit): HeaderPeriod {
    val locale = KoshaTheme.locale
    val label = when (shown) {
        is Period.Month -> YearMonth.parse(shown.month).let { ym ->
            ym.month.getDisplayName(TextStyle.FULL_STANDALONE, locale) + if (ym.year != current.year) " ${ym.year}" else ""
        }
        is Period.Year -> shown.year.toString()
        Period.All -> stringResource(R.string.tx_period_all)
        is Period.Range -> shortDay(LocalDate.parse(shown.from), locale, year = true) + " – " + shortDay(LocalDate.parse(shown.to), locale, year = true)
    }
    val full = if (shown is Period.Month) YearMonth.parse(shown.month).format(DateTimeFormatter.ofPattern("LLLL yyyy", locale)) else label
    val year = shown is Period.Year
    val back = stringResource(if (year) R.string.tx_previous_year else R.string.shell_previous_month)
    val on = stringResource(if (year) R.string.tx_next_year else R.string.shell_next_month)
    return HeaderPeriod(
        label = label,
        spoken = stringResource(R.string.tx_period_label, full),
        onOpen = onOpen,
        previous = shown.shift(-1)?.let { p -> back to { onPick(p) } },
        next = shown.shift(1)?.let { p -> on to { onPick(p) } },
    )
}

/**
 * Picks Activity's dates (transactions.tsx PeriodPicker): a month on the month grid, a year, every date, or two
 * days. Switching to a month or a year keeps where you are, but never moves past now.
 */
@Composable
internal fun PeriodSheet(shown: Period, current: YearMonth, onPick: (Period) -> Unit, onDismiss: () -> Unit) {
    val kosha = rememberKosha()
    var step by remember { mutableStateOf("kind") }
    val anchor = shown.days?.second?.let(YearMonth::from)?.takeIf { it < current } ?: current
    when (step) {
        "month" -> MonthSheet(
            (shown as? Period.Month)?.let { YearMonth.parse(it.month) } ?: anchor,
            onPick = { onPick(Period.Month(it.toString())) },
            onDismiss = onDismiss,
        )
        "year" -> Sheet(stringResource(R.string.tx_period_year), onDismiss) {
            Card(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), tight = true) {
                (current.year downTo current.year - 19).forEachIndexed { i, y ->
                    if (i > 0) Hairline()
                    Choice(y.toString(), (shown as? Period.Year)?.year == y) { onPick(Period.Year(y)) }
                }
            }
        }
        "range" -> {
            val days = shown.days
            RangeSheet(days?.first ?: current.atDay(1), days?.second ?: Dates.today(kosha.tz), onDismiss) { from, to ->
                onPick(Period.Range(Dates.text(from), Dates.text(to)))
            }
        }
        else -> Sheet(stringResource(R.string.tx_period_custom_title), onDismiss) {
            Card(Modifier.fillMaxWidth(), tight = true) {
                Choice(stringResource(R.string.tx_period_month), shown is Period.Month) { step = "month" }
                Hairline()
                Choice(stringResource(R.string.tx_period_year), shown is Period.Year) { step = "year" }
                Hairline()
                Choice(stringResource(R.string.tx_period_all), shown == Period.All) { onPick(Period.All) }
                Hairline()
                Choice(stringResource(R.string.tx_period_custom), shown is Period.Range) { step = "range" }
            }
        }
    }
}

@Composable
private fun Choice(label: String, on: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectable(on, role = Role.RadioButton, onClick = onClick)
            .heightIn(min = KoshaTheme.dims.settingsRow).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        KText(label, Modifier.weight(1f))
        Radio(on)
    }
}

/** From and Until on one month grid: tap a field, then a day. Until is never before From. */
@Composable
private fun RangeSheet(start: LocalDate, end: LocalDate, onDismiss: () -> Unit, onPick: (LocalDate, LocalDate) -> Unit) {
    val kosha = rememberKosha()
    val locale = KoshaTheme.locale
    val c = KoshaTheme.colors
    val today = Dates.today(kosha.tz)
    var from by remember { mutableStateOf(start) }
    var to by remember { mutableStateOf(end) }
    var editing by remember { mutableStateOf("from") }
    var month by remember { mutableStateOf(YearMonth.from(start)) }
    Sheet(stringResource(R.string.tx_period_custom_title), onDismiss) {
        Card(Modifier.fillMaxWidth(), tight = true) {
            listOf("from" to (stringResource(R.string.tx_date_from) to from), "to" to (stringResource(R.string.tx_until) to to)).forEachIndexed { i, (key, v) ->
                if (i > 0) Hairline()
                Row(
                    Modifier.fillMaxWidth().clickable(role = Role.Button, onClickLabel = v.first) { editing = key; month = YearMonth.from(v.second) }
                        .heightIn(min = KoshaTheme.dims.settingsRow).padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    KText(v.first, Modifier.weight(1f), style = KoshaTheme.type.small, color = c.ink3)
                    KText(shortDay(v.second, locale, year = true), color = if (editing == key) c.ink else c.ink2)
                    if (editing == key) PhIcon("caret-down", Modifier.padding(start = 6.dp), size = 14.dp, tint = c.ink3)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        MonthGrid(month, if (editing == "from") from else to, today, { day ->
            if (editing == "from") {
                from = day
                if (to < day) to = day
                editing = "to"
            } else if (day < from) {
                from = day
            } else to = day
        }, { month = it })
        Spacer(Modifier.height(12.dp))
        Button(stringResource(R.string.tx_show), { onPick(from, to) }, Modifier.fillMaxWidth())
    }
}
