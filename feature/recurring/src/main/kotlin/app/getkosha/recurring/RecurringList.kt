package app.getkosha.recurring

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.getkosha.design.Button
import app.getkosha.design.ButtonSize
import app.getkosha.design.ButtonStyle
import app.getkosha.design.Caption
import app.getkosha.design.Card
import app.getkosha.design.Chip
import app.getkosha.design.Hairline
import app.getkosha.design.IconButton
import app.getkosha.design.IconTile
import app.getkosha.design.KText
import app.getkosha.design.KoshaTheme
import app.getkosha.design.ListRow
import app.getkosha.design.PhIcon
import app.getkosha.design.R
import app.getkosha.design.AccountDot
import app.getkosha.design.categoryIconName
import app.getkosha.model.Meta
import app.getkosha.model.Sign
import app.getkosha.model.convert
import app.getkosha.ui.LoadingNote
import app.getkosha.ui.LocalNav
import app.getkosha.ui.MainHeader
import app.getkosha.ui.MonthSheet
import app.getkosha.ui.MoneyText
import app.getkosha.ui.Route
import app.getkosha.ui.Skeleton
import app.getkosha.ui.TabBarSpace
import app.getkosha.ui.meta
import app.getkosha.ui.rememberKosha
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlin.math.abs

/** The interval as a phrase: "every month", "every 3 weeks". */
@Composable
fun everyText(every: Int, unit: String): String = pluralStringResource(
    when (unit) {
        "day" -> R.plurals.subs_every_day
        "week" -> R.plurals.subs_every_week
        "year" -> R.plurals.subs_every_year
        else -> R.plurals.subs_every_month
    },
    every,
    every,
)

/** The name a schedule shows: its own, or the accounts it moves between. */
fun Sub.title(m: Meta?): String =
    if (toWalletId != null) "${m?.wallets?.find { it.id == walletId }?.name ?: ""} → ${m?.wallets?.find { it.id == toWalletId }?.name ?: ""}" else name

/**
 * The Recurring tab: what is committed each month, payments that look recurring, then bills, subscriptions,
 * income and transfers sorted by cost per month, and the ended ones folded away. The selected month (shared with
 * Overview and Activity, swipe sideways to move it) lists what falls due in it.
 */
@Composable
fun RecurringScreen() {
    val kosha = rememberKosha()
    val nav = LocalNav.current
    val m = meta()
    val month by kosha.month.collectAsStateWithLifecycle()
    val shownCurrency by kosha.currency.collectAsStateWithLifecycle()
    val display = shownCurrency ?: m?.primary ?: "eur"
    val today = today()
    val thisMonth = YearMonth.from(today)
    val loaded = rememberLoad<SubscriptionsData>("subscriptions") { get("/api/app/subscriptions") }
    val spotted = rememberLoad<RecurringList>("recurring/suggestions") { get("/api/v1/recurring", mapOf("includeSuggestions" to true)) }
    val writer = rememberWriter()
    val scope = rememberCoroutineScope()
    val hidden = remember { mutableStateListOf<String>() }
    var showEnded by rememberSaveable { mutableStateOf(false) }
    var monthSheet by remember { mutableStateOf(false) }
    val rates = m?.rates ?: emptyMap()
    val data = loaded.value
    val subs = data?.subscriptions.orEmpty()
    val perMonth = { s: Sub -> abs(convert(s.perMonth, s.currency, display, rates)) }
    val active = subs.filter { !it.ended }.sortedByDescending(perMonth)
    val ended = subs.filter { it.ended }.sortedByDescending { it.endDay ?: "" }
    val transfers = active.count { it.isTransfer }

    Column(Modifier.fillMaxSize()) {
        MainHeader(
            title = stringResource(R.string.subs_title),
            subtitle = if (data == null) null else listOf(
                stringResource(R.string.subs_count_active, active.size - transfers),
                pluralStringResource(R.plurals.subs_count_transfers, transfers, transfers),
                stringResource(R.string.subs_count_ended, ended.size),
            ).joinToString(" · "),
            showCurrency = true,
            action = { IconButton("plus", stringResource(R.string.subs_new_button), { nav.navigate(Route.RecurringForm()) }, solid = true, tint = KoshaTheme.colors.ink) },
        )
        LazyColumn(
            Modifier.fillMaxSize().swipeMonths { kosha.month.value = month.plusMonths(it.toLong()) },
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = TabBarSpace.calculateBottomPadding()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { MonthLine(month, thisMonth, { monthSheet = true }, { kosha.month.value = thisMonth }) }
            if (data == null) {
                item { if (loaded.refreshing) Skeleton(Modifier.fillMaxWidth(), 160.dp) else LoadingNote() }
                return@LazyColumn
            }
            if (loaded.offline) item { LoadingNote() }
            item { Committed(active, data.income, display, perMonth, rates) }
            val suggestions = spotted.value?.suggestions.orEmpty().filter { it.payee !in hidden }
            if (suggestions.isNotEmpty()) {
                item {
                    Spotted(
                        suggestions,
                        today,
                        onAdd = { nav.navigate(Route.RecurringForm(fromPayee = it.payee)) },
                        onDismiss = { g ->
                            hidden += g.payee
                            scope.launch {
                                val done = writer.run { post<Map<String, String>, JsonObject>("/api/v1/recurring/suggestions/dismiss", mapOf("payee" to g.payee)) }
                                if (done == null) hidden -= g.payee
                            }
                        },
                    )
                }
            }
            if (active.isEmpty()) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        KText(stringResource(R.string.subs_empty), style = KoshaTheme.type.small, color = KoshaTheme.colors.ink2)
                    }
                }
            }
            Group.entries.forEach { g ->
                val rows = active.filter { it.group() == g }.filter { month == thisMonth || Schedule.inMonth(it, month).isNotEmpty() }
                if (rows.isEmpty()) return@forEach
                item(key = g.name) { Caption(stringResource(g.title()), Modifier.padding(start = 2.dp, top = 6.dp)) }
                item(key = "${g.name}-rows") {
                    Card(Modifier.fillMaxWidth(), tight = true) {
                        rows.forEachIndexed { i, s ->
                            if (i > 0) Hairline()
                            SubRow(s, today, month, thisMonth) { nav.navigate(Route.RecurringDetail(s.id)) }
                        }
                    }
                }
            }
            if (month != thisMonth && active.none { Schedule.inMonth(it, month).isNotEmpty() }) {
                item { KText(stringResource(R.string.app_rec_none_in_month, monthText(month, "MMMM")), style = KoshaTheme.type.small, color = KoshaTheme.colors.ink3) }
            }
            if (ended.isNotEmpty()) {
                item(key = "ended") {
                    Card(Modifier.fillMaxWidth(), tight = true) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable(role = Role.Button) { showEnded = !showEnded }
                                .heightIn(min = 56.dp)
                                .padding(horizontal = 16.dp)
                                .semantics { stateDescription = if (showEnded) "expanded" else "collapsed" },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            PhIcon("archive", tint = KoshaTheme.colors.ink3)
                            KText(stringResource(R.string.subs_ended_count, ended.size), Modifier.weight(1f), color = KoshaTheme.colors.ink2)
                            PhIcon(if (showEnded) "caret-up" else "caret-down", size = 16.dp, tint = KoshaTheme.colors.ink3)
                        }
                        if (showEnded) ended.forEach { s ->
                            Hairline()
                            SubRow(s, today, thisMonth, thisMonth) { nav.navigate(Route.RecurringDetail(s.id)) }
                        }
                    }
                }
            }
        }
    }
    if (monthSheet) MonthSheet(month, onPick = { kosha.month.value = it; monthSheet = false }, onDismiss = { monthSheet = false })
}

private fun Group.title() = when (this) {
    Group.Bills -> R.string.subs_group_bills
    Group.Subscriptions -> R.string.subs_group_subscriptions
    Group.Income -> R.string.subs_group_income
    Group.Transfers -> R.string.subs_group_transfers
}

/** Which month the list is for; another month offers the way back. */
@Composable
private fun MonthLine(month: YearMonth, thisMonth: YearMonth, onPick: () -> Unit, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Chip(monthText(month), small = true, soft = true, onClick = onPick, leading = { PhIcon("calendar-blank", size = 16.dp) })
        if (month != thisMonth) Chip(stringResource(R.string.overview_this_month), small = true, onClick = onBack)
    }
}

/** Committed per month: bills and subscriptions, per year with the share of a year's income, and savings moved. */
@Composable
private fun Committed(active: List<Sub>, income: List<SubscriptionsData.Income>, display: String, perMonth: (Sub) -> Long, rates: Map<String, Double>) {
    val c = KoshaTheme.colors
    val locale = KoshaTheme.locale
    val costs = active.filter { it.isCost() }
    val bills = costs.filter { it.group() == Group.Bills }.sumOf(perMonth)
    val subs = costs.filter { it.group() == Group.Subscriptions }.sumOf(perMonth)
    val committed = bills + subs
    val moved = active.filter { it.isTransfer }.sumOf(perMonth)
    val yearIncome = income.sumOf { convert(it.total, it.currency, display, rates) }
    Card(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            KText(stringResource(R.string.subs_committed), style = KoshaTheme.type.small, color = c.ink3)
            HeroMoney(committed, display, size = 34.sp)
            KText(
                stringResource(R.string.subs_committed_hint, money(bills, display), money(subs, display)),
                style = KoshaTheme.type.small,
                color = c.ink3,
            )
            Hairline()
            Line(stringResource(R.string.subs_per_year)) {
                MoneyText(committed * 12, display, style = KoshaTheme.type.num.copy(fontSize = KoshaTheme.type.small.fontSize))
                KText(
                    " · " + if (yearIncome > 0) stringResource(R.string.subs_share_of_income, percent(committed * 12.0 / yearIncome, locale)) else stringResource(R.string.subs_no_income),
                    style = KoshaTheme.type.small,
                    color = c.ink3,
                )
            }
            if (moved > 0) {
                Line(stringResource(R.string.subs_moved_to_savings)) {
                    MoneyText(moved, display, style = KoshaTheme.type.num.copy(fontSize = KoshaTheme.type.small.fontSize))
                    KText(" " + stringResource(R.string.subs_moved_hint), style = KoshaTheme.type.small, color = c.ink3)
                }
            }
        }
    }
}

@Composable
private fun Line(label: String, value: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        KText(label, Modifier.weight(1f), style = KoshaTheme.type.small, color = KoshaTheme.colors.ink3)
        value()
    }
}

/** Money as text, for amounts inside a sentence. Hide amounts masks it. */
@Composable
fun money(minor: Long, currency: String, sign: Sign = Sign.Natural): String =
    if (app.getkosha.ui.hideAmounts()) "••••" else app.getkosha.model.Money.format(minor, currency, KoshaTheme.locale, sign)

/** "Looks recurring": regular payments Kosha spotted, to make recurring or dismiss. */
@Composable
private fun Spotted(list: List<Suggestion>, today: LocalDate, onAdd: (Suggestion) -> Unit, onDismiss: (Suggestion) -> Unit) {
    val m = meta()
    val c = KoshaTheme.colors
    Card(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            KText(stringResource(R.string.subs_spotted_title), style = KoshaTheme.type.head)
            list.forEachIndexed { i, g ->
                if (i > 0) Hairline()
                val cat = m?.categories?.find { it.id == g.categoryId }
                val currency = m?.wallets?.find { it.id == g.accountId }?.currency ?: m?.primary ?: "eur"
                val amount = money(abs(g.amount), currency)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconTile(categoryIconName(cat?.icon))
                    Column(Modifier.weight(1f)) {
                        KText(g.payee)
                        KText(
                            listOf(
                                (if (g.variable) stringResource(R.string.subs_about_amount, amount) else amount) + " " + everyText(g.every, g.unit),
                                pluralStringResource(R.plurals.subs_spotted_paid, g.count, g.count, dayText(g.lastDay)),
                                stringResource(R.string.subs_spotted_next, nextLabel(g.nextDay, today)),
                            ).joinToString(" · "),
                            style = KoshaTheme.type.xs,
                            color = c.ink3,
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(stringResource(R.string.subs_make_recurring), { onAdd(g) }, Modifier.weight(1f), size = ButtonSize.Small)
                    Button(stringResource(R.string.subs_not_recurring), { onDismiss(g) }, Modifier.weight(1f), style = ButtonStyle.Secondary, size = ButtonSize.Small)
                }
            }
        }
    }
}

/** "1 Dec", or "Oct '27" when it is most of a year away (subscriptions.tsx nextLabel). */
@Composable
fun nextLabel(day: String, today: LocalDate): String {
    val d = LocalDate.parse(day)
    return if (d > today.plusDays(300)) "${monthText(YearMonth.from(d), "MMM")} '${day.substring(2, 4)}" else dayText(day)
}

/** One schedule: its icon, name, how often and when next (or when it falls in the selected month), and its amount. */
@Composable
private fun SubRow(s: Sub, today: LocalDate, month: YearMonth, thisMonth: YearMonth, onClick: () -> Unit) {
    val m = meta()
    val c = KoshaTheme.colors
    val locale = KoshaTheme.locale
    val cat = m?.categories?.find { it.id == s.categoryId }
    val wallets = m?.wallets.orEmpty()
    val overdue = s.next?.status == "overdue"
    val every = everyText(s.every, s.unit).capitalised(locale)
    val inMonth = if (month != thisMonth && !s.ended) Schedule.inMonth(s, month).firstOrNull() else null
    val line = when {
        s.ended -> stringResource(R.string.subs_ended_on_short, dayText(s.endDay ?: s.startDay))
        inMonth != null -> {
            val paid = s.history.firstOrNull { (it.dueDay ?: it.day) == inMonth.toString() }
            every + " · " + if (paid != null) stringResource(R.string.app_rec_paid_on, dayText(paid.day)) else stringResource(R.string.app_rec_due_on, dayText(inMonth.toString()))
        }
        overdue -> stringResource(R.string.app_rec_overdue, dayText(s.next.day))
        else -> listOfNotNull(
            every,
            s.next?.let { stringResource(R.string.app_rec_next, nextLabel(it.day, today)) },
            if (s.isVariable) stringResource(R.string.subs_varies_asks) else null,
            if (s.isTransfer) stringResource(R.string.subs_not_counted) else null,
        ).joinToString(" · ")
    }
    ListRow(onClick = onClick) {
        IconTile(if (s.isTransfer) "arrows-left-right" else categoryIconName(cat?.icon))
        Column(Modifier.weight(1f)) {
            if (s.isTransfer) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    val to = wallets.indexOfFirst { it.id == s.toWalletId }
                    AccountDot(c.account(to))
                    KText(stringResource(R.string.app_rec_to_account, wallets.getOrNull(to)?.name ?: ""), maxLines = 1)
                }
            } else KText(s.name, maxLines = 1)
            KText(line, style = KoshaTheme.type.xs, color = if (overdue && inMonth == null && !s.ended) c.warn else c.ink3)
        }
        Column(horizontalAlignment = Alignment.End) {
            val income = s.amount > 0 && !s.isTransfer
            MoneyText(
                if (income) s.amount else abs(s.amount),
                s.currency,
                sign = if (income) Sign.Always else Sign.None,
                color = when {
                    income -> c.pos
                    s.isTransfer || s.ended -> c.ink3
                    else -> c.ink
                },
            )
            if (!(s.unit == "month" && s.every == 1) && !s.ended) {
                KText(stringResource(R.string.app_rec_per_month_short, money(abs(s.perMonth), s.currency)), style = KoshaTheme.type.num.copy(fontSize = KoshaTheme.type.xs.fontSize), color = c.ink3)
            }
        }
    }
}
