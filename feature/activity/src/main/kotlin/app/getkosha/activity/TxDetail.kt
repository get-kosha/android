@file:UseSerializers(RoundedLong::class)

package app.getkosha.activity

import kotlinx.serialization.UseSerializers
import app.getkosha.model.RoundedLong

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.getkosha.design.AccountDot
import app.getkosha.design.AccountList
import app.getkosha.design.AccountOption
import app.getkosha.design.AmountDisplay
import app.getkosha.design.AmountKeypad
import app.getkosha.design.Button
import app.getkosha.design.ButtonSize
import app.getkosha.design.ButtonStyle
import app.getkosha.design.Card
import app.getkosha.design.CategoryGrid
import app.getkosha.design.CategoryTile
import app.getkosha.design.Chip
import app.getkosha.design.Field
import app.getkosha.design.Hairline
import app.getkosha.design.IconButton
import app.getkosha.design.IconTile
import app.getkosha.design.KText
import app.getkosha.design.KoshaTheme
import app.getkosha.design.ListOption
import app.getkosha.design.ListPicker
import app.getkosha.design.MonthGrid
import app.getkosha.design.PhIcon
import app.getkosha.design.R
import app.getkosha.design.Segmented
import app.getkosha.design.Sheet
import app.getkosha.design.TimeWheel
import app.getkosha.design.Toast
import app.getkosha.design.categoryIconName
import app.getkosha.model.Categories
import app.getkosha.model.Dates
import app.getkosha.model.EntryKind
import app.getkosha.model.Keypad
import app.getkosha.model.KoshaJson
import app.getkosha.model.Money
import app.getkosha.model.Sign
import app.getkosha.model.Transaction
import app.getkosha.model.Transactions
import app.getkosha.model.convert
import app.getkosha.model.parseTags
import app.getkosha.ui.ActivitySearch
import app.getkosha.ui.AmountText
import app.getkosha.ui.LoadingNote
import app.getkosha.ui.LocalNav
import app.getkosha.ui.LocalToasts
import app.getkosha.ui.MoneyText
import app.getkosha.ui.Route
import app.getkosha.ui.canWrite
import app.getkosha.ui.meta
import app.getkosha.ui.openActivity
import app.getkosha.ui.rememberKosha
import app.getkosha.ui.shortDay
import app.getkosha.ui.write
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import kotlin.math.abs

@Serializable
internal data class Patch(val transactions: List<JsonObject>)

/** GET /api/app/payee-history: how often this payee comes up and this year's payments (finance.server.ts payeeHistory). */
@Serializable
private data class PayeeHistory(val count: Int = 0, val thisYear: List<Row> = emptyList()) {
    @Serializable
    data class Row(val amount: Long, val currency: String)
}

/** Reads one transaction for a detail: the row saved on this phone first, then the server's. */
@Composable
internal fun rememberTransaction(id: String): app.getkosha.data.Loaded<Transaction>? {
    val kosha = rememberKosha()
    val version by kosha.version.collectAsStateWithLifecycle()
    return remember(id, version) {
        kosha.load("tx/$id") { findTransaction(id) }
    }.collectAsStateWithLifecycle(null).value
}

/**
 * A transaction, saved as you edit (update_transactions): amount on the keypad, payee, category, account, date and
 * time, type, tags, note and event; its attachments; "At this payee"; Make this recurring…, Duplicate and Delete.
 * A reconciled entry asks before its amount, account or day changes. Read-only for the demo.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TxDetailScreen(id: String) {
    val kosha = rememberKosha()
    val nav = LocalNav.current
    val toasts = LocalToasts.current
    val m = meta() ?: return
    val c = KoshaTheme.colors
    val locale = KoshaTheme.locale
    val scope = rememberCoroutineScope()
    val writable = canWrite()
    val loaded = rememberTransaction(id)
    var edited by remember(id) { mutableStateOf<Transaction?>(null) }
    val t = edited ?: loaded?.value
    var sheet by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf<JsonObject?>(null) }
    val shown = kosha.currency.collectAsStateWithLifecycle().value ?: m.primary
    val offline = stringResource(R.string.common_offline_title)
    val readOnly = stringResource(R.string.common_demo_read_only)

    Column(Modifier.fillMaxSize().background(c.bg).statusBarsPadding()) {
        val kind = when {
            t == null -> ""
            t.refundOf != null -> stringResource(R.string.tx_kind_refund)
            t.amount > 0 -> stringResource(R.string.tx_kind_income)
            Categories.isNeutral(t.categoryId) -> stringResource(R.string.tx_kind_correction)
            else -> stringResource(R.string.tx_kind_expense)
        }
        Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton("caret-left", stringResource(R.string.common_back), { nav.popBackStack() })
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                if (status == "saved") PhIcon("check", size = 14.dp, tint = c.pos)
                KText(
                    when (status) {
                        "saving" -> stringResource(R.string.tx_header_saving, kind)
                        "saved" -> stringResource(R.string.tx_header_saved, kind)
                        else -> kind
                    },
                    style = KoshaTheme.type.small, color = c.ink3, maxLines = 1,
                )
            }
            Spacer(Modifier.width(48.dp))
        }
        if (t == null) {
            Box(Modifier.padding(16.dp)) {
                if (loaded?.refreshing != false) LoadingNote() else KText(loaded.error ?: stringResource(R.string.common_offline_body), style = KoshaTheme.type.small, color = c.ink3)
            }
            return@Column
        }
        // Saves one change. A reconciled entry asks first when the change touches its amount, account or day.
        val save = { change: JsonObject, touchesReconciled: Boolean ->
            when {
                !writable -> toasts.show(Toast(readOnly))
                t.reconciled && touchesReconciled -> {
                    pending = change
                    sheet = "reconciled"
                }
                else -> {
                    pending = null
                    status = "saving"
                    scope.launch {
                        val body = Patch(listOf(buildJsonObject { put("id", t.id); change.forEach { (k, v) -> put(k, v) } }))
                        val r = kosha.write(toasts, offline) { kosha.api.patch<Patch, Transactions>("/api/v1/transactions", body) }
                        val fresh = r?.transactions?.firstOrNull()
                        if (fresh != null) {
                            edited = fresh
                            kosha.cache.write("tx/${fresh.id}", KoshaJson.encodeToString(Transaction.serializer(), fresh))
                            Seen.remember(listOf(fresh))
                        }
                        status = if (fresh != null) "saved" else null
                    }
                }
            }
        }
        val edit = { name: String -> if (writable) sheet = name else toasts.show(Toast(readOnly)) }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 40.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 10.dp).clickable(role = Role.Button, onClickLabel = stringResource(R.string.tx_amount)) { edit("amount") },
                contentAlignment = Alignment.Center,
            ) {
                MoneyText(
                    t.amount, t.currency, sign = if (Categories.isNeutral(t.categoryId)) Sign.None else Sign.Always,
                    style = KoshaTheme.type.num.copy(fontSize = 40.sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.04).em),
                    color = if (t.amount > 0 && !Categories.isNeutral(t.categoryId)) c.pos else c.ink,
                )
            }
            val category = m.categories.firstOrNull { it.id == t.categoryId }
            // A refund keeps its spending entry's category, which changes with that entry.
            val refund = t.refundOf != null
            Card(Modifier.fillMaxWidth(), tight = true) {
                FieldRow(stringResource(R.string.tx_payee), { edit("payee") }) { KText(t.payee.ifEmpty { "—" }, maxLines = 2) }
                Hairline()
                FieldRow(stringResource(R.string.tx_category), if (refund) null else { { edit("category") } }) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PhIcon(categoryIconName(category?.icon), size = 18.dp)
                        KText(category?.name ?: t.categoryName, maxLines = 1)
                    }
                }
                Hairline()
                FieldRow(stringResource(R.string.tx_account), { edit("account") }) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AccountDot(c.account(m.wallets.indexOfFirst { it.id == t.accountId }))
                        KText(t.accountName, maxLines = 1)
                    }
                }
                Hairline()
                FieldRow(stringResource(R.string.tx_date), { edit("date") }) {
                    AmountText(AnnotatedString(shortDay(LocalDate.parse(t.day), locale, weekday = true, year = true) + " · " + t.time), style = KoshaTheme.type.body)
                }
                if (!refund) Hairline()
                if (!refund) Row(Modifier.fillMaxWidth().heightIn(min = KoshaTheme.dims.settingsRow).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    KText(stringResource(R.string.tx_type), Modifier.width(76.dp), style = KoshaTheme.type.small, color = c.ink3)
                    Segmented(
                        listOf(
                            "expense" to stringResource(R.string.tx_kind_expense),
                            "income" to stringResource(R.string.tx_kind_income),
                            "transfer" to stringResource(R.string.tx_kind_transfer),
                        ),
                        if (t.amount > 0) "income" else "expense",
                        { kind ->
                            when {
                                kind == "transfer" -> if (writable) nav.navigate(Route.Add(kind = "transfer", duplicateOf = t.id)) else toasts.show(Toast(readOnly))
                                (kind == "income") != (t.amount > 0) -> save(buildJsonObject { put("amount", -t.amount) }, true)
                            }
                        },
                        Modifier.weight(1f),
                        small = true,
                    )
                }
            }
            if (refund) {
                val name = category?.name ?: t.categoryName
                KText(stringResource(R.string.tx_refund_for, t.payee.ifEmpty { name }, name), style = KoshaTheme.type.small, color = c.ink3)
            }
            Refunds(t, writable) { edited = null }
            Card(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    KText(stringResource(R.string.tx_tags), style = KoshaTheme.type.small, color = c.ink3)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        t.tags.forEach { tag ->
                            val label = stringResource(R.string.tx_tag_link, tag)
                            Chip("#$tag", Modifier.semantics { contentDescription = label }, small = true, onClick = { nav.navigate(Route.TagView(tag = tag)) })
                        }
                        Chip(stringResource(R.string.tx_add_tags), small = true, soft = true, onClick = { edit("tags") }, leading = { PhIcon("plus", size = 14.dp) })
                    }
                    Hairline()
                    Column(Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable(role = Role.Button) { edit("note") }, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        KText(stringResource(R.string.tx_note), style = KoshaTheme.type.small, color = c.ink3)
                        KText(t.note.ifEmpty { stringResource(R.string.tx_note_placeholder) }, color = if (t.note.isEmpty()) c.ink3 else c.ink)
                    }
                    Hairline()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        KText(stringResource(R.string.tx_event), Modifier.weight(1f), style = KoshaTheme.type.small, color = c.ink3)
                        val event = m.events.firstOrNull { it.id == t.eventId }
                        Chip(
                            event?.name ?: stringResource(R.string.tx_add_to_event), small = true, soft = true,
                            onClick = { if (event != null && !writable) nav.navigate(Route.TagView(eventId = event.id)) else edit("event") },
                            leading = { PhIcon("calendar-star", size = 14.dp) },
                        )
                        if (event != null) {
                            IconButton("arrow-right", stringResource(R.string.app_act_event_all_dates), { nav.navigate(Route.TagView(eventId = event.id)) })
                        }
                    }
                }
            }
            if (m.attachments) Attachments(t, writable) { edit("attach") }
            if (t.reconciled) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PhIcon("check-circle", size = 18.dp, tint = c.pos)
                    KText(stringResource(R.string.tx_reconciled_note), style = KoshaTheme.type.small, color = c.ink2)
                }
            }
            if (t.payee.isNotBlank()) AtPayee(t.payee, shown)
            if (writable && !refund) {
                LinkCard("arrows-clockwise", stringResource(R.string.add_make_recurring)) { nav.navigate(Route.RecurringForm(fromTransaction = t.id)) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(stringResource(R.string.tx_duplicate), { nav.navigate(Route.Add(duplicateOf = t.id)) }, Modifier.weight(1f), style = ButtonStyle.Secondary, size = ButtonSize.Medium, icon = "copy")
                    Button(stringResource(R.string.common_delete), { sheet = "delete" }, Modifier.weight(1f), style = ButtonStyle.Danger, size = ButtonSize.Medium, icon = "trash")
                }
            }
        }
        val close = { sheet = null }
        when (sheet) {
            "amount" -> AmountSheet(t.amount, t.currency, if (t.amount > 0) EntryKind.Income else EntryKind.Expense, close) { minor ->
                close()
                save(buildJsonObject { put("amount", if (t.amount > 0) minor else -minor) }, true)
            }
            "payee" -> TextSheet(stringResource(R.string.tx_payee), t.payee, singleLine = true, close) { close(); save(buildJsonObject { put("payee", it.trim()) }, false) }
            "note" -> TextSheet(stringResource(R.string.tx_note), t.note, singleLine = false, close) { close(); save(buildJsonObject { put("note", it.trim()) }, false) }
            "category" -> Sheet(stringResource(R.string.tx_pick_category), close) {
                val income = t.amount > 0
                CategoryGrid(
                    m.categories.filter { !it.isArchived && !Categories.isSystem(it.id) && it.isIncome == income }
                        .sortedByDescending { it.uses }
                        .map { CategoryTile(it.id, categoryIconName(it.icon), it.name) },
                    picked = t.categoryId,
                    onPick = { close(); save(buildJsonObject { put("categoryId", it.id) }, false) },
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                )
            }
            "account" -> Sheet(stringResource(R.string.tx_account), close) {
                AccountList(
                    m.wallets.filter { !it.isArchived }.map {
                        AccountOption(it.id, it.name, it.currency.uppercase(), Money.format(it.balance, it.currency, locale), m.wallets.indexOf(it))
                    },
                    t.accountId,
                    { close(); save(buildJsonObject { put("accountId", it.id) }, true) },
                    Modifier.verticalScroll(rememberScrollState()),
                )
            }
            "date" -> DateSheet(t.day, t.time, kosha.tz, close) { day, time ->
                close()
                save(buildJsonObject { if (day != t.day) put("day", day); put("time", time) }, day != t.day)
            }
            "tags" -> TagsSheet(t.tags, m.tags, close) { close(); save(buildJsonObject { put("tags", buildJsonArray { it.forEach { tag -> add(JsonPrimitive(tag)) } }) }, false) }
            "event" -> Sheet(stringResource(R.string.tx_event), close) {
                ListPicker(
                    listOf(ListOption("", stringResource(R.string.tx_no_event))) + m.events.map { ListOption(it.id, it.name) },
                    t.eventId ?: "",
                    { close(); save(buildJsonObject { put("eventId", if (it.value.isEmpty()) JsonNull else JsonPrimitive(it.value)) }, false) },
                )
            }
            "attach" -> AttachSheet(t.id, close)
            "delete" -> DeleteConfirm(t) { deleted -> close(); if (deleted) nav.popBackStack() }
            "reconciled" -> Sheet(stringResource(R.string.tx_reconciled_note), { pending = null; close() }) {
                Button(stringResource(R.string.common_continue), {
                    val change = pending
                    close()
                    change?.let { save(it, false) }
                }, Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                Button(stringResource(R.string.common_cancel), { pending = null; close() }, Modifier.fillMaxWidth(), style = ButtonStyle.Secondary)
            }
        }
    }
}

/** A label on the left, the value, and a caret: one editable field of a detail. */
@Composable
internal fun FieldRow(label: String, onClick: (() -> Unit)?, end: (@Composable () -> Unit)? = null, value: @Composable () -> Unit) {
    val c = KoshaTheme.colors
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(role = Role.Button, onClickLabel = label, onClick = onClick) else Modifier)
            .heightIn(min = KoshaTheme.dims.settingsRow).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        KText(label, Modifier.width(76.dp), style = KoshaTheme.type.small, color = c.ink3)
        Box(Modifier.weight(1f)) { value() }
        if (end != null) end() else if (onClick != null) PhIcon("caret-right", size = 16.dp, tint = c.ink3)
    }
}

@Composable
internal fun LinkCard(icon: String, label: String, sub: String? = null, onClick: () -> Unit) {
    val c = KoshaTheme.colors
    Card(Modifier.fillMaxWidth(), tight = true) {
        Row(
            Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).heightIn(min = KoshaTheme.dims.settingsRow).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (sub != null) IconTile(icon) else PhIcon(icon)
            Column(Modifier.weight(1f)) {
                KText(label, style = if (sub != null) KoshaTheme.type.head else KoshaTheme.type.body)
                if (sub != null) AmountText(AnnotatedString(sub), style = KoshaTheme.type.xs, color = c.ink3)
            }
            PhIcon("caret-right", size = 16.dp, tint = c.ink3)
        }
    }
}

/** "At this payee: 14× · usually €27.60", opening Activity searched by the payee. */
@Composable
private fun AtPayee(payee: String, shown: String) {
    val kosha = rememberKosha()
    val nav = LocalNav.current
    val m = meta() ?: return
    val history = remember(payee) { kosha.load("payee/$payee") { get<PayeeHistory>("/api/app/payee-history", mapOf("name" to payee)) } }
        .collectAsStateWithLifecycle(null).value?.value ?: return
    if (history.count < 2) return
    // The usual amount is this year's average, as the web's panel shows it.
    val usual = if (history.thisYear.isEmpty()) null else abs(history.thisYear.sumOf { convert(it.amount, it.currency, shown, m.rates) }) / history.thisYear.size
    LinkCard(
        "storefront", stringResource(R.string.tx_at_payee),
        sub = if (usual != null) stringResource(R.string.shell_search_payee_usually, history.count.toString(), Money.format(usual, shown, KoshaTheme.locale))
        else pluralStringResource(R.plurals.tx_count, history.count, history.count.toString()),
    ) { nav.openActivity(ActivitySearch(query = payee.take(40), month = null)) }
}

/** The keypad in a sheet, for an amount that already exists. Saves positive minor units. */
@Composable
internal fun AmountSheet(minor: Long, currency: String, kind: EntryKind, onDismiss: () -> Unit, title: String = stringResource(R.string.tx_amount), onSave: (Long) -> Unit) {
    var input by remember { mutableStateOf(plainAmount(minor)) }
    val value = Keypad.minorUnits(input, Money.decimals(currency))
    Sheet(title, onDismiss) {
        AmountDisplay(input, currency, kind)
        AmountKeypad(input, { input = it }, Modifier.padding(vertical = 8.dp), decimals = Money.decimals(currency))
        Button(stringResource(R.string.common_save), { value?.let(onSave) }, Modifier.fillMaxWidth(), enabled = value != null)
    }
}

/** Words in a sheet: payee, note or provider. */
@Composable
internal fun TextSheet(title: String, start: String, singleLine: Boolean, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(start) }
    Sheet(title, onDismiss) {
        Field(text, { text = it.take(if (singleLine) 200 else 2000) }, Modifier.fillMaxWidth(), singleLine = singleLine)
        Spacer(Modifier.height(12.dp))
        Button(stringResource(R.string.common_save), { onSave(text) }, Modifier.fillMaxWidth())
    }
}

/** The day on a month grid, and the time on the wheel. */
@Composable
internal fun DateSheet(day: String, time: String, tz: String, onDismiss: () -> Unit, withTime: Boolean = true, onSave: (String, String) -> Unit) {
    val today = Dates.today(tz)
    var picked by remember { mutableStateOf(LocalDate.parse(day)) }
    var month by remember { mutableStateOf(YearMonth.from(picked)) }
    var clock by remember { mutableStateOf(runCatching { LocalTime.parse(time) }.getOrDefault(LocalTime.NOON)) }
    var wheel by remember { mutableStateOf(false) }
    val locale = KoshaTheme.locale
    Sheet(stringResource(R.string.tx_pick_date), onDismiss) {
        if (wheel) {
            TimeWheel(clock.hour, clock.minute, { h, mm -> clock = LocalTime.of(h, mm) }, { clock = Dates.now(tz) },
                twelveHour = !android.text.format.DateFormat.is24HourFormat(androidx.compose.ui.platform.LocalContext.current))
        } else {
            MonthGrid(month, picked, today, { picked = it }, { month = it })
            if (withTime) {
                FieldRow(stringResource(R.string.tx_time), { wheel = true }) { KText(Dates.text(clock), style = KoshaTheme.type.num) }
            }
        }
        Spacer(Modifier.height(12.dp))
        Button(stringResource(R.string.common_done), { if (wheel) wheel = false else onSave(Dates.text(picked), Dates.text(clock)) }, Modifier.fillMaxWidth())
    }
}

/** Tags: type them (a space separates them) or pick ones used before. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagsSheet(start: List<String>, used: List<String>, onDismiss: () -> Unit, onSave: (List<String>) -> Unit) {
    var tags by remember { mutableStateOf(start) }
    var typed by remember { mutableStateOf("") }
    Sheet(stringResource(R.string.tx_tags), onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                tags.forEach { tag ->
                    val label = stringResource(R.string.common_remove) + " #$tag"
                    Chip("#$tag", Modifier.semantics { contentDescription = label }, small = true, on = true, onClick = { tags = tags - tag }, leading = { PhIcon("x", size = 14.dp) })
                }
            }
            Field(
                typed,
                {
                    // A space or comma finishes a tag.
                    if (it.endsWith(" ") || it.endsWith(",")) {
                        tags = (tags + parseTags(it)).distinct()
                        typed = ""
                    } else typed = it
                },
                Modifier.fillMaxWidth(), placeholder = stringResource(R.string.tx_tags_placeholder), icon = "hash",
            )
            val suggestions = used.filter { it !in tags && it.startsWith(typed.trim().removePrefix("#").lowercase()) }.take(12)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                suggestions.forEach { tag -> Chip("#$tag", small = true, soft = true, onClick = { tags = tags + tag; typed = "" }) }
            }
            Button(stringResource(R.string.common_save), { onSave((tags + parseTags(typed)).distinct()) }, Modifier.fillMaxWidth())
        }
    }
}
