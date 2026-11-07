package app.getkosha.accounts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.getkosha.design.AccountDot
import app.getkosha.design.Button
import app.getkosha.design.ButtonSize
import app.getkosha.design.ButtonStyle
import app.getkosha.design.Caption
import app.getkosha.design.Card
import app.getkosha.design.Hairline
import app.getkosha.design.IconButton
import app.getkosha.design.IconTile
import app.getkosha.design.KText
import app.getkosha.design.KoshaColors
import app.getkosha.design.KoshaTheme
import app.getkosha.design.ListRow
import app.getkosha.design.PhIcon
import app.getkosha.design.R
import app.getkosha.design.Segmented
import app.getkosha.design.dayLabel
import app.getkosha.model.AccountType
import app.getkosha.model.Money
import app.getkosha.model.Sign
import app.getkosha.model.Wallet
import app.getkosha.model.convert
import app.getkosha.recurring.HeroMoney
import app.getkosha.recurring.dayText
import app.getkosha.recurring.money
import app.getkosha.recurring.monthText
import app.getkosha.recurring.percent
import app.getkosha.recurring.rememberLoad
import app.getkosha.recurring.today
import app.getkosha.recurring.typeLabel
import app.getkosha.ui.LoadingNote
import app.getkosha.ui.LocalNav
import app.getkosha.ui.MainHeader
import app.getkosha.ui.MoneyText
import app.getkosha.ui.Route
import app.getkosha.ui.Skeleton
import app.getkosha.ui.TabBarSpace
import app.getkosha.ui.meta
import app.getkosha.ui.rememberKosha
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Where an account opens: investments have their own page. */
fun Wallet.route(): Route = if (type == AccountType.Investment) Route.InvestmentDetail(id) else Route.AccountDetail(id)

/** "today", "yesterday" or the date, for "last entry …". */
@Composable
fun lastEntry(day: String, today: LocalDate): String {
    val d = LocalDate.parse(day)
    return if (d == today || d == today.minusDays(1)) dayLabel(d, today).lowercase(KoshaTheme.locale) else dayText(day)
}

/**
 * The Accounts tab: net worth with a stacked chart by account (1Y · 2Y · All), the rates other currencies are shown
 * at, bank and cash, credit cards, investments, archived accounts, and links to exchanges, reconcile and import.
 */
@Composable
fun AccountsScreen() {
    val kosha = rememberKosha()
    val nav = LocalNav.current
    val m = meta()
    val c = KoshaTheme.colors
    val shown by kosha.currency.collectAsStateWithLifecycle()
    val month by kosha.month.collectAsStateWithLifecycle()
    val display = shown ?: m?.primary ?: "eur"
    val today = today()
    val overview = rememberLoad<AccountsOverview>("accounts-overview/$month/$display") {
        get("/api/app/accounts-overview", mapOf("month" to month.toString(), "currency" to shown))
    }
    val fx = rememberLoad<Exchanges>("exchanges") { get("/api/v1/exchanges", mapOf("limit" to 500)) }.value?.exchanges.orEmpty()
    var gainsFor by remember { mutableStateOf<Wallet?>(null) }
    var showArchived by rememberSaveable { mutableStateOf(false) }
    val wallets = m?.wallets.orEmpty()
    val open = wallets.filter { !it.isArchived }
    val archived = wallets.filter { it.isArchived }
    val currencies = open.map { it.currency }.distinct()

    Column(Modifier.fillMaxSize()) {
        MainHeader(
            title = stringResource(R.string.accounts_title),
            subtitle = if (open.isEmpty()) null else pluralStringResource(R.plurals.accounts_count, open.size, open.size) + " · " +
                pluralStringResource(R.plurals.app_acc_currencies, currencies.size, currencies.size),
            showCurrency = true,
            action = { IconButton("plus", stringResource(R.string.accounts_new), { nav.navigate(Route.AccountForm()) }, solid = true, tint = c.ink) },
        )
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = TabBarSpace.calculateBottomPadding()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                val o = overview.value
                if (o == null) {
                    if (overview.refreshing) Skeleton(Modifier.fillMaxWidth(), 280.dp) else LoadingNote()
                } else {
                    Hero(o, wallets)
                }
            }
            overview.value?.let { o -> item { RatesNote(o, open, display) } }
            listOf(
                listOf(AccountType.Bank, AccountType.Cash) to R.string.accounts_group_bank_cash,
                listOf(AccountType.Credit) to R.string.accounts_group_credit,
                listOf(AccountType.Investment) to R.string.accounts_group_investment,
            ).forEach { (types, title) ->
                val list = open.filter { it.type in types }
                if (list.isEmpty()) return@forEach
                item(key = title) { Caption(stringResource(title), Modifier.padding(start = 2.dp, top = 4.dp)) }
                item(key = "$title-list") {
                    Card(Modifier.fillMaxWidth(), tight = true) {
                        list.forEachIndexed { i, w ->
                            if (i > 0) Hairline()
                            when (w.type) {
                                AccountType.Credit -> CardRow(w, wallets.indexOf(w)) { nav.navigate(w.route()) }
                                AccountType.Investment -> InvestmentRow(w, wallets.indexOf(w), today, { gainsFor = w }) { nav.navigate(w.route()) }
                                else -> BankRow(w, wallets.indexOf(w), display, overview.value, today) { nav.navigate(w.route()) }
                            }
                        }
                    }
                }
            }
            if (archived.isNotEmpty()) {
                item(key = "archived") {
                    Card(Modifier.fillMaxWidth(), tight = true) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable(role = Role.Button) { showArchived = !showArchived }
                                .heightIn(min = 56.dp)
                                .padding(horizontal = 16.dp)
                                .semantics { stateDescription = if (showArchived) "expanded" else "collapsed" },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            PhIcon("archive", tint = c.ink3)
                            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                KText(stringResource(R.string.accounts_archived_count, archived.size), color = c.ink2)
                                KText(" · " + stringResource(R.string.app_acc_still_counted), style = KoshaTheme.type.xs, color = c.ink3)
                            }
                            PhIcon(if (showArchived) "caret-up" else "caret-down", size = 16.dp, tint = c.ink3)
                        }
                        if (showArchived) archived.forEach { w ->
                            Hairline()
                            BankRow(w, wallets.indexOf(w), display, overview.value, today) { nav.navigate(w.route()) }
                        }
                    }
                }
            }
            item(key = "links") {
                val year = today.year.toString()
                val thisYear = fx.filter { it.day.startsWith(year) }
                val cost = thisYear.sumOf { convert(Math.round(AccountsMath.exchange(it.sent, it.fee, it.received, it.marketRate).totalCost), it.fromCurrency, display, m?.rates.orEmpty()) }
                Card(Modifier.fillMaxWidth(), tight = true) {
                    LinkRow(
                        "arrows-left-right",
                        stringResource(R.string.accounts_fx_title),
                        if (thisYear.isEmpty()) null else pluralStringResource(R.plurals.app_acc_fx_this_year, thisYear.size, thisYear.size) + " · " +
                            stringResource(R.string.accounts_fx_cost, money(cost, display)),
                    ) { nav.navigate(Route.Exchanges) }
                    Hairline()
                    LinkRow("check-square", stringResource(R.string.shell_page_reconcile), stringResource(R.string.app_acc_reconcile_hint)) { nav.navigate(Route.Reconcile()) }
                    Hairline()
                    LinkRow(
                        "file-arrow-up",
                        stringResource(R.string.shell_page_import),
                        stringResource(if (m?.ai?.statements == true) R.string.app_acc_import_hint else R.string.app_acc_import_hint_no_pdf),
                    ) { nav.navigate(Route.Import) }
                }
            }
        }
    }
    gainsFor?.let { GainsSheet(it, onDismiss = { gainsFor = null }) }
}

@Composable
private fun LinkRow(icon: String, title: String, detail: String?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).heightIn(min = KoshaTheme.dims.settingsRow).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconTile(icon)
        Column(Modifier.weight(1f)) {
            KText(title)
            if (detail != null) KText(detail, style = KoshaTheme.type.small, color = KoshaTheme.colors.ink3)
        }
        PhIcon("caret-right", size = 16.dp, tint = KoshaTheme.colors.ink3)
    }
}

private val RANGES = listOf(13, 25, null)

/** Net worth now, how it moved this month and over 12 months, and the stacked chart by account. */
@Composable
private fun Hero(o: AccountsOverview, wallets: List<Wallet>) {
    val c = KoshaTheme.colors
    val locale = KoshaTheme.locale
    var range by rememberSaveable { mutableStateOf<Int?>(13) }
    val all = o.series
    val now = Math.round(all.lastOrNull()?.netWorth ?: 0.0)
    val prev = all.getOrNull(all.size - 2)?.netWorth?.let(Math::round)
    val yearAgo = if (all.size > 1) Math.round(all[maxOf(0, all.size - 13)].netWorth) else null
    val points = AccountsMath.range(all, range)
    Card(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                KText(stringResource(R.string.accounts_net_worth), Modifier.weight(1f), style = KoshaTheme.type.small, color = c.ink3)
                Segmented(
                    listOf(13 to stringResource(R.string.accounts_range_1y), 25 to stringResource(R.string.accounts_range_2y), null to stringResource(R.string.accounts_range_all)),
                    range,
                    { range = it },
                    Modifier.width(160.dp),
                    small = true,
                )
            }
            HeroMoney(now, o.currency, size = 34.sp)
            if (prev != null) {
                val month = now - prev
                Row {
                    KText(
                        stringResource(R.string.overview_worth_this_month, money(month, o.currency, Sign.Always)),
                        style = KoshaTheme.type.small,
                        color = if (month >= 0) c.pos else c.bad,
                    )
                    if (yearAgo != null && yearAgo != 0L) {
                        KText(
                            " · " + percent((now - yearAgo).toDouble() / kotlin.math.abs(yearAgo), locale, 1, sign = true) + " " +
                                pluralStringResource(R.plurals.accounts_change_period, minOf(12, all.size - 1), minOf(12, all.size - 1)),
                            style = KoshaTheme.type.small,
                            color = c.ink3,
                        )
                    }
                }
            }
            if (points.size > 1) {
                WorthChart(points, wallets, o.currency)
            } else {
                KText(stringResource(R.string.accounts_worth_empty), Modifier.padding(vertical = 24.dp), style = KoshaTheme.type.small, color = c.ink3)
            }
        }
    }
}

/** A layer's colour: the account's own, or shades of ink in neutral mode; gains are a light ink band. */
private fun layerColor(c: KoshaColors, id: String?, index: Int, k: Int): Color = when {
    id == null -> c.ink3.copy(alpha = 0.35f)
    c.neutral -> c.ink.copy(alpha = listOf(0.85f, 0.6f, 0.42f, 0.28f, 0.18f, 0.1f)[k % 6])
    else -> c.account(index).copy(alpha = 0.85f)
}

/**
 * Net worth over time stacked by account in each account's colour, card debt below zero, gains on top, and the
 * net worth itself as a 2px line. Drawn on Canvas.
 */
@Composable
private fun WorthChart(points: List<AccountsOverview.Point>, wallets: List<Wallet>, currency: String) {
    val c = KoshaTheme.colors
    val locale = KoshaTheme.locale
    val layers = remember(points, wallets) { AccountsMath.layers(points, wallets.map { it.id }) }
    val stacked = remember(layers) { AccountsMath.stack(layers) }
    val lows = stacked.flatMap { l -> l.map { it.first } } + 0.0
    val highs = stacked.flatMap { l -> l.map { it.second } } + points.map { it.netWorth }
    val below = lows.min()
    val above = highs.max()
    // Card debt that is only a sliver starts the axis at zero rather than mirroring the scale below it.
    val lo = if (-below < above * 0.05) 0.0 else below
    val hi = maxOf(above, lo + 1)
    val first = YearMonth.parse(points.first().month)
    val last = YearMonth.parse(points.last().month)
    val described = stringResource(R.string.accounts_net_worth) + ", " + monthText(first, "MMMyyyy") + " " +
        Money.format(Math.round(points.first().netWorth), currency, locale) + ", " + monthText(last, "MMMyyyy") + " " +
        Money.format(Math.round(points.last().netWorth), currency, locale)
    Canvas(Modifier.fillMaxWidth().height(140.dp).semantics { contentDescription = described }) {
        val w = size.width
        val h = size.height
        val x = { i: Int -> if (points.size == 1) 0f else i * w / (points.size - 1) }
        val y = { v: Double -> (h - ((v.coerceAtLeast(lo) - lo) / (hi - lo) * h)).toFloat() }
        layers.forEachIndexed { k, layer ->
            val band = stacked[k]
            val path = Path().apply {
                moveTo(x(0), y(band[0].second))
                for (i in 1 until band.size) lineTo(x(i), y(band[i].second))
                for (i in band.indices.reversed()) lineTo(x(i), y(band[i].first))
                close()
            }
            drawPath(path, layerColor(c, layer.id, wallets.indexOfFirst { it.id == layer.id }, k))
        }
        val line = Path().apply {
            moveTo(x(0), y(points[0].netWorth))
            for (i in 1 until points.size) lineTo(x(i), y(points[i].netWorth))
        }
        drawPath(line, c.ink, style = Stroke(width = 2.dp.toPx()))
        if (lo < 0) drawLine(c.line, Offset(0f, y(0.0)), Offset(w, y(0.0)), strokeWidth = 1.dp.toPx())
    }
    Row(Modifier.fillMaxWidth()) {
        KText(monthText(first, "MMMyyyy"), Modifier.weight(1f), style = KoshaTheme.type.xs, color = c.ink3)
        KText(monthText(last, "MMMyyyy"), style = KoshaTheme.type.xs, color = c.ink3)
    }
    // The key, two columns.
    val keys = layers.mapIndexed { k, l ->
        val wallet = wallets.find { it.id == l.id }
        val name = when {
            l.id == null -> stringResource(R.string.accounts_chart_gains)
            wallet == null -> stringResource(R.string.accounts_chart_other)
            l.values.all { it <= 0 } -> stringResource(R.string.app_acc_owed, wallet.name)
            else -> wallet.name
        }
        name to layerColor(c, l.id, wallets.indexOfFirst { it.id == l.id }, k)
    }
    Column(Modifier.semantics(mergeDescendants = true) { }, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        keys.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { (name, color) ->
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
                        KText(name, style = KoshaTheme.type.xs, color = c.ink2, maxLines = 1)
                    }
                }
                if (row.size == 1) Box(Modifier.weight(1f))
            }
        }
    }
}

/** "INR shown at ₹94.60 = €1 · updated today", for each other currency the accounts hold. */
@Composable
private fun RatesNote(o: AccountsOverview, open: List<Wallet>, display: String) {
    val m = meta() ?: return
    val locale = KoshaTheme.locale
    val others = open.map { it.currency }.filter { it != display }.distinct()
    if (others.isEmpty()) return
    val one = Money.format(100, display, locale).replace(Regex("""[.,]00(?=\D*$)"""), "")
    val parts = others.map { cur ->
        stringResource(R.string.accounts_rate_shown, cur.uppercase(), Money.format(convert(100, display, cur, m.rates), cur, locale), one)
    }
    val zone = runCatching { ZoneId.of(m.tz) }.getOrDefault(ZoneId.of("UTC"))
    val updated = if (o.ratesUpdated <= 0) null else {
        val day = Instant.ofEpochSecond(o.ratesUpdated).atZone(zone).toLocalDate()
        when (ChronoUnit.DAYS.between(day, LocalDate.parse(o.today))) {
            0L -> stringResource(R.string.accounts_rates_updated_today)
            1L -> stringResource(R.string.accounts_rates_updated_yesterday)
            else -> stringResource(R.string.accounts_rates_updated_on, dayText(day.toString(), "dMMMyyyy"))
        }
    }
    KText((parts + listOfNotNull(updated)).joinToString(" · "), Modifier.padding(horizontal = 2.dp), style = KoshaTheme.type.xs, color = KoshaTheme.colors.ink3)
}

@Composable
private fun BankRow(w: Wallet, index: Int, display: String, o: AccountsOverview?, today: LocalDate, onClick: () -> Unit) {
    val m = meta()
    val c = KoshaTheme.colors
    val foreign = w.currency != display
    val worth = AccountsMath.shownWorth(w)
    val last = o?.accounts?.get(w.id)?.lastEntry
    val detail = listOfNotNull(
        typeLabel(w.type),
        when {
            foreign -> "≈ " + money(convert(AccountsMath.worth(w), w.currency, display, m?.rates.orEmpty()), display)
            last != null -> stringResource(R.string.accounts_last_entry, lastEntry(last, today))
            else -> null
        },
    ).joinToString(" · ")
    ListRow(onClick = onClick) {
        AccountDot(c.account(index), size = 10.dp)
        Column(Modifier.weight(1f)) {
            KText(w.name, color = if (w.isArchived) c.ink2 else c.ink)
            KText(detail, style = KoshaTheme.type.xs, color = c.ink3)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            MoneyText(worth, w.currency, color = if (worth < 0) c.bad else c.ink)
            if (foreign) KText(" " + w.currency.uppercase(), style = KoshaTheme.type.num, color = c.ink3)
        }
    }
}

@Composable
private fun CardRow(w: Wallet, index: Int, onClick: () -> Unit) {
    val c = KoshaTheme.colors
    val locale = KoshaTheme.locale
    val limit = w.creditLimit ?: 0
    val owed = maxOf(0, limit - w.balance)
    Column(
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AccountDot(c.account(index), size = 10.dp)
            Column(Modifier.weight(1f)) {
                KText(w.name)
                if (limit > 0) KText(stringResource(R.string.accounts_available, money(w.balance, w.currency)), style = KoshaTheme.type.xs, color = c.ink3)
            }
            MoneyText(-owed, w.currency)
        }
        if (limit > 0) {
            val used = owed.toFloat() / limit
            Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(c.soft)) {
                Box(Modifier.fillMaxWidth(used.coerceIn(0f, 1f)).height(6.dp).clip(CircleShape).background(c.bar))
            }
            KText(
                stringResource(R.string.app_acc_credit_used, percent(used.toDouble(), locale), money(limit, w.currency)),
                style = KoshaTheme.type.xs,
                color = c.ink3,
            )
        }
    }
}

/** How fresh an investment's value is: "Value is 41 days old" in warning colour once it is stale. */
@Composable
fun valueAge(w: Wallet, today: LocalDate): Pair<String, Boolean> {
    val day = w.gainDay ?: return stringResource(R.string.accounts_no_value) to true
    val age = ChronoUnit.DAYS.between(LocalDate.parse(day), today)
    return if (age > STALE_DAYS) stringResource(R.string.accounts_value_age, age.toString()) to true
    else stringResource(R.string.accounts_value_as_of, dayText(day)) to false
}

@Composable
private fun InvestmentRow(w: Wallet, index: Int, today: LocalDate, onGains: () -> Unit, onClick: () -> Unit) {
    val c = KoshaTheme.colors
    val (age, stale) = valueAge(w, today)
    Column(
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AccountDot(c.account(index), size = 10.dp)
            Column(Modifier.weight(1f)) {
                KText(w.name)
                KText(age, style = KoshaTheme.type.xs, color = if (stale) c.warn else c.ink3)
            }
            Column(horizontalAlignment = Alignment.End) {
                MoneyText(AccountsMath.worth(w), w.currency)
                val gain = w.gain
                if (gain != null && w.gainDay != null) {
                    MoneyText(gain, w.currency, sign = Sign.Always, style = KoshaTheme.type.num.copy(fontSize = KoshaTheme.type.xs.fontSize), color = if (gain >= 0) c.pos else c.bad)
                }
            }
        }
        Button(stringResource(R.string.accounts_update_gains), onGains, Modifier.padding(start = 22.dp), style = ButtonStyle.Secondary, size = ButtonSize.Small)
    }
}
