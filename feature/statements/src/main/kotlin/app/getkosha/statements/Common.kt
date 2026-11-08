package app.getkosha.statements

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.getkosha.design.Button
import app.getkosha.design.Chip
import app.getkosha.design.KText
import app.getkosha.design.KoshaTheme
import app.getkosha.design.MonthGrid
import app.getkosha.design.PhIcon
import app.getkosha.design.R
import app.getkosha.design.Radio
import app.getkosha.design.Sheet
import app.getkosha.design.AmountDisplay
import app.getkosha.design.AmountKeypad
import app.getkosha.model.Dates
import app.getkosha.model.EntryKind
import app.getkosha.model.Keypad
import app.getkosha.model.Money
import app.getkosha.model.Sign
import app.getkosha.network.KoshaApi
import app.getkosha.ui.hideAmounts
import app.getkosha.ui.rememberKosha
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import kotlin.math.abs

// What Reconcile and Import share: reading a statement on the server, short days, money inside sentences, and
// the sheets for a day, an amount and a choice.

/**
 * Reads a file on the server (POST /api/app/import/read). A [layout] re-reads a CSV with the columns as set.
 * [walletId] names the account when it exists already (Reconcile); Import's accounts may not exist yet.
 */
suspend fun KoshaApi.read(file: Picked, card: Boolean, layout: Layout? = null, walletId: String? = null): Read =
    post("/api/app/import/read", ReadBody(file.name, file.content, if (file.pdf) "pdf" else null, card, layout?.json(), walletId))

/**
 * A CSV layout with the columns the AI model worked out (POST /api/app/import/layout), for one Kosha couldn't read
 * alone, with its date format worked out from that column. Null when the model couldn't tell.
 */
suspend fun KoshaApi.aiLayout(read: Read): Layout? {
    val base = read.layout ?: return null
    val roles = runCatching {
        post<RowsBody, Roles>("/api/app/import/layout", RowsBody(read.rows.take(12).map { it.take(60) }))
    }.getOrNull() ?: return null
    val format = guessDateFormat(read.rows.drop(roles.header + 1).map { it.getOrNull(roles.date) ?: "" }) ?: base.dateFormat
    return base.copy(
        header = roles.header, date = roles.date, description = roles.description, amount = roles.amount,
        debit = roles.debit, credit = roles.credit, balance = roles.balance, dateFormat = format,
    )
}

/** A CSV's closing and opening balances from its running balance column (import.tsx csvStmt). A card's is ambiguous. */
data class Balances(val closing: Long?, val opening: Long?, val closingDay: String?)

fun csvBalances(lines: List<Line>, card: Boolean): Balances {
    if (lines.isEmpty()) return Balances(null, null, null)
    val asc = lines.size > 1 && lines.first().day <= lines.last().day
    val latest = if (asc) lines.last() else lines.first()
    val oldest = if (asc) lines.first() else lines.last()
    return Balances(
        closing = if (card) null else latest.balance,
        opening = if (card || oldest.balance == null) null else oldest.balance - oldest.amount,
        closingDay = lines.maxOf { it.day },
    )
}

/** The first and last day of some lines. */
fun span(lines: List<Line>): Pair<String, String>? =
    if (lines.isEmpty()) null else lines.minOf { it.day } to lines.maxOf { it.day }

/** A day as statements write it: "31 Oct", with the year when it isn't this one. */
@Composable
fun shortDay(day: String): String {
    val d = runCatching { LocalDate.parse(day) }.getOrNull() ?: return day
    val year = Dates.today(rememberKosha().tz).year
    return d.format(DateTimeFormatter.ofPattern(if (d.year == year) "d MMM" else "d MMM yyyy", KoshaTheme.locale))
}

/** An amount inside a sentence, with a true minus; masked while Hide amounts is on. */
@Composable
fun moneyWords(): (Long, String) -> String {
    val locale = KoshaTheme.locale
    val hidden = hideAmounts()
    return { minor, currency -> if (hidden) "••••" else Money.format(minor, currency, locale, Sign.Natural) }
}

/** A count as the language writes it ("1,204"). */
@Composable
fun countText(n: Int): String = java.text.NumberFormat.getIntegerInstance(KoshaTheme.locale).format(n)

/** A field that opens a picker: radius 14, a hairline, 46 high, an optional icon. */
@Composable
fun PickField(value: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: String? = null, label: String? = null, muted: Boolean = false, mono: Boolean = false) {
    val c = KoshaTheme.colors
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier
            .heightIn(min = 48.dp)
            .clip(shape)
            .background(c.surface)
            .border(1.dp, c.line, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (icon != null) PhIcon(icon, size = 18.dp, tint = c.ink2)
        if (label != null) KText(label, style = KoshaTheme.type.xs, color = c.ink3)
        KText(value, Modifier.weight(1f), style = if (mono) KoshaTheme.type.num else KoshaTheme.type.body, color = if (muted) c.ink3 else c.ink, maxLines = 1)
    }
}

/** A day on the month grid, in the user's time zone. */
@Composable
fun DaySheet(title: String, day: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val today = Dates.today(rememberKosha().tz)
    val picked = runCatching { LocalDate.parse(day) }.getOrDefault(today)
    var month by remember { mutableStateOf(YearMonth.from(picked)) }
    Sheet(title, onDismiss) {
        MonthGrid(month, picked, today, onPick = { onPick(it.toString()); onDismiss() }, onMonth = { month = it })
    }
}

/**
 * An amount on Kosha's keypad, never the system keyboard. [signed] adds a "below zero" switch for a balance that
 * can be overdrawn. Done with nothing typed clears it (null).
 */
@Composable
fun AmountSheet(title: String, start: Long?, currency: String, signed: Boolean, onDone: (Long?) -> Unit, onDismiss: () -> Unit) {
    val decimals = Money.decimals(currency)
    var input by rememberSaveable {
        mutableStateOf(start?.let { BigDecimal.valueOf(abs(it), 2).stripTrailingZeros().toPlainString() } ?: "")
    }
    var negative by rememberSaveable { mutableStateOf((start ?: 0) < 0) }
    Sheet(title, onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AmountDisplay(input, currency, if (negative) EntryKind.Expense else EntryKind.Transfer)
            if (signed) {
                Chip(
                    stringResource(R.string.app_rcn_below_zero),
                    Modifier.align(Alignment.CenterHorizontally),
                    on = negative,
                    small = true,
                    onClick = { negative = !negative },
                    leading = { PhIcon("plus-minus", size = 16.dp) },
                )
            }
            AmountKeypad(input, { input = it }, decimals = decimals)
            val value = Keypad.evaluate(input, decimals)
            Button(
                stringResource(R.string.common_done),
                onClick = {
                    if (input.isEmpty()) onDone(null)
                    else value?.let { v ->
                        val minor = v.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact()
                        onDone(if (negative) -minor else minor)
                    }
                    onDismiss()
                },
                Modifier.fillMaxWidth(),
                enabled = input.isEmpty() || value != null,
            )
        }
    }
}

/** One choice from a short list, as radio rows in a sheet. */
@Composable
fun ChoiceSheet(title: String, options: List<Pair<String, String>>, selected: String?, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    Sheet(title, onDismiss) {
        Column(Modifier.selectableGroup()) {
            options.forEach { (value, label) ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .selectable(value == selected, role = Role.RadioButton) { onPick(value); onDismiss() }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    KText(label, Modifier.weight(1f))
                    Radio(value == selected)
                }
            }
        }
    }
}

/** A note on a tinted ground: warn for things to check, soft for information. */
@Composable
fun Note(text: String, modifier: Modifier = Modifier, icon: String = "warning", tone: Color = KoshaTheme.colors.warn) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(tone.copy(alpha = 0.1f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PhIcon(icon, size = 16.dp, tint = tone)
        KText(text, Modifier.weight(1f), style = KoshaTheme.type.small, color = tone)
    }
}

/** A section heading above a card: an uppercase caption, with something on the right. */
@Composable
fun Section(title: String, aside: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            app.getkosha.design.Caption(title, Modifier.weight(1f))
            if (aside != null) KText(aside, style = KoshaTheme.type.xs, color = KoshaTheme.colors.ink3)
        }
        content()
    }
}

/** A "Show N more" or group row inside a card: label, line under it, and a caret. */
@Composable
fun MoreRow(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, sub: String? = null, open: Boolean = false, head: Boolean = false) {
    val c = KoshaTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            KText(text, style = if (head) KoshaTheme.type.head else KoshaTheme.type.small, color = if (head) c.ink else c.ink2)
            if (sub != null) KText(sub, style = KoshaTheme.type.xs, color = c.ink3)
        }
        PhIcon(if (open) "caret-up" else "caret-down", size = 16.dp, tint = c.ink3)
    }
}

/** A small round spinner stand-in: a soft dot, as the design's reading rows show. */
@Composable
fun Busy(modifier: Modifier = Modifier) {
    Box(modifier.padding(3.dp).heightIn(min = 18.dp).clip(RoundedCornerShape(9.dp)).background(KoshaTheme.colors.soft).padding(9.dp))
}
