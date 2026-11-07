package app.getkosha.accounts

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.getkosha.design.Button
import app.getkosha.design.Card
import app.getkosha.design.Field
import app.getkosha.design.Hairline
import app.getkosha.design.KText
import app.getkosha.design.KoshaTheme
import app.getkosha.design.ListOption
import app.getkosha.design.ListPicker
import app.getkosha.design.PhIcon
import app.getkosha.design.R
import app.getkosha.design.Sheet
import app.getkosha.model.AccountType
import app.getkosha.model.Sign
import app.getkosha.recurring.DetailRow
import app.getkosha.recurring.FormPage
import app.getkosha.recurring.HintLine
import app.getkosha.recurring.KeypadSheet
import app.getkosha.recurring.money
import app.getkosha.recurring.rememberWriter
import app.getkosha.recurring.typeLabel
import app.getkosha.ui.LocalNav
import app.getkosha.ui.MoneyText
import app.getkosha.ui.Route
import app.getkosha.ui.meta
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.Currency
import kotlin.math.abs

private enum class Picker { Currency, Limit, Balance }

private val TYPES = listOf(
    AccountType.Bank to "bank",
    AccountType.Cash to "money",
    AccountType.Credit to "credit-card",
    AccountType.Investment to "chart-line-up",
)

/**
 * A new account or an edit: name, type, currency, and for a card its credit limit and what it owes, otherwise
 * its opening (or current) balance. A changed balance on an existing account is booked as a correction.
 */
@Composable
fun AccountFormScreen(id: String?) {
    val nav = LocalNav.current
    val m = meta()
    val c = KoshaTheme.colors
    val locale = KoshaTheme.locale
    val writer = rememberWriter()
    val scope = rememberCoroutineScope()
    val initial = m?.wallets?.find { it.id == id }
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var type by remember { mutableStateOf(initial?.type ?: AccountType.Bank) }
    var currency by remember { mutableStateOf(initial?.currency ?: m?.primary ?: "eur") }
    var limit by remember { mutableStateOf(initial?.creditLimit) }
    // For a card people know what they owe; the account keeps what's left (limit minus owed).
    var balance by remember {
        mutableStateOf(initial?.let { if (it.type == AccountType.Credit) (it.creditLimit ?: 0) - it.balance else it.balance })
    }
    var picker by remember { mutableStateOf<Picker?>(null) }
    var busy by remember { mutableStateOf(false) }
    val locked = (initial?.txCount ?: 0) > 0
    val credit = type == AccountType.Credit
    val target = balance?.let { if (credit) (limit ?: 0) - abs(it) else it }
    val shownBefore = initial?.let { if (it.type == AccountType.Credit) (it.creditLimit ?: 0) - it.balance else it.balance }
    val changedBalance = initial == null || balance != shownBefore || (credit && limit != initial.creditLimit && balance != null)
    val correction = if (initial != null && target != null) target - initial.balance else 0L
    val needLimit = stringResource(R.string.accounts_enter_credit_limit)
    val done = stringResource(if (initial != null) R.string.accounts_updated else R.string.accounts_added)
    val balanceLabel = stringResource(
        when (type) {
            AccountType.Credit -> R.string.accounts_currently_owed
            AccountType.Investment -> R.string.accounts_invested_so_far
            else -> R.string.accounts_current_balance
        },
    )

    fun save() {
        if (credit && (limit ?: 0) <= 0) return writer.toast(needLimit)
        val body = buildJsonObject {
            put("name", name.trim())
            put("type", type.name.lowercase())
            put("currency", currency)
            if (credit) put("creditLimit", limit) else if (initial != null) put("creditLimit", JsonNull)
            if (target != null && changedBalance) put("balance", target)
        }
        busy = true
        scope.launch {
            val ok = writer.run {
                if (initial != null) send("PATCH", "/api/v1/accounts/${initial.id}", body).let { initial.id }
                else post<JsonObject, CreatedAccount>("/api/v1/accounts", body).id
            }
            busy = false
            if (ok != null) {
                writer.toast(done)
                nav.popBackStack()
                if (initial == null) nav.navigate(if (type == AccountType.Investment) Route.InvestmentDetail(ok) else Route.AccountDetail(ok))
            }
        }
    }

    FormPage(
        if (initial != null) stringResource(R.string.accounts_edit_title, initial.name) else stringResource(R.string.accounts_new),
        onCancel = { nav.popBackStack() },
        footer = {
            Button(
                stringResource(if (initial != null) R.string.common_save else R.string.accounts_add_account),
                { save() },
                Modifier.fillMaxWidth(),
                enabled = !busy && name.isNotBlank(),
            )
        },
    ) {
        Field(name, { name = it }, label = stringResource(R.string.accounts_name))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            KText(stringResource(R.string.accounts_type), style = KoshaTheme.type.small, color = c.ink3)
            Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TYPES.forEach { (t, icon) ->
                    val on = t == type
                    val shape = RoundedCornerShape(14.dp)
                    Column(
                        Modifier
                            .weight(1f)
                            .heightIn(min = 64.dp)
                            .clip(shape)
                            .background(c.surface)
                            .border(if (on) 1.5.dp else 1.dp, if (on) c.selection else c.line, shape)
                            .selectable(on, role = Role.RadioButton) { type = t }
                            .padding(horizontal = 4.dp, vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        PhIcon(icon, size = 22.dp, tint = if (on) c.selection else c.ink2)
                        KText(typeLabel(t), style = KoshaTheme.type.xs, color = if (on) c.ink else c.ink2, align = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }
            }
        }

        Card(Modifier.fillMaxWidth(), tight = true) {
            DetailRow(stringResource(R.string.accounts_currency), if (locked) null else ({ picker = Picker.Currency }), labelWidth = 100.dp) {
                KText(currency.uppercase(), style = KoshaTheme.type.num)
                KText(runCatching { Currency.getInstance(currency.uppercase()).getDisplayName(locale) }.getOrDefault(""), color = c.ink3, maxLines = 1)
            }
            if (credit) {
                Hairline()
                DetailRow(stringResource(R.string.accounts_credit_limit), { picker = Picker.Limit }, labelWidth = 100.dp, trailing = "pencil-simple") {
                    val l = limit
                    if (l != null) MoneyText(l, currency, sign = Sign.None) else KText("—", color = c.ink3)
                }
            }
            Hairline()
            DetailRow(balanceLabel, { picker = Picker.Balance }, labelWidth = 100.dp, trailing = "pencil-simple") {
                val b = balance
                if (b != null) MoneyText(b, currency, Modifier.weight(1f), sign = Sign.Natural)
                else KText("—", Modifier.weight(1f), color = c.ink3)
                if (initial == null) KText(stringResource(R.string.accounts_optional), style = KoshaTheme.type.xs, color = c.ink3)
            }
        }
        when {
            initial != null && correction != 0L && changedBalance ->
                HintLine("scales", stringResource(R.string.accounts_save_adds_correction, money(correction, currency, Sign.Always)))
            credit -> HintLine("info", stringResource(R.string.app_acc_debt_hint))
            type == AccountType.Investment -> HintLine("info", stringResource(R.string.accounts_investment_hint))
        }
    }

    when (picker) {
        Picker.Currency -> Sheet(stringResource(R.string.accounts_currency), { picker = null }) {
            val codes = remember(m) {
                (listOf(m?.primary ?: "eur") + m?.order.orEmpty() + m?.wallets.orEmpty().map { it.currency } + m?.rates?.keys.orEmpty().sorted()).distinct()
            }
            ListPicker(
                codes.map { code -> ListOption(code, code.uppercase(), runCatching { Currency.getInstance(code.uppercase()).getDisplayName(locale) }.getOrNull()) },
                currency,
                { currency = it.value; picker = null },
            )
        }
        Picker.Limit -> KeypadSheet(stringResource(R.string.accounts_credit_limit), limit, currency, { limit = it; picker = null }, { picker = null })
        Picker.Balance -> KeypadSheet(
            balanceLabel,
            balance,
            currency,
            { balance = it; picker = null },
            { picker = null },
            positive = type == AccountType.Credit || type == AccountType.Investment,
            clearLabel = if (initial == null) stringResource(R.string.tx_clear) else null,
            onClear = { balance = null; picker = null },
        )
        null -> Unit
    }
}
