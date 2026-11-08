package app.getkosha.activity

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.getkosha.design.AccountDot
import app.getkosha.design.AccountList
import app.getkosha.design.AccountOption
import app.getkosha.design.AmountDisplay
import app.getkosha.design.AmountKeypad
import app.getkosha.design.Button
import app.getkosha.design.ButtonSize
import app.getkosha.design.ButtonStyle
import app.getkosha.design.Card
import app.getkosha.design.Hairline
import app.getkosha.design.IconButton
import app.getkosha.design.KText
import app.getkosha.design.KoshaTheme
import app.getkosha.design.MonthGrid
import app.getkosha.design.PhIcon
import app.getkosha.design.R
import app.getkosha.design.Sheet
import app.getkosha.design.Toast
import app.getkosha.model.Dates
import app.getkosha.model.EntryKind
import app.getkosha.model.Keypad
import app.getkosha.model.Money
import app.getkosha.model.Sign
import app.getkosha.model.Transaction
import app.getkosha.model.Transactions
import app.getkosha.model.canRefund
import app.getkosha.model.convert
import app.getkosha.model.refundLeft
import app.getkosha.model.refundedInFull
import app.getkosha.ui.AmountText
import app.getkosha.ui.LocalToasts
import app.getkosha.ui.MoneyText
import app.getkosha.ui.meta
import app.getkosha.ui.rememberKosha
import app.getkosha.ui.shortDay
import app.getkosha.ui.write
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

/**
 * Money that came back for a spending entry (transaction-form.tsx Refunds): all of it or part, in one go or
 * several, into any account. Each refund is its own entry, so balances see it, and totals count it as spending
 * undone rather than income. Removing one deletes that entry. [onChanged] runs after a refund is saved or removed.
 */
@Composable
internal fun Refunds(t: Transaction, writable: Boolean, onChanged: () -> Unit) {
    val kosha = rememberKosha()
    val toasts = LocalToasts.current
    val m = meta() ?: return
    val c = KoshaTheme.colors
    val locale = KoshaTheme.locale
    val refunds = t.refunds.orEmpty()
    if (!t.canRefund && refunds.isEmpty()) return
    var adding by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<Transaction.Refund?>(null) }
    val readOnly = stringResource(R.string.common_demo_read_only)
    val money = { r: Transaction.Refund -> Money.format(r.amount, r.currency, locale) }

    if (refunds.isNotEmpty()) {
        Card(Modifier.fillMaxWidth(), tight = true) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                KText(stringResource(R.string.tx_refunds), Modifier.weight(1f), style = KoshaTheme.type.small, color = c.ink3)
                AmountText(
                    AnnotatedString(
                        if (t.refundedInFull) stringResource(R.string.tx_refunded_full)
                        else stringResource(R.string.tx_refunded_part, Money.format(t.refunded, t.currency, locale), Money.format(-t.amount, t.currency, locale)),
                    ),
                    style = KoshaTheme.type.small, color = if (t.refundedInFull) c.pos else c.ink3,
                )
            }
            refunds.forEach { r ->
                Hairline()
                Row(
                    Modifier.fillMaxWidth().heightIn(min = KoshaTheme.dims.settingsRow).padding(start = 16.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    PhIcon("arrow-u-up-left", size = 16.dp, tint = c.ink3)
                    MoneyText(r.amount, r.currency, sign = Sign.Always, color = c.pos)
                    val account = m.wallets.firstOrNull { it.id == r.accountId }
                    AccountDot(c.account(m.wallets.indexOf(account)))
                    KText(
                        (account?.name ?: "") + " · " + shortDay(LocalDate.parse(r.day), locale),
                        Modifier.weight(1f), style = KoshaTheme.type.xs, color = c.ink3, maxLines = 1,
                    )
                    if (writable) IconButton("x", stringResource(R.string.tx_remove_refund, money(r)), { removing = r })
                }
            }
        }
    }
    if (t.canRefund && !t.refundedInFull) {
        Button(
            stringResource(if (refunds.isEmpty()) R.string.tx_mark_refunded else R.string.tx_add_refund),
            { if (writable) adding = true else toasts.show(Toast(readOnly)) },
            Modifier.fillMaxWidth(), style = ButtonStyle.Secondary, size = ButtonSize.Medium, icon = "arrow-u-up-left",
        )
    }
    if (adding) RefundSheet(t, { adding = false }) { adding = false; onChanged() }
    removing?.let { r ->
        val scope = rememberCoroutineScope()
        val res = LocalResources.current
        val failed = stringResource(R.string.tx_delete_failed)
        var busy by remember { mutableStateOf(false) }
        Sheet("", { removing = null }) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                KText(stringResource(R.string.tx_remove_refund_title), style = KoshaTheme.type.title, align = TextAlign.Center)
                val account = m.wallets.firstOrNull { it.id == r.accountId }?.name ?: ""
                AmountText(AnnotatedString(stringResource(R.string.tx_remove_refund_body, money(r), account)), style = KoshaTheme.type.small, color = c.ink2, align = TextAlign.Center)
            }
            Spacer(Modifier.height(14.dp))
            Button(
                stringResource(R.string.common_remove),
                {
                    busy = true
                    scope.launch {
                        val done = kosha.write(toasts, failed) { kosha.api.post<Ids, kotlinx.serialization.json.JsonObject>("/api/v1/transactions/delete", Ids(listOf(r.id))) }
                        busy = false
                        if (done != null) {
                            toasts.show(Toast(res.getString(R.string.tx_refund_removed)))
                            removing = null
                            onChanged()
                        }
                    }
                },
                Modifier.fillMaxWidth(), style = ButtonStyle.Danger, enabled = !busy,
            )
            Spacer(Modifier.height(10.dp))
            Button(stringResource(R.string.common_cancel), { removing = null }, Modifier.fillMaxWidth(), style = ButtonStyle.Secondary)
        }
    }
}

/**
 * Records a refund (create_transactions with refundOf): how much came back on the keypad, which account it came
 * back to, and the day, no earlier than the spending. It starts at what is still to come back, in the chosen
 * account's currency. The payee, tags and event are the entry's; the server gives it the entry's category.
 */
@Composable
private fun RefundSheet(t: Transaction, onDismiss: () -> Unit, onSaved: () -> Unit) {
    val kosha = rememberKosha()
    val toasts = LocalToasts.current
    val m = meta() ?: return
    val c = KoshaTheme.colors
    val locale = KoshaTheme.locale
    val scope = rememberCoroutineScope()
    val res = LocalResources.current
    val today = Dates.today(kosha.tz)
    val earliest = LocalDate.parse(t.day)
    var accountId by remember { mutableStateOf(t.accountId) }
    // null until typed: what's still to come back, in the chosen account's currency.
    var typed by remember { mutableStateOf<String?>(null) }
    var day by remember { mutableStateOf(maxOf(today, earliest)) }
    var month by remember { mutableStateOf(YearMonth.from(day)) }
    var step by remember { mutableStateOf("amount") }
    var busy by remember { mutableStateOf(false) }
    val account = m.wallets.firstOrNull { it.id == accountId }
    val currency = account?.currency ?: t.currency
    val decimals = Money.decimals(currency)
    val left = convert(t.refundLeft, t.currency, currency, m.rates)
    val input = typed ?: if (left > 0) plainAmount(left, decimals) else ""
    val value = Keypad.minorUnits(input, decimals)?.takeIf { it > 0 }
    val failed = stringResource(R.string.tx_refund_failed)

    Sheet(stringResource(R.string.tx_mark_refunded), onDismiss) {
        when (step) {
            "account" -> AccountList(
                m.wallets.filter { !it.isArchived || it.id == t.accountId }.map {
                    AccountOption(it.id, it.name, it.currency.uppercase(), Money.format(it.balance, it.currency, locale), m.wallets.indexOf(it))
                },
                accountId,
                { accountId = it.id; step = "amount" },
                Modifier.verticalScroll(rememberScrollState()),
            )
            "date" -> {
                MonthGrid(month, day, today, { if (!it.isBefore(earliest)) { day = it; step = "amount" } }, { month = it })
            }
            else -> {
                AmountDisplay(input, currency, EntryKind.Income, note = stringResource(R.string.tx_refund_amount))
                AmountKeypad(input, { typed = it }, Modifier.padding(vertical = 8.dp), decimals = decimals)
                Card(Modifier.fillMaxWidth(), tight = true) {
                    FieldRow(stringResource(R.string.tx_refund_to), { step = "account" }) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AccountDot(c.account(m.wallets.indexOf(account)))
                            KText(account?.name ?: "", maxLines = 1)
                        }
                    }
                    Hairline()
                    FieldRow(stringResource(R.string.tx_date), { step = "date" }) {
                        KText(if (day == today) stringResource(R.string.tx_today) else shortDay(day, locale, year = day.year != today.year), maxLines = 1)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Button(
                    stringResource(R.string.tx_save_refund),
                    {
                        val minor = value ?: return@Button
                        busy = true
                        scope.launch {
                            val body = Patch(listOf(buildJsonObject {
                                put("id", UUID.randomUUID().toString())
                                put("refundOf", t.id)
                                put("accountId", accountId)
                                put("payee", t.payee)
                                put("amount", minor)
                                put("categoryId", t.categoryId)
                                put("day", Dates.text(day))
                                put("tags", buildJsonArray { t.tags.forEach { add(JsonPrimitive(it)) } })
                                put("eventId", t.eventId?.let(::JsonPrimitive) ?: JsonNull)
                            }))
                            val saved = kosha.write(toasts, failed) { kosha.api.post<Patch, Transactions>("/api/v1/transactions", body) }
                            busy = false
                            if (saved != null) {
                                toasts.show(Toast(res.getString(R.string.tx_refund_saved, Money.format(minor, currency, locale))))
                                onSaved()
                            }
                        }
                    },
                    Modifier.fillMaxWidth(), enabled = value != null && !busy,
                )
            }
        }
    }
}
