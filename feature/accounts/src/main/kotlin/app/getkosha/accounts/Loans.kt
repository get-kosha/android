package app.getkosha.accounts

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.getkosha.design.*
import app.getkosha.design.R
import app.getkosha.model.*
import app.getkosha.recurring.*
import app.getkosha.ui.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*
import java.time.LocalDate
import java.util.UUID
import kotlin.math.abs

@Composable
fun LoansScreen() {
    val nav = LocalNav.current
    val loaded = rememberLoad<Loans>("loans") { get("/api/v1/loans") }
    var direction by rememberSaveable { mutableStateOf("all") }
    var status by rememberSaveable { mutableStateOf("open") }
    var query by rememberSaveable { mutableStateOf("") }
    val today = today().toString()
    FormPage(stringResource(R.string.loans_title), { nav.popBackStack() }, {
        Button(stringResource(R.string.loans_new), { nav.navigate(Route.LoanForm()) }, Modifier.fillMaxWidth(), enabled = canWrite())
    }) {
        AccountLoansSwitch(true)
        LoanSummary(showLink = false)
        Segmented(listOf("all" to stringResource(R.string.loans_all), "lent" to stringResource(R.string.loans_owed_to_you), "borrowed" to stringResource(R.string.loans_you_owe)), direction, { direction = it })
        Segmented(listOf("open" to stringResource(R.string.loans_open), "paid" to stringResource(R.string.loans_paid), "all" to stringResource(R.string.loans_all)), status, { status = it })
        Field(query, { query = it }, label = stringResource(R.string.common_search))
        if (loaded.offline) KText(stringResource(R.string.common_offline_body))
        loaded.error?.let { KText(it, color = KoshaTheme.colors.bad) }
        val loans = loaded.value?.loans
        if (loans == null) LoadingNote() else {
            val visible = loans.filter { (direction == "all" || it.direction == direction) && (status == "all" || (it.remaining == 0L) == (status == "paid")) && (it.person + " " + it.purpose).contains(query, true) }
                .sortedWith(compareBy<Loan> { if (it.remaining > 0 && it.dueDay?.let { due -> due < today } == true) 0 else 1 }.thenBy { it.dueDay ?: "9999" }.thenByDescending { it.day })
            if (visible.isEmpty()) KText(stringResource(R.string.loans_empty))
            visible.forEach { loan ->
                Card(Modifier.fillMaxWidth(), tight = true) {
                    ListRow(onClick = { nav.navigate(Route.LoanDetail(loan.id)) }) {
                        Column(Modifier.weight(1f)) {
                            KText(loan.person)
                            KText(loan.purpose, style = KoshaTheme.type.small)
                            KText(stringResource(if (loan.direction == "lent") R.string.loans_lent else R.string.loans_borrowed), style = KoshaTheme.type.small)
                            KText(dayText(loan.day, "dMMMyyyy"), style = KoshaTheme.type.small)
                            LoanDue(loan)
                        }
                        MoneyText(loan.remaining, loan.currency)
                    }
                }
            }
        }
    }
}

@Composable
private fun LoanDue(loan: Loan) {
    KText(stringResource(if (loan.remaining == 0L) R.string.loans_paid else if (loan.repaid > 0) R.string.loans_partial else R.string.loans_unpaid))
    loan.dueDay?.let { due -> KText(stringResource(if (loan.remaining > 0 && due < today().toString()) R.string.loans_overdue else R.string.loans_due_on, dayText(due, "dMMMyyyy"))) }
}

@Composable
fun LoanDetailScreen(id: String) {
    val nav = LocalNav.current
    val loaded = rememberLoad<LoanDetail>("loan/$id") { get("/api/v1/loans/$id") }
    val detail = loaded.value
    val writer = rememberWriter()
    var deleting by remember { mutableStateOf<LoanPayment?>(null) }
    var unlink by remember { mutableStateOf<LoanPayment?>(null) }
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    var remove by rememberSaveable { mutableStateOf(false) }
    val m = meta()
    FormPage(detail?.loan?.person ?: stringResource(R.string.loans_title), { nav.popBackStack() }, {
        if (detail != null && detail.loan.remaining > 0) Button(stringResource(if (detail.loan.direction == "lent") R.string.loans_received else R.string.loans_made), { nav.navigate(Route.LoanForm(id, true)) }, Modifier.fillMaxWidth(), enabled = canWrite())
    }) {
        if (loaded.offline) KText(stringResource(R.string.common_offline_body))
        loaded.error?.let { KText(it, color = KoshaTheme.colors.bad) }
        if (detail == null) LoadingNote() else {
            val loan = detail.loan
            KText(stringResource(if (loan.direction == "lent") R.string.loans_lent else R.string.loans_borrowed))
            KText(loan.purpose)
            LoanDue(loan)
            listOf((if (loan.opening) R.string.loans_starting else R.string.loans_principal) to loan.principal, R.string.loans_repaid to loan.repaid, R.string.loans_remaining to loan.remaining).forEach { (label, amount) ->
                DetailRow(stringResource(label)) { MoneyText(amount, loan.currency) }
            }
            KText(stringResource(R.string.loans_tracked_since, dayText(loan.day, "dMMMyyyy")))
            KText(loan.note)
            Button(stringResource(R.string.loans_terms), { nav.navigate(Route.LoanForm(id)) }, enabled = canWrite(), style = ButtonStyle.Secondary)
            KText(stringResource(R.string.loans_history))
            detail.payments.sortedBy { it.day }.forEach { payment ->
                Card(Modifier.fillMaxWidth()) {
                    KText(stringResource(if (payment.kind == "funding") R.string.loans_original else R.string.loans_repayment))
                    KText(dayText(payment.day, "dMMMyyyy") + " · " + payment.accountName)
                    MoneyText(payment.amount, payment.currency, sign = Sign.Always)
                    KText(stringResource(R.string.loans_applied) + " · " + money(payment.principal, loan.currency))
                    KText(payment.note)
                    if (payment.kind == "repayment") Button(stringResource(R.string.common_delete), { deleting = payment }, enabled = canWrite(), style = ButtonStyle.Danger)
                    if (payment.reconciled) KText(stringResource(R.string.tx_delete_reconciled))
                    Button(stringResource(R.string.loans_unlink), { unlink = payment; category = null }, enabled = canWrite() && (payment.kind != "funding" || detail.payments.none { it.kind == "repayment" }), style = ButtonStyle.Secondary)
                }
            }
            if (loan.opening && detail.payments.isEmpty()) Button(stringResource(R.string.loans_remove), { remove = true }, enabled = canWrite(), style = ButtonStyle.Danger)
        }
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
        if (!online) KText(offline)
        error?.let { KText(it, color = KoshaTheme.colors.bad) }
        if (locked) {
            KText(stringResource(R.string.loans_offline))
            KText(person)
            MoneyText(amount ?: 0, currency)
            KText(dayText(day, "dMMMyyyy"))
            KText(account?.name ?: "")
            KText(note)
        }
        if (!locked) {
            if (!repayment) {
                if (!editing) Segmented(listOf("lent" to stringResource(R.string.loans_lent), "borrowed" to stringResource(R.string.loans_borrowed)), direction, { direction = it; transactionId = null })
                Field(person, { person = it }, label = stringResource(R.string.loans_person))
                Field(purpose, { purpose = it }, label = stringResource(R.string.loans_purpose))
            }
            if (!editing) {
                val sources = listOf("new" to stringResource(R.string.loans_new_movement), "existing" to stringResource(R.string.loans_existing)) + if (repayment) emptyList() else listOf("opening" to stringResource(R.string.loans_opening))
                sources.forEach { (value, label) -> ListRow(onClick = { source = value; transactionId = null }, selected = value == source) { KText(label) } }
                KText(stringResource(when (source) { "opening" -> R.string.loans_opening_note; "existing" -> R.string.loans_link_note; else -> R.string.loans_neutral }))
                if (source == "existing") Button(stringResource(R.string.loans_choose_entry), { picker = "entry" }, style = ButtonStyle.Secondary)
                if (source == "existing" && transactionId != null) DetailRow(stringResource(R.string.loans_account_amount)) {
                    Column { KText(account?.name ?: ""); MoneyText(bankAmount ?: 0, account?.currency ?: currency) }
                }
                if (source == "new") DetailRow(stringResource(R.string.tx_account), { picker = "account" }) { KText(account?.name ?: stringResource(R.string.loans_choose_account)) }
                if (!repayment && source == "opening") DetailRow(stringResource(R.string.accounts_currency), { picker = "currency" }) { KText(currency.uppercase()) }
                DetailRow(stringResource(if (repayment) R.string.loans_applied else R.string.loans_principal), if (source == "existing" && !cross) null else ({ picker = "amount" })) { MoneyText(amount ?: 0, currency) }
                if (repayment && source == "new" && cross) DetailRow(stringResource(R.string.loans_account_amount), { picker = "bank" }) { MoneyText(bankAmount ?: 0, account?.currency ?: currency) }
                if (repayment) {
                    Button(stringResource(R.string.loans_full), { amount = loan!!.remaining }, style = ButtonStyle.Secondary, enabled = source != "existing" || cross)
                    KText(stringResource(R.string.loans_after, money(loan!!.remaining - (amount ?: 0), currency)))
                    if (amount != null && !validRepayment(amount, loan.remaining)) KText(overpaid, color = KoshaTheme.colors.bad)
                }
                DetailRow(stringResource(R.string.tx_date), if (source == "existing") null else ({ picker = "day" })) { KText(dayText(day, "dMMMyyyy")) }
                if (!repayment && source == "new" && amount != null) KText(stringResource(if (direction == "lent") R.string.loans_lent_effect else R.string.loans_borrowed_effect, money(amount!!, currency), account?.name ?: "", person, money(amount!!, currency)))
            }
            if (!repayment) DetailRow(stringResource(R.string.loans_due), { picker = "due" }) { KText(due?.let { dayText(it, "dMMMyyyy") } ?: stringResource(R.string.loans_no_due)) }
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
