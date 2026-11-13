package app.getkosha.ui

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.getkosha.design.*
import app.getkosha.design.R
import app.getkosha.model.Dates
import app.getkosha.model.Loan
import app.getkosha.model.Loans
import app.getkosha.model.Money
import app.getkosha.model.convert
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** All-date loans, fetched independently of the selected report month. Null until loaded. */
@Composable
fun rememberLoans(): List<Loan>? {
    val kosha = rememberKosha()
    val version by kosha.version.collectAsStateWithLifecycle()
    return remember(version) { kosha.load<Loans>("loans") { get("/api/v1/loans") } }
        .collectAsStateWithLifecycle(null).value?.value?.loans
}

/** "10 Nov 2026" in the language's own order. */
@Composable
fun loanDay(day: String): String {
    val locale = KoshaTheme.locale
    return remember(day, locale) {
        DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "dMMMyyyy"), locale).format(LocalDate.parse(day))
    }
}

/** "Due 20 Nov 2026" or "Overdue · 2 Nov 2026" (warning colour), for a loan with money still owed. */
@Composable
fun LoanDueText(loan: Loan, today: String) {
    val due = loan.dueDay ?: return
    if (loan.remaining <= 0) return
    val late = due < today
    KText(
        stringResource(if (late) R.string.loans_overdue else R.string.loans_due_on, loanDay(due)),
        style = KoshaTheme.type.xs,
        color = if (late) KoshaTheme.colors.warn else KoshaTheme.colors.ink3,
    )
}

/** Owed to you | You owe, side by side, in the shown currency at current rates. */
@Composable
fun LoanTotals(loans: List<Loan>) {
    val kosha = rememberKosha()
    val m = meta() ?: return
    val shown by kosha.currency.collectAsStateWithLifecycle()
    val currency = shown ?: m.primary
    val c = KoshaTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            listOf("lent" to R.string.loans_owed_to_you, "borrowed" to R.string.loans_you_owe).forEachIndexed { i, (direction, label) ->
                if (i > 0) Box(Modifier.fillMaxHeight().width(1.dp).background(c.line))
                Column(Modifier.weight(1f).padding(start = if (i > 0) 16.dp else 0.dp, end = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    KText(stringResource(label), style = KoshaTheme.type.small, color = c.ink3)
                    HeroMoney(loans.filter { it.direction == direction }.sumOf { convert(it.remaining, it.currency, currency, m.rates) }, currency, size = 26.sp)
                }
            }
        }
        KText(stringResource(R.string.loans_current), style = KoshaTheme.type.xs, color = c.ink3)
    }
}

/** Overview's Loans card: the all-date totals and loans due within a week. Hidden while nothing is owed. */
@Composable
fun LoansCard() {
    val nav = LocalNav.current
    val m = meta() ?: return
    val loans = rememberLoans() ?: return
    if (loans.none { it.remaining > 0 }) return
    val c = KoshaTheme.colors
    val today = remember(m.tz) { Dates.today(m.tz) }
    val due = loans.filter { it.remaining > 0 && it.dueDay != null && it.dueDay!! <= today.plusDays(7).toString() }
        .sortedBy { it.dueDay }.take(3)
    Card(Modifier.fillMaxWidth(), tight = true) {
        Row(
            Modifier.fillMaxWidth().clickable(role = Role.Button) { nav.navigate(Route.Loans) }
                .heightIn(min = 48.dp).padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PhIcon("hand-coins", tint = c.ink2)
            KText(stringResource(R.string.loans_title), Modifier.weight(1f).semantics { heading() }, style = KoshaTheme.type.head)
            PhIcon("caret-right", size = 16.dp, tint = c.ink3)
        }
        Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) { LoanTotals(loans) }
        due.forEach { loan ->
            Hairline()
            ListRow(onClick = { nav.navigate(Route.LoanDetail(loan.id)) }) {
                Column(Modifier.weight(1f)) {
                    KText(loan.person, maxLines = 1)
                    LoanDueText(loan, today.toString())
                }
                Column(horizontalAlignment = Alignment.End) {
                    MoneyText(loan.remaining, loan.currency)
                    KText(stringResource(if (loan.direction == "lent") R.string.loans_owed_to_you else R.string.loans_you_owe), style = KoshaTheme.type.xs, color = c.ink3)
                }
            }
        }
    }
}

/** Accounts' one-line link to Loans: "Owed to you €3,000.00 · You owe €0.00". Hidden while nothing is owed. */
@Composable
fun LoansLink() {
    val nav = LocalNav.current
    val kosha = rememberKosha()
    val m = meta() ?: return
    val loans = rememberLoans() ?: return
    if (loans.none { it.remaining > 0 }) return
    val shown by kosha.currency.collectAsStateWithLifecycle()
    val currency = shown ?: m.primary
    val locale = KoshaTheme.locale
    val c = KoshaTheme.colors
    val line = listOf("lent" to R.string.loans_owed_to_you, "borrowed" to R.string.loans_you_owe).map { (direction, label) ->
        stringResource(label) + " " + Money.format(loans.filter { it.direction == direction }.sumOf { convert(it.remaining, it.currency, currency, m.rates) }, currency, locale)
    }.joinToString(" · ")
    Card(Modifier.fillMaxWidth(), tight = true) {
        ListRow(onClick = { nav.navigate(Route.Loans) }) {
            IconTile("hand-coins")
            Column(Modifier.weight(1f)) {
                KText(stringResource(R.string.loans_title))
                KText(if (hideAmounts()) stringResource(R.string.loans_current) else line, style = KoshaTheme.type.xs, color = c.ink3)
            }
            PhIcon("caret-right", size = 16.dp, tint = c.ink3)
        }
    }
}

@Composable
fun AccountLoansSwitch(loans: Boolean) {
    val nav = LocalNav.current
    Segmented(listOf(false to stringResource(R.string.accounts_title), true to stringResource(R.string.loans_title)), loans, { selected ->
        if (selected != loans) {
            if (selected) nav.navigate(Route.Loans) else { nav.navigate(Route.Accounts) { popUpTo<Route.Accounts> { inclusive = true }; launchSingleTop = true } }
        }
    })
}
