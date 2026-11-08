package app.getkosha.activity

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.getkosha.design.Card
import app.getkosha.design.Hairline
import app.getkosha.design.IconButton
import app.getkosha.design.KText
import app.getkosha.design.KoshaTheme
import app.getkosha.design.PhIcon
import app.getkosha.design.R
import app.getkosha.design.categoryIconName
import app.getkosha.model.Dates
import app.getkosha.model.TransactionList
import app.getkosha.model.convert
import app.getkosha.ui.HeroMoney
import app.getkosha.ui.LoadingNote
import app.getkosha.ui.LocalNav
import app.getkosha.ui.MoneyText
import app.getkosha.ui.Page
import app.getkosha.ui.Route
import app.getkosha.ui.meta
import app.getkosha.ui.rememberKosha
import app.getkosha.ui.shortDay
import java.time.LocalDate

/**
 * One tag or event across every date: its name and days, how many entries and what they cost, spending by
 * category, and the entries by day ("added to the event by hand" where the day is outside the event's dates).
 */
@Composable
fun TagViewScreen(tag: String?, eventId: String?) {
    val kosha = rememberKosha()
    val nav = LocalNav.current
    val m = meta() ?: return
    val c = KoshaTheme.colors
    val locale = KoshaTheme.locale
    val shown = kosha.currency.collectAsStateWithLifecycle().value ?: m.primary
    val version by kosha.version.collectAsStateWithLifecycle()
    val event = eventId?.let { id -> m.events.firstOrNull { it.id == id } }
    val filters = Filters(tag = tag, eventId = eventId)
    // ponytail: the entries by day stop at 2,000; a tag or event is rarely bigger. The totals above come from the server.
    val loaded = remember(tag, eventId, version) {
        kosha.load(filters.key(Dates.month(kosha.tz))) {
            var page = get<TransactionList>("/api/v1/transactions", filters.params(Dates.month(kosha.tz), limit = 500))
            var all = page
            while (page.nextOffset != null && all.transactions.size < 2000) {
                page = get("/api/v1/transactions", filters.params(Dates.month(kosha.tz), offset = page.nextOffset ?: 0, limit = 500))
                all = all.copy(transactions = all.transactions + page.transactions)
            }
            all
        }
    }.collectAsStateWithLifecycle(null).value
    val list = loaded?.value
    val rows = list?.transactions.orEmpty().also { Seen.remember(it) }
    Page(
        title = event?.name ?: tag?.let { "#$it" } ?: "",
        onBack = { nav.popBackStack() },
        subtitle = if (event != null) stringResource(R.string.app_act_event_all_dates) else stringResource(R.string.tx_tag_all_dates, tag ?: ""),
        actions = {
            if (event != null) IconButton("pencil-simple", stringResource(R.string.common_edit), { nav.navigate(Route.Settings("events")) })
        },
    ) {
        if (list == null) {
            if (loaded?.refreshing != false) LoadingNote() else KText(loaded.error ?: stringResource(R.string.common_offline_body), style = KoshaTheme.type.small, color = c.ink3)
            return@Page
        }
        val spent = -list.totals.sumOf { convert(it.spent, it.currency, shown, m.rates) }
        // Spending by category from the server's per-category, per-currency totals (net of refunds; transfers and
        // corrections left out), converted at the server's rates. Categories that came out as income aren't shown.
        val byCategory = list.byCategory.groupBy { it.categoryId }
            .mapValues { (_, l) -> -l.sumOf { convert(it.amount, it.currency, shown, m.rates) } }
            .filterValues { it > 0 }
            .entries.sortedByDescending { it.value }
        val first = event?.startDay ?: rows.minOfOrNull { it.day }
        val last = event?.endDay ?: rows.maxOfOrNull { it.day }
        Card(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    if (first != null) {
                        val range = if (last == null || last == first) shortDay(LocalDate.parse(first), locale, year = true)
                        else shortDay(LocalDate.parse(first), locale) + " – " + shortDay(LocalDate.parse(last), locale, year = true)
                        KText(range, style = KoshaTheme.type.small, color = c.ink3)
                    }
                    KText(pluralStringResource(R.plurals.tx_count, list.total, list.total.toString()), style = KoshaTheme.type.small, color = c.ink3)
                }
                HeroMoney(spent, shown, size = 34.sp)
                val max = byCategory.firstOrNull()?.value?.coerceAtLeast(1) ?: 1
                byCategory.forEach { (id, total) ->
                    val cat = m.categories.firstOrNull { it.id == id }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PhIcon(categoryIconName(cat?.icon), size = 18.dp)
                        KText(cat?.name ?: stringResource(R.string.overview_unknown_category), Modifier.weight(1f), style = KoshaTheme.type.small)
                        MoneyText(total, shown, style = KoshaTheme.type.num.copy(fontSize = 13.sp))
                    }
                    Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(c.soft)) {
                        Box(Modifier.fillMaxWidth((total.toFloat() / max).coerceIn(0.01f, 1f)).height(6.dp).clip(CircleShape).background(c.bar))
                    }
                }
            }
        }
        rows.byDay().forEach { (day, entries) ->
            DayHead(day, entries.dayTotal(shown, m.rates), shown, Modifier.padding(start = 2.dp, top = 6.dp))
            Card(Modifier.fillMaxWidth(), tight = true) {
                entries.forEachIndexed { i, t ->
                    if (i > 0) Hairline()
                    val byHand = event != null && t.outsideEvent(event.startDay, event.endDay)
                    TxRow(t, m, shown, note = if (byHand) stringResource(R.string.app_act_by_hand) else null) { openEntry(kosha, nav, t) }
                }
            }
        }
    }
}
