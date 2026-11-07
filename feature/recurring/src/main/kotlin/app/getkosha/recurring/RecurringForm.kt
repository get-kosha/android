package app.getkosha.recurring

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.getkosha.design.AccountDot
import app.getkosha.design.AccountList
import app.getkosha.design.AccountOption
import app.getkosha.design.Button
import app.getkosha.design.Card
import app.getkosha.design.CategoryGrid
import app.getkosha.design.CategoryTile
import app.getkosha.design.Field
import app.getkosha.design.Hairline
import app.getkosha.design.KText
import app.getkosha.design.KoshaTheme
import app.getkosha.design.PhIcon
import app.getkosha.design.R
import app.getkosha.design.Segmented
import app.getkosha.design.Sheet
import app.getkosha.design.Stepper
import app.getkosha.design.categoryIconName
import app.getkosha.model.AccountType
import app.getkosha.model.Categories
import app.getkosha.model.Meta
import app.getkosha.model.Money
import app.getkosha.model.Sign
import app.getkosha.model.TransactionList
import app.getkosha.model.Wallet
import app.getkosha.ui.LocalNav
import app.getkosha.ui.MoneyText
import app.getkosha.ui.Route
import app.getkosha.ui.meta
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.LocalDate
import kotlin.math.abs

/** What the recurring form holds; filled once from the schedule, a suggestion or an entry. */
@Stable
private class Form(today: LocalDate, wallet: String, autoPay: Boolean) {
    var kind by mutableStateOf(FormKind.Subscription)
    var name by mutableStateOf("")
    var amount by mutableStateOf<Long?>(null)
    var variable by mutableStateOf(false)
    var walletId by mutableStateOf(wallet)
    var toWalletId by mutableStateOf<String?>(null)
    var toAmount by mutableStateOf<Long?>(null)
    var categoryId by mutableStateOf<String?>(null)
    var every by mutableStateOf(1)
    var unit by mutableStateOf("month")
    var start by mutableStateOf(today)
    var end by mutableStateOf<LocalDate?>(null)
    var autoPay by mutableStateOf(autoPay)
    var note = ""
    var link: List<String> = emptyList()
    var filled by mutableStateOf(false)

    fun from(s: Sub) {
        kind = when (s.group()) {
            Group.Transfers -> FormKind.Transfer
            Group.Income -> FormKind.Income
            Group.Bills -> FormKind.Bill
            Group.Subscriptions -> FormKind.Subscription
        }
        name = s.name
        amount = abs(s.amount)
        variable = s.isVariable
        walletId = s.walletId
        toWalletId = s.toWalletId
        toAmount = s.toAmount
        categoryId = s.categoryId.takeIf { !s.isTransfer }
        every = s.every
        unit = s.unit
        start = LocalDate.parse(s.next?.dueDay ?: s.startDay)
        end = s.endDay?.let(LocalDate::parse)
        autoPay = s.auto
        note = s.note
        filled = true
    }

    /** A regular payment Kosha spotted: its past payments are filed under the new schedule. */
    fun from(g: Suggestion) {
        kind = if (g.amount > 0) FormKind.Income else if (g.variable) FormKind.Bill else FormKind.Subscription
        name = g.payee
        amount = abs(g.amount)
        walletId = g.accountId
        categoryId = g.categoryId
        every = g.every
        unit = g.unit
        start = LocalDate.parse(g.nextDay)
        link = g.transactionIds
        filled = true
    }

    /** An entry made recurring: the schedule starts a month after it, and the entry is its first payment. */
    fun from(t: app.getkosha.model.Transaction) {
        kind = if (t.amount > 0) FormKind.Income else FormKind.Subscription
        name = t.payee
        amount = abs(t.amount)
        walletId = t.accountId
        categoryId = t.categoryId.takeIf { !Categories.isSystem(it) }
        start = LocalDate.parse(t.day).plusMonths(1)
        link = listOf(t.id)
        filled = true
    }
}

private enum class Picker { Amount, ToAmount, Account, ToAccount, Category, Start, End }

/** "Bank account · EUR" and similar, for account pickers. */
@Composable
fun typeLabel(type: AccountType) = stringResource(
    when (type) {
        AccountType.Bank -> R.string.accounts_type_bank
        AccountType.Cash -> R.string.accounts_type_cash
        AccountType.Credit -> R.string.accounts_type_credit
        AccountType.Investment -> R.string.accounts_type_investment
    },
)

/** The account picker's rows: open accounts with their colour (by position in the account order) and balance. */
@Composable
fun accountOptions(m: Meta?, except: String? = null): List<AccountOption> {
    val locale = KoshaTheme.locale
    val wallets = m?.wallets.orEmpty()
    return wallets.mapIndexedNotNull { i, w: Wallet ->
        if (w.isArchived || w.id == except) null
        else AccountOption(w.id, w.name, "${typeLabel(w.type)} · ${w.currency.uppercase()}", Money.format(w.balance, w.currency, locale), i)
    }
}

/**
 * A new schedule or an edit: Bill · Subscription · Income · Transfer, its name, usual amount on the keypad,
 * account (and the account it moves to), category, how often, first payment and end, and whether payments are
 * recorded on their day. Started from a suggestion or an entry, it files the past payments under it.
 */
@Composable
fun RecurringFormScreen(id: String?, fromPayee: String?, fromTransaction: String?) {
    val nav = LocalNav.current
    val m = meta()
    val c = KoshaTheme.colors
    val locale = KoshaTheme.locale
    val today = today()
    val writer = rememberWriter()
    val scope = rememberCoroutineScope()
    val wallets = m?.wallets.orEmpty()
    val form = remember {
        Form(today, wallets.firstOrNull { !it.isArchived }?.id ?: "", m?.pref("default_auto_pay", "1") == "1")
    }
    var picker by remember { mutableStateOf<Picker?>(null) }
    var busy by remember { mutableStateOf(false) }

    // Fill the form once from what it starts from.
    val subs = if (id != null) rememberLoad<SubscriptionsData>("subscriptions") { get("/api/app/subscriptions") }.value else null
    val suggestions = if (fromPayee != null && fromTransaction == null) rememberLoad<RecurringList>("recurring/suggestions") { get("/api/v1/recurring", mapOf("includeSuggestions" to true)) }.value else null
    val entries = if (fromTransaction != null) {
        rememberLoad<TransactionList>("recurring/from/$fromTransaction") {
            // There is no read for one transaction: search its payee (or everything) from the start.
            get("/api/v1/transactions", mapOf("query" to fromPayee?.take(40), "from" to "2000-01-01", "limit" to if (fromPayee != null) 100 else 500))
        }.value
    } else null
    LaunchedEffect(subs, suggestions, entries) {
        if (form.filled) return@LaunchedEffect
        subs?.subscriptions?.find { it.id == id }?.let(form::from)
        suggestions?.suggestions?.find { it.payee == fromPayee }?.let(form::from)
        entries?.transactions?.find { it.id == fromTransaction }?.let(form::from)
        if (!form.filled && fromPayee != null && form.name.isEmpty()) form.name = fromPayee
    }

    val transfer = form.kind == FormKind.Transfer
    val income = form.kind == FormKind.Income
    val wallet = wallets.find { it.id == form.walletId }
    val toWallet = wallets.find { it.id == form.toWalletId }
    val currency = wallet?.currency ?: m?.primary ?: "eur"
    val cross = transfer && toWallet != null && toWallet.currency != wallet?.currency
    val variable = transfer && form.variable
    val categories = m?.categories.orEmpty().filter {
        !Categories.isSystem(it.id) && (!it.isArchived || it.id == form.categoryId) && it.isIncome == income
    }
    val category = m?.categories?.find { it.id == form.categoryId }

    val msgAmount = stringResource(if (variable) R.string.subs_enter_usual_amount else R.string.subs_enter_amount)
    val msgTo = stringResource(R.string.subs_pick_to_account)
    val msgCategory = stringResource(R.string.subs_pick_category)
    val done = stringResource(
        when {
            id != null -> R.string.common_saved
            transfer -> R.string.subs_transfer_added
            income -> R.string.subs_income_added
            form.kind == FormKind.Bill -> R.string.subs_bill_added
            else -> R.string.subs_subscription_added
        },
    )

    fun save() {
        val amount = form.amount
        when {
            amount == null || amount <= 0 -> return writer.toast(msgAmount)
            transfer && (form.toWalletId == null || form.toWalletId == form.walletId) -> return writer.toast(msgTo)
            !transfer && (form.categoryId == null || categories.none { it.id == form.categoryId }) -> return writer.toast(msgCategory)
        }
        val original = subs?.subscriptions?.find { it.id == id }
        val body = buildJsonObject {
            put("name", form.name.trim().ifEmpty { if (transfer) "${wallet?.name} → ${toWallet?.name}" else "" })
            put("accountId", form.walletId)
            put("amount", abs(amount) * if (income) 1 else -1)
            if (!transfer) put("categoryId", form.categoryId)
            // Changing every, unit or the start starts a new schedule and drops one-off edits, so send them only when changed.
            if (original == null || original.every != form.every) put("every", form.every)
            if (original == null || original.unit != form.unit) put("unit", form.unit)
            if (original == null || LocalDate.parse(original.next?.dueDay ?: original.startDay) != form.start) put("startDay", form.start.toString())
            put("endDay", form.end?.toString()?.let(::JsonPrimitive) ?: JsonNull)
            put("autoPay", form.autoPay && !variable)
            put("kind", if (form.kind == FormKind.Subscription) "subscription" else "repetitive")
            put("note", form.note)
            if (transfer) {
                put("toAccountId", form.toWalletId)
                put("variable", variable)
                // create_recurring takes no null here; update_recurring clears it with null.
                val to = form.toAmount?.takeIf { cross && !variable }
                if (to != null) put("toAmount", to) else if (original != null) put("toAmount", JsonNull)
            } else if (original != null) {
                put("toAccountId", JsonNull)
                put("variable", false)
            }
            if (original == null && form.link.isNotEmpty()) put("linkTransactionIds", JsonArray(form.link.map(::JsonPrimitive)))
        }
        busy = true
        scope.launch {
            val saved = writer.run {
                if (id != null) send("PATCH", "/api/v1/recurring/$id", body).let { id }
                else post<JsonObject, Created>("/api/v1/recurring", body).id
            }
            busy = false
            if (saved != null) {
                writer.toast(done)
                nav.popBackStack()
                if (id == null) nav.navigate(Route.RecurringDetail(saved))
            }
        }
    }

    FormPage(
        if (id != null) stringResource(R.string.subs_edit_title, form.name) else stringResource(R.string.subs_new_title),
        onCancel = { nav.popBackStack() },
        footer = {
            Button(
                stringResource(
                    when {
                        id != null -> R.string.subs_save_changes
                        transfer -> R.string.subs_add_transfer
                        income -> R.string.subs_add_income
                        form.kind == FormKind.Bill -> R.string.subs_add_bill
                        else -> R.string.subs_add_subscription
                    },
                ),
                { save() },
                Modifier.fillMaxWidth(),
                enabled = !busy,
            )
        },
    ) {
        Segmented(
            listOf(
                FormKind.Bill to stringResource(R.string.subs_kind_bill),
                FormKind.Subscription to stringResource(R.string.subs_kind_subscription),
                FormKind.Income to stringResource(R.string.subs_kind_income),
                FormKind.Transfer to stringResource(R.string.subs_kind_transfer),
            ),
            form.kind,
            { k ->
                if ((k == FormKind.Income) != income) form.categoryId = null
                form.kind = k
            },
            Modifier.fillMaxWidth(),
            small = true,
        )
        Field(form.name, { form.name = it }, label = stringResource(R.string.subs_name))

        Card(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(role = Role.Button) { picker = Picker.Amount }) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                KText(stringResource(if (variable) R.string.subs_usual_amount else R.string.subs_amount), style = KoshaTheme.type.small, color = c.ink3)
                if (form.amount != null) {
                    MoneyText(form.amount!!, currency, sign = if (income) Sign.Always else Sign.None, style = KoshaTheme.type.amount.copy(fontSize = 40.sp), color = if (income) c.pos else c.ink)
                } else {
                    KText(Money.format(0, currency, locale), style = KoshaTheme.type.amount.copy(fontSize = 40.sp), color = c.ink3)
                }
            }
        }

        Card(Modifier.fillMaxWidth(), tight = true) {
            if (transfer) {
                SwitchRow(stringResource(R.string.subs_variable), stringResource(R.string.app_rec_asks_each_time), form.variable) { form.variable = it }
                Hairline()
            }
            DetailRow(stringResource(if (transfer) R.string.subs_from else R.string.subs_account), { picker = Picker.Account }, labelWidth = 80.dp) {
                AccountDot(c.account(wallets.indexOf(wallet)))
                KText(wallet?.name ?: "")
            }
            Hairline()
            if (transfer) {
                DetailRow(stringResource(R.string.subs_to), { picker = Picker.ToAccount }, labelWidth = 80.dp) {
                    if (toWallet != null) AccountDot(c.account(wallets.indexOf(toWallet)))
                    KText(toWallet?.name ?: stringResource(R.string.subs_account), color = if (toWallet == null) c.ink3 else c.ink)
                }
                if (cross && !variable) {
                    Hairline()
                    DetailRow(stringResource(R.string.subs_amount_received), { picker = Picker.ToAmount }, labelWidth = 80.dp) {
                        val to = form.toAmount
                        if (to != null) MoneyText(to, toWallet.currency, sign = Sign.None)
                        else KText(stringResource(R.string.subs_leave_empty_rate), style = KoshaTheme.type.small, color = c.ink3)
                    }
                }
            } else {
                DetailRow(stringResource(R.string.subs_category), { picker = Picker.Category }, labelWidth = 80.dp) {
                    if (category != null) PhIcon(categoryIconName(category.icon), size = 18.dp)
                    KText(category?.name ?: stringResource(R.string.subs_pick_category), color = if (category == null) c.ink3 else c.ink)
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            KText(stringResource(R.string.subs_repeats_every), style = KoshaTheme.type.small, color = c.ink3)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Stepper(form.every, { form.every = it }, max = 365)
                Segmented(
                    listOf(
                        "day" to stringResource(R.string.app_rec_unit_day),
                        "week" to stringResource(R.string.app_rec_unit_week),
                        "month" to stringResource(R.string.app_rec_unit_month),
                        "year" to stringResource(R.string.app_rec_unit_year),
                    ),
                    form.unit,
                    { form.unit = it },
                    Modifier.weight(1f),
                    small = true,
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DateField(stringResource(if (id != null) R.string.subs_next_payment else R.string.subs_first_payment), "calendar-blank", dayText(form.start.toString(), "dMMMyyyy"), Modifier.weight(1f)) { picker = Picker.Start }
            DateField(stringResource(R.string.subs_ends), "calendar-x", form.end?.let { dayText(it.toString(), "dMMMyyyy") } ?: stringResource(R.string.subs_never), Modifier.weight(1f)) { picker = Picker.End }
        }
        val backfill = if (id == null && form.autoPay && !variable) Schedule.backfill(form.start, form.every, form.unit, form.end, today) else 0
        if (backfill > 0) {
            HintLine("clock-counter-clockwise", pluralStringResource(R.plurals.app_rec_backfill, backfill, dayText(form.start.toString()), backfill))
        }

        if (!variable) {
            Card(Modifier.fillMaxWidth(), tight = true) {
                SwitchRow(stringResource(R.string.subs_auto_pay), stringResource(R.string.app_rec_on_due_date), form.autoPay) { form.autoPay = it }
            }
        }
        if (id != null) KText(stringResource(R.string.subs_changes_hint), style = KoshaTheme.type.xs, color = c.ink3)
    }

    when (picker) {
        Picker.Amount -> KeypadSheet(
            stringResource(if (variable) R.string.subs_usual_amount else R.string.subs_amount),
            form.amount,
            currency,
            onDone = { form.amount = it; picker = null },
            onDismiss = { picker = null },
        )
        Picker.ToAmount -> KeypadSheet(
            stringResource(R.string.subs_amount_received),
            form.toAmount,
            toWallet?.currency ?: currency,
            onDone = { form.toAmount = it; picker = null },
            onDismiss = { picker = null },
            subtitle = stringResource(R.string.subs_leave_empty_rate),
            clearLabel = stringResource(R.string.tx_clear),
            onClear = { form.toAmount = null; picker = null },
        )
        Picker.Account, Picker.ToAccount -> Sheet(stringResource(R.string.subs_account), { picker = null }) {
            val to = picker == Picker.ToAccount
            Column(Modifier.verticalScroll(rememberScrollState())) {
                AccountList(accountOptions(m, except = if (to) form.walletId else null), if (to) form.toWalletId else form.walletId, {
                    if (to) form.toWalletId = it.id else form.walletId = it.id
                    if (form.toWalletId == form.walletId) form.toWalletId = null
                    picker = null
                })
            }
        }
        Picker.Category -> Sheet(stringResource(R.string.subs_category), { picker = null }) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                CategoryGrid(
                    categories.map { CategoryTile(it.id, categoryIconName(it.icon), it.name) },
                    form.categoryId,
                    { form.categoryId = it.id; picker = null },
                )
            }
        }
        Picker.Start -> DateSheet(stringResource(R.string.subs_first_payment), form.start, { form.start = it; picker = null }, { picker = null })
        Picker.End -> DateSheet(
            stringResource(R.string.subs_ends),
            form.end,
            { form.end = it.takeIf { d -> d >= form.start } ?: form.start; picker = null },
            { picker = null },
            clearLabel = stringResource(R.string.subs_never),
            onClear = { form.end = null; picker = null },
        )
        null -> Unit
    }
}

@Composable
private fun DateField(label: String, icon: String, value: String, modifier: Modifier, onClick: () -> Unit) {
    val c = KoshaTheme.colors
    val shape = RoundedCornerShape(14.dp)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        KText(label, style = KoshaTheme.type.small, color = c.ink3)
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clip(shape)
                .background(c.surface)
                .border(1.dp, c.line, shape)
                .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PhIcon(icon, size = 18.dp)
            KText(value, maxLines = 1)
        }
    }
}
