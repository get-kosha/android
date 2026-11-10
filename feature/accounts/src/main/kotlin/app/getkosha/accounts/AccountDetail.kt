package app.getkosha.accounts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.getkosha.design.Button
import app.getkosha.design.ButtonSize
import app.getkosha.design.ButtonStyle
import app.getkosha.design.Card
import app.getkosha.design.Hairline
import app.getkosha.design.IconButton
import app.getkosha.design.IconTile
import app.getkosha.design.KText
import app.getkosha.design.KoshaTheme
import app.getkosha.design.ListRow
import app.getkosha.design.PhIcon
import app.getkosha.design.Pill
import app.getkosha.design.R
import app.getkosha.design.categoryIconName
import app.getkosha.design.dayLabel
import app.getkosha.model.AccountType
import app.getkosha.model.Sign
import app.getkosha.model.Transaction
import app.getkosha.model.TransactionList
import app.getkosha.model.Wallet
import app.getkosha.recurring.ConfirmSheet
import app.getkosha.recurring.HeroMoney
import app.getkosha.recurring.dayText
import app.getkosha.recurring.monthText
import app.getkosha.recurring.percent
import app.getkosha.recurring.rememberLoad
import app.getkosha.recurring.rememberWriter
import app.getkosha.recurring.today
import app.getkosha.recurring.typeLabel
import app.getkosha.ui.LocalNav
import app.getkosha.ui.MoneyText
import app.getkosha.ui.Page
import app.getkosha.ui.ActivitySearch
import app.getkosha.ui.Route
import app.getkosha.ui.openActivity
import app.getkosha.ui.meta
import app.getkosha.ui.rememberKosha
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** The account by id from meta, or null once it is gone (deleted) or before meta loads. */
@Composable
fun wallet(id: String): Pair<Wallet?, Int> {
    val wallets = meta()?.wallets.orEmpty()
    val i = wallets.indexOfFirst { it.id == id }
    return wallets.getOrNull(i) to i
}

/** "Bank account · EUR". */
@Composable
private fun subtitle(w: Wallet) = (if (w.type == AccountType.Bank) stringResource(R.string.accounts_type_bank_account) else typeLabel(w.type)) +
    " · " + w.currency.uppercase() + if (w.isArchived) " · " + stringResource(R.string.accounts_archived) else ""

/**
 * A bank, cash or card account: its balance, this month's in, out and moved, whether it matches the bank
 * (Reconcile, Fix balance), recent entries, Edit, Archive and Delete. [fix] opens Fix balance at once, as
 * Route.FixBalance does; closing it then goes back.
 */
@Composable
fun AccountDetailScreen(id: String, fix: Boolean = false) {
    val nav = LocalNav.current
    val kosha = rememberKosha()
    val c = KoshaTheme.colors
    val today = today()
    val (w, _) = wallet(id)
    val month by kosha.month.collectAsStateWithLifecycle()
    val shown by kosha.currency.collectAsStateWithLifecycle()
    val display = shown ?: meta()?.primary ?: "eur"
    var fixing by remember { mutableStateOf(fix) }
    if (w == null) {
        Page(stringResource(R.string.accounts_title), onBack = { nav.popBackStack() }) { }
        return
    }
    val overview = rememberLoad<AccountsOverview>("accounts-overview/$month/$display") {
        get("/api/app/accounts-overview", mapOf("month" to month.toString(), "currency" to shown))
    }.value
    val flow = overview?.accounts?.get(id)
    val recent = rememberLoad<TransactionList>("account/$id/recent") {
        // Without a range the search covers this month only; recent entries go back as far as needed.
        get("/api/v1/transactions", mapOf("accountId" to id, "from" to "2000-01-01", "limit" to 5, "includeCorrections" to true))
    }.value?.transactions.orEmpty()

    Page(
        w.name,
        onBack = { nav.popBackStack() },
        subtitle = subtitle(w),
        actions = { Button(stringResource(R.string.common_edit), { nav.navigate(Route.AccountForm(id)) }, style = ButtonStyle.Secondary, size = ButtonSize.Small) },
    ) {
        Card(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                KText(stringResource(if (w.type == AccountType.Credit) R.string.accounts_currently_owed else R.string.accounts_balance), style = KoshaTheme.type.small, color = c.ink3)
                HeroMoney(AccountsMath.shownWorth(w), w.currency)
                when {
                    w.type == AccountType.Credit && w.creditLimit != null ->
                        KText(stringResource(R.string.accounts_available, app.getkosha.recurring.money(w.balance, w.currency)), style = KoshaTheme.type.xs, color = c.ink3)
                    flow?.lastEntry != null -> KText(stringResource(R.string.accounts_last_entry, lastEntry(flow.lastEntry, today)), style = KoshaTheme.type.xs, color = c.ink3)
                }
            }
        }

        if (flow != null) {
            val m = monthText(month, "MMM")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Flow(stringResource(R.string.accounts_in_month, m), Math.round(flow.inflow), w.currency, Sign.Always, c.pos, Modifier.weight(1f))
                Flow(stringResource(R.string.accounts_out_month, m), Math.round(flow.out), w.currency, Sign.Natural, c.ink, Modifier.weight(1f))
                Flow(stringResource(R.string.accounts_moved), Math.round(flow.moved), w.currency, Sign.None, c.ink2, Modifier.weight(1f))
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (w.reconciledDay != null) PhIcon("seal-check", size = 18.dp, tint = c.pos)
                    KText(
                        w.reconciledDay?.let { stringResource(R.string.accounts_reconciled_to, dayText(it)) } ?: stringResource(R.string.accounts_match_bank),
                        Modifier.weight(1f),
                        style = KoshaTheme.type.head,
                    )
                }
                KText(
                    stringResource(if (w.reconciledDay != null) R.string.accounts_match_bank else R.string.accounts_reconcile_hint),
                    style = KoshaTheme.type.small,
                    color = c.ink2,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(stringResource(R.string.accounts_reconcile), { nav.navigate(Route.Reconcile(id)) }, Modifier.weight(1f), size = ButtonSize.Medium)
                    Button(stringResource(R.string.accounts_fix_balance), { fixing = true }, Modifier.weight(1f), style = ButtonStyle.Secondary, size = ButtonSize.Medium)
                }
            }
        }

        RecentCard(w, recent, today)
        ArchiveRow(w)
    }
    if (fixing) {
        FixBalanceSheet(w, onDismiss = {
            fixing = false
            if (fix) nav.popBackStack()
        })
    }
}

@Composable
private fun Flow(label: String, minor: Long, currency: String, sign: Sign, color: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    Card(modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            KText(label, style = KoshaTheme.type.xs, color = KoshaTheme.colors.ink3, maxLines = 1)
            MoneyText(minor, currency, sign = sign, color = if (minor == 0L) KoshaTheme.colors.ink3 else color)
        }
    }
}

/** Recent entries of one account; a transfer reads "To Cash" or "From N26 Main". */
@Composable
private fun RecentCard(w: Wallet, recent: List<Transaction>, today: java.time.LocalDate, transfersOnly: Boolean = false) {
    val nav = LocalNav.current
    val m = meta()
    val c = KoshaTheme.colors
    if (recent.isEmpty()) {
        Card(Modifier.fillMaxWidth()) {
            KText(stringResource(R.string.accounts_recent), style = KoshaTheme.type.head)
            KText(stringResource(R.string.accounts_no_transactions), Modifier.padding(top = 6.dp), style = KoshaTheme.type.small, color = c.ink3)
        }
        return
    }
    Card(Modifier.fillMaxWidth(), tight = true) {
        KText(stringResource(R.string.accounts_recent), Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp), style = KoshaTheme.type.head)
        recent.forEach { t ->
            Hairline()
            val loanLink = t.loan
            val transfer = t.transfer
            val counter = m?.wallets?.find { it.id == transfer?.counterAccountId }?.name ?: stringResource(R.string.accounts_another_account)
            val cat = m?.categories?.find { it.id == t.categoryId }
            ListRow(onClick = { nav.navigate(if (loanLink != null) Route.LoanDetail(loanLink.id) else if (transfer != null) Route.TransferDetail(transfer.id) else Route.TxDetail(t.id)) }) {
                IconTile(if (transfer != null) "arrows-left-right" else categoryIconName(cat?.icon))
                Column(Modifier.weight(1f)) {
                    KText(
                        when {
                            transfer == null -> t.payee.ifEmpty { stringResource(R.string.accounts_untitled) }
                            t.amount < 0 -> stringResource(R.string.app_rec_to_account, counter)
                            else -> stringResource(R.string.app_acc_from_account, counter)
                        },
                        maxLines = 1,
                    )
                    KText(
                        dayLabel(java.time.LocalDate.parse(t.day), today) + " · " + (if (loanLink != null) stringResource(if (loanLink.kind == "funding") R.string.loans_original else R.string.loans_repayment) else if (transfer != null) stringResource(R.string.subs_kind_transfer) else t.categoryName.ifEmpty { cat?.name ?: "" }),
                        style = KoshaTheme.type.xs,
                        color = c.ink3,
                    )
                }
                when {
                    loanLink != null -> MoneyText(t.amount, t.currency, sign = Sign.Always, color = c.ink2)
                    transfersOnly -> MoneyText(t.amount, t.currency, sign = Sign.Always, color = if (t.amount > 0) c.pos else c.ink)
                    transfer != null -> MoneyText(t.amount, t.currency, color = c.ink2)
                    else -> MoneyText(t.amount, t.currency, sign = Sign.Always, color = if (t.amount > 0) c.pos else c.ink)
                }
            }
        }
        // Every entry of this account, in Activity.
        if (!transfersOnly && w.txCount > recent.size) {
            Hairline()
            ListRow(onClick = { nav.openActivity(ActivitySearch(accountId = w.id)) }) {
                KText(stringResource(R.string.accounts_all_count, w.txCount), Modifier.weight(1f), color = c.ink2)
            }
        }
    }
}

/** Archive or unarchive; Delete while the account has no transactions. */
@Composable
private fun ArchiveRow(w: Wallet) {
    val nav = LocalNav.current
    val writer = rememberWriter()
    val scope = rememberCoroutineScope()
    var confirm by remember { mutableStateOf(false) }
    val toast = stringResource(if (w.isArchived) R.string.accounts_restored_toast else R.string.accounts_archived_toast, w.name)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            stringResource(if (w.isArchived) R.string.accounts_unarchive else R.string.accounts_archive),
            {
                scope.launch {
                    val ok = writer.run { send("PATCH", "/api/v1/accounts/${w.id}", buildJsonObject { put("archived", !w.isArchived) }) }
                    if (ok != null) writer.toast(toast)
                }
            },
            style = ButtonStyle.Secondary,
            size = ButtonSize.Medium,
            icon = "archive",
        )
        if (w.txCount == 0) {
            Button(stringResource(R.string.common_delete), { confirm = true }, style = ButtonStyle.Danger, size = ButtonSize.Medium)
        }
    }
    if (confirm) {
        ConfirmSheet(
            stringResource(R.string.accounts_delete_title, w.name),
            stringResource(R.string.accounts_delete_description),
            stringResource(R.string.accounts_delete_account),
            onConfirm = {
                scope.launch {
                    val ok = writer.run { send("DELETE", "/api/v1/accounts/${w.id}") }
                    confirm = false
                    if (ok != null) nav.popBackStack()
                }
            },
            onDismiss = { confirm = false },
        )
    }
}

/**
 * An investment: its value, money put in, unrealized gains with the return, Update gains, the gains history
 * (each entry deletable), and recent transfers in.
 */
@Composable
fun InvestmentDetailScreen(id: String) {
    val nav = LocalNav.current
    val c = KoshaTheme.colors
    val locale = KoshaTheme.locale
    val today = today()
    val writer = rememberWriter()
    val scope = rememberCoroutineScope()
    val (w, _) = wallet(id)
    var gains by remember { mutableStateOf(false) }
    if (w == null) {
        Page(stringResource(R.string.accounts_title), onBack = { nav.popBackStack() }) { }
        return
    }
    val history = rememberLoad<Gains>("gains/$id") { get("/api/v1/gains", mapOf("accountId" to id)) }.value?.gains.orEmpty()
    val transfers = rememberLoad<TransactionList>("account/$id/transfers") {
        get("/api/v1/transactions", mapOf("accountId" to id, "type" to "transfer", "from" to "2000-01-01", "limit" to 5))
    }.value?.transactions.orEmpty()
    val (age, stale) = valueAge(w, today)
    val gain = w.gain ?: 0

    Page(
        w.name,
        onBack = { nav.popBackStack() },
        subtitle = subtitle(w),
        actions = { Button(stringResource(R.string.common_edit), { nav.navigate(Route.AccountForm(id)) }, style = ButtonStyle.Secondary, size = ButtonSize.Small) },
    ) {
        Card(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                KText(stringResource(R.string.app_acc_value), style = KoshaTheme.type.small, color = c.ink3)
                HeroMoney(AccountsMath.worth(w), w.currency)
                Pill(age, color = if (stale) c.warn else c.ink2)
                Hairline()
                Row {
                    KText(stringResource(R.string.accounts_invested_so_far), Modifier.weight(1f), style = KoshaTheme.type.small, color = c.ink3)
                    MoneyText(w.balance, w.currency, style = KoshaTheme.type.num.copy(fontSize = KoshaTheme.type.small.fontSize))
                }
                Row {
                    KText(stringResource(R.string.accounts_chart_gains), Modifier.weight(1f), style = KoshaTheme.type.small, color = c.ink3)
                    MoneyText(gain, w.currency, sign = Sign.Always, style = KoshaTheme.type.num.copy(fontSize = KoshaTheme.type.small.fontSize), color = if (gain >= 0) c.pos else c.bad)
                    if (w.balance != 0L) {
                        KText(" · " + percent(gain.toDouble() / w.balance, locale, 1), style = KoshaTheme.type.num.copy(fontSize = KoshaTheme.type.small.fontSize), color = if (gain >= 0) c.pos else c.bad)
                    }
                }
                Button(stringResource(R.string.accounts_update_gains), { gains = true }, Modifier.fillMaxWidth(), size = ButtonSize.Medium, icon = "trend-up")
            }
        }

        if (history.isNotEmpty()) {
            Card(Modifier.fillMaxWidth(), tight = true) {
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    KText(stringResource(R.string.accounts_gains_history), Modifier.weight(1f), style = KoshaTheme.type.head)
                    KText(stringResource(R.string.app_acc_gain_over_in), style = KoshaTheme.type.xs, color = c.ink3)
                }
                history.forEach { g ->
                    Hairline()
                    Row(
                        Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        KText(dayText(g.day), Modifier.width(56.dp), style = KoshaTheme.type.num.copy(fontSize = KoshaTheme.type.small.fontSize), color = c.ink3)
                        Column(Modifier.weight(1f)) {
                            MoneyText(g.invested + g.gain, w.currency)
                            if (g.note.isNotEmpty()) KText(g.note, style = KoshaTheme.type.xs, color = c.ink3, maxLines = 1)
                        }
                        MoneyText(g.gain, w.currency, sign = Sign.Always, style = KoshaTheme.type.num.copy(fontSize = KoshaTheme.type.small.fontSize), color = if (g.gain >= 0) c.pos else c.bad)
                        IconButton("trash", stringResource(R.string.accounts_delete_valuation, dayText(g.day, "dMMMyyyy")), {
                            scope.launch { writer.run { send("DELETE", "/api/v1/gains/${g.id}") } }
                        })
                    }
                }
            }
        }

        RecentCard(w, transfers, today, transfersOnly = true)
        ArchiveRow(w)
    }
    if (gains) GainsSheet(w, onDismiss = { gains = false })
}
