@file:UseSerializers(RoundedLong::class)

package app.getkosha.overview

import kotlinx.serialization.UseSerializers
import app.getkosha.model.RoundedLong

import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
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
import app.getkosha.design.PhIcon
import app.getkosha.design.Pill
import app.getkosha.design.R
import app.getkosha.design.Sparkle
import app.getkosha.design.Toast
import app.getkosha.design.categoryIconName
import app.getkosha.model.AccountType
import app.getkosha.model.Dates
import app.getkosha.model.KoshaJson
import app.getkosha.model.Meta
import app.getkosha.model.Money
import app.getkosha.model.Sign
import app.getkosha.model.Wallet
import app.getkosha.ui.ActivitySearch
import app.getkosha.ui.AmountText
import app.getkosha.ui.HeroMoney
import app.getkosha.ui.LoadingNote
import app.getkosha.ui.LocalNav
import app.getkosha.ui.LocalToasts
import app.getkosha.ui.MainHeader
import app.getkosha.ui.MoneyText
import app.getkosha.ui.Route
import app.getkosha.ui.Skeleton
import app.getkosha.ui.TabBanners
import app.getkosha.ui.TabBarSpace
import app.getkosha.ui.canWrite
import app.getkosha.ui.meta
import app.getkosha.ui.monthSwipe
import app.getkosha.ui.openActivity
import app.getkosha.ui.openTab
import app.getkosha.ui.percent
import app.getkosha.ui.rememberKosha
import app.getkosha.ui.shortDay
import app.getkosha.ui.wholeMoney
import app.getkosha.ui.write
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.nullable
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle as DateStyle

/**
 * Overview: how this month compares with a usual one, what is due, and where the money stands. Every figure comes
 * from the dashboard read (GET /api/app/dashboard) and get_net_worth in the display currency; swiping sideways
 * moves a month.
 */
@Composable
fun OverviewScreen() {
    val kosha = rememberKosha()
    val m = meta() ?: return
    val month by kosha.month.collectAsStateWithLifecycle()
    val shown = kosha.currency.collectAsStateWithLifecycle().value ?: m.primary
    val version by kosha.version.collectAsStateWithLifecycle()
    var search by remember { mutableStateOf(false) }
    val dash = remember(month, shown, version) {
        kosha.load("dashboard/$month/$shown") { get<Dashboard>("/api/app/dashboard", mapOf("month" to month.toString(), "currency" to shown)) }
    }.collectAsStateWithLifecycle(null).value
    val worth = remember(shown, version) {
        kosha.load("networth/$shown") { get<NetWorth>("/api/v1/reports/net-worth", mapOf("currency" to shown)) }
    }.collectAsStateWithLifecycle(null).value?.value
    val recap = if (m.ai.recap) {
        remember(month, version) {
            kosha.load("recap/$month", RecapView.serializer().nullable) { get<RecapView?>("/api/app/recap", mapOf("month" to month.toString())) }
        }.collectAsStateWithLifecycle(null).value?.value
    } else null
    val d = dash?.value
    Column(Modifier.fillMaxSize().background(KoshaTheme.colors.bg)) {
        MainHeader(action = { IconButton("magnifying-glass", stringResource(R.string.common_search), { search = true }) })
        Column(
            Modifier
                .weight(1f)
                .monthSwipe(kosha)
                .verticalScroll(rememberScrollState())
                .padding(TabBarSpace),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TabBanners()
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when {
                    d == null && dash?.refreshing != false -> Loading()
                    d == null -> KText(dash.error ?: stringResource(R.string.common_offline_body), style = KoshaTheme.type.small, color = KoshaTheme.colors.ink3)
                    m.wallets.isNotEmpty() && m.wallets.all { it.entryCount == 0 } -> FirstOverview(d, m, worth)
                    else -> Month(d, m, worth, recap)
                }
            }
        }
    }
    if (search) SearchSheet(onDismiss = { search = false })
}

@Composable
private fun Loading() {
    Card(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Skeleton(Modifier.width(90.dp), 12.dp)
                Skeleton(Modifier.width(70.dp), 12.dp)
            }
            Skeleton(Modifier.width(170.dp), 38.dp)
            Skeleton(Modifier.width(150.dp), 12.dp)
            Skeleton(Modifier.fillMaxWidth(), 110.dp)
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        repeat(2) {
            Card(Modifier.weight(1f)) {
                Skeleton(Modifier.width(80.dp), 12.dp)
                Spacer(Modifier.height(8.dp))
                Skeleton(Modifier.width(110.dp), 22.dp)
            }
        }
    }
    LoadingNote(Modifier.fillMaxWidth())
}

@Composable
private fun Month(d: Dashboard, m: Meta, worth: NetWorth?, recap: RecapView?) {
    val kosha = rememberKosha()
    val today = Dates.today(kosha.tz)
    val current = YearMonth.from(today).toString()
    val stillToPay = d.stillToPay(current, m.rates)
    PaceCard(d, current)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        val saved = d.current.income - d.current.expense
        MiniStat(
            stringResource(if (d.pace.today != null) R.string.overview_saved_so_far else R.string.overview_saved),
            saved, d.primary, positive = saved > 0,
            sub = if (d.current.income > 0) stringResource(R.string.overview_pct_of_income, percent(saved.toDouble() / d.current.income, KoshaTheme.locale, signed = false)) else null,
            modifier = Modifier.weight(1f),
        )
        val bills = d.billsDue(current, m.rates)
        MiniStat(
            stringResource(R.string.overview_still_to_pay), stillToPay, d.primary,
            sub = if (bills > 0) pluralStringResource(R.plurals.overview_bills_before, bills, bills, shortDay(LocalDate.parse(d.monthEnd()), KoshaTheme.locale))
            else stringResource(R.string.overview_nothing_left),
            modifier = Modifier.weight(1f),
        )
    }
    if (recap != null) RecapCard(recap)
    NeedsAttention(d, m, today)
    ComingUp(d, m, today)
    WhereItWent(d, m)
    app.getkosha.ui.LoansCard()
    NetWorthCard(d, worth)
}

/** "Spent so far": the hero, Day N of M, the pace sentence, the chart, and the usual and projected month. */
@Composable
private fun PaceCard(d: Dashboard, current: String) {
    val c = KoshaTheme.colors
    val locale = KoshaTheme.locale
    val live = d.pace.today != null
    val future = d.month > current
    val name = YearMonth.parse(d.month).month.getDisplayName(DateStyle.FULL_STANDALONE, locale)
    val money = { v: Long -> wholeMoney(v, d.primary, locale) }
    Card(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                KText(
                    if (live) stringResource(R.string.overview_pace_spent_so_far) else stringResource(R.string.overview_pace_spent_in, name),
                    Modifier.weight(1f).semantics { heading() }, style = KoshaTheme.type.small, color = c.ink3,
                )
                KText(
                    if (live) stringResource(R.string.overview_pace_day, d.dayN().toString(), d.pace.daysInMonth.toString())
                    else stringResource(R.string.overview_pace_days, d.pace.daysInMonth.toString()),
                    style = KoshaTheme.type.small, color = c.ink3,
                )
            }
            HeroMoney(d.current.expense, d.primary)
            val diff = d.underUsual()
            when {
                future -> KText(stringResource(R.string.overview_pace_future), style = KoshaTheme.type.small, color = c.ink3)
                diff == null -> KText(stringResource(R.string.overview_pace_no_history), style = KoshaTheme.type.small, color = c.ink3)
                diff >= 0 -> AmountText(
                    AnnotatedString(stringResource(if (live) R.string.overview_pace_under_pace else R.string.overview_pace_under_month, money(diff))),
                    style = KoshaTheme.type.small.copy(fontWeight = FontWeight.Medium), color = c.pos,
                )
                else -> AmountText(
                    AnnotatedString(stringResource(if (live) R.string.overview_pace_over_pace else R.string.overview_pace_over_month, money(-diff))),
                    style = KoshaTheme.type.small.copy(fontWeight = FontWeight.Medium),
                    color = if (-diff > 0.1 * (d.usualBy() ?: 0)) c.warn else c.ink2,
                )
            }
            if (!future && (d.pace.usual != null || d.pace.current.isNotEmpty())) PaceChart(d.pace, d.projected(), Modifier.padding(top = 4.dp))
            val usualMonth = d.usualMonth()
            if (usualMonth != null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    AmountText(AnnotatedString(stringResource(R.string.overview_pace_usual_month, money(usualMonth))), style = KoshaTheme.type.small, color = c.ink3)
                    d.projected()?.let {
                        AmountText(AnnotatedString(stringResource(R.string.overview_pace_projected, money(it))), style = KoshaTheme.type.small, color = c.ink3)
                    }
                }
            }
        }
    }
}

/**
 * The pace chart: the usual month dashed in ink3, this month to today as a 2dp ink line over a 6% fill, and a dot
 * on today. It draws in from the left unless Appearance › Animations is reduced or off.
 */
@Composable
private fun PaceChart(pace: Dashboard.Pace, projected: Long?, modifier: Modifier = Modifier) {
    val c = KoshaTheme.colors
    val animate = meta()?.pref("motion", "on") == "on"
    val reveal = remember(pace) { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(pace) { reveal.animateTo(1f, tween(700)) }
    Canvas(modifier.fillMaxWidth().height(120.dp).clipToBounds()) {
        val days = pace.daysInMonth.coerceAtLeast(1)
        val top = maxOf(pace.current.lastOrNull()?.toDouble() ?: 0.0, pace.usual?.lastOrNull() ?: 0.0, projected?.toDouble() ?: 0.0, 1.0)
        val pad = size.height / 12
        val x = { day: Int -> size.width * day / days }
        val y = { v: Double -> (size.height - pad - (v / top) * (size.height - 2 * pad)).toFloat() }
        clipRect(right = size.width * reveal.value) {
            pace.usual?.let { usual ->
                val path = Path().apply {
                    moveTo(0f, y(0.0))
                    usual.forEachIndexed { i, v -> lineTo(x(i + 1), y(v)) }
                }
                drawPath(path, c.ink3, style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))))
            }
            if (pace.current.isNotEmpty()) {
                val line = Path().apply {
                    moveTo(0f, y(0.0))
                    pace.current.forEachIndexed { i, v -> lineTo(x(i + 1), y(v.toDouble())) }
                }
                val area = Path().apply {
                    addPath(line)
                    lineTo(x(pace.current.size), y(0.0))
                    close()
                }
                drawPath(area, c.ink.copy(alpha = 0.06f))
                drawPath(line, c.ink, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                if (pace.today != null) drawCircle(c.ink, 3.5.dp.toPx(), Offset(x(pace.current.size), y(pace.current.last().toDouble())))
            }
        }
    }
}

@Composable
private fun MiniStat(label: String, minor: Long, currency: String, modifier: Modifier, positive: Boolean = false, sub: String? = null) {
    Card(modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            KText(label, style = KoshaTheme.type.small, color = KoshaTheme.colors.ink3)
            MoneyText(minor, currency, style = KoshaTheme.type.num.copy(fontSize = 22.sp), color = if (positive) KoshaTheme.colors.pos else KoshaTheme.colors.ink)
            if (sub != null) KText(sub, style = KoshaTheme.type.xs, color = KoshaTheme.colors.ink3)
        }
    }
}

/** The month's recaps are written once per phone run when Overview opens on them, as the web does on first visit. */
private val autoWritten = mutableSetOf<String>()

/**
 * Monthly recap (AI, only with meta.ai.recap). In a month's first week it shows last month's, written on the first
 * visit, until hidden (the `ai_recap_hidden` preference holds the hidden month). A past month offers to write one.
 */
@Composable
private fun RecapCard(view: RecapView) {
    val kosha = rememberKosha()
    val c = KoshaTheme.colors
    val locale = KoshaTheme.locale
    val writable = canWrite()
    val scope = rememberCoroutineScope()
    var recap by remember(view) { mutableStateOf(view.recap) }
    var writing by remember(view) { mutableStateOf(false) }
    var error by remember(view) { mutableStateOf<String?>(null) }
    var hidden by remember(view) { mutableStateOf(false) }
    val failed = stringResource(R.string.overview_recap_write_failed)
    val name = YearMonth.parse(view.month).month.getDisplayName(DateStyle.FULL_STANDALONE, locale)
    val write = { rewrite: Boolean ->
        writing = true
        error = null
        scope.launch {
            try {
                recap = kosha.api.post<RecapRequest, Recap>("/api/v1/ai/recap", RecapRequest(view.month, rewrite))
                kosha.cache.write("recap/${kosha.month.value}", KoshaJson.encodeToString(RecapView.serializer(), view.copy(recap = recap)))
            } catch (e: Exception) {
                error = (e as? app.getkosha.network.ApiException)?.message ?: failed
            } finally {
                writing = false
            }
        }
    }
    LaunchedEffect(view.month) {
        if (view.auto && view.recap == null && writable && autoWritten.add(view.month)) write(false)
    }
    if (hidden || (recap == null && !writable)) return
    if (recap == null && !writing && error == null && !view.auto) {
        Card(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Sparkle(size = 16.dp)
                KText(stringResource(R.string.overview_recap_offer, name), Modifier.weight(1f), style = KoshaTheme.type.small, color = c.ink3)
                Button(stringResource(R.string.overview_recap_write), { write(false) }, style = ButtonStyle.Secondary, size = ButtonSize.Small)
            }
        }
        return
    }
    Card(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Sparkle()
                KText(stringResource(R.string.overview_recap_title, name), Modifier.weight(1f).semantics { heading() }, style = KoshaTheme.type.head)
                if (view.auto) {
                    IconButton("x", stringResource(R.string.overview_recap_hide, name), {
                        hidden = true
                        kosha.setPreference("ai_recap_hidden", view.month)
                    }, Modifier.padding(0.dp))
                }
            }
            val shownRecap = recap
            when {
                error != null -> {
                    KText(stringResource(R.string.overview_recap_error, error ?: ""), style = KoshaTheme.type.small, color = c.ink3)
                    Button(stringResource(R.string.common_try_again), { write(recap != null) }, style = ButtonStyle.Secondary, size = ButtonSize.Small)
                }
                writing && shownRecap == null -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    KText(stringResource(R.string.overview_recap_writing, name), style = KoshaTheme.type.xs, color = c.ink3)
                    Skeleton(Modifier.fillMaxWidth(0.66f), 18.dp)
                    Skeleton(Modifier.fillMaxWidth(), 12.dp)
                    Skeleton(Modifier.fillMaxWidth(0.8f), 12.dp)
                }
                shownRecap != null -> {
                    if (shownRecap.headline.isNotEmpty()) {
                        AmountText(AnnotatedString(shownRecap.headline), style = KoshaTheme.type.body.copy(fontSize = 17.sp, fontWeight = FontWeight.Medium), color = c.ink)
                    }
                    if (shownRecap.summary.isNotEmpty()) {
                        AmountText(AnnotatedString(shownRecap.summary), style = KoshaTheme.type.small.copy(lineHeight = 19.5.sp), color = c.ink2)
                    }
                    shownRecap.points.forEach { p ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            val tone = when (p.tone) { "good" -> c.pos; "bad" -> c.warn; else -> c.ink3 }
                            Box(Modifier.size(6.dp).clip(CircleShape).background(tone))
                            AmountText(AnnotatedString(p.text), style = KoshaTheme.type.small, color = c.ink2)
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (shownRecap.writtenDay.isNotEmpty()) {
                            KText(
                                stringResource(R.string.overview_recap_written, shortDay(LocalDate.parse(shownRecap.writtenDay), locale)),
                                Modifier.weight(1f), style = KoshaTheme.type.xs, color = c.ink3,
                            )
                        } else Spacer(Modifier.weight(1f))
                        if (writable) {
                            Button(
                                stringResource(R.string.overview_recap_rewrite), { write(true) },
                                style = ButtonStyle.Secondary, size = ButtonSize.Small, enabled = !writing,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Serializable
private data class RecapRequest(val month: String, val rewrite: Boolean)

@Serializable
private data class PayBody(val status: String)

/** Needs attention: overdue bills (Mark paid), cards owed (Pay), categories above usual (Review), old values (Update). */
@Composable
private fun NeedsAttention(d: Dashboard, m: Meta, today: LocalDate) {
    val items = d.attention(today, m.wallets)
    if (items.isEmpty()) return
    val kosha = rememberKosha()
    val nav = LocalNav.current
    val toasts = LocalToasts.current
    val c = KoshaTheme.colors
    val locale = KoshaTheme.locale
    val scope = rememberCoroutineScope()
    val writable = canWrite()
    var busy by remember { mutableStateOf<String?>(null) }
    val offline = stringResource(R.string.overview_att_mark_failed)
    val res = LocalResources.current
    Card(Modifier.fillMaxWidth(), tight = true) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            KText(stringResource(R.string.overview_att_title), Modifier.weight(1f).semantics { heading() }, style = KoshaTheme.type.head)
            Pill(items.size.toString(), color = c.warn)
        }
        items.forEach { item ->
            Hairline()
            Row(
                Modifier.fillMaxWidth().heightIn(min = KoshaTheme.dims.row).padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (item) {
                    is Attention.Overdue -> IconTile(categoryIconName(m.categories.firstOrNull { it.id == item.payment.categoryId }?.icon))
                    is Attention.CardOwed -> DotTile(m, item.card)
                    is Attention.AboveUsual -> IconTile(categoryIconName(m.categories.firstOrNull { it.id == item.category.id }?.icon))
                    is Attention.Stale -> DotTile(m, item.account)
                }
                val (title, sub) = when (item) {
                    is Attention.Overdue -> stringResource(R.string.overview_att_due, item.payment.name, shortDay(LocalDate.parse(item.payment.day), locale)) to
                        stringResource(
                            R.string.overview_att_not_paid,
                            Money.format(if (item.payment.toWalletId != null) kotlin.math.abs(item.payment.amount) else item.payment.amount, item.payment.currency, locale),
                        )
                    is Attention.CardOwed -> stringResource(R.string.overview_att_card_owed, item.card.name, Money.format(item.owed, item.card.currency, locale)) to
                        stringResource(R.string.overview_att_card_hint)
                    is Attention.AboveUsual -> stringResource(
                        R.string.overview_att_category_above,
                        m.categories.firstOrNull { it.id == item.category.id }?.name ?: stringResource(R.string.overview_att_a_category),
                        percent(item.ratio, locale, signed = false),
                    ) to pluralStringResource(
                        R.plurals.overview_att_category_sub, item.category.count,
                        Money.format(item.category.total, d.primary, locale), Money.format(item.usual, d.primary, locale), item.category.count,
                    )
                    is Attention.Stale -> (
                        if (item.age == null) stringResource(R.string.overview_att_no_value, item.account.name)
                        else pluralStringResource(R.plurals.overview_att_value_age, item.age.toInt(), item.account.name, item.age)
                        ) to stringResource(R.string.overview_att_understated)
                }
                Column(Modifier.weight(1f)) {
                    AmountText(AnnotatedString(title), style = KoshaTheme.type.body, color = c.ink)
                    AmountText(AnnotatedString(sub), style = KoshaTheme.type.xs, color = c.ink3)
                }
                when (item) {
                    is Attention.Overdue -> Button(
                        stringResource(R.string.overview_att_mark_paid),
                        {
                            busy = item.key
                            scope.launch {
                                val done = kosha.write(toasts, offline) {
                                    kosha.api.patch<PayBody, kotlinx.serialization.json.JsonObject>(
                                        "/api/v1/recurring/${item.payment.subscriptionId}/payments/${item.payment.dueDay}", PayBody("paid"),
                                    )
                                }
                                if (done != null) toasts.show(Toast(res.getString(R.string.overview_att_marked_paid, item.payment.name)))
                                busy = null
                            }
                        },
                        style = ButtonStyle.Secondary, size = ButtonSize.Small, enabled = writable && busy != item.key,
                    )
                    is Attention.CardOwed -> Button(
                        stringResource(R.string.overview_att_pay), { nav.navigate(Route.Add(kind = "transfer")) },
                        style = ButtonStyle.Secondary, size = ButtonSize.Small, enabled = writable,
                    )
                    is Attention.AboveUsual -> Button(
                        stringResource(R.string.overview_att_review), { nav.openActivity(ActivitySearch(categoryId = item.category.id, month = d.month)) },
                        style = ButtonStyle.Secondary, size = ButtonSize.Small,
                    )
                    is Attention.Stale -> Button(
                        stringResource(R.string.overview_att_update), { nav.navigate(Route.InvestmentDetail(item.account.id)) },
                        style = ButtonStyle.Secondary, size = ButtonSize.Small,
                    )
                }
            }
        }
    }
}

/** An account's colour square on an icon tile (a wallet icon in neutral mode). */
@Composable
private fun DotTile(m: Meta, account: Wallet) {
    val c = KoshaTheme.colors
    if (!c.accountColours) {
        IconTile("wallet")
        return
    }
    Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(c.soft), contentAlignment = Alignment.Center) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(c.account(m.wallets.indexOfFirst { it.id == account.id })))
    }
}

/** Coming up: payments before month end (or the next 30 days), transfers greyed, and a way to Recurring. */
@Composable
private fun ComingUp(d: Dashboard, m: Meta, today: LocalDate) {
    val (end, soon) = d.comingUp(today, m.pref("upcoming_horizon", "month") == "30d")
    if (d.upcoming.isEmpty()) return
    val nav = LocalNav.current
    val c = KoshaTheme.colors
    val locale = KoshaTheme.locale
    // The web's cutoff for a past month is "YYYY-MM-31", which isn't a day in every month.
    val endDay = shortDay(runCatching { LocalDate.parse(end) }.getOrElse { YearMonth.parse(end.take(7)).atEndOfMonth() }, locale)
    Card(Modifier.fillMaxWidth(), tight = true) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            KText(stringResource(R.string.overview_coming_title), Modifier.weight(1f).semantics { heading() }, style = KoshaTheme.type.head)
            AmountText(
                AnnotatedString(stringResource(R.string.overview_coming_total, Money.format(soon.outflowTotal(d.primary, m.rates), d.primary, locale), endDay)),
                style = KoshaTheme.type.small, color = c.ink3,
            )
        }
        if (soon.isEmpty()) {
            Hairline()
            KText(stringResource(R.string.overview_coming_empty, endDay), Modifier.padding(16.dp), style = KoshaTheme.type.small, color = c.ink3)
        }
        soon.take(6).forEach { u ->
            Hairline()
            Row(
                Modifier.fillMaxWidth().heightIn(min = KoshaTheme.dims.row).padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                KText(
                    shortDay(LocalDate.parse(u.day), locale), Modifier.width(52.dp),
                    style = KoshaTheme.type.num.copy(fontSize = 13.sp), color = if (u.day < today.toString()) c.warn else c.ink3, maxLines = 1,
                )
                val to = u.toWalletId?.let { id -> m.wallets.firstOrNull { it.id == id } }
                if (to != null) {
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        KText(stringResource(R.string.tx_to))
                        app.getkosha.design.AccountDot(c.account(m.wallets.indexOf(to)))
                        KText(to.name, maxLines = 1)
                    }
                } else {
                    KText(u.name.ifEmpty { m.categories.firstOrNull { it.id == u.categoryId }?.name ?: stringResource(R.string.overview_untitled) }, Modifier.weight(1f), maxLines = 2)
                }
                if (u.toWalletId != null) MoneyText(kotlin.math.abs(u.amount), u.currency, sign = Sign.None, color = c.ink3)
                else MoneyText(u.amount, u.currency)
            }
        }
        Hairline()
        Box(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button) { nav.openTab(Route.Recurring) },
            contentAlignment = Alignment.Center,
        ) { KText(stringResource(R.string.app_ov_all_recurring), style = KoshaTheme.type.small, color = c.ink2) }
    }
}

/** Where it went: the top categories with a bar and a pill against the usual by the same day. */
@Composable
private fun WhereItWent(d: Dashboard, m: Meta) {
    val nav = LocalNav.current
    val c = KoshaTheme.colors
    val locale = KoshaTheme.locale
    val top = d.categories.take(5)
    val rest = d.categories.drop(5)
    val max = (d.categories.firstOrNull()?.total ?: 1L).coerceAtLeast(1L)
    Card(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                KText(stringResource(R.string.overview_where_title), Modifier.weight(1f).semantics { heading() }, style = KoshaTheme.type.head)
                if (top.isNotEmpty()) KText(stringResource(R.string.overview_where_vs_usual, d.dayN().toString()), style = KoshaTheme.type.xs, color = c.ink3)
            }
            if (top.isEmpty()) KText(stringResource(R.string.overview_where_empty), style = KoshaTheme.type.small, color = c.ink3)
            top.forEach { cat ->
                val info = m.categories.firstOrNull { it.id == cat.id }
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                        .clickable(role = Role.Button) { nav.openActivity(ActivitySearch(categoryId = cat.id, month = d.month)) },
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(Modifier.heightIn(min = 32.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PhIcon(categoryIconName(info?.icon))
                        KText(info?.name ?: stringResource(R.string.overview_unknown_category), Modifier.weight(1f), maxLines = 1)
                        MoneyText(cat.total, d.primary)
                        UsualPill(vsUsual(cat.total, cat.usualToDate, cat.fixed))
                    }
                    Track(cat.total.toFloat() / max)
                }
            }
            if (rest.isNotEmpty()) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    KText(pluralStringResource(R.plurals.overview_others, rest.size, rest.size), Modifier.weight(1f), style = KoshaTheme.type.small, color = c.ink3)
                    MoneyText(rest.sumOf { it.total }, d.primary, style = KoshaTheme.type.num.copy(fontSize = 13.sp), color = c.ink2)
                }
            }
            Box(
                Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable(role = Role.Button) { nav.openActivity(ActivitySearch(month = d.month)) },
                contentAlignment = Alignment.Center,
            ) { KText(stringResource(R.string.overview_all_activity), style = KoshaTheme.type.small, color = c.ink2) }
        }
    }
}

@Composable
private fun UsualPill(v: VsUsual) {
    val c = KoshaTheme.colors
    val locale = KoshaTheme.locale
    val (text, color) = when (v) {
        VsUsual.Fixed -> stringResource(R.string.overview_chip_fixed) to c.ink2
        VsUsual.New -> stringResource(R.string.overview_chip_new) to c.ink2
        is VsUsual.Change -> percent(v.ratio, locale) to when {
            v.warn -> c.warn
            v.good -> c.pos
            else -> c.ink2
        }
    }
    Box(Modifier.width(56.dp), contentAlignment = Alignment.Center) { Pill(text, color = color) }
}

@Composable
private fun Track(share: Float) {
    val c = KoshaTheme.colors
    Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(c.soft)) {
        Box(Modifier.fillMaxWidth(share.coerceIn(0.01f, 1f)).height(6.dp).clip(CircleShape).background(c.bar))
    }
}

/** Net worth: today's total, the change this month and over 12 months, and a way to Accounts. */
@Composable
private fun NetWorthCard(d: Dashboard, worth: NetWorth?) {
    val nav = LocalNav.current
    val c = KoshaTheme.colors
    val locale = KoshaTheme.locale
    Card(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                KText(stringResource(R.string.overview_net_worth), Modifier.weight(1f).semantics { heading() }, style = KoshaTheme.type.small, color = c.ink3)
                Box(Modifier.heightIn(min = 44.dp).clickable(role = Role.Button) { nav.openTab(Route.Accounts) }, contentAlignment = Alignment.Center) {
                    KText(stringResource(R.string.overview_accounts_link), style = KoshaTheme.type.small, color = c.ink2)
                }
            }
            if (worth != null) HeroMoney(worth.total, worth.currency.ifEmpty { d.primary }, size = 32.sp) else Skeleton(Modifier.width(170.dp), 32.dp)
            val (thisMonth, growth) = d.worthChange()
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (thisMonth != null) {
                    AmountText(
                        AnnotatedString(stringResource(R.string.overview_worth_this_month, Money.format(thisMonth, d.primary, locale, Sign.Always))),
                        style = KoshaTheme.type.small, color = if (thisMonth >= 0) c.pos else c.bad,
                    )
                }
                if (growth != null) KText(stringResource(R.string.overview_worth_growth, percent(growth, locale, digits = 1)), style = KoshaTheme.type.small, color = c.ink3)
            }
        }
    }
}

/** Overview on day one: nothing spent yet, a way to fill in the history, and the new accounts. */
@Composable
private fun FirstOverview(d: Dashboard, m: Meta, worth: NetWorth?) {
    val nav = LocalNav.current
    val c = KoshaTheme.colors
    val locale = KoshaTheme.locale
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("kosha.overview", Context.MODE_PRIVATE) }
    var historyHidden by remember { mutableStateOf(prefs.getBoolean("history_hidden", false)) }
    Card(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                KText(stringResource(R.string.overview_pace_spent_so_far), style = KoshaTheme.type.small, color = c.ink3)
                if (d.pace.today != null) KText(stringResource(R.string.overview_pace_day, d.dayN().toString(), d.pace.daysInMonth.toString()), style = KoshaTheme.type.small, color = c.ink3)
            }
            HeroMoney(0, d.primary, color = c.ink3)
            KText(stringResource(R.string.app_ov_first_entry), style = KoshaTheme.type.small, color = c.ink3)
        }
    }
    if (!historyHidden) {
        Card(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(c.brand.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                    PhIcon("clock-counter-clockwise", size = 18.dp, tint = c.brand)
                }
                Column(Modifier.weight(1f)) {
                    KText(stringResource(R.string.welcome_history_title), style = KoshaTheme.type.head)
                    KText(stringResource(R.string.app_ov_from_statements), style = KoshaTheme.type.xs, color = c.ink3)
                }
                Button(stringResource(R.string.app_ov_import), { nav.navigate(Route.Import) }, style = ButtonStyle.Secondary, size = ButtonSize.Small)
                IconButton("x", stringResource(R.string.app_ov_hide_card), {
                    historyHidden = true
                    prefs.edit { putBoolean("history_hidden", true) }
                })
            }
        }
    }
    val accounts = m.wallets.filter { !it.isArchived }
    Card(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                KText(stringResource(R.string.overview_net_worth), style = KoshaTheme.type.small, color = c.ink3)
                KText(pluralStringResource(R.plurals.app_ov_accounts, accounts.size, accounts.size), style = KoshaTheme.type.xs, color = c.ink3)
            }
            if (worth != null) HeroMoney(worth.total, worth.currency.ifEmpty { d.primary }, size = 30.sp)
            KText(stringResource(R.string.app_ov_chart_later), Modifier.padding(bottom = 4.dp), style = KoshaTheme.type.xs, color = c.ink3)
            accounts.forEach { a ->
                Hairline()
                AccountLine(m, a)
            }
        }
    }
}

@Composable
private fun ColumnScope.AccountLine(m: Meta, a: Wallet) {
    val c = KoshaTheme.colors
    val locale = KoshaTheme.locale
    Row(Modifier.fillMaxWidth().heightIn(min = KoshaTheme.dims.row).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        app.getkosha.design.AccountDot(c.account(m.wallets.indexOf(a)), size = 10.dp)
        Column(Modifier.weight(1f)) {
            KText(a.name)
            val type = stringResource(
                when (a.type) {
                    AccountType.Bank -> R.string.accounts_type_bank
                    AccountType.Cash -> R.string.accounts_type_cash
                    AccountType.Credit -> R.string.accounts_type_credit
                    AccountType.Investment -> R.string.accounts_type_investment
                },
            )
            val detail = when (a.type) {
                AccountType.Credit -> " · " + stringResource(R.string.accounts_available, Money.format(a.balance, a.currency, locale))
                AccountType.Investment -> if (a.gain != null) " · " + stringResource(R.string.welcome_gain, Money.format(a.gain ?: 0, a.currency, locale)) else ""
                else -> ""
            }
            AmountText(AnnotatedString(type + detail), style = KoshaTheme.type.xs, color = c.ink3)
        }
        MoneyText(a.worth(), a.currency)
    }
}
