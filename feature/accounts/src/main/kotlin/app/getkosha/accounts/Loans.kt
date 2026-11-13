package app.getkosha.accounts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.getkosha.design.*
import app.getkosha.design.R
import app.getkosha.model.*
import app.getkosha.recurring.*
import app.getkosha.ui.*
import app.getkosha.ui.HeroMoney
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*
import java.time.LocalDate
import java.util.UUID
import kotlin.math.abs

@Composable
fun LoansScreen() {
    val nav = LocalNav.current
    val c = KoshaTheme.colors
    val loaded = rememberLoad<Loans>("loans") { get("/api/v1/loans") }
    var direction by rememberSaveable { mutableStateOf("all") }
    var status by rememberSaveable { mutableStateOf("open") }
    var query by rememberSaveable { mutableStateOf("") }
    val today = today().toString()
    val writable = canWrite()
    Page(
        stringResource(R.string.loans_title),
        onBack = { nav.popBackStack() },
        actions = { if (writable) IconButton("plus", stringResource(R.string.loans_new), { nav.navigate(Route.LoanForm()) }, solid = true, tint = c.ink) },
    ) {
        AccountLoansSwitch(true)
        if (loaded.offline) KText(stringResource(R.string.common_offline_body), style = KoshaTheme.type.small, color = c.ink3)
        loaded.error?.let { KText(it, style = KoshaTheme.type.small, color = c.bad) }
        val loans = loaded.value?.loans
        if (loans == null) { LoadingNote(); return@Page }
        Card(Modifier.fillMaxWidth()) { LoanTotals(loans) }
        Segmented(listOf("all" to stringResource(R.string.loans_all), "lent" to stringResource(R.string.loans_owed_to_you), "borrowed" to stringResource(R.string.loans_you_owe)), direction, { direction = it })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("open" to R.string.loans_open, "paid" to R.string.loans_paid, "all" to R.string.loans_all).forEach { (value, label) ->
                Chip(stringResource(label), on = status == value, small = true, onClick = { status = value })
            }
        }
        if (loans.size > 6) Field(query, { query = it }, placeholder = stringResource(R.string.common_search), icon = "magnifying-glass")
        val visible = loans.filter { (direction == "all" || it.direction == direction) && (status == "all" || (it.remaining == 0L) == (status == "paid")) && (it.person + " " + it.purpose).contains(query, true) }
            .sortedWith(compareBy<Loan> { if (it.remaining > 0 && it.dueDay?.let { due -> due < today } == true) 0 else 1 }.thenBy { it.dueDay ?: "9999" }.thenByDescending { it.day })
        Card(Modifier.fillMaxWidth(), tight = true) {
            if (visible.isEmpty()) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    PhIcon("hand-coins", size = 28.dp, tint = c.ink3)
                    KText(stringResource(R.string.loans_empty), style = KoshaTheme.type.small, color = c.ink3, align = TextAlign.Center)
                    if (writable) Button(stringResource(R.string.loans_new), { nav.navigate(Route.LoanForm()) }, size = ButtonSize.Medium)
                }
            }
            visible.forEachIndexed { i, loan ->
                if (i > 0) Hairline()
                LoanRow(loan, today) { nav.navigate(Route.LoanDetail(loan.id)) }
            }
        }
        KText(stringResource(R.string.loans_currency_note), Modifier.padding(horizontal = 2.dp), style = KoshaTheme.type.xs, color = c.ink3)
    }
}

/** A person, how and when, the state of repayment, and what is still owed with its due date. */
@Composable
private fun LoanRow(loan: Loan, today: String, onClick: () -> Unit) {
    val c = KoshaTheme.colors
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).heightIn(min = 72.dp).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(c.soft), contentAlignment = Alignment.Center) { PhIcon("hand-coins", size = 18.dp) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            KText(loan.person, style = KoshaTheme.type.head, maxLines = 1)
            KText(stringResource(if (loan.direction == "lent") R.string.loans_lent else R.string.loans_borrowed) + " · " + loanDay(loan.day), style = KoshaTheme.type.xs, color = c.ink3, maxLines = 1)
            if (loan.purpose.isNotBlank()) KText(loan.purpose, style = KoshaTheme.type.xs, color = c.ink3, maxLines = 1)
            LoanState(loan)
            LoanDueText(loan, today)
        }
        MoneyText(loan.remaining, loan.currency)
        PhIcon("caret-right", size = 16.dp, tint = c.ink3)
    }
}

@Composable
private fun LoanState(loan: Loan) {
    val paid = loan.remaining == 0L
    KText(
        stringResource(if (paid) R.string.loans_paid else if (loan.repaid > 0) R.string.loans_partial else R.string.loans_unpaid),
        style = KoshaTheme.type.xs,
        color = if (paid) KoshaTheme.colors.pos else KoshaTheme.colors.ink3,
    )
}

@Composable
fun LoanDetailScreen(id: String) {
    val nav = LocalNav.current
    val c = KoshaTheme.colors
    val loaded = rememberLoad<LoanDetail>("loan/$id") { get("/api/v1/loans/$id") }
    val detail = loaded.value
    val writer = rememberWriter()
    var deleting by remember { mutableStateOf<LoanPayment?>(null) }
    var unlink by remember { mutableStateOf<LoanPayment?>(null) }
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    var remove by rememberSaveable { mutableStateOf(false) }
    val m = meta()
    val today = today().toString()
    val writable = canWrite()
    val loan = detail?.loan
    Page(
        loan?.person ?: stringResource(R.string.loans_title),
        onBack = { nav.popBackStack() },
        subtitle = loan?.let { it.purpose.ifBlank { stringResource(if (it.direction == "lent") R.string.loans_lent else R.string.loans_borrowed) } },
        actions = { if (loan != null && writable) Button(stringResource(R.string.common_edit), { nav.navigate(Route.LoanForm(id)) }, style = ButtonStyle.Secondary, size = ButtonSize.Small) },
    ) {
        if (loaded.offline) KText(stringResource(R.string.common_offline_body), style = KoshaTheme.type.small, color = c.ink3)
        loaded.error?.let { KText(it, style = KoshaTheme.type.small, color = c.bad) }
        if (detail == null || loan == null) { LoadingNote(); return@Page }
        Card(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    KText(stringResource(if (loan.direction == "lent") R.string.loans_owed_to_you else R.string.loans_you_owe), Modifier.weight(1f), style = KoshaTheme.type.small, color = c.ink3)
                    LoanState(loan)
                }
                HeroMoney(loan.remaining, loan.currency)
                val share = if (loan.principal > 0) (loan.repaid.toFloat() / loan.principal).coerceIn(0f, 1f) else 0f
                Box(
                    Modifier.padding(top = 10.dp).fillMaxWidth().height(6.dp).clip(CircleShape).background(c.soft)
                        .semantics { progressBarRangeInfo = ProgressBarRangeInfo(share, 0f..1f) },
                ) { Box(Modifier.fillMaxWidth(share).height(6.dp).clip(CircleShape).background(c.bar)) }
                if (loan.dueDay?.let { it < today } == true) LoanDueText(loan, today)
            }
            Column(Modifier.padding(top = 12.dp)) {
                Fact(stringResource(if (loan.opening) R.string.loans_starting else R.string.loans_principal)) { MoneyText(loan.principal, loan.currency) }
                Fact(stringResource(R.string.loans_repaid)) { MoneyText(loan.repaid, loan.currency) }
                Fact(stringResource(R.string.tx_date)) { KText(loanDay(loan.day)) }
                Fact(stringResource(R.string.loans_due).substringBefore(" · ")) { KText(loan.dueDay?.let { loanDay(it) } ?: stringResource(R.string.loans_no_due)) }
            }
            if (loan.note.isNotBlank()) KText(loan.note, Modifier.padding(top = 8.dp), style = KoshaTheme.type.small, color = c.ink2)
            if (loan.remaining > 0 && writable) {
                Button(stringResource(if (loan.direction == "lent") R.string.loans_received else R.string.loans_made), { nav.navigate(Route.LoanForm(id, true)) }, Modifier.fillMaxWidth().padding(top = 16.dp))
            }
        }
        Caption(stringResource(R.string.loans_history), Modifier.padding(start = 2.dp, top = 4.dp))
        Card(Modifier.fillMaxWidth(), tight = true) {
            if (loan.opening) KText(stringResource(R.string.loans_tracked_since, loanDay(loan.day)), Modifier.padding(16.dp), style = KoshaTheme.type.xs, color = c.ink3)
            detail.payments.sortedBy { it.day }.forEachIndexed { i, payment ->
                if (i > 0 || loan.opening) Hairline()
                PaymentRow(
                    payment, loan, writable,
                    canUnlink = payment.kind != "funding" || detail.payments.none { it.kind == "repayment" },
                    onDelete = { deleting = payment },
                    onUnlink = { unlink = payment; category = null },
                )
            }
        }
        if (loan.opening && detail.payments.isEmpty() && writable) Button(stringResource(R.string.loans_remove), { remove = true }, Modifier.fillMaxWidth(), style = ButtonStyle.Danger)
        HintLine("info", stringResource(R.string.loans_neutral))
    }
    deleting?.let { payment ->
        ConfirmSheet(stringResource(R.string.common_delete), stringResource(if (payment.reconciled) R.string.tx_delete_reconciled else R.string.tx_delete_transaction_title) + "\n" + dayText(payment.day, "dMMMyyyy") + " · " + payment.accountName + " · " + money(payment.amount, payment.currency), stringResource(R.string.common_delete), {
            writer.launch {
                send("POST", "/api/v1/transactions/delete", buildJsonObject { put("ids", buildJsonArray { add(payment.transactionId) }) })
                deleting = null
            }
        }, { deleting = null })
    }
    unlink?.let { payment ->
        val categories = m?.categories.orEmpty().filter { !it.isArchived && it.parentId == null && !Categories.isSystem(it.id) && it.isIncome == (payment.amount > 0) }
        Sheet(stringResource(R.string.loans_unlink), { unlink = null }) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                KText(stringResource(R.string.loans_unlink_warning))
                ListPicker(categories.map { ListOption(it.id, it.name) }, category, { category = it.value }, Modifier.heightIn(max = 240.dp))
                Button(stringResource(R.string.loans_unlink), {
                    writer.launch {
                        send("POST", "/api/v1/loan-payments/${payment.transactionId}/unlink", buildJsonObject { put("transactionId", payment.transactionId); put("categoryId", category) })
                        unlink = null
                        if (payment.kind == "funding") nav.popBackStack()
                    }
                }, enabled = category != null)
            }
        }
    }
    if (remove) ConfirmSheet(stringResource(R.string.loans_remove), stringResource(R.string.loans_delete_warning), stringResource(R.string.common_delete), {
        writer.launch { send("DELETE", "/api/v1/loans/$id"); remove = false; nav.popBackStack() }
    }, { remove = false })
}

/** Saved primitive draft fields survive recreation; a submitted payload is frozen until its retry succeeds. */
@Composable
fun LoanFormScreen(id: String?, repayment: Boolean) {
    val nav = LocalNav.current
    val kosha = rememberKosha()
    val m = meta() ?: return
    val loaded = if (id != null) rememberLoad<LoanDetail>("loan/$id") { get("/api/v1/loans/$id") } else null
    val loan = loaded?.value?.loan
    if (id != null && loan == null) {
        FormPage(stringResource(R.string.loans_title), { nav.popBackStack() }, {}) { KText(loaded?.error ?: stringResource(R.string.common_offline_body)) }
        return
    }
    val editing = id != null && !repayment
    val baseline by rememberSaveable(id) { mutableStateOf(loan?.let { KoshaJson.encodeToString(Loan.serializer(), it) }) }
    val original = remember(baseline) { baseline?.let { KoshaJson.decodeFromString<Loan>(it) } }
    val toasts = LocalToasts.current
    val scope = rememberCoroutineScope()
    val online by kosha.online.collectAsStateWithLifecycle()
    var requestId by rememberSaveable { mutableStateOf(UUID.randomUUID().toString()) }
    var frozen by rememberSaveable { mutableStateOf<String?>(null) }
    var person by rememberSaveable { mutableStateOf(loan?.person ?: "") }
    var purpose by rememberSaveable { mutableStateOf(loan?.purpose ?: "") }
    var note by rememberSaveable { mutableStateOf(if (editing) loan?.note ?: "" else "") }
    var direction by rememberSaveable { mutableStateOf(loan?.direction ?: "lent") }
    var source by rememberSaveable { mutableStateOf("new") }
    var accountId by rememberSaveable { mutableStateOf(m.wallets.firstOrNull { !it.isArchived }?.id) }
    var currency by rememberSaveable { mutableStateOf(loan?.currency ?: m.wallets.firstOrNull { it.id == accountId }?.currency ?: m.primary) }
    var day by rememberSaveable { mutableStateOf(Dates.today(m.tz).toString()) }
    var due by rememberSaveable { mutableStateOf(loan?.dueDay) }
    var amount by rememberSaveable { mutableStateOf<Long?>(null) }
    var bankAmount by rememberSaveable { mutableStateOf<Long?>(null) }
    var transactionId by rememberSaveable { mutableStateOf<String?>(null) }
    var picker by rememberSaveable { mutableStateOf<String?>(null) }
    var search by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val account = m.wallets.firstOrNull { it.id == accountId }
    val cross = repayment && account?.currency != currency
    val locked = frozen != null
    val title = stringResource(if (editing) R.string.loans_terms else if (repayment) R.string.loans_repayment else R.string.loans_new)
    val offline = stringResource(R.string.loans_offline)
    val invalidDate = stringResource(R.string.loans_invalid_date)
    val overpaid = stringResource(R.string.loans_overpaid)
    val invalid = stringResource(R.string.loans_invalid_payment)
    val saved = stringResource(R.string.loans_saved)
    val valid = person.isNotBlank() && person.trim().length <= 200 && purpose.trim().length <= 200 && note.trim().length <= 2000 &&
        (editing || (amount != null && amount!! > 0 && (!repayment || validRepayment(amount, loan!!.remaining)) &&
            (source == "opening" || (source == "existing" && transactionId != null) || (source == "new" && account != null && !account.isArchived && (!cross || (bankAmount ?: 0) > 0)))))

    fun save() {
        if (m.demo) return
        if (!online) { error = offline; return }
        if (frozen == null && ((!editing && (day > Dates.today(m.tz).toString() || (repayment && day < loan!!.day))) || (due != null && due!! < (loan?.day ?: day)))) { error = invalidDate; return }
        if (frozen == null && repayment && !validRepayment(amount, loan!!.remaining)) { error = overpaid; return }
        val body = frozen?.let { KoshaJson.parseToJsonElement(it).jsonObject } ?: buildJsonObject {
            put("id", if (editing) id else requestId)
            if (editing) {
                loanMetadataChanges(original!!, person, purpose, note, due).forEach { (key, value) -> put(key, value) }
            } else {
                put("amount", amount); put("day", day); put("note", note.trim())
                if (repayment) {
                    put("loanId", id)
                    if (source == "existing") put("transactionId", transactionId)
                    else { put("accountId", accountId); if (cross) put("accountAmount", bankAmount) }
                } else {
                    put("direction", direction); put("person", person.trim()); put("purpose", purpose.trim())
                    put("dueDay", due?.let(::JsonPrimitive) ?: JsonNull)
                    put("source", source); put("currency", currency)
                    if (source == "new") put("accountId", accountId)
                    if (source == "existing") put("transactionId", transactionId)
                }
            }
        }
        if (!editing) frozen = body.toString()
        busy = true
        error = null
        scope.launch {
            try {
                if (editing) kosha.api.send("PATCH", "/api/v1/loans/$id", body)
                else kosha.api.post<JsonObject, JsonObject>(if (repayment) "/api/v1/loans/$id/repayments" else "/api/v1/loans", body)
                kosha.changed()
                toasts.show(Toast(saved))
                nav.popBackStack()
                if (id == null) nav.navigate(Route.LoanDetail(requestId))
            } catch (e: java.io.IOException) {
                error = offline
            } catch (e: app.getkosha.network.ApiException) {
                error = e.message
                if (!e.retryable) { frozen = null; requestId = UUID.randomUUID().toString() }
            } catch (e: app.getkosha.network.SignedOutException) {
                error = e.message
            } catch (e: app.getkosha.network.RetryLaterException) {
                error = e.message
            } finally { busy = false }
        }
    }

    FormPage(title, { nav.popBackStack() }, {
        Button(stringResource(if (locked) R.string.common_try_again else R.string.common_save), { save() }, Modifier.fillMaxWidth(), enabled = !busy && canWrite() && (valid || locked))
    }) {
        val c = KoshaTheme.colors
        if (!online) HintLine("wifi-slash", offline)
        error?.let { KText(it, style = KoshaTheme.type.small, color = c.bad) }
        if (locked) {
            Card(Modifier.fillMaxWidth(), tight = true) {
                DetailRow(stringResource(R.string.loans_person)) { KText(person, maxLines = 1) }
                Hairline()
                DetailRow(stringResource(R.string.tx_amount)) { MoneyText(amount ?: 0, currency) }
                Hairline()
                DetailRow(stringResource(R.string.tx_date)) { KText(dayText(day, "dMMMyyyy")) }
                if (account != null) {
                    Hairline()
                    DetailRow(stringResource(R.string.tx_account)) { KText(account.name, maxLines = 1) }
                }
            }
            if (note.isNotBlank()) KText(note, style = KoshaTheme.type.small, color = c.ink2)
        } else {
            if (!repayment) {
                if (!editing) Segmented(listOf("lent" to stringResource(R.string.loans_lent), "borrowed" to stringResource(R.string.loans_borrowed)), direction, { direction = it; transactionId = null })
                Field(person, { person = it }, label = stringResource(R.string.loans_person))
                Field(purpose, { purpose = it }, label = stringResource(R.string.loans_purpose))
            }
            if (!editing) {
                val sources = listOf("new" to stringResource(R.string.loans_new_movement), "existing" to stringResource(R.string.loans_existing)) + if (repayment) emptyList() else listOf("opening" to stringResource(R.string.loans_opening))
                Card(Modifier.fillMaxWidth(), tight = true) {
                    sources.forEachIndexed { i, (value, label) ->
                        if (i > 0) Hairline()
                        Row(
                            Modifier.fillMaxWidth().selectable(value == source, role = Role.RadioButton) { source = value; transactionId = null }
                                .heightIn(min = KoshaTheme.dims.row).padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            KText(label, Modifier.weight(1f))
                            Radio(value == source)
                        }
                    }
                }
                HintLine("info", stringResource(when (source) { "opening" -> R.string.loans_opening_note; "existing" -> R.string.loans_link_note; else -> R.string.loans_neutral }))
                if (source == "existing") Button(stringResource(R.string.loans_choose_entry), { picker = "entry" }, Modifier.fillMaxWidth(), style = ButtonStyle.Secondary, icon = "link")
                val moneyIn = if (repayment) direction == "lent" else direction == "borrowed"
                val rows = buildList<@Composable () -> Unit> {
                    if (source == "existing" && transactionId != null) add {
                        DetailRow(stringResource(R.string.loans_account_amount)) {
                            KText(account?.name ?: "", Modifier.weight(1f), maxLines = 1)
                            MoneyText(bankAmount ?: 0, account?.currency ?: currency)
                        }
                    }
                    if (source == "new") add {
                        DetailRow(stringResource(if (moneyIn) R.string.loans_received_into else R.string.loans_paid_from), { picker = "account" }) {
                            KText(account?.name ?: stringResource(R.string.loans_choose_account), maxLines = 1)
                        }
                    }
                    if (!repayment && source == "opening") add {
                        DetailRow(stringResource(R.string.accounts_currency), { picker = "currency" }) { KText(currency.uppercase(), style = KoshaTheme.type.num) }
                    }
                    add {
                        val label = if (repayment) (if (cross) R.string.loans_applied else R.string.tx_amount) else if (source == "opening") R.string.loans_starting else R.string.loans_principal
                        DetailRow(stringResource(label), if (source == "existing" && !cross) null else ({ picker = "amount" })) { MoneyText(amount ?: 0, currency) }
                    }
                    if (repayment && source == "new" && cross) add {
                        DetailRow(stringResource(R.string.loans_account_amount), { picker = "bank" }) { MoneyText(bankAmount ?: 0, account?.currency ?: currency) }
                    }
                    add { DetailRow(stringResource(R.string.tx_date), if (source == "existing") null else ({ picker = "day" })) { KText(dayText(day, "dMMMyyyy")) } }
                    if (!repayment) add { DueRow(due) { picker = "due" } }
                }
                Card(Modifier.fillMaxWidth(), tight = true) {
                    rows.forEachIndexed { i, row -> if (i > 0) Hairline(); row() }
                }
                if (repayment) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        KText(stringResource(R.string.loans_after, money(loan!!.remaining - (amount ?: 0), currency)), Modifier.weight(1f), style = KoshaTheme.type.small, color = c.ink3)
                        Button(stringResource(R.string.loans_full), { amount = loan!!.remaining }, style = ButtonStyle.Secondary, size = ButtonSize.Small, enabled = source != "existing" || cross)
                    }
                    if (amount != null && !validRepayment(amount, loan!!.remaining)) KText(overpaid, style = KoshaTheme.type.small, color = c.bad)
                }
                if (!repayment && source == "new" && amount != null) {
                    HintLine("arrows-left-right", stringResource(if (direction == "lent") R.string.loans_lent_effect else R.string.loans_borrowed_effect, money(amount!!, currency), account?.name ?: "", person, money(amount!!, currency)))
                }
            } else if (!repayment) {
                Card(Modifier.fillMaxWidth(), tight = true) { DueRow(due) { picker = "due" } }
            }
            Field(note, { note = it }, label = stringResource(R.string.tx_note), singleLine = false)
        }
    }
    when (picker) {
        "amount", "bank" -> KeypadSheet(stringResource(if (picker == "bank") R.string.loans_account_amount else R.string.loans_principal), if (picker == "bank") bankAmount else amount, if (picker == "bank") account?.currency ?: currency else currency, { if (picker == "bank") bankAmount = it else amount = it; picker = null }, { picker = null })
        "day", "due" -> DateSheet(stringResource(if (picker == "due") R.string.loans_due else R.string.tx_date), (if (picker == "due") due else day)?.let(LocalDate::parse), { if (picker == "due") due = it.toString() else day = it.toString(); picker = null }, { picker = null }, clearLabel = if (picker == "due") stringResource(R.string.loans_no_due) else null, onClear = if (picker == "due") ({ due = null; picker = null }) else null)
        "account" -> Sheet(stringResource(R.string.tx_account), { picker = null }) { ListPicker( m.wallets.filter { !it.isArchived }.map { ListOption(it.id, it.name, it.currency.uppercase()) }, accountId, { option -> accountId = option.value; if (!repayment) currency = m.wallets.first { it.id == option.value }.currency; bankAmount = null; picker = null }) }
        "currency" -> Sheet(stringResource(R.string.accounts_currency), { picker = null }) { ListPicker( (m.rates.keys + currency).distinct().sorted().map { ListOption(it, it.uppercase()) }, currency, { currency = it.value; picker = null }) }
        "entry" -> {
            val entries = rememberLoad<TransactionList>("loan-entries/$search") { get("/api/v1/transactions", mapOf("query" to search.take(40), "limit" to 100)) }
            Sheet(stringResource(R.string.loans_choose_entry), { picker = null }) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Field(search, { search = it }, label = stringResource(R.string.loans_find))
                    entries.error?.let { KText(it) }
                    val eligible = entries.value?.transactions.orEmpty().filter { it.canLinkLoan(if (repayment) direction == "lent" else direction == "borrowed") && (!repayment || it.day >= loan!!.day) }
                    androidx.compose.foundation.lazy.LazyColumn(Modifier.heightIn(max = 320.dp)) {
                        items(eligible.size, key = { eligible[it].id }) { index ->
                            val tx = eligible[index]
                            ListRow(onClick = {
                                transactionId = tx.id; accountId = tx.accountId; day = tx.day
                                bankAmount = abs(tx.amount)
                                if (!repayment) currency = tx.currency
                                amount = if (tx.currency == currency) abs(tx.amount) else null
                                picker = null
                            }) { Column { KText(tx.payee + " · " + tx.accountName); KText(dayText(tx.day, "dMMMyyyy") + " · " + money(tx.amount, tx.currency)) } }
                        }
                    }
                    if (entries.value == null) KText(entries.error ?: invalid)
                }
            }
        }
    }
}

@Composable
private fun Fact(label: String, value: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        KText(label, Modifier.weight(1f), style = KoshaTheme.type.small, color = KoshaTheme.colors.ink3)
        value()
    }
}

/** One movement in a loan's history: in or out, when and where, and what it did to the principal. */
@Composable
private fun PaymentRow(payment: LoanPayment, loan: Loan, writable: Boolean, canUnlink: Boolean, onDelete: () -> Unit, onUnlink: () -> Unit) {
    val c = KoshaTheme.colors
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val incoming = payment.amount > 0
            PhIcon(if (incoming) "arrow-down-left" else "arrow-up-right", Modifier.padding(top = 2.dp), size = 18.dp, tint = if (incoming) c.pos else c.ink3)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                KText(stringResource(if (payment.kind == "funding") R.string.loans_original else R.string.loans_repayment))
                KText(loanDay(payment.day) + " · " + payment.accountName, style = KoshaTheme.type.xs, color = c.ink3)
                if (payment.note.isNotBlank()) KText(payment.note, style = KoshaTheme.type.xs, color = c.ink3)
                if (payment.currency != loan.currency) KText(stringResource(R.string.loans_applied) + " · " + money(payment.principal, loan.currency), style = KoshaTheme.type.xs, color = c.ink3)
            }
            MoneyText(payment.amount, payment.currency, sign = Sign.Always, color = if (incoming) c.pos else c.ink)
        }
        if (writable && (canUnlink || payment.kind == "repayment")) {
            Row(Modifier.padding(start = 30.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (canUnlink) Button(stringResource(R.string.loans_unlink), onUnlink, style = ButtonStyle.Soft, size = ButtonSize.Small)
                if (payment.kind == "repayment") Button(stringResource(R.string.common_delete), onDelete, style = ButtonStyle.Danger, size = ButtonSize.Small)
            }
        }
    }
}

@Composable
private fun DueRow(due: String?, onClick: () -> Unit) {
    DetailRow(stringResource(R.string.loans_due).substringBefore(" · "), onClick) {
        KText(due?.let { dayText(it, "dMMMyyyy") } ?: stringResource(R.string.loans_no_due), color = if (due == null) KoshaTheme.colors.ink3 else KoshaTheme.colors.ink)
    }
}
