package app.getkosha.recurring

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.getkosha.design.AccountDot
import app.getkosha.design.Button
import app.getkosha.design.ButtonSize
import app.getkosha.design.ButtonStyle
import app.getkosha.design.Card
import app.getkosha.design.Hairline
import app.getkosha.design.IconTile
import app.getkosha.design.KText
import app.getkosha.design.KoshaTheme
import app.getkosha.design.PhIcon
import app.getkosha.design.Pill
import app.getkosha.design.R
import app.getkosha.design.categoryIconName
import app.getkosha.model.Sign
import app.getkosha.ui.LoadingNote
import app.getkosha.ui.LocalNav
import app.getkosha.ui.MoneyText
import app.getkosha.ui.Page
import app.getkosha.ui.Route
import app.getkosha.ui.Skeleton
import app.getkosha.ui.meta
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.LocalDate
import kotlin.math.abs

/** Payments per month for each unit (core.ts PER_MONTH). */
private fun perMonthFactor(unit: String) = when (unit) {
    "day" -> 365.25 / 12
    "week" -> 52.18 / 12
    "year" -> 1.0 / 12
    else -> 1.0
}

/** A paid payment or a skipped one, for the Payments list. */
private data class PaymentLine(val due: String, val day: String, val amount: Long?, val paid: Paid?, val skipped: Instance?)

private sealed interface Open {
    data class Payment(val instance: Instance, val pay: Boolean) : Open
    data object End : Open
    data object Delete : Open
}

/**
 * One schedule: its next payment (pay, enter the amount, change or skip it), what was paid so far and per month,
 * the payments with their bills, documents by month, its details, and ending or deleting it.
 */
@Composable
fun RecurringDetailScreen(id: String) {
    val nav = LocalNav.current
    val m = meta()
    val c = KoshaTheme.colors
    val locale = KoshaTheme.locale
    val today = today()
    val writer = rememberWriter()
    val scope = rememberCoroutineScope()
    val loaded = rememberLoad<SubscriptionsData>("subscriptions") { get("/api/app/subscriptions") }
    val s = loaded.value?.subscriptions?.find { it.id == id }
    val docsOn = m?.attachments == true && s != null && !s.isTransfer
    val files = if (docsOn) rememberLoad<List<ScheduleFile>>("recurring/$id/files") { get("/api/app/recurring/$id/attachments") }.value.orEmpty() else emptyList()
    var open by remember { mutableStateOf<Open?>(null) }
    var docs by remember { mutableStateOf(false) }
    var all by remember { mutableStateOf(false) }

    if (s == null) {
        Page(stringResource(R.string.subs_title), onBack = { nav.popBackStack() }) {
            if (loaded.refreshing) Skeleton(Modifier.fillMaxWidth(), 160.dp) else LoadingNote()
        }
        return
    }
    if (docs) {
        DocumentsPage(s, files, onBack = { docs = false })
        return
    }

    val wallets = m?.wallets.orEmpty()
    val account = wallets.indexOfFirst { it.id == s.walletId }
    val to = wallets.indexOfFirst { it.id == s.toWalletId }
    val cat = m?.categories?.find { it.id == s.categoryId }
    val group = s.group()
    val kindLabel = stringResource(
        when (group) {
            Group.Bills -> R.string.subs_kind_bill
            Group.Subscriptions -> R.string.subs_kind_subscription
            Group.Income -> R.string.subs_kind_income
            Group.Transfers -> R.string.subs_kind_transfer
        },
    )
    val since = s.history.lastOrNull()?.day ?: s.startDay
    val every = everyText(s.every, s.unit)
    val sign = if (s.isTransfer) Sign.None else Sign.Always
    val markedPaid = stringResource(R.string.subs_marked_paid)
    val skippedText = stringResource(R.string.subs_skipped)
    val resumed = stringResource(R.string.subs_resumed_toast, s.name)

    val act = { instance: Instance, status: String, done: String ->
        scope.launch {
            val ok = writer.run {
                send("PATCH", "/api/v1/recurring/${s.id}/payments/${instance.dueDay}", buildJsonObject { put("status", status) })
            }
            if (ok != null) writer.toast(done)
        }
    }

    Page(
        s.title(m),
        onBack = { nav.popBackStack() },
        subtitle = stringResource(R.string.subs_detail_meta, kindLabel, every, monthText(java.time.YearMonth.from(LocalDate.parse(since)), "MMMyyyy")),
        actions = {
            Button(stringResource(R.string.common_edit), { nav.navigate(Route.RecurringForm(id = s.id)) }, style = ButtonStyle.Secondary, size = ButtonSize.Small)
        },
    ) {
        // Next payment
        Card(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val next = s.next
                when {
                    s.ended -> {
                        KText(stringResource(R.string.subs_ended_on, dayText(s.endDay ?: s.startDay, "dMMMyyyy")), style = KoshaTheme.type.head)
                        KText(stringResource(R.string.subs_ended_hint), style = KoshaTheme.type.small, color = c.ink3)
                    }
                    next == null -> KText(stringResource(R.string.subs_no_more_scheduled), style = KoshaTheme.type.small, color = c.ink3)
                    else -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            KText(stringResource(R.string.subs_next_payment), Modifier.weight(1f), style = KoshaTheme.type.small, color = c.ink3)
                            if (s.isVariable) Pill(stringResource(R.string.subs_varies))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconTile(if (s.isTransfer) "arrows-left-right" else categoryIconName(cat?.icon))
                            Column(Modifier.weight(1f)) {
                                KText(dayText(next.day, "EEEdMMM"), style = KoshaTheme.type.head, color = if (next.status == "overdue") c.warn else c.ink)
                                KText(
                                    listOfNotNull(
                                        when {
                                            s.isVariable -> stringResource(R.string.app_rec_asks_that_day)
                                            s.auto -> stringResource(R.string.subs_auto_recorded)
                                            else -> stringResource(R.string.subs_mark_when_out)
                                        },
                                        if (next.edited) stringResource(R.string.subs_changed_once) else null,
                                        s.endDay?.let { stringResource(R.string.subs_ends_on, dayText(it, "dMMMyyyy")) },
                                    ).joinToString(" · "),
                                    style = KoshaTheme.type.xs,
                                    color = c.ink3,
                                )
                            }
                            if (!s.isVariable) MoneyText(if (s.isTransfer) abs(next.amount) else next.amount, s.currency, sign = sign)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (s.isVariable) {
                                Button(stringResource(R.string.subs_enter_amount_button), { open = Open.Payment(next, pay = true) }, Modifier.weight(1f), size = ButtonSize.Small)
                            } else {
                                Button(
                                    stringResource(if (s.isTransfer) R.string.subs_mark_done_now else R.string.subs_mark_paid_now),
                                    { act(next, "paid", markedPaid) },
                                    Modifier.weight(1f),
                                    size = ButtonSize.Small,
                                )
                            }
                            Button(stringResource(R.string.subs_skip_this), { act(next, "skipped", skippedText) }, Modifier.weight(1f), style = ButtonStyle.Secondary, size = ButtonSize.Small)
                        }
                        if (!s.isVariable) {
                            Button(stringResource(R.string.subs_change_amount), { open = Open.Payment(next, pay = false) }, Modifier.fillMaxWidth(), style = ButtonStyle.Soft, size = ButtonSize.Small)
                        }
                    }
                }
            }
        }

        // Paid so far, per month
        val paidTotal = s.history.sumOf { it.amount }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Stat(
                stringResource(if (s.isTransfer) R.string.subs_moved_so_far else R.string.subs_paid_so_far),
                abs(paidTotal),
                s.currency,
                pluralStringResource(R.plurals.app_rec_payments_count, s.history.size, s.history.size),
                Modifier.weight(1f),
            )
            val recent = s.history.take(12)
            val (perMonth, hint) = if (s.isVariable && recent.isNotEmpty()) {
                Math.round(abs(recent.sumOf { it.amount }.toDouble() / recent.size) * perMonthFactor(s.unit) / s.every) to
                    pluralStringResource(R.plurals.app_rec_average_last, recent.size, recent.size)
            } else {
                abs(s.perMonth) to money(abs(s.amount), s.currency) + " " + every
            }
            Stat(stringResource(R.string.subs_column_per_month), perMonth, s.currency, hint, Modifier.weight(1f))
        }

        // Payments
        val lines = (
            s.history.map { PaymentLine(it.dueDay ?: it.day, it.day, it.amount, it, null) } +
                s.instances.filter { it.status == "skipped" }.map { PaymentLine(it.dueDay, it.day, null, null, it) }
            ).sortedByDescending { it.due }
        Card(Modifier.fillMaxWidth(), tight = true) {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp)) {
                KText(stringResource(R.string.subs_payments), Modifier.weight(1f), style = KoshaTheme.type.head)
            }
            if (lines.isEmpty()) {
                KText(stringResource(R.string.subs_no_payments), Modifier.padding(16.dp), style = KoshaTheme.type.small, color = c.ink3)
            }
            (if (all) lines else lines.take(5)).forEach { line ->
                Hairline()
                PaymentRow(s, line, files.filter { f -> line.paid != null && f.transactionId == line.paid.id }) {
                    when {
                        line.skipped != null -> open = Open.Payment(line.skipped, pay = false)
                        line.paid?.transferId != null -> nav.navigate(Route.TransferDetail(line.paid.transferId))
                        line.paid != null -> nav.navigate(Route.TxDetail(line.paid.id))
                    }
                }
            }
            if (lines.size > 5 && !all) {
                Hairline()
                LinkRow("list-bullets", stringResource(R.string.subs_all_payments, lines.size), null) { all = true }
            }
            if (docsOn && files.isNotEmpty()) {
                Hairline()
                LinkRow("file-zip", stringResource(R.string.app_rec_documents_by_month), files.size.toString()) { docs = true }
            }
        }

        // Details
        Card(Modifier.fillMaxWidth(), tight = true) {
            DetailRow(stringResource(if (s.isTransfer) R.string.subs_from else R.string.subs_account)) {
                AccountDot(c.account(account))
                KText(wallets.getOrNull(account)?.name ?: "")
            }
            if (s.isTransfer) {
                Hairline()
                DetailRow(stringResource(R.string.subs_to)) {
                    AccountDot(c.account(to))
                    KText(wallets.getOrNull(to)?.name ?: "")
                }
            } else {
                Hairline()
                DetailRow(stringResource(R.string.subs_category)) {
                    PhIcon(categoryIconName(cat?.icon), size = 18.dp)
                    KText(cat?.name ?: "")
                }
            }
            Hairline()
            DetailRow(stringResource(R.string.subs_amount)) {
                if (s.isVariable) KText(stringResource(R.string.subs_variable))
                else MoneyText(if (s.isTransfer) abs(s.amount) else s.amount, s.currency, sign = sign)
            }
            Hairline()
            DetailRow(stringResource(R.string.subs_ends)) {
                KText(s.endDay?.let { dayText(it, "dMMMyyyy") } ?: stringResource(R.string.subs_never))
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (s.ended) {
                Button(stringResource(R.string.subs_resume), {
                    scope.launch {
                        // Resuming keeps the old schedule from today on and doesn't back-fill the gap (subscriptions.tsx setEnd).
                        val start = LocalDate.parse(s.startDay)
                        val nextDay = if (start >= today) start else Schedule.next(start, s.every, s.unit, null, today) ?: today
                        val ok = writer.run {
                            send("PATCH", "/api/v1/recurring/${s.id}", buildJsonObject {
                                put("endDay", JsonNull)
                                put("startDay", nextDay.toString())
                            })
                        }
                        if (ok != null) writer.toast(resumed)
                    }
                }, Modifier.weight(1f), style = ButtonStyle.Secondary, size = ButtonSize.Medium)
            } else {
                Button(stringResource(endThis(group)), { open = Open.End }, Modifier.weight(1f), style = ButtonStyle.Secondary, size = ButtonSize.Medium)
            }
            Button(stringResource(R.string.common_delete), { open = Open.Delete }, Modifier.weight(1f), style = ButtonStyle.Danger, size = ButtonSize.Medium)
        }
        if (!s.ended) {
            KText(stringResource(R.string.subs_end_description), Modifier.fillMaxWidth(), style = KoshaTheme.type.xs, color = c.ink3, align = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }

    val endedToast = stringResource(R.string.subs_ended_toast, s.name)
    val deletedToast = stringResource(R.string.subs_deleted)
    when (val o = open) {
        is Open.Payment -> PaymentSheet(s, o.instance, o.pay, onDismiss = { open = null })
        Open.End -> ConfirmSheet(
            stringResource(R.string.subs_end_title, s.name),
            stringResource(R.string.subs_end_description),
            stringResource(endShort(group)),
            onConfirm = {
                scope.launch {
                    val ok = writer.run { send("PATCH", "/api/v1/recurring/${s.id}", buildJsonObject { put("endDay", today.toString()) }) }
                    open = null
                    if (ok != null) writer.toast(endedToast)
                }
            },
            onDismiss = { open = null },
        )
        Open.Delete -> ConfirmSheet(
            stringResource(R.string.subs_delete_title, s.name),
            stringResource(R.string.subs_delete_description),
            stringResource(R.string.common_delete),
            onConfirm = {
                scope.launch {
                    val ok = writer.run { send("DELETE", "/api/v1/recurring/${s.id}") }
                    open = null
                    if (ok != null) {
                        writer.toast(deletedToast)
                        nav.popBackStack()
                    }
                }
            },
            onDismiss = { open = null },
        )
        null -> Unit
    }
}

private fun endThis(g: Group) = when (g) {
    Group.Bills -> R.string.subs_end_this_bill
    Group.Subscriptions -> R.string.subs_end_this_subscription
    Group.Income -> R.string.subs_end_this_income
    Group.Transfers -> R.string.subs_end_this_transfer
}

private fun endShort(g: Group) = when (g) {
    Group.Bills -> R.string.subs_end_bill
    Group.Subscriptions -> R.string.subs_end_subscription
    Group.Income -> R.string.subs_end_income
    Group.Transfers -> R.string.subs_end_transfer
}

@Composable
private fun Stat(label: String, minor: Long, currency: String, hint: String, modifier: Modifier) {
    Card(modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            KText(label, style = KoshaTheme.type.small, color = KoshaTheme.colors.ink3)
            MoneyText(minor, currency, sign = Sign.None, style = KoshaTheme.type.num.copy(fontSize = androidx.compose.ui.unit.TextUnit(20f, androidx.compose.ui.unit.TextUnitType.Sp)))
            KText(hint, style = KoshaTheme.type.xs, color = KoshaTheme.colors.ink3)
        }
    }
}

@Composable
private fun LinkRow(icon: String, label: String, count: String?, onClick: () -> Unit) {
    val c = KoshaTheme.colors
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).heightIn(min = KoshaTheme.dims.row).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PhIcon(icon, size = 16.dp)
        KText(label, Modifier.weight(1f), style = KoshaTheme.type.small, color = c.ink2)
        if (count != null) KText(count, style = KoshaTheme.type.num, color = c.ink3)
        PhIcon("caret-right", size = 16.dp, tint = c.ink3)
    }
}

@Composable
private fun PaymentRow(s: Sub, line: PaymentLine, files: List<ScheduleFile>, onClick: () -> Unit) {
    val c = KoshaTheme.colors
    val m = meta()
    val paid = line.paid
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).heightIn(min = KoshaTheme.dims.row).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        KText(dayText(line.day), Modifier.width(56.dp), style = KoshaTheme.type.num.copy(fontSize = KoshaTheme.type.small.fontSize), color = c.ink3)
        Column(Modifier.weight(1f)) {
            if (paid == null) {
                KText(stringResource(R.string.subs_skipped), style = KoshaTheme.type.small, color = c.ink3)
            } else {
                KText(stringResource(if (s.isTransfer) R.string.subs_moved else R.string.subs_paid), style = KoshaTheme.type.small)
                val changed = !s.isVariable && abs(paid.amount) != abs(s.amount)
                val elsewhere = paid.walletId.isNotEmpty() && paid.walletId != s.walletId
                when {
                    files.isNotEmpty() -> Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        PhIcon("paperclip", size = 14.dp, tint = c.ink3)
                        KText(files.joinToString(", ") { it.name }, style = KoshaTheme.type.xs, color = c.ink3, maxLines = 1)
                    }
                    elsewhere -> KText(m?.wallets?.find { it.id == paid.walletId }?.name ?: "", style = KoshaTheme.type.xs, color = c.ink3)
                    changed -> KText(stringResource(R.string.subs_changed_once), style = KoshaTheme.type.xs, color = c.ink3)
                }
            }
        }
        if (paid == null) KText("—", style = KoshaTheme.type.num, color = c.ink3)
        else MoneyText(if (s.isTransfer) abs(paid.amount) else paid.amount, s.currency, sign = if (s.isTransfer) Sign.None else Sign.Always, color = if (s.isTransfer) c.ink2 else c.ink)
    }
}
