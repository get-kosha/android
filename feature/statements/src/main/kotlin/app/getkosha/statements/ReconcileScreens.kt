package app.getkosha.statements

import android.content.Context
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.getkosha.data.Kosha
import app.getkosha.design.AccountDot
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
import app.getkosha.design.ListRow
import app.getkosha.design.PhIcon
import app.getkosha.design.R
import app.getkosha.design.Radio
import app.getkosha.design.Sheet
import app.getkosha.ui.Skeleton
import app.getkosha.design.Sparkle
import app.getkosha.design.Tick
import app.getkosha.design.Toast
import app.getkosha.design.ToastState
import app.getkosha.design.categoryIconName
import app.getkosha.model.AccountType
import app.getkosha.model.Categories
import app.getkosha.model.Dates
import app.getkosha.model.Meta
import app.getkosha.model.Sign
import app.getkosha.model.guessCategory
import app.getkosha.network.ApiException
import app.getkosha.ui.LocalNav
import app.getkosha.ui.LocalToasts
import app.getkosha.ui.MoneyText
import app.getkosha.ui.Page
import app.getkosha.ui.Route
import app.getkosha.ui.canWrite
import app.getkosha.ui.meta
import app.getkosha.ui.rememberKosha
import app.getkosha.ui.write
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.UUID

// Reconcile (06-accounts-ReconcileStart, ReconcileTick, ReconcileDone): check an account against a bank statement,
// as the web's reconcile.tsx does. The matching, the ticks and the hints run here with the web's own rules
// (Statement.kt); reading the statement file happens on the server.

/** Investments are valued, not reconciled: a broker statement shows market value, which Kosha keeps as gains. */
private fun reconcilable(type: AccountType) = type != AccountType.Investment

@Composable
internal fun ReconcileScreen(accountId: String?) {
    val model: ReconcileModel = viewModel { ReconcileModel(accountId) }
    BackHandler(model.step == ReconcileModel.Step.Tick) { model.step = ReconcileModel.Step.Start }
    when (model.step) {
        ReconcileModel.Step.Start -> ReconcileStart(model)
        ReconcileModel.Step.Tick -> ReconcileTick(model)
        ReconcileModel.Step.Done -> ReconcileDone(model)
    }
}

// ---------- reading a statement ----------

/** Opens the system picker for one statement and reads it into the reconciliation, then shows the ticks. */
@Composable
private fun rememberReader(model: ReconcileModel): () -> Unit {
    val context = LocalContext.current
    val kosha = rememberKosha()
    val toasts = LocalToasts.current
    val scope = rememberCoroutineScope()
    val m = meta()
    val pdf = m?.ai?.statements == true
    val writes = canWrite()
    val demo = stringResource(R.string.common_demo_read_only)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null && m != null) scope.launch { readStatement(context, kosha, model, m, uri, toasts) }
    }
    return {
        // Reading a file is a request that only accounts which can change things may make.
        if (!writes) toasts.show(Toast(demo)) else launcher.launch(statementTypes(pdf))
    }
}

private suspend fun readStatement(context: Context, kosha: Kosha, model: ReconcileModel, m: Meta, uri: Uri, toasts: ToastState) {
    val d = model.data?.value ?: return
    val acc = d.account
    val res = context.resources
    model.reading = fileName(context, uri)
    try {
        val file = readFile(context, uri)
        if (file.pdf && !m.ai.statements) {
            toasts.show(Toast(res.getString(R.string.reconcile_pdf_unsupported)))
            return
        }
        val r = kosha.api.read(file, acc.credit, walletId = acc.id)
        when (r.format) {
            "pdf" -> {
                if (r.currency != null && r.currency != acc.currency) {
                    toasts.show(Toast(res.getString(R.string.reconcile_currency_mismatch, r.currency.uppercase(), acc.name, acc.currency.uppercase())))
                }
                model.statement = Statement(file.name, "pdf", r.lines, byAi = true, opening = r.opening, off = r.off)
                r.to?.let { model.day = it }
                r.closing?.let { model.balance = it }
            }
            "ofx", "qfx" -> {
                if (r.lines.isEmpty()) {
                    toasts.show(Toast(res.getString(R.string.reconcile_ofx_empty)))
                    return
                }
                model.statement = Statement(file.name, "ofx", r.lines, r.withdrawn)
                r.closingDay?.let { model.day = it }
                // Cards report what's owed as a negative balance.
                r.closing?.let { model.balance = if (acc.credit) -it else it }
            }
            else -> {
                val remembered = r.signature != null && r.signature in d.layouts
                var read = r
                var byAi = false
                if (!remembered && !r.confident && m.ai.statements) {
                    kosha.api.aiLayout(r)?.let { l ->
                        read = kosha.api.read(file, acc.credit, l, acc.id)
                        byAi = true
                    }
                }
                openCsv(
                    model,
                    Statement(file.name, "csv", read.lines, file = file, rows = read.rows, layout = read.layout, signature = read.signature,
                        remembered = remembered, byAi = byAi, confident = read.confident || byAi),
                    acc.credit,
                )
            }
        }
        model.step = ReconcileModel.Step.Tick
    } catch (e: IOException) {
        toasts.show(Toast(res.getString(R.string.auth_error_offline)))
    } catch (e: ApiException) {
        toasts.show(Toast(e.message ?: res.getString(R.string.reconcile_read_failed)))
    } catch (e: Exception) {
        toasts.show(Toast(res.getString(R.string.reconcile_read_failed)))
    } finally {
        model.reading = null
    }
}

/** A CSV's latest line sets the statement day, and its running balance the closing and opening balances. */
private fun openCsv(model: ReconcileModel, s: Statement, credit: Boolean) {
    val b = csvBalances(s.lines, credit)
    model.statement = s.copy(opening = b.opening)
    if (s.lines.isEmpty()) return
    b.closingDay?.let { model.day = it }
    b.closing?.let { model.balance = it }
}

// ---------- ReconcileStart ----------

@Composable
private fun ReconcileStart(model: ReconcileModel) {
    val nav = LocalNav.current
    val kosha = rememberKosha()
    val toasts = LocalToasts.current
    val scope = rememberCoroutineScope()
    val m = meta() ?: return
    val c = KoshaTheme.colors
    val money = moneyWords()
    val wallets = m.wallets.filter { !it.isArchived && reconcilable(it.type) }
    val read = rememberReader(model)
    val acc = model.data?.value?.account
    val today = Dates.text(Dates.today(kosha.tz))
    var sheet by rememberSaveable { mutableStateOf<String?>(null) }
    val offline = stringResource(R.string.auth_error_offline)

    Page(stringResource(R.string.reconcile_title), onBack = { nav.popBackStack() }, subtitle = stringResource(R.string.reconcile_picker_sub)) {
        if (wallets.isEmpty()) {
            Card { KText(stringResource(R.string.reconcile_picker_empty), style = KoshaTheme.type.small, color = c.ink2) }
            return@Page
        }
        Card(tight = true) {
            wallets.forEachIndexed { i, w ->
                if (i > 0) Hairline()
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 60.dp)
                        .selectable(w.id == model.accountId, role = Role.RadioButton) { model.select(w.id) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    AccountDot(c.account(m.wallets.indexOf(w)), size = 10.dp)
                    Column(Modifier.weight(1f)) {
                        KText(w.name)
                        KText(
                            w.reconciledDay?.let { stringResource(R.string.reconcile_reconciled_to, shortDay(it)) } ?: stringResource(R.string.reconcile_never_reconciled),
                            style = KoshaTheme.type.small,
                            color = c.ink3,
                        )
                    }
                    Radio(w.id == model.accountId)
                }
            }
        }

        val d = model.data?.value
        if (d == null) {
            if (model.data?.offline == true) Note(stringResource(R.string.common_offline_body), icon = "cloud-slash", tone = c.ink2)
            else Skeleton(Modifier.fillMaxWidth(), height = 120.dp)
            return@Page
        }
        val acc = d.account
        val credit = acc.credit
        val limit = acc.creditLimit ?: 0
        val fromKosha = { v: Long -> if (credit) limit - v else v }
        // The quick check: the bank shows the same balance as Kosha, so everything up to today is reconciled.
        val upToToday = d.open.filter { it.paid == 1 && it.day <= today }
        val quick = d.reconciled + upToToday.sumOf { it.amount }

        Card {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                KText(stringResource(R.string.app_rcn_quick_title), style = KoshaTheme.type.head)
                KText(
                    stringResource(if (credit) R.string.reconcile_quick_owed else R.string.reconcile_quick_balance, money(fromKosha(quick), acc.currency)),
                    style = KoshaTheme.type.small,
                    color = c.ink2,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        stringResource(R.string.reconcile_same_as_bank),
                        onClick = {
                            scope.launch {
                                model.busy = "finish"
                                kosha.write(toasts, offline) {
                                    kosha.api.post<FinishBody, Finished>("/api/app/reconcile/finish", FinishBody(acc.id, today, quick, upToToday.map { it.id }, false))
                                }?.let {
                                    model.done = Done(it.id, upToToday.size, it.adjustment, today)
                                    model.step = ReconcileModel.Step.Done
                                }
                                model.busy = null
                            }
                        },
                        Modifier.weight(1f),
                        style = ButtonStyle.Secondary,
                        size = ButtonSize.Medium,
                        enabled = model.busy == null && canWrite(),
                    )
                    Button(
                        stringResource(R.string.accounts_fix_balance),
                        onClick = { nav.navigate(Route.FixBalance(acc.id)) },
                        style = ButtonStyle.Secondary,
                        size = ButtonSize.Medium,
                    )
                }
            }
        }

        Card {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                KText(stringResource(R.string.app_rcn_statement_title), style = KoshaTheme.type.head)
                StatementFields(model, acc, onDay = { sheet = "day" }, onBalance = { sheet = "balance" }, placeholder = fromKosha(quick))
                Button(
                    stringResource(R.string.app_rcn_tick_off),
                    onClick = { model.step = ReconcileModel.Step.Tick },
                    Modifier.fillMaxWidth(),
                    size = ButtonSize.Medium,
                )
                Button(
                    model.reading?.let { stringResource(R.string.reconcile_reading, it) } ?: stringResource(R.string.app_rcn_import_instead),
                    onClick = read,
                    Modifier.fillMaxWidth(),
                    style = ButtonStyle.Soft,
                    size = ButtonSize.Medium,
                    icon = "file-arrow-up",
                    enabled = model.reading == null,
                )
                KText(
                    stringResource(if (m.ai.statements) R.string.app_rcn_formats_pdf else R.string.app_rcn_formats),
                    style = KoshaTheme.type.xs,
                    color = c.ink3,
                )
            }
        }
    }
    StatementSheets(model, acc, sheet) { sheet = null }
}

/** The statement day and closing balance, side by side. */
@Composable
private fun StatementFields(model: ReconcileModel, acc: ReconcileData.Acc, onDay: () -> Unit, onBalance: () -> Unit, placeholder: Long) {
    val c = KoshaTheme.colors
    val money = moneyWords()
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            KText(stringResource(R.string.reconcile_statement_date), style = KoshaTheme.type.small, color = c.ink3, maxLines = 1)
            PickField(shortDay(model.day), onDay, Modifier.fillMaxWidth(), icon = "calendar-blank")
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            KText(balanceLabel(acc), style = KoshaTheme.type.small, color = c.ink3, maxLines = 1)
            val b = model.balance
            PickField(money(b ?: placeholder, acc.currency), onBalance, Modifier.fillMaxWidth(), muted = b == null, mono = true)
        }
    }
}

@Composable
private fun balanceLabel(acc: ReconcileData.Acc) =
    stringResource(if (acc.credit) R.string.reconcile_owed_label else R.string.reconcile_balance_label, acc.currency.uppercase())

@Composable
private fun StatementSheets(model: ReconcileModel, acc: ReconcileData.Acc?, sheet: String?, onDismiss: () -> Unit) {
    if (acc == null) return
    when (sheet) {
        "day" -> DaySheet(stringResource(R.string.reconcile_statement_date), model.day, { model.day = it }, onDismiss)
        "balance" -> AmountSheet(balanceLabel(acc), model.balance, acc.currency, signed = true, onDone = { model.balance = it }, onDismiss = onDismiss)
    }
}

// ---------- ReconcileTick ----------

private const val PAGE = 5

/** The hint words, from the reconcile_hint_* messages. */
private fun hintWords(r: android.content.res.Resources) = object : HintWords {
    override fun anEntry() = r.getString(R.string.reconcile_hint_an_entry)
    override fun exactUnticked(name: String, amount: String) = r.getString(R.string.reconcile_hint_exact_unticked, name, amount)
    override fun exactTicked(name: String, amount: String) = r.getString(R.string.reconcile_hint_exact_ticked, name, amount)
    override fun wrongWayIn(name: String) = r.getString(R.string.reconcile_hint_wrong_way_in, name)
    override fun wrongWayOut(name: String) = r.getString(R.string.reconcile_hint_wrong_way_out, name)
    override fun swapped(name: String, meant: String, amount: String) = r.getString(R.string.reconcile_hint_swapped, name, meant, amount)
    override fun decimal(name: String, meant: String, amount: String) = r.getString(R.string.reconcile_hint_decimal, name, meant, amount)
    override fun pair(first: String, second: String) = r.getString(R.string.reconcile_hint_pair, first, second)
    override fun duplicate(name: String) = r.getString(R.string.reconcile_hint_duplicate, name)
    override fun late() = r.getString(R.string.reconcile_hint_late)
    override fun lookOut(amount: String) = r.getString(R.string.reconcile_hint_look_out, amount)
    override fun lookIn(amount: String) = r.getString(R.string.reconcile_hint_look_in, amount)
}

private sealed interface TickSheet {
    data object Day : TickSheet
    data object Balance : TickSheet
    data class Payee(val ref: String, val value: String) : TickSheet
    data class Category(val ref: String, val income: Boolean, val value: String) : TickSheet
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ReconcileTick(model: ReconcileModel) {
    val res = LocalResources.current
    val kosha = rememberKosha()
    val toasts = LocalToasts.current
    val scope = rememberCoroutineScope()
    val m = meta() ?: return
    val d = model.data?.value ?: return
    val c = KoshaTheme.colors
    val money = moneyWords()
    val read = rememberReader(model)
    val writes = canWrite()
    val offline = stringResource(R.string.auth_error_offline)
    var sheet by remember { mutableStateOf<TickSheet?>(null) }
    var shownAll by rememberSaveable { mutableStateOf(false) }

    val a = d.account
    val credit = a.credit
    val limit = a.creditLimit ?: 0
    val cur = a.currency
    val fmt = { v: Long -> money(v, cur) }
    val last = d.history.firstOrNull()
    // Statement figures are as the bank prints them: a balance, or for a card what is owed.
    val toKosha = { v: Long -> if (credit) limit - v else v }
    val fromKosha = toKosha
    val statement = model.balance?.let(toKosha)
    val st = model.statement
    val lines = st?.lines.orEmpty()
    val day = model.day

    fun name(e: ReconcileData.Row): String {
        if (e.transferId == null) {
            return e.name.ifEmpty { m.categories.firstOrNull { it.id == e.categoryId }?.name.orEmpty() }
                .ifEmpty { res.getString(R.string.reconcile_no_payee) }
        }
        val account = m.wallets.firstOrNull { it.id == e.counterWalletId }?.name
        return when {
            account != null -> res.getString(if (e.amount < 0) R.string.reconcile_transfer_to_name else R.string.reconcile_transfer_from_name, account)
            else -> res.getString(if (e.amount < 0) R.string.reconcile_transfer_to_other else R.string.reconcile_transfer_from_other)
        }
    }
    fun entry(e: ReconcileData.Row) = Entry(e.id, e.day, e.amount, name(e), e.bankRef, e.paid)

    // What matches, what's missing and what counts as ticked: the same plan the public API uses.
    val plan = remember(lines, st?.withdrawn, d, day, model.notSame) {
        planReconciliation(lines, d.open.map(::entry), d.done.map(::entry), day, last?.statementDay, model.notSame, st?.withdrawn.orEmpty())
    }
    val missing = plan.missing.map { lines[it] }
    val closeIds = plan.close.map { it.entry }.toSet()
    val withdrawn = plan.withdrawn.toSet()
    val splitLines = plan.pairs.filter { it.kind == Kind.Split }.groupBy({ it.entry }, { it.line })
    fun isTicked(e: ReconcileData.Row) = model.ticks[e.id] ?: plan.ticks(entry(e))
    fun toggle(e: ReconcileData.Row) {
        model.ticks = model.ticks + (e.id to !isTicked(e))
    }
    val earlier = d.open.filter { it.day <= day && !plan.inPeriod(entry(it)) }
    val period = d.open.filter { plan.inPeriod(entry(it)) }
    val after = d.open.filter { it.day > day }
    val ticked = d.open.filter(::isTicked)
    val cleared = d.reconciled + ticked.sumOf { it.amount }
    val diff = statement?.let { it - cleared }

    // ---------- payees and categories for lines missing from Kosha ----------
    val guessing = m.pref("suggest_category", "0") == "1"
    val categories = m.categories.filter { !it.isArchived && it.parentId == null && !Categories.isNeutral(it.id) }
    val others = m.wallets.filter { !it.isArchived && it.id != a.id }
    fun basePayee(l: Line) = l.payee?.takeIf { it.isNotBlank() } ?: payeeFor(l.description, m.titles, d.aliases)
    class RowPick(val payee: String, val category: String, val skip: Boolean, val aiPayee: Boolean, val aiCategory: Boolean)
    fun row(l: Line): RowPick {
        val p = model.picks[l.ref.orEmpty()] ?: LinePick()
        val key = descriptorKey(l.description)
        val ai = model.tidy[key]
        val payee = p.payee ?: if (d.aliases[key] != null) basePayee(l) else ai?.payee ?: basePayee(l)
        fun fits(id: String?) = id?.takeIf { x -> categories.any { it.id == x && it.isIncome == (l.amount > 0) } }
        val ruled = if (guessing) fits(guessCategory(payee, m.rules)) ?: fits(guessCategory(l.description, m.rules)) else null
        val category = p.category ?: ruled ?: (if (guessing) fits(ai?.categoryId) else null) ?: ""
        return RowPick(
            payee, category, p.skip,
            aiPayee = p.payee == null && ai != null && payee == ai.payee && ai.payee != basePayee(l),
            aiCategory = p.category == null && ruled == null && category.isNotEmpty() && category == ai?.categoryId,
        )
    }
    fun pick(ref: String, change: (LinePick) -> LinePick) {
        model.picks = model.picks + (ref to change(model.picks[ref] ?: LinePick()))
    }

    // AI tidies the payee names and categories Kosha couldn't work out itself, once per statement.
    LaunchedEffect(st, missing.size) {
        if (!m.ai.statements || m.pref("ai_share_payee", "0") != "1" || st == null || missing.isEmpty()) return@LaunchedEffect
        val sig = "${st.name}:${lines.size}"
        if (model.tidyAsked == sig) return@LaunchedEffect
        model.tidyAsked = sig
        val todo = LinkedHashMap<String, TidyItem>()
        for (l in missing) {
            val key = descriptorKey(l.description)
            if (key.isEmpty() || d.aliases[key] != null) continue
            val r = row(l)
            val unknownPayee = l.payee == null && r.payee !in m.titles
            if (unknownPayee || r.category.isEmpty()) todo[key] = TidyItem(key, l.description, l.amount)
        }
        if (todo.isEmpty()) return@LaunchedEffect
        model.busy = "tidy"
        // Without it, Kosha's own names stand.
        runCatching { kosha.api.post<TidyBody, Map<String, Tidy>>("/api/app/import/tidy", TidyBody(todo.values.take(300))) }
            .onSuccess { model.tidy = it }
        if (model.busy == "tidy") model.busy = null
    }

    // ---------- finding a difference ----------
    val item = { e: ReconcileData.Row -> Item(e.id, e.amount, e.day, name(e)) }
    val missingTotal = missing.filter { !row(it).skip }.sumOf { it.amount }
    // Reconciled entries should still add up to the last statement, unless one was changed since.
    val drift = if (last != null) d.reconciled - last.balance else 0
    val openingGap = if (st?.opening != null && last != null && !credit && st.opening != last.printed) st.opening - last.printed else 0
    val hints: List<Hint> = if (diff == null || diff == 0L) emptyList() else buildList {
        if (openingGap != 0L) add(Hint(res.getString(R.string.reconcile_hint_opening_gap, fmt(st!!.opening!!), fmt(last!!.printed)), emptyList()))
        val single = missing.firstOrNull { it.amount == diff }
        if (single != null && missing.size > 1) add(Hint(res.getString(R.string.reconcile_hint_single_missing, row(single).payee, fmt(single.amount)), emptyList()))
        if (missing.isNotEmpty()) {
            add(
                Hint(
                    if (missingTotal == diff) res.getQuantityString(R.plurals.reconcile_hint_missing_closes, missing.size, missing.size.toString())
                    else res.getQuantityString(R.plurals.reconcile_hint_missing_count, missing.size, fmt(missingTotal), missing.size.toString()),
                    emptyList(),
                ),
            )
        }
        for (id in withdrawn) {
            val e = d.open.first { it.id == id }
            add(Hint(res.getString(R.string.reconcile_hint_withdrawn, name(e), fmt(e.amount)), listOf(id)))
        }
        for (g in plan.splits) {
            val entries = g.entries.map { id -> d.open.first { it.id == id } }
            add(
                Hint(
                    if (g.lines.size == 1) res.getString(R.string.reconcile_hint_split, fmt(lines[g.lines[0]].amount), entries.joinToString(" + ") { name(it) })
                    else res.getString(R.string.reconcile_hint_split_lines, name(entries[0]), g.lines.joinToString(" + ") { fmt(lines[it].amount) }),
                    g.entries,
                ),
            )
        }
        for (p in plan.close) {
            val e = d.open.first { it.id == p.entry }
            add(Hint(res.getString(R.string.reconcile_hint_close, name(e), fmt(e.amount), fmt(lines[p.line].amount)), listOf(e.id)))
        }
        // Its last-resort "look for a fee" only helps when nothing above already explains the gap.
        addAll(
            differenceHints(diff, ticked.map(item), (earlier + period).filter { !isTicked(it) }.map(item), day, fmt, hintWords(res))
                .filter { it.ids.isNotEmpty() || (missing.isEmpty() && plan.close.isEmpty() && openingGap == 0L) },
        )
    }.take(6)
    val canAskAi = m.ai.statements && m.pref("ai_share_payee", "0") == "1" && m.pref("ai_share_amount", "0") == "1"
    val askFailed = stringResource(R.string.reconcile_ask_failed)
    fun askAi() {
        if (diff == null) return
        scope.launch {
            model.busy = "ai"
            val near = addDays(plan.periodStart, -30)
            val ex = { e: ReconcileData.Row -> ExplainItem(e.id, e.day, e.amount, name(e)) }
            try {
                model.ai = kosha.api.post<ExplainBody, Explained>(
                    "/api/app/reconcile/explain",
                    ExplainBody(
                        day, diff, cur,
                        ticked.filter { it.day >= near }.takeLast(400).map(ex),
                        (earlier + period).filter { !isTicked(it) && it.day >= near }.takeLast(400).map(ex),
                        missing.take(400).map { ExplainLine(it.day, it.amount, it.description) },
                        plan.close.map { ExplainClose(it.entry, lines[it.line].amount) },
                    ),
                )
            } catch (e: ApiException) {
                toasts.show(Toast(e.message ?: askFailed))
            } catch (e: IOException) {
                toasts.show(Toast(offline))
            }
            model.busy = null
        }
    }
    // A hinted entry can sit in a collapsed group, so Show opens that group first.
    fun show(ids: List<String>) {
        val groups = model.openGroups.toMutableSet()
        if (earlier.any { it.id in ids }) groups += "earlier"
        if (after.any { it.id in ids }) groups += "after"
        if (period.drop(PAGE).any { it.id in ids }) shownAll = true
        model.openGroups = groups
        model.focus = ids
    }

    // ---------- actions ----------
    suspend fun addMissing() {
        val chosen = missing.filter { !row(it).skip }
        val unsorted = chosen.count { row(it).category.isEmpty() }
        if (unsorted > 0) {
            toasts.show(Toast(res.getQuantityString(R.plurals.reconcile_pick_category_first, unsorted, unsorted.toString())))
            return
        }
        model.busy = "add"
        val plain = chosen.filter { !row(it).category.startsWith("transfer:") }
        val moves = chosen - plain.toSet()
        val ok = kosha.write(toasts, offline) {
            if (plain.isNotEmpty()) {
                kosha.api.post<BankEntries, kotlinx.serialization.json.JsonObject>(
                    "/api/v1/transactions",
                    BankEntries(plain.map { l ->
                        val r = row(l)
                        BankEntry(model.added.getOrPut(l.ref!!) { UUID.randomUUID().toString() }, a.id, r.payee.trim(), l.amount, r.category, l.day, l.ref)
                    }),
                )
            }
            // Transfers, the payee names and the CSV's columns go through the import endpoint, which remembers them.
            val aliases = chosen.map { Alias(descriptorKey(it.description), row(it).payee.trim()) }.filter { it.key.isNotEmpty() && it.payee.isNotEmpty() }
            val layouts = if (st?.source == "csv" && st.layout != null && st.signature != null) {
                listOf(kotlinx.serialization.json.buildJsonObject {
                    put("signature", kotlinx.serialization.json.JsonPrimitive(st.signature))
                    put("layout", st.layout.json())
                })
            } else emptyList()
            if (moves.isNotEmpty() || aliases.isNotEmpty() || layouts.isNotEmpty()) kosha.api.post<LinesBody, kotlinx.serialization.json.JsonElement>(
                "/api/app/import/lines",
                LinesBody(a.id, layouts, aliases, moves.map { l ->
                    val r = row(l)
                    NewLine(l.day, l.amount, "", r.payee.trim(), transferWith = r.category.removePrefix("transfer:"), ref = l.ref!!)
                }),
            )
        }
        if (ok != null) toasts.show(Toast(res.getQuantityString(R.plurals.reconcile_added, chosen.size, a.name, chosen.size.toString())))
        model.busy = null
    }

    suspend fun finish(adjust: Boolean) {
        val bal = statement ?: return
        model.busy = "finish"
        val ids = ticked.map { it.id }
        kosha.write(toasts, offline) {
            kosha.api.post<FinishBody, Finished>("/api/app/reconcile/finish", FinishBody(a.id, day, bal, ids, adjust))
        }?.let {
            model.done = Done(it.id, ids.size, it.adjustment, day)
            model.statement = null
            model.ticks = emptyMap()
            model.step = ReconcileModel.Step.Done
        }
        model.busy = null
    }

    // ---------- page ----------
    val owedLabels = credit
    run {
        Page(
            stringResource(R.string.reconcile_account_title, a.name),
            onBack = { model.step = ReconcileModel.Step.Start },
            subtitle = last?.let { stringResource(R.string.reconcile_last_reconciled, shortDay(it.statementDay)) } ?: stringResource(R.string.reconcile_check_each),
            footer = {
                val busy = model.busy != null
                when {
                    statement == null -> Button(stringResource(R.string.reconcile_enter_balance), { sheet = TickSheet.Balance }, Modifier.fillMaxWidth())
                    diff == 0L -> Button(stringResource(R.string.reconcile_finish), { scope.launch { finish(false) } }, Modifier.fillMaxWidth(), enabled = !busy && writes)
                    else -> Button(
                        stringResource(R.string.reconcile_finish_with_correction, money(diff!!, cur)),
                        { scope.launch { finish(true) } },
                        Modifier.fillMaxWidth(),
                        style = ButtonStyle.Secondary,
                        enabled = !busy && writes,
                    )
                }
            },
        ) {
            // The file, and another one.
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when {
                    model.reading != null -> Chip(stringResource(R.string.reconcile_reading, model.reading!!), soft = true, small = true)
                    st != null -> {
                        Chip(
                            st.name + " · " + pluralStringResource(R.plurals.reconcile_file_lines, st.lines.size, countText(st.lines.size)),
                            Modifier.weight(1f, fill = false),
                            soft = true,
                            small = true,
                            leading = { if (st.byAi) Sparkle(size = 12.dp) else PhIcon(if (st.source == "csv") "file-csv" else "file-text", size = 16.dp) },
                        )
                        KText(
                            stringResource(R.string.reconcile_another_file),
                            Modifier.heightIn(min = 48.dp).clickable(role = Role.Button, onClick = read).padding(horizontal = 6.dp, vertical = 14.dp),
                            style = KoshaTheme.type.small,
                            color = c.ink2,
                        )
                        IconButton("x", stringResource(R.string.common_remove), { model.statement = null })
                    }
                    else -> Chip(
                        stringResource(R.string.app_rcn_import_instead),
                        soft = true,
                        small = true,
                        onClick = read,
                        leading = { PhIcon("file-arrow-up", size = 16.dp) },
                    )
                }
            }
            if (st?.source == "csv" && st.layout != null && st.file != null) {
                Card {
                    Columns(
                        st.rows, st.layout, st.lines.size, columnsStatus(st.remembered, st.byAi, st.confident), st.byAi,
                        startOpen = !st.remembered && !st.confident,
                        onChange = { layout ->
                            scope.launch {
                                runCatching { kosha.api.read(st.file, credit, layout, a.id) }
                                    .onSuccess { r -> openCsv(model, st.copy(lines = r.lines, rows = r.rows, layout = layout, remembered = false, byAi = false), credit) }
                                    .onFailure { toasts.show(Toast((it as? ApiException)?.message ?: offline)) }
                            }
                        },
                    )
                }
            }
            if (drift != 0L && last != null) Note(stringResource(R.string.reconcile_drift, shortDay(last.statementDay), fmt(if (credit) -drift else drift)))
            if (st?.off != null && st.off != 0L) Note(stringResource(R.string.reconcile_pdf_off, fmt(kotlin.math.abs(st.off))))

            // Statement, ticked and difference.
            Card {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Figure(
                        stringResource(if (owedLabels) R.string.reconcile_figure_statement_owed else R.string.reconcile_figure_statement),
                        Modifier.weight(1f).clickable(role = Role.Button) { sheet = TickSheet.Balance },
                        sub = shortDay(day),
                        onSub = { sheet = TickSheet.Day },
                    ) { if (statement == null) KText("—", style = KoshaTheme.type.num) else MoneyText(fromKosha(statement), cur) }
                    Figure(
                        stringResource(if (owedLabels) R.string.reconcile_figure_ticked_owed else R.string.reconcile_figure_ticked),
                        Modifier.weight(1f),
                        sub = stringResource(R.string.reconcile_ticked_count, countText(ticked.size)),
                    ) { MoneyText(fromKosha(cleared), cur) }
                    Figure(stringResource(R.string.reconcile_figure_difference), Modifier.weight(1f)) {
                        when (diff) {
                            null -> KText("—", style = KoshaTheme.type.num)
                            0L -> KText(stringResource(R.string.reconcile_figure_none), style = KoshaTheme.type.num, color = c.pos)
                            else -> MoneyText(if (credit) -diff else diff, cur, color = c.bad)
                        }
                    }
                }
            }

            // Finding the difference.
            if (diff != null && diff != 0L && (hints.isNotEmpty() || canAskAi)) {
                Card {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            KText(stringResource(R.string.reconcile_finding_title), Modifier.weight(1f), style = KoshaTheme.type.head)
                            if (canAskAi) {
                                Chip(
                                    stringResource(R.string.reconcile_ask_ai),
                                    small = true,
                                    onClick = { if (model.busy == null) askAi() },
                                    leading = { if (model.busy == "ai") Busy() else Sparkle() },
                                )
                            }
                        }
                        model.ai?.let { ai ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Sparkle(Modifier.padding(top = 2.dp))
                                KText(ai.summary, style = KoshaTheme.type.small)
                            }
                            ai.suggestions.forEach { HintRow(it.text, it.ids, icon = null, onShow = ::show) }
                        }
                        hints.forEach { HintRow(it.text, it.ids, icon = "magnifying-glass", onShow = ::show) }
                    }
                }
            }

            // On the statement, not in Kosha.
            if (missing.isNotEmpty()) {
                Section(stringResource(R.string.reconcile_missing_title, missing.size.toString()), if (model.busy == "tidy") stringResource(R.string.reconcile_naming_payees) else null) {
                    Card {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            missing.forEachIndexed { i, l ->
                                if (i > 0) Hairline()
                                val r = row(l)
                                val ref = l.ref.orEmpty()
                                val cat = m.categories.firstOrNull { it.id == r.category }
                                val other = r.category.takeIf { it.startsWith("transfer:") }?.let { id -> m.wallets.firstOrNull { it.id == id.removePrefix("transfer:") } }
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(
                                        Modifier.toggleable(!r.skip, role = Role.Checkbox) { pick(ref) { p -> p.copy(skip = !p.skip) } }
                                            .semantics { contentDescription = res.getString(if (r.skip) R.string.reconcile_include_line else R.string.reconcile_leave_out_line) },
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    ) {
                                        Tick(!r.skip)
                                        Column(Modifier.weight(1f)) {
                                            KText("${l.description} · ${shortDay(l.day)}", style = KoshaTheme.type.xs, color = c.ink3, maxLines = 1)
                                            KText(r.payee, style = KoshaTheme.type.head, color = if (r.skip) c.ink3 else c.ink)
                                        }
                                        MoneyText(l.amount, cur, sign = Sign.Natural, color = if (l.amount > 0) c.pos else c.ink)
                                    }
                                    if (!r.skip) {
                                        Row(Modifier.padding(start = 32.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Chip(
                                                stringResource(R.string.reconcile_payee),
                                                small = true,
                                                onClick = { sheet = TickSheet.Payee(ref, r.payee) },
                                                leading = { PhIcon("pencil-simple", size = 16.dp) },
                                            )
                                            Chip(
                                                when {
                                                    cat != null -> cat.name
                                                    other != null -> res.getString(if (l.amount < 0) R.string.reconcile_transfer_to else R.string.reconcile_transfer_from, other.name)
                                                    else -> res.getString(R.string.reconcile_pick_category)
                                                },
                                                Modifier.weight(1f, fill = false),
                                                small = true,
                                                onClick = { sheet = TickSheet.Category(ref, l.amount > 0, r.category) },
                                                leading = {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        PhIcon(if (cat != null) categoryIconName(cat.icon) else if (other != null) "arrows-left-right" else "plus", size = 16.dp, tint = if (r.category.isEmpty()) c.warn else c.ink2)
                                                    }
                                                },
                                            )
                                            if (r.aiCategory || r.aiPayee) Sparkle(Modifier.align(Alignment.CenterVertically), size = 11.dp)
                                        }
                                    }
                                }
                            }
                            KText(stringResource(R.string.reconcile_saving_teaches), style = KoshaTheme.type.xs, color = c.ink3)
                            val count = missing.count { !row(it).skip }
                            Button(
                                stringResource(R.string.reconcile_add_to_kosha, count.toString()),
                                { scope.launch { addMissing() } },
                                Modifier.fillMaxWidth(),
                                size = ButtonSize.Medium,
                                enabled = count > 0 && model.busy == null && writes,
                            )
                        }
                    }
                }
            }

            // Close, but not the same amount.
            if (plan.close.isNotEmpty()) {
                Section(stringResource(R.string.reconcile_close_title, plan.close.size.toString())) {
                    plan.close.forEach { p ->
                        val e = d.open.first { it.id == p.entry }
                        val l = lines[p.line]
                        Card {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    IconTile(if (e.transferId != null) "arrows-left-right" else categoryIconName(m.categories.firstOrNull { it.id == e.categoryId }?.icon))
                                    Column(Modifier.weight(1f)) {
                                        KText(name(e), style = KoshaTheme.type.head)
                                        KText(
                                            if (p.kind == Kind.Corrected) stringResource(R.string.reconcile_corrected_detail, fmt(l.amount), shortDay(l.day), fmt(e.amount))
                                            else stringResource(R.string.reconcile_close_detail, fmt(e.amount), shortDay(e.day), fmt(l.amount), shortDay(l.day)),
                                            style = KoshaTheme.type.xs,
                                            color = c.ink3,
                                        )
                                    }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (e.transferId == null) {
                                        Button(
                                            stringResource(R.string.reconcile_use_amount, fmt(l.amount)),
                                            {
                                                scope.launch {
                                                    model.busy = e.id
                                                    kosha.write(toasts, offline) {
                                                        kosha.api.patch<AmountChanges, kotlinx.serialization.json.JsonObject>("/api/v1/transactions", AmountChanges(listOf(AmountChange(e.id, l.amount))))
                                                    }
                                                    model.busy = null
                                                }
                                            },
                                            Modifier.weight(1f),
                                            size = ButtonSize.Small,
                                            enabled = model.busy == null && writes,
                                        )
                                    }
                                    Button(
                                        stringResource(R.string.reconcile_not_same),
                                        { model.notSame = model.notSame + l.ref.orEmpty() },
                                        Modifier.weight(1f),
                                        style = ButtonStyle.Secondary,
                                        size = ButtonSize.Small,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // The entries to tick.
            Section(
                stringResource(if (st != null) R.string.reconcile_in_kosha else R.string.reconcile_tick_title),
                stringResource(R.string.reconcile_ticked_count, countText(ticked.size)),
            ) {
                Card(tight = true) {
                    var first = true
                    fun sep(): Boolean = (!first).also { first = false }
                    if (earlier.isNotEmpty()) {
                        sep()
                        val open = "earlier" in model.openGroups
                        MoreRow(
                            stringResource(R.string.reconcile_before_group, earlier.size.toString()),
                            { model.openGroups = if (open) model.openGroups - "earlier" else model.openGroups + "earlier" },
                            sub = stringResource(R.string.reconcile_before_hint),
                            open = open,
                            head = true,
                        )
                        if (open) earlier.forEach { e -> Hairline(); EntryRow(e, name(e), cur, isTicked(e), e.id in model.focus, null) { toggle(e) } }
                    }
                    if (period.isEmpty() && earlier.isEmpty()) {
                        KText(stringResource(R.string.reconcile_nothing, shortDay(day)), Modifier.padding(16.dp), style = KoshaTheme.type.small, color = c.ink3)
                    }
                    val shown = if (shownAll) period else period.take(PAGE)
                    shown.forEach { e ->
                        if (sep()) Hairline()
                        val note = when {
                            e.id in closeIds -> stringResource(R.string.reconcile_note_amount_differs)
                            e.id in withdrawn -> stringResource(R.string.reconcile_note_withdrawn)
                            splitLines[e.id] != null -> splitLines.getValue(e.id).let { ls ->
                                if (ls.size == 1) stringResource(R.string.reconcile_note_part_of, fmt(lines[ls[0]].amount))
                                else stringResource(R.string.reconcile_note_split_lines)
                            }
                            st != null && e.id !in plan.matched ->
                                stringResource(if (e.day > addDays(day, -3)) R.string.reconcile_note_next_statement else R.string.reconcile_note_not_on_statement)
                            e.paid != 1 -> stringResource(R.string.reconcile_note_still_due)
                            else -> null
                        }
                        EntryRow(e, name(e), cur, isTicked(e), e.id in model.focus, note) { toggle(e) }
                    }
                    if (period.size > shown.size) {
                        Hairline()
                        MoreRow(stringResource(R.string.import_show_more, countText(period.size - shown.size)), { shownAll = true })
                    }
                }
            }
            if (after.isNotEmpty()) {
                val open = "after" in model.openGroups
                Card(tight = true) {
                    MoreRow(
                        stringResource(R.string.reconcile_after_group, shortDay(day), after.size.toString()),
                        { model.openGroups = if (open) model.openGroups - "after" else model.openGroups + "after" },
                        sub = stringResource(R.string.reconcile_after_hint),
                        open = open,
                        head = true,
                    )
                    if (open) after.forEach { e -> Hairline(); EntryRow(e, name(e), cur, isTicked(e), e.id in model.focus, null) { toggle(e) } }
                }
            }
        }
    }

    when (val s = sheet) {
        TickSheet.Day -> StatementSheets(model, a, "day") { sheet = null }
        TickSheet.Balance -> StatementSheets(model, a, "balance") { sheet = null }
        is TickSheet.Payee -> TextSheet(stringResource(R.string.reconcile_payee), s.value, { v -> pick(s.ref) { it.copy(payee = v) } }) { sheet = null }
        is TickSheet.Category -> Sheet(stringResource(R.string.reconcile_pick_category), { sheet = null }) {
            ListPicker(
                categories.filter { it.isIncome == s.income }.map { ListOption(it.id, it.name) } +
                    others.map {
                        ListOption("transfer:${it.id}", stringResource(if (s.income) R.string.reconcile_transfer_from else R.string.reconcile_transfer_to, it.name))
                    },
                s.value,
                { o -> pick(s.ref) { it.copy(category = o.value) }; sheet = null },
            )
        }
        null -> {}
    }
}

/** One of the three figures: a label, the amount, and a line under it. */
@Composable
private fun Figure(label: String, modifier: Modifier = Modifier, sub: String? = null, onSub: (() -> Unit)? = null, value: @Composable () -> Unit) {
    val c = KoshaTheme.colors
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        KText(label, style = KoshaTheme.type.xs, color = c.ink3)
        value()
        if (sub != null) {
            KText(
                sub,
                if (onSub != null) Modifier.clickable(role = Role.Button, onClick = onSub) else Modifier,
                style = KoshaTheme.type.xs,
                color = c.ink3,
            )
        }
    }
}

@Composable
private fun HintRow(text: String, ids: List<String>, icon: String?, onShow: (List<String>) -> Unit) {
    val c = KoshaTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
        if (icon != null) PhIcon(icon, Modifier.padding(top = 2.dp), size = 16.dp, tint = c.ink3)
        KText(text, Modifier.weight(1f), style = KoshaTheme.type.small)
        if (ids.isNotEmpty()) {
            KText(
                stringResource(R.string.reconcile_show),
                Modifier.heightIn(min = 48.dp).clickable(role = Role.Button) { onShow(ids) }.padding(horizontal = 6.dp, vertical = 2.dp),
                style = KoshaTheme.type.small,
                color = c.ink2,
            )
        }
    }
}

/** An entry to tick: tick, payee and day (or a note in warn), amount. A hinted one is highlighted and scrolled to. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EntryRow(e: ReconcileData.Row, name: String, currency: String, ticked: Boolean, focus: Boolean, note: String?, onToggle: () -> Unit) {
    val c = KoshaTheme.colors
    val bring = remember { BringIntoViewRequester() }
    LaunchedEffect(focus) { if (focus) bring.bringIntoView() }
    Row(
        Modifier
            .fillMaxWidth()
            .bringIntoViewRequester(bring)
            .background(if (focus) c.warn.copy(alpha = 0.1f) else androidx.compose.ui.graphics.Color.Transparent)
            .toggleable(ticked, role = Role.Checkbox, onValueChange = { onToggle() })
            .heightIn(min = KoshaTheme.dims.row)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Tick(ticked)
        Column(Modifier.weight(1f)) {
            KText(name)
            KText(note ?: shortDay(e.day), style = KoshaTheme.type.xs, color = if (note != null) c.warn else c.ink3)
        }
        MoneyText(
            e.amount,
            currency,
            color = when {
                !ticked -> c.ink3
                e.transferId != null -> c.ink2
                e.amount > 0 -> c.pos
                else -> c.ink
            },
        )
    }
}

/** Words for one value: a field and Save. */
@Composable
internal fun TextSheet(title: String, start: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf(start) }
    Sheet(title, onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Field(text, { text = it }, Modifier.fillMaxWidth())
            Button(stringResource(R.string.common_save), { onSave(text.trim()); onDismiss() }, Modifier.fillMaxWidth(), enabled = text.isNotBlank())
        }
    }
}

// ---------- ReconcileDone ----------

@Composable
private fun ReconcileDone(model: ReconcileModel) {
    val nav = LocalNav.current
    val kosha = rememberKosha()
    val toasts = LocalToasts.current
    val scope = rememberCoroutineScope()
    val c = KoshaTheme.colors
    val money = moneyWords()
    val d = model.data?.value
    val done = model.done
    val name = d?.account?.name.orEmpty()
    val offline = stringResource(R.string.auth_error_offline)
    val undone = stringResource(R.string.reconcile_undone)
    Column(Modifier.fillMaxSize().background(c.bg).statusBarsPadding().navigationBarsPadding()) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Spacer(Modifier.height(72.dp))
            Box(Modifier.size(72.dp).clip(RoundedCornerShape(24.dp)).background(c.pos.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                PhIcon("seal-check", size = 32.dp, tint = c.pos, fill = true)
            }
            KText(stringResource(R.string.reconcile_done_title, name), style = KoshaTheme.type.title, align = TextAlign.Center)
            if (done != null) {
                KText(
                    listOfNotNull(
                        pluralStringResource(R.plurals.reconcile_done_checked, done.entries, shortDay(done.day), countText(done.entries)),
                        if (done.adjustment != 0L) stringResource(R.string.reconcile_done_correction, money(done.adjustment, d?.account?.currency ?: "")) else null,
                        stringResource(R.string.reconcile_done_marked),
                    ).joinToString(" "),
                    Modifier.padding(horizontal = 24.dp),
                    style = KoshaTheme.type.small,
                    color = c.ink2,
                    align = TextAlign.Center,
                )
            }
            if (d != null && d.history.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                Card(Modifier.fillMaxWidth(), tight = true) {
                    KText(stringResource(R.string.reconcile_past), Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp), style = KoshaTheme.type.head)
                    d.history.forEachIndexed { i, h ->
                        Hairline()
                        ListRow {
                            PhIcon("seal-check", size = 18.dp, tint = if (i == 0) c.pos else c.ink3)
                            Column(Modifier.weight(1f)) {
                                KText(shortDay(h.statementDay))
                                KText(
                                    listOfNotNull(
                                        pluralStringResource(R.plurals.reconcile_history_entries, h.entries, countText(h.entries)),
                                        if (h.adjustment != 0L) stringResource(R.string.reconcile_corrected_by, money(h.adjustment, d.account.currency)) else null,
                                    ).joinToString(" · "),
                                    style = KoshaTheme.type.xs,
                                    color = c.ink3,
                                )
                            }
                            // Only the latest can be undone.
                            if (i == 0) {
                                Button(
                                    stringResource(R.string.common_undo),
                                    {
                                        scope.launch {
                                            model.busy = "undo"
                                            kosha.write(toasts, offline) { kosha.api.post<IdBody, kotlinx.serialization.json.JsonElement>("/api/app/reconcile/undo", IdBody(h.id)) }
                                                ?.let {
                                                    toasts.show(Toast(undone))
                                                    model.done = null
                                                    model.step = ReconcileModel.Step.Start
                                                }
                                            model.busy = null
                                        }
                                    },
                                    style = ButtonStyle.Secondary,
                                    size = ButtonSize.Small,
                                    enabled = model.busy == null && canWrite(),
                                )
                            }
                        }
                    }
                }
            }
        }
        Button(
            stringResource(R.string.reconcile_back_to_accounts),
            { if (!nav.popBackStack(Route.Accounts, inclusive = false)) nav.navigate(Route.Accounts) },
            Modifier.fillMaxWidth().padding(16.dp),
        )
    }
}
