package app.getkosha.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.getkosha.design.*
import app.getkosha.design.R
import app.getkosha.model.Loans
import app.getkosha.model.convert

/** All-date balances, fetched independently of the selected report month. */
@Composable
fun LoanSummary(showLink: Boolean = true) {
    val kosha = rememberKosha()
    val nav = LocalNav.current
    val m = meta() ?: return
    val version by kosha.version.collectAsStateWithLifecycle()
    val display by kosha.currency.collectAsStateWithLifecycle()
    val currency = display ?: m.primary
    val loaded = remember(version) { kosha.load<Loans>("loans") { get("/api/v1/loans") } }
        .collectAsStateWithLifecycle(null).value
    Card {
        if (loaded?.offline == true) KText(stringResource(R.string.common_offline_body))
        loaded?.error?.let { KText(it, color = KoshaTheme.colors.bad) }
        if (showLink) ListRow(onClick = { nav.navigate(Route.Loans) }) { KText(stringResource(R.string.loans_title)) }
        KText(stringResource(R.string.loans_current), style = KoshaTheme.type.small)
        val loans = loaded?.value?.loans
        if (loans == null) {
            KText(loaded?.error ?: stringResource(if (loaded?.offline == true) R.string.common_offline_body else R.string.common_loading))
        } else {
            listOf("lent" to R.string.loans_owed_to_you, "borrowed" to R.string.loans_you_owe).forEach { (direction, label) ->
                Column {
                    KText(stringResource(label))
                    MoneyText(loans.filter { it.direction == direction }.sumOf { convert(it.remaining, it.currency, currency, m.rates) }, currency)
                }
            }
            KText(stringResource(R.string.loans_currency_note), style = KoshaTheme.type.small)
            loans.filter { it.remaining > 0 && it.dueDay != null && it.dueDay!! <= app.getkosha.model.Dates.today(m.tz).plusDays(7).toString() }
                .sortedBy { it.dueDay }.take(3).forEach { loan ->
                    ListRow(onClick = { nav.navigate(Route.LoanDetail(loan.id)) }) {
                        Column {
                            KText(loan.person + " · " + stringResource(if (loan.direction == "lent") R.string.loans_owed_to_you else R.string.loans_you_owe))
                            KText(stringResource(if (loan.dueDay!! < app.getkosha.model.Dates.today(m.tz).toString()) R.string.loans_overdue else R.string.loans_due_on, loan.dueDay!!))
                            MoneyText(loan.remaining, loan.currency)
                        }
                    }
                }
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
