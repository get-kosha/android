@file:UseSerializers(RoundedLong::class)

package app.getkosha.activity

import kotlinx.serialization.UseSerializers
import app.getkosha.model.RoundedLong

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.getkosha.design.AccountDot
import app.getkosha.design.AmountDisplay
import app.getkosha.design.AmountKeypad
import app.getkosha.design.Button
import app.getkosha.design.ButtonSize
import app.getkosha.design.ButtonStyle
import app.getkosha.design.Card
import app.getkosha.design.Chip
import app.getkosha.design.Field
import app.getkosha.design.Hairline
import app.getkosha.design.IconButton
import app.getkosha.design.IconTile
import app.getkosha.design.KText
import app.getkosha.design.KoshaTheme
import app.getkosha.design.ListOption
import app.getkosha.design.ListPicker
import app.getkosha.design.PhIcon
import app.getkosha.design.R
import app.getkosha.design.Segmented
import app.getkosha.design.Sheet
import app.getkosha.design.Switch
import app.getkosha.design.Toast
import app.getkosha.design.categoryIconName
import app.getkosha.model.Categories
import app.getkosha.model.Dates
import app.getkosha.model.EntryKind
import app.getkosha.model.Keypad
import app.getkosha.model.KoshaJson
import app.getkosha.model.Meta
import app.getkosha.model.Money
import app.getkosha.model.Sign
import app.getkosha.model.Transaction
import app.getkosha.model.TransactionList
import app.getkosha.model.convert
import app.getkosha.ui.ActivityRequests
import app.getkosha.ui.AmountText
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
import app.getkosha.ui.rememberKosha
import app.getkosha.ui.shortDay
import app.getkosha.ui.write
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs
import kotlin.math.roundToInt

private val FiltersSaver = Saver<Filters, String>(
    save = { KoshaJson.encodeToString(Filters.serializer(), it) },
    restore = { KoshaJson.decodeFromString(Filters.serializer(), it) },
)

/**
 * Activity: the month's entries from search_transactions, with a search field, All · Out · In · Transfers, the
 * Filters sheet, a summary of the server's totals in the display currency, entries grouped by day, entries
 * waiting in the outbox on top, swipe left to delete and right to duplicate, and paging.
 */
@Composable
fun ActivityScreen() {
    val kosha = rememberKosha()
    val nav = LocalNav.current
    val toasts = LocalToasts.current
    val m = meta() ?: return
    val c = KoshaTheme.colors
    val month by kosha.month.collectAsStateWithLifecycle()
    val shown = kosha.currency.collectAsStateWithLifecycle().value ?: m.primary
    val version by kosha.version.collectAsStateWithLifecycle()
    val waiting by kosha.outbox.items.collectAsStateWithLifecycle()
    val writable = canWrite()
    val scope = rememberCoroutineScope()
    var filters by rememberSaveable(stateSaver = FiltersSaver) { mutableStateOf(Filters()) }
    var typed by rememberSaveable { mutableStateOf(filters.query) }
    var sheet by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Transaction?>(null) }
    // A search handed over from Overview, Search, Ask or a transaction.
    val asked by ActivityRequests.next.collectAsStateWithLifecycle()
    LaunchedEffect(asked) {
        val s = asked ?: return@LaunchedEffect
        s.month?.let { kosha.month.value = YearMonth.parse(it) }
        filters = Filters.of(s)
        typed = filters.query
        ActivityRequests.next.value = null
    }
    LaunchedEffect(typed) {
        delay(300)
        if (typed != filters.query) filters = filters.copy(query = typed)
    }
    val loaded = remember(filters, month, version) {
        kosha.load(filters.key(month)) { get<TransactionList>("/api/v1/transactions", filters.params(month)) }
    }.collectAsStateWithLifecycle(null).value
    var more by remember(filters, month, version) { mutableStateOf<List<Transaction>>(emptyList()) }
    var next by remember(filters, month, version) { mutableStateOf<Int?>(null) }
    var loadingMore by remember(filters, month, version) { mutableStateOf(false) }
    val list = loaded?.value
    LaunchedEffect(list) { list?.let { Seen.remember(it.transactions); next = it.nextOffset } }
    val rows = (list?.transactions ?: emptyList()) + more
    val searching = filters.count > 0 || filters.query.isNotBlank() || filters.type != null
    // Entries waiting for a connection, shown first in their day with a "waiting" mark.
    val pending = if (searching) emptyList() else waiting.mapNotNull { q ->
        val day = q.entry.day ?: Dates.text(Dates.today(kosha.tz))
        if (!day.startsWith(month.toString())) null
        else Transaction(
            id = q.entry.id, day = day, payee = q.entry.payee, amount = q.entry.amount, currency = q.currency,
            accountId = q.entry.accountId, accountName = q.accountName, categoryId = q.entry.categoryId ?: "",
            categoryName = q.categoryName, tags = q.entry.tags,
        )
    }
    val waitingIds = pending.map { it.id }.toSet()
    val groups = (pending + rows).sortedByDescending { it.day }.byDay()
    val loadMore = {
        val offset = next
        if (offset != null && !loadingMore) {
            loadingMore = true
            scope.launch {
                runCatching { kosha.api.get<TransactionList>("/api/v1/transactions", filters.params(month, offset)) }.onSuccess {
                    Seen.remember(it.transactions)
                    more = more + it.transactions
                    next = it.nextOffset
                }
                loadingMore = false
            }
        }
    }
    val readOnly = stringResource(R.string.common_demo_read_only)

    Column(Modifier.fillMaxSize().background(c.bg)) {
        MainHeader()
        LazyColumn(Modifier.weight(1f).monthSwipe(kosha), contentPadding = TabBarSpace) {
            item { TabBanners(Modifier.padding(bottom = 8.dp)) }
            item {
                Row(Modifier.padding(horizontal = 16.dp).padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Field(typed, { typed = it.take(40) }, Modifier.weight(1f), placeholder = stringResource(R.string.tx_search_placeholder), icon = "magnifying-glass")
                    Box {
                        IconButton(
                            "funnel",
                            if (filters.count > 0) stringResource(R.string.tx_filters_on, filters.count.toString()) else stringResource(R.string.tx_filters),
                            { sheet = true }, solid = true,
                        )
                        if (filters.count > 0) Box(Modifier.align(Alignment.TopEnd).padding(8.dp).size(8.dp).clip(CircleShape).background(c.selection))
                    }
                }
            }
            item {
                TypeSegments(filters.type, { filters = filters.copy(type = it) }, Modifier.padding(horizontal = 16.dp).padding(top = 10.dp))
            }
            item {
                val totals = list?.totals.orEmpty().summary(shown, m.rates)
                SummaryCard(totals, shown, empty = rows.isEmpty(), modifier = Modifier.padding(horizontal = 16.dp).padding(top = 12.dp))
            }
            // A tag or event filter narrows to this month; its own view covers every date.
            if (filters.tag != null || filters.eventId != null) {
                item {
                    Box(Modifier.padding(horizontal = 16.dp).padding(top = 12.dp)) {
                        val event = m.events.firstOrNull { it.id == filters.eventId }
                        if (event != null) LinkCard("calendar-star", event.name, stringResource(R.string.app_act_event_all_dates)) { nav.navigate(Route.TagView(eventId = event.id)) }
                        else LinkCard("hash", stringResource(R.string.tx_tag_all_dates, filters.tag ?: "")) { nav.navigate(Route.TagView(tag = filters.tag)) }
                    }
                }
            }
            when {
                list == null && loaded?.refreshing != false -> item { LoadingRows() }
                groups.isEmpty() && searching -> item {
                    Column(Modifier.fillMaxWidth().padding(top = 48.dp, start = 16.dp, end = 16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        KText(stringResource(R.string.tx_no_match), style = KoshaTheme.type.small, color = c.ink3, align = TextAlign.Center)
                        Button(stringResource(R.string.tx_clear_filters), { filters = Filters(); typed = "" }, style = ButtonStyle.Secondary, size = ButtonSize.Medium)
                    }
                }
                groups.isEmpty() -> item {
                    if (list == null) KText(loaded.error ?: stringResource(R.string.common_offline_body), Modifier.padding(16.dp), style = KoshaTheme.type.small, color = c.ink3)
                    else EmptyMonth()
                }
                else -> {
                    groups.forEach { (day, entries) ->
                        item(key = "day-$day") {
                            DayHead(day, entries.dayTotal(shown, m.rates), shown)
                        }
                        item(key = "rows-$day") {
                            Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp), tight = true) {
                                entries.forEachIndexed { i, t ->
                                    if (i > 0) Hairline()
                                    val isWaiting = t.id in waitingIds
                                    SwipeRow(
                                        enabled = !isWaiting,
                                        onDelete = { if (writable) deleting = t else toasts.show(Toast(readOnly)) },
                                        onDuplicate = { if (writable) nav.navigate(Route.Add(duplicateOf = t.id)) else toasts.show(Toast(readOnly)) },
                                    ) {
                                        TxRow(t, m, shown, waiting = isWaiting, onClick = if (isWaiting) null else ({ openEntry(kosha, nav, t) }))
                                    }
                                }
                            }
                        }
                    }
                    if (next != null) {
                        item {
                            LaunchedEffect(next) { loadMore() }
                            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                Button(stringResource(R.string.tx_load_earlier), loadMore, style = ButtonStyle.Secondary, size = ButtonSize.Medium, enabled = !loadingMore)
                            }
                        }
                    }
                }
            }
        }
    }
    if (sheet) FiltersSheet(filters, month, m, onApply = { filters = it; typed = it.query; sheet = false }, onDismiss = { sheet = false })
    deleting?.let { t -> DeleteConfirm(t, onDone = { deleting = null }) }
}

/** Opens an entry's detail, keeping its row on this phone so the detail opens at once, offline too. */
internal fun openEntry(kosha: app.getkosha.data.Kosha, nav: androidx.navigation.NavController, t: Transaction) {
    kosha.cache.write("tx/${t.id}", KoshaJson.encodeToString(Transaction.serializer(), t))
    nav.navigate(if (t.transfer != null) Route.TransferDetail(t.id) else Route.TxDetail(t.id))
}

/** All · Out · In · Transfers. */
@Composable
internal fun TypeSegments(type: String?, onChange: (String?) -> Unit, modifier: Modifier = Modifier) {
    Segmented(
        listOf(
            "" to stringResource(R.string.tx_type_all),
            "expense" to stringResource(R.string.tx_out),
            "income" to stringResource(R.string.tx_in),
            "transfer" to stringResource(R.string.tx_type_transfers),
        ),
        type ?: "",
        { onChange(it.ifEmpty { null }) },
        modifier.fillMaxWidth(),
        small = true,
    )
}

@Composable
private fun SummaryCard(s: Summary, currency: String, empty: Boolean, modifier: Modifier) {
    val c = KoshaTheme.colors
    Card(modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                Triple(R.string.tx_out, s.out, if (empty) c.ink3 else c.ink),
                Triple(R.string.tx_in, s.inn, if (empty) c.ink3 else if (s.inn > 0) c.pos else c.ink),
                Triple(R.string.tx_net, s.net, if (empty) c.ink3 else c.ink),
            ).forEach { (label, value, color) ->
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    KText(stringResource(label), style = KoshaTheme.type.xs, color = c.ink3)
                    MoneyText(value, currency, sign = if (value == 0L) Sign.Natural else Sign.Always, color = color)
                }
            }
        }
        if (s.moved > 0) {
            Spacer(Modifier.height(8.dp))
            AmountText(AnnotatedString(stringResource(R.string.tx_moved_not_counted, Money.format(s.moved, currency, KoshaTheme.locale))), style = KoshaTheme.type.xs, color = c.ink3)
        }
    }
}

/** "Today · Sat 7 Nov" and the day's total. */
@Composable
internal fun DayHead(day: String, total: Long, currency: String, modifier: Modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 6.dp)) {
    val kosha = rememberKosha()
    val c = KoshaTheme.colors
    val locale = KoshaTheme.locale
    val date = LocalDate.parse(day)
    val today = Dates.today(kosha.tz)
    val label = shortDay(date, locale, weekday = true, year = date.year != today.year).let {
        when (date) {
            today -> stringResource(R.string.add_today) + " · " + it
            today.minusDays(1) -> stringResource(R.string.add_yesterday) + " · " + it
            else -> it
        }
    }
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        KText(label, Modifier.weight(1f).semantics { heading() }, style = KoshaTheme.type.small.copy(fontWeight = FontWeight.Medium), color = c.ink)
        if (total != 0L) MoneyText(total, currency, sign = Sign.Always, style = KoshaTheme.type.num.copy(fontSize = 13.sp), color = if (total > 0) c.pos else c.ink3)
    }
}

/**
 * One entry: its category tile, payee, "category · account · #tags", and the amount. Income shows + in the
 * positive colour; transfers and neutral entries show in ink2 without a sign. A foreign amount shows ≈ in the
 * display currency under it.
 */
@Composable
internal fun TxRow(t: Transaction, m: Meta, display: String, waiting: Boolean = false, note: String? = null, onClick: (() -> Unit)?) {
    val c = KoshaTheme.colors
    val locale = KoshaTheme.locale
    val category = m.categories.firstOrNull { it.id == t.categoryId }
    val transfer = t.transfer
    val neutral = Categories.isNeutral(t.categoryId)
    val accountIndex = m.wallets.indexOfFirst { it.id == t.accountId }
    Row(
        Modifier
            .fillMaxWidth()
            .background(c.surface)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .heightIn(min = KoshaTheme.dims.row)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconTile(if (transfer != null) "arrows-left-right" else categoryIconName(category?.icon))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (transfer != null) {
                val to = m.wallets.firstOrNull { it.id == transfer.counterAccountId }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    AccountDot(c.account(accountIndex))
                    KText(t.accountName, Modifier.weight(1f, fill = false), color = c.ink2, maxLines = 1)
                    PhIcon("arrow-right", size = 14.dp, tint = c.ink3)
                    if (to != null) AccountDot(c.account(m.wallets.indexOf(to)))
                    KText(to?.name ?: "", Modifier.weight(1f, fill = false), color = c.ink2, maxLines = 1)
                }
                KText(stringResource(R.string.tx_kind_transfer) + " · " + stringResource(R.string.subs_not_counted), style = KoshaTheme.type.xs, color = c.ink3, maxLines = 1)
            } else {
                KText(t.payee.ifEmpty { category?.name ?: stringResource(R.string.tx_untitled) }, maxLines = 1)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    KText((category?.name ?: t.categoryName) + " · ", style = KoshaTheme.type.xs, color = c.ink3, maxLines = 1)
                    AccountDot(c.account(accountIndex))
                    val rest = buildList {
                        if (t.status == "upcoming") add(stringResource(R.string.tx_kind_upcoming))
                        addAll(t.tags.map { "#$it" })
                        if (note != null) add(note)
                    }
                    KText(t.accountName + rest.joinToString("") { " · $it" }, Modifier.weight(1f, fill = false), style = KoshaTheme.type.xs, color = c.ink3, maxLines = 1)
                    if (t.recurringId != null) PhIcon("arrows-clockwise", size = 14.dp, tint = c.ink3)
                    if (t.attachmentCount > 0) PhIcon("paperclip", size = 14.dp, tint = c.ink3)
                }
            }
        }
        if (waiting) {
            Row(
                Modifier.height(22.dp).clip(CircleShape).background(c.soft).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                PhIcon("cloud-arrow-up", size = 14.dp)
                KText(stringResource(R.string.app_waiting), style = KoshaTheme.type.xs, color = c.ink2)
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            when {
                transfer != null -> MoneyText(t.sent, t.currency, sign = Sign.None, color = c.ink2)
                neutral -> MoneyText(t.amount, t.currency, sign = Sign.None, color = c.ink2)
                t.amount > 0 -> MoneyText(t.amount, t.currency, sign = Sign.Always, color = c.pos)
                else -> MoneyText(t.amount, t.currency)
            }
            if (t.currency != display) {
                val amount = if (transfer != null) t.sent else t.amount
                AmountText(
                    AnnotatedString("≈ " + Money.format(convert(abs(amount), t.currency, display, m.rates), display, locale)),
                    style = KoshaTheme.type.xs, color = c.ink3,
                )
            }
        }
    }
}

/**
 * A row that swipes: left reveals Delete, right reveals Duplicate. Letting go past the reveal does it; TalkBack
 * gets both as actions.
 */
@Composable
internal fun SwipeRow(enabled: Boolean, onDelete: () -> Unit, onDuplicate: () -> Unit, content: @Composable () -> Unit) {
    val c = KoshaTheme.colors
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    val reveal = with(LocalDensity.current) { 88.dp.toPx() }
    val deleteLabel = stringResource(R.string.common_delete)
    val duplicateLabel = stringResource(R.string.tx_duplicate)
    if (!enabled) {
        content()
        return
    }
    Box(
        Modifier.semantics {
            customActions = listOf(
                CustomAccessibilityAction(deleteLabel) { onDelete(); true },
                CustomAccessibilityAction(duplicateLabel) { onDuplicate(); true },
            )
        },
    ) {
        Row(Modifier.matchParentSize()) {
            if (offset.value > 0) {
                Column(Modifier.fillMaxHeight().width(88.dp).background(c.soft), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    PhIcon("copy", size = 22.dp)
                    KText(duplicateLabel, style = KoshaTheme.type.xs, color = c.ink2)
                }
            }
            Spacer(Modifier.weight(1f))
            if (offset.value < 0) {
                Column(Modifier.fillMaxHeight().width(88.dp).background(c.bad), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    PhIcon("trash", size = 22.dp, tint = Color.White)
                    KText(deleteLabel, style = KoshaTheme.type.xs, color = Color.White)
                }
            }
        }
        Box(
            Modifier
                .offset { IntOffset(offset.value.roundToInt(), 0) }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            val v = offset.value
                            scope.launch { offset.animateTo(0f, tween(200)) }
                            if (v <= -reveal) onDelete()
                            if (v >= reveal) onDuplicate()
                        },
                        onDragCancel = { scope.launch { offset.animateTo(0f) } },
                    ) { change, amount ->
                        change.consume()
                        scope.launch { offset.snapTo((offset.value + amount).coerceIn(-reveal * 1.4f, reveal * 1.4f)) }
                    }
                },
        ) { content() }
    }
}

@Composable
private fun LoadingRows() {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(Modifier.fillMaxWidth(), tight = true) {
            repeat(4) { i ->
                if (i > 0) Hairline()
                Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Skeleton(Modifier.size(36.dp), 36.dp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Skeleton(Modifier.fillMaxWidth(0.6f), 12.dp)
                        Skeleton(Modifier.fillMaxWidth(0.4f), 10.dp)
                    }
                    Skeleton(Modifier.width(64.dp), 14.dp)
                }
            }
        }
        LoadingNote(Modifier.fillMaxWidth())
    }
}

/** No entries this month: what Activity is for, Add a transaction, or import a statement. */
@Composable
private fun EmptyMonth() {
    val nav = LocalNav.current
    val c = KoshaTheme.colors
    Column(
        Modifier.fillMaxWidth().padding(start = 32.dp, end = 32.dp, top = 72.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(56.dp).clip(RoundedCornerShape(18.dp)).background(c.soft), contentAlignment = Alignment.Center) { PhIcon("receipt", size = 28.dp) }
        KText(stringResource(R.string.app_act_empty_title), style = KoshaTheme.type.head, align = TextAlign.Center)
        KText(stringResource(R.string.app_act_empty_body), style = KoshaTheme.type.small, color = c.ink3, align = TextAlign.Center)
        if (canWrite()) {
            Button(stringResource(R.string.add_heading), { nav.navigate(Route.Add()) }, Modifier.padding(top = 6.dp), size = ButtonSize.Medium, icon = "plus")
            Box(Modifier.heightIn(min = 48.dp).clickable(role = Role.Button) { nav.navigate(Route.Import) }.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                KText(stringResource(R.string.app_act_or_import), style = KoshaTheme.type.small, color = c.ink2)
            }
        }
    }
}

@Serializable
internal data class Ids(val ids: List<String>)

/**
 * "Delete this transaction?" with its payee, amount and day; "Its receipt is deleted with it." when it has files.
 * A transfer is deleted from both accounts.
 */
@Composable
internal fun DeleteConfirm(t: Transaction, onDone: (deleted: Boolean) -> Unit) {
    val kosha = rememberKosha()
    val toasts = LocalToasts.current
    val c = KoshaTheme.colors
    val locale = KoshaTheme.locale
    val scope = rememberCoroutineScope()
    val res = LocalResources.current
    val transfer = t.transfer != null
    var busy by remember { mutableStateOf(false) }
    val offline = stringResource(R.string.tx_delete_failed)
    Sheet("", { onDone(false) }) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(52.dp).clip(CircleShape).background(c.bad.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) { PhIcon("trash", size = 24.dp, tint = c.bad) }
            KText(
                stringResource(if (transfer) R.string.tx_delete_transfer_title else R.string.tx_delete_transaction_title),
                Modifier.semantics { heading() }, style = KoshaTheme.type.title.copy(fontSize = 20.sp), align = TextAlign.Center,
            )
            val amount = if (transfer) Money.format(t.sent, t.currency, locale, Sign.None) else Money.format(t.amount, t.currency, locale)
            AmountText(
                AnnotatedString(stringResource(R.string.tx_delete_summary, t.payee.ifEmpty { t.categoryName }, amount, shortDay(LocalDate.parse(t.day), locale))),
                style = KoshaTheme.type.small, color = c.ink2, align = TextAlign.Center,
            )
            if (transfer) KText(stringResource(R.string.tx_delete_transfer_both), style = KoshaTheme.type.small, color = c.ink2, align = TextAlign.Center)
            if (t.attachmentCount > 0) {
                KText(pluralStringResource(R.plurals.app_act_receipt_deleted, t.attachmentCount, t.attachmentCount), style = KoshaTheme.type.small, color = c.ink2, align = TextAlign.Center)
            }
            if (t.reconciled) KText(stringResource(R.string.tx_delete_reconciled), style = KoshaTheme.type.small, color = c.warn, align = TextAlign.Center)
        }
        Spacer(Modifier.height(14.dp))
        Button(
            stringResource(if (transfer) R.string.tx_delete_transfer else R.string.tx_delete_transaction),
            {
                busy = true
                scope.launch {
                    val done = kosha.write(toasts, offline) { kosha.api.post<Ids, JsonObject>("/api/v1/transactions/delete", Ids(listOf(t.id))) }
                    busy = false
                    if (done != null) toasts.show(Toast(res.getString(R.string.tx_deleted)))
                    onDone(done != null)
                }
            },
            Modifier.fillMaxWidth(), style = ButtonStyle.Danger, enabled = !busy,
        )
        Spacer(Modifier.height(10.dp))
        Button(stringResource(R.string.common_cancel), { onDone(false) }, Modifier.fillMaxWidth(), style = ButtonStyle.Secondary)
    }
}

/**
 * Filters: Show, Account, Category, Amount (at least / at most, on the keypad, in each entry's own currency),
 * Tag, Event and balance corrections, with a live count on "Show N transactions".
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FiltersSheet(start: Filters, month: YearMonth, m: Meta, onApply: (Filters) -> Unit, onDismiss: () -> Unit) {
    val kosha = rememberKosha()
    val c = KoshaTheme.colors
    val locale = KoshaTheme.locale
    var draft by remember { mutableStateOf(start) }
    var page by remember { mutableStateOf("") }
    var count by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(draft) {
        delay(250)
        count = runCatching { kosha.api.get<TransactionList>("/api/v1/transactions", draft.params(month, limit = 1)).total }.getOrNull()
    }
    val any = stringResource(R.string.app_act_any)
    val title = when (page) {
        "category" -> stringResource(R.string.tx_category)
        "tag" -> stringResource(R.string.tx_tag)
        "event" -> stringResource(R.string.tx_event)
        "min" -> stringResource(R.string.app_act_at_least)
        "max" -> stringResource(R.string.tx_at_most)
        else -> stringResource(R.string.tx_filters)
    }
    Sheet(title, onDismiss) {
        when (page) {
            "category" -> ListPicker(
                listOf(ListOption("", stringResource(R.string.tx_any_category))) +
                    m.categories.filter { !it.isArchived && !Categories.isNeutral(it.id) }.map { ListOption(it.id, it.name) },
                draft.categoryId ?: "",
                { draft = draft.copy(categoryId = it.value.ifEmpty { null }); page = "" },
            )
            "tag" -> ListPicker(
                listOf(ListOption("", any)) + m.tags.map { ListOption(it, "#$it") },
                draft.tag ?: "",
                { draft = draft.copy(tag = it.value.ifEmpty { null }); page = "" },
            )
            "event" -> ListPicker(
                listOf(ListOption("", any)) + m.events.map { ListOption(it.id, it.name) },
                draft.eventId ?: "",
                { draft = draft.copy(eventId = it.value.ifEmpty { null }); page = "" },
            )
            "min", "max" -> {
                val startValue = if (page == "min") draft.minAmount else draft.maxAmount
                var input by remember(page) { mutableStateOf(startValue?.let { plainAmount(it) } ?: "") }
                AmountDisplay(input, m.primary, EntryKind.Transfer, { input = it }, note = stringResource(R.string.tx_amount_note))
                AmountKeypad(input, { input = it }, Modifier.padding(vertical = 8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(stringResource(R.string.tx_clear), {
                        draft = if (page == "min") draft.copy(minAmount = null) else draft.copy(maxAmount = null)
                        page = ""
                    }, Modifier.weight(1f), style = ButtonStyle.Secondary)
                    Button(stringResource(R.string.common_done), {
                        val v = Keypad.minorUnits(input)
                        draft = if (page == "min") draft.copy(minAmount = v) else draft.copy(maxAmount = v)
                        page = ""
                    }, Modifier.weight(1f))
                }
            }
            else -> Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Box(Modifier.heightIn(min = 44.dp).clickable(role = Role.Button) { draft = Filters(query = draft.query) }, contentAlignment = Alignment.Center) {
                        KText(stringResource(R.string.tx_clear_all), style = KoshaTheme.type.small, color = c.ink2)
                    }
                }
                Labeled(stringResource(R.string.tx_show)) { TypeSegments(draft.type, { draft = draft.copy(type = it) }) }
                Labeled(stringResource(R.string.tx_account)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        m.wallets.filter { !it.isArchived }.forEach { w ->
                            Chip(
                                w.name, small = true, on = draft.accountId == w.id,
                                onClick = { draft = draft.copy(accountId = w.id.takeIf { it != draft.accountId }) },
                                leading = { AccountDot(c.account(m.wallets.indexOf(w))) },
                            )
                        }
                    }
                }
                Labeled(stringResource(R.string.tx_category)) {
                    PickField("tag", m.categories.firstOrNull { it.id == draft.categoryId }?.name ?: stringResource(R.string.tx_any_category), draft.categoryId != null) { page = "category" }
                }
                Labeled(stringResource(R.string.tx_amount)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AmountField(stringResource(R.string.app_act_at_least), draft.minAmount, m.primary, any, Modifier.weight(1f)) { page = "min" }
                        AmountField(stringResource(R.string.tx_at_most), draft.maxAmount, m.primary, any, Modifier.weight(1f)) { page = "max" }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Labeled(stringResource(R.string.tx_tag), Modifier.weight(1f)) { PickField("hash", draft.tag?.let { "#$it" } ?: any, draft.tag != null) { page = "tag" } }
                    Labeled(stringResource(R.string.tx_event), Modifier.weight(1f)) {
                        PickField("calendar-star", m.events.firstOrNull { it.id == draft.eventId }?.name ?: any, draft.eventId != null) { page = "event" }
                    }
                }
                Card(Modifier.fillMaxWidth(), tight = true) {
                    val label = stringResource(R.string.app_act_show_corrections)
                    Row(Modifier.fillMaxWidth().heightIn(min = KoshaTheme.dims.settingsRow).padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        KText(label, Modifier.weight(1f))
                        Switch(draft.corrections, { draft = draft.copy(corrections = it) }, label)
                    }
                }
                Button(
                    count?.let { pluralStringResource(R.plurals.tx_show_count, it, it.toString()) } ?: stringResource(R.string.tx_show),
                    { onApply(draft) }, Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** An amount as the keypad types it: 2000 minor units is "20" (decimals only when there are cents). */
internal fun plainAmount(minor: Long): String {
    val v = java.math.BigDecimal.valueOf(abs(minor), 2).stripTrailingZeros()
    return (if (v.scale() < 0) v.setScale(0) else v).toPlainString()
}

@Composable
private fun Labeled(label: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        KText(label, style = KoshaTheme.type.small, color = KoshaTheme.colors.ink3)
        content()
    }
}

@Composable
private fun PickField(icon: String, value: String, set: Boolean, onClick: () -> Unit) {
    val c = KoshaTheme.colors
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(shape).background(c.surface)
            .border(1.dp, c.line, shape).clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        PhIcon(icon, size = 18.dp, tint = c.ink3)
        KText(value, Modifier.weight(1f), color = if (set) c.ink else c.ink3, maxLines = 1)
        PhIcon("caret-right", size = 16.dp, tint = c.ink3)
    }
}

@Composable
private fun AmountField(label: String, value: Long?, currency: String, any: String, modifier: Modifier, onClick: () -> Unit) {
    val c = KoshaTheme.colors
    Row(
        modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(14.dp)).background(c.soft).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        KText(label, style = KoshaTheme.type.small, color = c.ink3)
        KText(value?.let { plainAmount(it) } ?: any, Modifier.weight(1f), style = KoshaTheme.type.num, color = if (value != null) c.ink else c.ink3, align = TextAlign.End, maxLines = 1)
    }
}
