package app.getkosha.accounts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.getkosha.design.AccountDot
import app.getkosha.design.Card
import app.getkosha.design.KText
import app.getkosha.design.KoshaTheme
import app.getkosha.design.PhIcon
import app.getkosha.design.R
import app.getkosha.design.Segmented
import app.getkosha.model.Sign
import app.getkosha.model.convert
import app.getkosha.recurring.dayText
import app.getkosha.recurring.money
import app.getkosha.recurring.rememberLoad
import app.getkosha.ui.LoadingNote
import app.getkosha.ui.LocalNav
import app.getkosha.ui.MoneyText
import app.getkosha.ui.Page
import app.getkosha.ui.Skeleton
import app.getkosha.ui.meta
import app.getkosha.ui.rememberKosha

/**
 * Currency exchanges by year: the route, what was sent (fee included) and received, the rate against the market
 * rate that day, the fee, and the cost ("Negative means you beat it").
 */
@Composable
fun ExchangesScreen() {
    val nav = LocalNav.current
    val kosha = rememberKosha()
    val m = meta()
    val c = KoshaTheme.colors
    val locale = KoshaTheme.locale
    val shown by kosha.currency.collectAsStateWithLifecycle()
    val display = shown ?: m?.primary ?: "eur"
    val loaded = rememberLoad<Exchanges>("exchanges") { get("/api/v1/exchanges", mapOf("limit" to 500)) }
    val list = loaded.value?.exchanges.orEmpty()
    val years = list.map { it.day.take(4) }.distinct()
    var picked by rememberSaveable { mutableStateOf<String?>(null) }
    val year = picked ?: years.firstOrNull()
    val rows = list.filter { it.day.startsWith(year ?: "") }.map { it to AccountsMath.exchange(it.sent, it.fee, it.received, it.marketRate) }
    val cost = rows.sumOf { (x, fx) -> convert(Math.round(fx.totalCost), x.fromCurrency, display, m?.rates.orEmpty()) }
    val wallets = m?.wallets.orEmpty()

    Page(
        stringResource(R.string.accounts_fx_title),
        onBack = { nav.popBackStack() },
        subtitle = if (rows.isEmpty()) null else pluralStringResource(R.plurals.app_acc_fx_count, rows.size, rows.size) + " · " + stringResource(R.string.accounts_fx_cost, money(cost, display)),
    ) {
        if (loaded.value == null) {
            if (loaded.refreshing) Skeleton(Modifier.fillMaxWidth(), 120.dp) else LoadingNote()
            return@Page
        }
        if (years.size > 1) Segmented(years.map { it to it }, year, { picked = it }, Modifier.fillMaxWidth(), small = true)
        if (rows.isEmpty()) {
            KText(stringResource(R.string.app_acc_fx_empty), style = KoshaTheme.type.small, color = c.ink3)
        }
        rows.forEach { (x, fx) ->
            val from = wallets.indexOfFirst { it.id == x.fromAccountId }
            val to = wallets.indexOfFirst { it.id == x.toAccountId }
            val total = Math.round(fx.totalCost)
            val counted = x.marketRate != null || x.fee != 0L
            Card(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        KText(dayText(x.day, "EEEdMMM"), Modifier.weight(1f), style = KoshaTheme.type.small, color = c.ink3)
                        if (counted) KText(stringResource(R.string.app_acc_fx_cost, money(total, x.fromCurrency)), style = KoshaTheme.type.small, color = if (total < 0) c.pos else c.ink)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AccountDot(c.account(from))
                        KText(wallets.getOrNull(from)?.name ?: "", style = KoshaTheme.type.head, maxLines = 1)
                        PhIcon("arrow-right", size = 14.dp, tint = c.ink3)
                        AccountDot(c.account(to))
                        KText(wallets.getOrNull(to)?.name ?: "", Modifier.weight(1f, fill = false), style = KoshaTheme.type.head, maxLines = 1)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MoneyText(x.sent, x.fromCurrency, Modifier.weight(1f), sign = Sign.None)
                        PhIcon("arrow-right", size = 16.dp, tint = c.ink3)
                        MoneyText(x.received, x.toCurrency, Modifier.weight(1f), sign = Sign.None, align = androidx.compose.ui.text.style.TextAlign.End)
                    }
                    Row {
                        KText(
                            listOfNotNull(
                                stringResource(R.string.app_acc_fx_rate, AccountsMath.rate(fx.rate, locale)),
                                x.marketRate?.let { stringResource(R.string.app_acc_fx_market, AccountsMath.rate(it, locale)) },
                                x.provider.ifEmpty { null },
                            ).joinToString(" · "),
                            Modifier.weight(1f),
                            style = KoshaTheme.type.xs,
                            color = c.ink3,
                        )
                        KText(
                            if (x.fee != 0L) stringResource(R.string.app_acc_fx_fee, money(x.fee, x.fromCurrency)) else stringResource(R.string.app_acc_fx_no_fee),
                            style = KoshaTheme.type.xs,
                            color = c.ink3,
                        )
                    }
                }
            }
        }
        KText(stringResource(R.string.accounts_fx_footer), style = KoshaTheme.type.xs, color = c.ink3)
    }
}
