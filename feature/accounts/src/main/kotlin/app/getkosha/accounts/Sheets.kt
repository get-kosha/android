package app.getkosha.accounts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import app.getkosha.design.Button
import app.getkosha.design.Chip
import app.getkosha.design.Field
import app.getkosha.design.KText
import app.getkosha.design.KoshaTheme
import app.getkosha.design.MonthGrid
import app.getkosha.design.PhIcon
import app.getkosha.design.R
import app.getkosha.design.Segmented
import app.getkosha.design.Sheet
import app.getkosha.design.dayLabel
import app.getkosha.model.AccountType
import app.getkosha.model.Sign
import app.getkosha.model.Wallet
import app.getkosha.recurring.AmountPad
import app.getkosha.recurring.money
import app.getkosha.recurring.percent
import app.getkosha.recurring.rememberAmountInput
import app.getkosha.recurring.rememberWriter
import app.getkosha.recurring.today
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.YearMonth
import kotlin.math.abs

/**
 * Fix balance: type the balance the bank shows (for a card, what it says is owed), and Kosha books the
 * difference as a balance correction through update_account { balance }.
 */
@Composable
fun FixBalanceSheet(w: Wallet, onDismiss: () -> Unit) {
    val c = KoshaTheme.colors
    val writer = rememberWriter()
    val scope = rememberCoroutineScope()
    val credit = w.type == AccountType.Credit
    val limit = w.creditLimit ?: 0
    val shown = if (credit) limit - w.balance else w.balance
    val state = rememberAmountInput(shown, w.currency, w.id)
    val entered = state.minor(w.currency)
    // A card keeps the credit left, so what is owed becomes the limit minus it.
    val target = entered?.let { if (credit) limit - abs(it) else it }
    val correction = target?.minus(w.balance) ?: 0
    val corrected = stringResource(R.string.accounts_corrected_by, money(correction, w.currency, Sign.Always))
    val matches = stringResource(R.string.accounts_balance_matches)
    Sheet(
        stringResource(R.string.accounts_fix_title, w.name),
        onDismiss,
        subtitle = stringResource(if (credit) R.string.accounts_fix_credit_description else R.string.accounts_fix_description),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AmountPad(state, w.currency, note = stringResource(R.string.app_acc_kosha_shows, money(shown, w.currency)), keypad = false)
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.soft).padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PhIcon("scales", size = 16.dp)
                KText(
                    if (correction != 0L) stringResource(R.string.accounts_fix_adds_correction, money(correction, w.currency, Sign.Always))
                    else stringResource(R.string.accounts_no_correction),
                    style = KoshaTheme.type.small,
                    color = c.ink2,
                )
            }
            app.getkosha.design.AmountKeypad(state.keypad, { state.fromKeypad(it) }, decimals = app.getkosha.model.Money.decimals(w.currency))
            Button(
                stringResource(R.string.common_save),
                {
                    scope.launch {
                        val ok = writer.run { send("PATCH", "/api/v1/accounts/${w.id}", buildJsonObject { put("balance", target) }) }
                        if (ok != null) {
                            writer.toast(if (correction != 0L) corrected else matches)
                            onDismiss()
                        }
                    }
                },
                Modifier.fillMaxWidth(),
                enabled = target != null,
            )
        }
    }
}

private enum class GainMode { Value, Gain }

private enum class GainPanel { Keypad, Date, Note }

/**
 * Update an investment: today's value from the broker, or the gain directly (the other follows from the money
 * put in), as of a day, with a note. Shows the gain and the return, and saves with record_gains.
 */
@Composable
fun GainsSheet(w: Wallet, onDismiss: () -> Unit) {
    val c = KoshaTheme.colors
    val locale = KoshaTheme.locale
    val writer = rememberWriter()
    val scope = rememberCoroutineScope()
    val today = today()
    var mode by remember { mutableStateOf(GainMode.Value) }
    var panel by remember { mutableStateOf(GainPanel.Keypad) }
    var day by remember { mutableStateOf(today) }
    var month by remember { mutableStateOf(YearMonth.from(today)) }
    var note by remember { mutableStateOf("") }
    // Switching between value and gain carries the figure over, so nothing typed is lost.
    var seed by remember { mutableStateOf(w.balance + (w.gain ?: 0)) }
    val state = rememberAmountInput(seed, w.currency, mode)
    val entered = state.minor(w.currency)
    val gain = entered?.let { if (mode == GainMode.Value) it - w.balance else it }
    val updated = stringResource(R.string.overview_gain_updated)
    val missing = stringResource(R.string.overview_gain_missing)
    Sheet(
        stringResource(R.string.overview_gain_title, w.name),
        onDismiss,
        subtitle = stringResource(R.string.overview_gain_description, money(w.balance, w.currency)),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Segmented(
                listOf(GainMode.Value to stringResource(R.string.overview_gain_value), GainMode.Gain to stringResource(R.string.overview_gain_gain)),
                mode,
                { next ->
                    if (next != mode) {
                        val g = gain ?: (w.gain ?: 0)
                        seed = if (next == GainMode.Gain) g else w.balance + g
                        mode = next
                    }
                },
                Modifier.fillMaxWidth(),
            )
            AmountPad(state, w.currency, keypad = false)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                if (gain != null) {
                    KText(
                        stringResource(R.string.overview_gain_gain) + " " + money(gain, w.currency, Sign.Always),
                        style = KoshaTheme.type.small,
                        color = if (gain >= 0) c.pos else c.bad,
                    )
                    KText(
                        " · " + stringResource(R.string.overview_gain_return, if (w.balance != 0L) percent(gain.toDouble() / w.balance, locale, 1, sign = true) else "—"),
                        style = KoshaTheme.type.small,
                        color = c.ink3,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip(
                    stringResource(R.string.overview_gain_as_of) + " " + dayLabel(day, today),
                    Modifier.weight(1f),
                    on = panel == GainPanel.Date,
                    onClick = { panel = if (panel == GainPanel.Date) GainPanel.Keypad else GainPanel.Date },
                    leading = { PhIcon("calendar-blank", size = 18.dp) },
                )
                Chip(
                    note.ifEmpty { stringResource(R.string.overview_gain_note) + " " + stringResource(R.string.accounts_optional) },
                    Modifier.weight(1f),
                    on = panel == GainPanel.Note,
                    onClick = { panel = if (panel == GainPanel.Note) GainPanel.Keypad else GainPanel.Note },
                    leading = { PhIcon("note-pencil", size = 18.dp) },
                )
            }
            when (panel) {
                GainPanel.Keypad -> app.getkosha.design.AmountKeypad(state.keypad, { state.fromKeypad(it) }, decimals = app.getkosha.model.Money.decimals(w.currency))
                GainPanel.Date -> MonthGrid(month, day, today, { day = it; panel = GainPanel.Keypad }, { month = it })
                GainPanel.Note -> Field(note, { note = it.take(500) }, placeholder = stringResource(R.string.overview_gain_note))
            }
            Button(
                stringResource(R.string.common_save),
                {
                    if (gain == null) {
                        writer.toast(missing)
                        return@Button
                    }
                    scope.launch {
                        val ok = writer.run {
                            send("POST", "/api/v1/gains", buildJsonObject {
                                put("gains", JsonArray(listOf(buildJsonObject {
                                    put("accountId", w.id)
                                    put("day", day.toString())
                                    put("gain", gain)
                                    put("note", note.trim())
                                })))
                            })
                        }
                        if (ok != null) {
                            writer.toast(updated)
                            onDismiss()
                        }
                    }
                },
                Modifier.fillMaxWidth(),
                enabled = gain != null,
            )
        }
    }
}
