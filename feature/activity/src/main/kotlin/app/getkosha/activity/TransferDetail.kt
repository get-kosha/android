@file:UseSerializers(RoundedLong::class)

package app.getkosha.activity

import kotlinx.serialization.UseSerializers
import app.getkosha.model.RoundedLong

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.getkosha.design.AccountDot
import app.getkosha.design.AccountList
import app.getkosha.design.AccountOption
import app.getkosha.design.Button
import app.getkosha.design.ButtonSize
import app.getkosha.design.ButtonStyle
import app.getkosha.design.Card
import app.getkosha.design.Hairline
import app.getkosha.design.IconButton
import app.getkosha.design.KText
import app.getkosha.design.KoshaTheme
import app.getkosha.design.PhIcon
import app.getkosha.design.R
import app.getkosha.design.Sheet
import app.getkosha.design.Toast
import app.getkosha.model.Categories
import app.getkosha.model.EntryKind
import app.getkosha.model.Money
import app.getkosha.model.Sign
import app.getkosha.ui.AmountText
import app.getkosha.ui.LoadingNote
import app.getkosha.ui.LocalNav
import app.getkosha.ui.LocalToasts
import app.getkosha.ui.MoneyText
import app.getkosha.ui.canWrite
import app.getkosha.ui.meta
import app.getkosha.ui.rateText
import app.getkosha.ui.rememberKosha
import app.getkosha.ui.shortDay
import app.getkosha.ui.write
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToLong

/** What an exchange cost against the market rate (core.ts exchange()), in the source currency's minor units. */
internal data class FxCost(val converted: Long, val rateCost: Double?, val totalCost: Double, val costPct: Double)

internal fun exchange(total: Long, fee: Long, received: Long, marketRate: Double?): FxCost {
    val converted = abs(total) - abs(fee)
    val rateCost = marketRate?.takeIf { it != 0.0 }?.let { (converted * it - abs(received)) / it }
    val totalCost = if (rateCost == null) abs(fee).toDouble() else abs(fee) + rateCost
    return FxCost(converted, rateCost, totalCost, if (total != 0L) totalCost / abs(total) * 100 else 0.0)
}

/**
 * A transfer: from, to, what was sent and received, the fee, the rate and its cost against the market, the
 * provider, the day and the note. Edits go through update_transfer, so both halves stay in step; deleting removes
 * it from both accounts.
 */
@Composable
fun TransferDetailScreen(id: String) {
    val kosha = rememberKosha()
    val nav = LocalNav.current
    val toasts = LocalToasts.current
    val m = meta() ?: return
    val c = KoshaTheme.colors
    val locale = KoshaTheme.locale
    val scope = rememberCoroutineScope()
    val writable = canWrite()
    val loaded = rememberTransaction(id)
    val t = loaded?.value
    var sheet by remember { mutableStateOf<String?>(null) }
    var saved by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<JsonObject?>(null) }
    val offline = stringResource(R.string.common_offline_title)
    val readOnly = stringResource(R.string.common_demo_read_only)
    Column(Modifier.fillMaxSize().background(c.bg).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton("caret-left", stringResource(R.string.common_back), { nav.popBackStack() })
            KText(
                if (saved) stringResource(R.string.tx_header_saved, stringResource(R.string.tx_kind_transfer)) else stringResource(R.string.tx_kind_transfer),
                Modifier.weight(1f), style = KoshaTheme.type.small, color = c.ink3, align = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Spacer(Modifier.width(48.dp))
        }
        val link = t?.transfer
        if (t == null || link == null) {
            Box(Modifier.padding(16.dp)) {
                if (loaded?.refreshing != false) LoadingNote() else KText(loaded.error ?: stringResource(R.string.common_offline_body), style = KoshaTheme.type.small, color = c.ink3)
            }
            return@Column
        }
        // Either half can open this; show it the way it went, from → to.
        val outgoing = t.amount < 0
        val from = m.wallets.firstOrNull { it.id == (if (outgoing) t.accountId else link.counterAccountId) }
        val to = m.wallets.firstOrNull { it.id == (if (outgoing) link.counterAccountId else t.accountId) }
        val fromCurrency = from?.currency ?: t.currency
        val toCurrency = to?.currency ?: t.currency
        val sent = (if (outgoing) abs(t.amount) else abs(link.counterAmount ?: 0)) + link.fee
        val received = if (outgoing) abs(link.counterAmount ?: 0) else t.amount
        val save = { change: JsonObject, touchesReconciled: Boolean ->
            when {
                !writable -> toasts.show(Toast(readOnly))
                t.reconciled && touchesReconciled -> {
                    pending = change
                    sheet = "reconciled"
                }
                else -> scope.launch {
                    saved = kosha.write(toasts, offline) { kosha.api.patch<JsonObject, JsonObject>("/api/v1/transfers/${link.id}", change) } != null || saved
                }
            }
        }
        val edit = { name: String -> if (writable) sheet = name else toasts.show(Toast(readOnly)) }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 40.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(
                Modifier.fillMaxWidth().clickable(role = Role.Button, onClickLabel = stringResource(R.string.tx_amount)) { edit("sent") }.padding(top = 4.dp, bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                MoneyText(sent, fromCurrency, sign = Sign.None, style = KoshaTheme.type.num.copy(fontSize = 40.sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.04).em))
                KText(stringResource(R.string.app_act_not_counted), style = KoshaTheme.type.small, color = c.ink3)
            }
            Card(Modifier.fillMaxWidth(), tight = true) {
                FieldRow(stringResource(R.string.tx_from), { edit("from") }, end = { MoneyText(sent, fromCurrency, sign = Sign.None, color = c.ink2) }) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (from != null) AccountDot(c.account(m.wallets.indexOf(from)))
                        KText(from?.name ?: "—", maxLines = 1)
                    }
                }
                Hairline()
                FieldRow(
                    stringResource(R.string.tx_to), { edit("to") },
                    end = {
                        Row(
                            Modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(8.dp))
                                .clickable(role = Role.Button, onClickLabel = stringResource(R.string.tx_amount_received)) { edit("received") },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            MoneyText(received, toCurrency, sign = Sign.None, color = c.ink2)
                            if (toCurrency != fromCurrency) KText(toCurrency.uppercase(), style = KoshaTheme.type.num, color = c.ink3)
                        }
                    },
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (to != null) AccountDot(c.account(m.wallets.indexOf(to)))
                        KText(to?.name ?: "—", maxLines = 1)
                    }
                }
                Hairline()
                val feeCategory = m.categories.firstOrNull { it.id == Categories.TRANSFER_FEE }?.name ?: stringResource(R.string.defaults_transfer_fees)
                FieldRow(stringResource(R.string.tx_fee), { edit("fee") }, end = {
                    if (link.fee > 0) KText(stringResource(R.string.app_act_fee_counted, feeCategory), style = KoshaTheme.type.xs, color = c.ink3)
                }) { MoneyText(link.fee, fromCurrency, sign = Sign.None) }
                Hairline()
                FieldRow(stringResource(R.string.tx_via), { edit("provider") }) { KText(link.provider?.ifEmpty { null } ?: "—", maxLines = 1) }
                Hairline()
                FieldRow(stringResource(R.string.tx_date), { edit("date") }) {
                    AmountText(AnnotatedString(shortDay(LocalDate.parse(t.day), locale, weekday = true, year = true) + " · " + t.time), style = KoshaTheme.type.body)
                }
            }
            if (fromCurrency != toCurrency && link.marketRate != null) {
                val cost = exchange(sent, link.fee, received, link.marketRate)
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.soft).padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    AmountText(
                        AnnotatedString(
                            stringResource(
                                R.string.tx_fx_becomes,
                                Money.format(cost.converted, fromCurrency, locale), Money.format(received, toCurrency, locale), rateText(link.marketRate, 1.0),
                            ),
                        ),
                        style = KoshaTheme.type.small, color = c.ink2,
                    )
                    AmountText(
                        AnnotatedString(
                            stringResource(
                                R.string.tx_fx_cost,
                                Money.format(cost.totalCost.roundToLong(), fromCurrency, locale), String.format(locale, "%.2f", cost.costPct),
                            ),
                        ),
                        style = KoshaTheme.type.small, color = c.ink,
                    )
                }
            }
            if (t.note.isNotEmpty() || writable) {
                LinkCard("note", t.note.ifEmpty { stringResource(R.string.tx_add_note) }) { edit("note") }
            }
            if (t.reconciled) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PhIcon("check-circle", size = 18.dp, tint = c.pos)
                    KText(stringResource(R.string.tx_reconciled_note), style = KoshaTheme.type.small, color = c.ink2)
                }
            }
            if (writable) {
                Button(stringResource(R.string.tx_delete_transfer), { sheet = "delete" }, Modifier.fillMaxWidth().padding(top = 6.dp), style = ButtonStyle.Danger, size = ButtonSize.Medium, icon = "trash")
                KText(stringResource(R.string.tx_delete_transfer_both), Modifier.fillMaxWidth(), style = KoshaTheme.type.xs, color = c.ink3, align = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
        val close = { sheet = null }
        val accounts = m.wallets.filter { !it.isArchived }.map {
            AccountOption(it.id, it.name, it.currency.uppercase(), Money.format(it.balance, it.currency, locale), m.wallets.indexOf(it))
        }
        when (sheet) {
            "sent" -> AmountSheet(sent, fromCurrency, EntryKind.Transfer, close) { close(); save(buildJsonObject { put("sent", it) }, true) }
            "received" -> AmountSheet(received, toCurrency, EntryKind.Transfer, close, stringResource(R.string.tx_received_currency, toCurrency.uppercase())) {
                close()
                save(buildJsonObject { put("received", it) }, true)
            }
            "fee" -> FeeSheet(link.fee, fromCurrency, close) { close(); save(buildJsonObject { put("fee", it) }, true) }
            "provider" -> TextSheet(stringResource(R.string.tx_provider), link.provider ?: "", singleLine = true, close) { close(); save(buildJsonObject { put("provider", it.trim().take(60)) }, false) }
            "note" -> TextSheet(stringResource(R.string.tx_note), t.note, singleLine = false, close) { close(); save(buildJsonObject { put("note", it.trim()) }, false) }
            "date" -> DateSheet(t.day, t.time, kosha.tz, close, withTime = false) { day, _ -> close(); save(buildJsonObject { put("day", day) }, true) }
            "from", "to" -> Sheet(stringResource(if (sheet == "from") R.string.tx_from else R.string.tx_to), close) {
                val field = if (sheet == "from") "fromAccountId" else "toAccountId"
                AccountList(accounts, if (sheet == "from") from?.id else to?.id, { close(); save(buildJsonObject { put(field, it.id) }, true) }, Modifier.verticalScroll(rememberScrollState()))
            }
            "delete" -> DeleteConfirm(t) { deleted -> close(); if (deleted) nav.popBackStack() }
            "reconciled" -> Sheet(stringResource(R.string.tx_reconciled_note), { pending = null; close() }) {
                Button(stringResource(R.string.common_continue), {
                    val change = pending
                    pending = null
                    close()
                    change?.let { save(it, false) }
                }, Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                Button(stringResource(R.string.common_cancel), { pending = null; close() }, Modifier.fillMaxWidth(), style = ButtonStyle.Secondary)
            }
        }
    }
}

/** The fee on the keypad; zero is allowed, so it can be cleared. */
@Composable
private fun FeeSheet(fee: Long, currency: String, onDismiss: () -> Unit, onSave: (Long) -> Unit) {
    var input by remember { mutableStateOf(if (fee == 0L) "" else plainAmount(fee)) }
    val decimals = Money.decimals(currency)
    Sheet(stringResource(R.string.tx_fee_currency, currency.uppercase()), onDismiss) {
        app.getkosha.design.AmountDisplay(input, currency, EntryKind.Transfer)
        app.getkosha.design.AmountKeypad(input, { input = it }, Modifier.padding(vertical = 8.dp), decimals = decimals)
        val value = if (input.isEmpty()) 0L else app.getkosha.model.Keypad.minorUnits(input, decimals)
        Button(stringResource(R.string.common_save), { value?.let(onSave) }, Modifier.fillMaxWidth(), enabled = value != null)
    }
}
