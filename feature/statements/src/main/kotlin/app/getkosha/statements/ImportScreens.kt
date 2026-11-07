package app.getkosha.statements

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
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
import app.getkosha.design.Pill
import app.getkosha.design.R
import app.getkosha.design.Segmented
import app.getkosha.design.Sheet
import app.getkosha.design.Sparkle
import app.getkosha.design.Toast
import app.getkosha.design.categoryIconName
import app.getkosha.design.dashedBorder
import app.getkosha.model.AccountType
import app.getkosha.model.Categories
import app.getkosha.model.Meta
import app.getkosha.ui.LocalNav
import app.getkosha.ui.LocalToasts
import app.getkosha.ui.MoneyText
import app.getkosha.ui.Page
import app.getkosha.ui.Route
import app.getkosha.ui.canWrite
import app.getkosha.ui.meta
import app.getkosha.ui.rememberKosha

// Import (06-accounts-ImportChoose, ImportAccounts, ImportPayees, ImportDone): many statements from every bank and
// card at once, sorted into accounts, payees and transfers, as the web's import.tsx does.

@Composable
internal fun ImportScreen() {
    val model: ImportModel = viewModel()
    val context = LocalContext.current
    model.labels = Labels(
        stringResource(R.string.import_type_bank),
        stringResource(R.string.import_type_card),
        stringResource(R.string.import_new_account),
    )
    // Statements shared to Kosha wait in the inbox; take them now, and any shared while this screen is open.
    LaunchedEffect(model) {
        ImportInbox.files.collect { if (it.isNotEmpty()) model.add(context, ImportInbox.take()) }
    }
    BackHandler(model.step == ImportModel.Step.Accounts || model.step == ImportModel.Step.Review || (model.step == ImportModel.Step.Saving && model.error != null)) {
        model.step = when (model.step) {
            ImportModel.Step.Accounts -> ImportModel.Step.Files
            ImportModel.Step.Review -> ImportModel.Step.Accounts
            else -> ImportModel.Step.Review
        }
    }
    // Leaving while saving would stop it half way; it carries on, and an error offers the way back.
    BackHandler(model.step == ImportModel.Step.Saving && model.error == null) {}
    when (model.step) {
        ImportModel.Step.Files -> ImportChoose(model)
        ImportModel.Step.Accounts -> ImportAccounts(model)
        ImportModel.Step.Review -> ImportPayees(model)
        ImportModel.Step.Saving -> ImportSaving(model)
        ImportModel.Step.Done -> ImportDone(model)
    }
}

/** "192 transactions in 4 accounts", made of one-count plurals (import_summary has two counts). */
@Composable
private fun transactionsIn(lines: Int, accounts: Int) = stringResource(
    R.string.app_imp_summary,
    pluralStringResource(R.plurals.import_transactions, lines, countText(lines)),
    pluralStringResource(R.plurals.app_imp_n_accounts, accounts, countText(accounts)),
)

private fun Meta.colour(walletId: String) = wallets.indexOfFirst { it.id == walletId }

// ---------- ImportChoose ----------

@Composable
private fun ImportChoose(model: ImportModel) {
    val nav = LocalNav.current
    val context = LocalContext.current
    val kosha = rememberKosha()
    val toasts = LocalToasts.current
    val c = KoshaTheme.colors
    val m = meta() ?: return
    val online by kosha.online.collectAsStateWithLifecycle()
    val writes = canWrite()
    val demo = stringResource(R.string.common_demo_read_only)
    val pdf = m.ai.statements
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) model.add(context, uris)
    }
    val lines = model.live.sumOf { model.linesOf(it).size }
    val empty = model.stmts.isEmpty() && model.reading.isEmpty()
    Page(
        stringResource(R.string.import_title),
        onBack = { nav.popBackStack() },
        subtitle = stringResource(R.string.import_sub),
        footer = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (lines > 0 && model.reading.isEmpty()) {
                    KText(transactionsIn(lines, model.live.size), Modifier.fillMaxWidth(), style = KoshaTheme.type.xs, color = c.ink3, align = TextAlign.Center)
                }
                Button(
                    stringResource(R.string.app_imp_check_accounts),
                    { model.step = ImportModel.Step.Accounts },
                    Modifier.fillMaxWidth(),
                    enabled = model.reading.isEmpty() && lines > 0,
                )
            }
        },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(c.surface)
                .dashedBorder(1.5.dp, c.line, 16.dp)
                .clickable(role = Role.Button, enabled = online) {
                    if (!writes) toasts.show(Toast(demo)) else launcher.launch(statementTypes(pdf))
                }
                .padding(horizontal = 16.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(c.soft), contentAlignment = Alignment.Center) {
                PhIcon("files", size = 22.dp)
            }
            KText(stringResource(if (empty) R.string.app_imp_choose else R.string.app_imp_choose_more), style = KoshaTheme.type.head)
            KText(
                stringResource(if (pdf) R.string.app_imp_formats_pdf else R.string.app_imp_formats),
                style = KoshaTheme.type.xs,
                color = c.ink3,
                align = TextAlign.Center,
            )
        }
        if (!online) Note(stringResource(R.string.app_imp_offline), icon = "cloud-slash", tone = c.ink2)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            PhIcon("share-network", size = 14.dp, tint = c.ink3)
            KText(stringResource(R.string.app_imp_share_hint), style = KoshaTheme.type.xs, color = c.ink3)
        }

        if (!empty || model.failed.isNotEmpty()) {
            val total = model.stmts.size + model.reading.size
            Section(
                if (model.reading.isNotEmpty()) pluralStringResource(R.plurals.import_reading_files, model.batch, countText(model.batch))
                else pluralStringResource(R.plurals.app_imp_n_files, total, countText(total)),
                if (model.reading.isNotEmpty()) stringResource(R.string.app_imp_read_progress, countText(model.batch - model.reading.size), countText(model.batch)) else null,
            ) {
                Card(tight = true) {
                    var first = true
                    @Composable
                    fun sep() {
                        if (!first) Hairline()
                        first = false
                    }
                    model.stmts.forEach { s ->
                        sep()
                        ListRow {
                            PhIcon(fileIcon(s.source), size = 18.dp)
                            Column(Modifier.weight(1f)) {
                                KText(s.file, maxLines = 1)
                                KText(stmtLines(s.lines), style = KoshaTheme.type.xs, color = c.ink3)
                            }
                            PhIcon("check-circle", size = 18.dp, tint = c.pos)
                        }
                    }
                    model.reading.forEach { r ->
                        sep()
                        ListRow {
                            PhIcon(if (r.pdf) "file-pdf" else "file-text", size = 18.dp)
                            Column(Modifier.weight(1f)) {
                                KText(r.name, maxLines = 1)
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    if (r.pdf) Sparkle(size = 11.dp)
                                    KText(stringResource(if (r.pdf) R.string.app_imp_reading_ai else R.string.app_imp_reading), style = KoshaTheme.type.xs, color = c.ink3)
                                }
                            }
                            Box(Modifier.size(18.dp).clip(CircleShape).background(c.soft))
                        }
                    }
                    model.failed.forEach { f ->
                        sep()
                        ListRow {
                            PhIcon("warning", size = 18.dp, tint = c.warn)
                            Column(Modifier.weight(1f)) {
                                KText(f.name, maxLines = 1)
                                KText(
                                    when (f.fail) {
                                        Fail.Pdf -> stringResource(R.string.import_error_pdf)
                                        Fail.Empty -> stringResource(R.string.import_error_no_transactions)
                                        Fail.Offline -> stringResource(R.string.auth_error_offline)
                                        Fail.Server -> f.message?.ifBlank { null } ?: stringResource(R.string.import_error_unreadable)
                                        Fail.Unreadable -> stringResource(R.string.import_error_unreadable)
                                    },
                                    style = KoshaTheme.type.xs,
                                    color = c.warn,
                                )
                            }
                            IconButton("x", stringResource(R.string.import_dismiss, f.name), { model.dismiss(f) })
                        }
                    }
                }
            }
        }
    }
}

private fun fileIcon(source: String) = when (source) {
    "csv" -> "file-csv"
    "pdf" -> "file-pdf"
    else -> "file-text"
}

@Composable
private fun stmtLines(lines: List<Line>): String {
    val r = span(lines) ?: return stringResource(R.string.import_stmt_no_lines)
    return pluralStringResource(R.plurals.import_stmt_lines, lines.size, countText(lines.size), shortDay(r.first), shortDay(r.second))
}

// ---------- ImportAccounts ----------

private sealed interface AcctSheet {
    data class Target(val acct: String) : AcctSheet
    data class Currency(val acct: String) : AcctSheet
    data class Limit(val acct: String) : AcctSheet
    data class Closing(val acct: String) : AcctSheet
    data class Move(val stmt: String) : AcctSheet
}

@Composable
private fun ImportAccounts(model: ImportModel) {
    val c = KoshaTheme.colors
    val toasts = LocalToasts.current
    val m = meta() ?: return
    val live = model.live
    var sheet by remember { mutableStateOf<AcctSheet?>(null) }
    val lines = live.sumOf { model.linesOf(it).size }
    val unnamed = live.any { it.target == "new" && it.name.isBlank() }
    val checkFailed = stringResource(R.string.import_error_check)
    LaunchedEffect(model.checkError) {
        model.checkError?.let { toasts.show(Toast(it.ifBlank { checkFailed })); model.checkError = null }
    }
    Page(
        stringResource(R.string.app_imp_check_accounts),
        onBack = { model.step = ImportModel.Step.Files },
        subtitle = listOf(
            pluralStringResource(R.plurals.app_imp_n_files, model.stmts.size, countText(model.stmts.size)),
            pluralStringResource(R.plurals.import_transactions, lines, countText(lines)),
            pluralStringResource(R.plurals.app_imp_n_accounts, live.size, countText(live.size)),
        ).joinToString(" · "),
        footer = {
            Button(
                stringResource(R.string.import_check_payees),
                { model.toReview() },
                Modifier.fillMaxWidth(),
                enabled = !model.busy && model.reading.isEmpty() && lines > 0 && !unnamed,
            )
        },
    ) {
        live.forEachIndexed { index, a -> AccountCard(model, m, a, index) { sheet = it } }
    }

    val s = sheet ?: return
    val close = { sheet = null }
    when (s) {
        is AcctSheet.Target -> {
            val a = model.accts.firstOrNull { it.id == s.acct } ?: return
            val taken = model.live.filter { it.id != a.id }.map { it.target }.toSet()
            ChoiceSheet(
                stringResource(R.string.import_into),
                listOf("new" to stringResource(R.string.import_new_account_option)) +
                    model.wallets().filter { it.id !in taken }.map { it.id to stringResource(R.string.import_into_wallet, it.name) },
                a.target,
                { t -> model.patch(a.id) { it.copy(target = t) } },
                close,
            )
        }
        is AcctSheet.Currency -> {
            val a = model.accts.firstOrNull { it.id == s.acct } ?: return
            val currencies = (listOf(m.primary) + m.wallets.map { it.currency } + listOf("eur", "inr", "usd", "gbp", a.currency)).distinct()
            Sheet(stringResource(R.string.import_field_currency), close) {
                ListPicker(currencies.map { ListOption(it, it.uppercase()) }, a.currency, { o -> model.patch(a.id) { it.copy(currency = o.value) }; close() })
            }
        }
        is AcctSheet.Limit -> {
            val a = model.accts.firstOrNull { it.id == s.acct } ?: return
            AmountSheet(stringResource(R.string.import_credit_limit, a.currency.uppercase()), a.limit, a.currency, signed = false,
                onDone = { v -> model.patch(a.id) { it.copy(limit = v) } }, onDismiss = close)
        }
        is AcctSheet.Closing -> {
            val a = model.accts.firstOrNull { it.id == s.acct } ?: return
            val i = model.info(a)
            val credit = i.type == AccountType.Credit
            val cl = model.closingOf(a)
            AmountSheet(
                stringResource(if (credit) R.string.import_owed_on else R.string.import_balance_on, shortDay(cl.day), i.currency.uppercase()),
                cl.balance, i.currency, signed = !credit,
                onDone = { v -> model.patch(a.id) { it.copy(closing = v, closingEdited = true) } },
                onDismiss = close,
            )
        }
        is AcctSheet.Move -> {
            val st = model.stmts.firstOrNull { it.id == s.stmt } ?: return
            ChoiceSheet(
                stringResource(R.string.import_move_label, st.file),
                model.live.filter { it.id != st.acct }.map { it.id to model.info(it).name } + ("new" to stringResource(R.string.import_new_account_option)),
                null,
                { model.move(st, it) },
                close,
            )
        }
    }
}

@Composable
private fun AccountCard(model: ImportModel, m: Meta, a: Acct, index: Int, open: (AcctSheet) -> Unit) {
    val c = KoshaTheme.colors
    val money = moneyWords()
    val i = model.info(a)
    val wallet = i.wallet
    val credit = i.type == AccountType.Credit
    val stmts = model.stmts.filter { it.acct == a.id }
    val lines = model.linesOf(a)
    val range = span(lines)
    val read = stmts.sumOf { it.lines.size }
    val closing = model.closingOf(a)
    // A new account takes the next colour after the person's accounts.
    val colour = wallet?.let { m.colour(it.id) } ?: (m.wallets.size + index)
    val gaps = statementGaps(stmts.mapNotNull { s -> span(s.lines)?.let { Span(s.file, it.first, it.second, s.opening, s.closing) } })
    val foreign = stmts.firstOrNull { it.currency != null && it.currency != i.currency }
    val matches = wallet != null && closing.balance != null && closing.fromFile &&
        (if (credit) i.limit - kotlin.math.abs(closing.balance) else closing.balance) == wallet.balance
    Card {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AccountDot(c.account(colour), size = 10.dp)
                KText(
                    if (wallet != null) stringResource(R.string.import_into_wallet, wallet.name) else stringResource(R.string.import_new_account_option),
                    Modifier.weight(1f),
                    style = KoshaTheme.type.head,
                )
                KText(
                    stringResource(R.string.settings_profile_change),
                    Modifier.heightIn(min = 48.dp).clickable(role = Role.Button) { open(AcctSheet.Target(a.id)) }.padding(horizontal = 4.dp, vertical = 14.dp),
                    style = KoshaTheme.type.small,
                    color = c.ink2,
                )
            }
            if (range != null) {
                KText(
                    pluralStringResource(R.plurals.import_account_range, lines.size, countText(lines.size), shortDay(range.first), shortDay(range.second)) +
                        if (read > lines.size) " " + pluralStringResource(R.plurals.import_account_dupes, read - lines.size, countText(read - lines.size)) else "",
                    style = KoshaTheme.type.xs,
                    color = c.ink3,
                )
            }
            if (matches) Pill(stringResource(R.string.app_imp_closing_matches), color = c.pos)

            if (wallet == null) {
                Field(a.name, { v -> model.patch(a.id) { it.copy(name = v) } }, Modifier.fillMaxWidth(), placeholder = stringResource(R.string.import_field_name))
                Segmented(
                    listOf(AccountType.Bank to stringResource(R.string.import_type_bank), AccountType.Credit to stringResource(R.string.import_type_card)),
                    a.type,
                    { model.setType(a, it) },
                    Modifier.fillMaxWidth(),
                    small = true,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PickField(a.currency.uppercase(), { open(AcctSheet.Currency(a.id)) }, Modifier.weight(1f), label = stringResource(R.string.import_field_currency), mono = true)
                    if (a.type == AccountType.Credit) {
                        PickField(
                            a.limit?.let { money(it, a.currency) } ?: stringResource(R.string.import_optional),
                            { open(AcctSheet.Limit(a.id)) },
                            Modifier.weight(1f),
                            label = stringResource(R.string.accounts_credit_limit),
                            muted = a.limit == null,
                            mono = a.limit != null,
                        )
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button) { open(AcctSheet.Closing(a.id)) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                KText(
                    stringResource(if (credit) R.string.import_owed_on else R.string.import_balance_on, shortDay(closing.day), i.currency.uppercase()),
                    Modifier.weight(1f),
                    style = KoshaTheme.type.small,
                    color = c.ink3,
                )
                if (closing.balance != null) MoneyText(closing.balance, i.currency)
                else KText(stringResource(R.string.import_optional), style = KoshaTheme.type.small, color = c.ink3)
            }
            KText(
                stringResource(if (closing.fromFile) R.string.import_closing_from_file else R.string.import_closing_hint),
                style = KoshaTheme.type.xs,
                color = c.ink3,
            )
            gaps.forEach { g ->
                Note(
                    when (g) {
                        is Gap.Uncovered -> stringResource(R.string.reconcile_gap_uncovered, shortDay(g.from), shortDay(g.to))
                        is Gap.Opening -> stringResource(R.string.reconcile_gap_opening, g.next, money(g.opening, i.currency), g.previous, money(g.closing, i.currency))
                    },
                )
            }
            if (foreign != null) Note(stringResource(R.string.import_foreign, foreign.file, foreign.currency!!.uppercase(), i.currency.uppercase()))
            stmts.filter { it.off != null && it.off != 0L }.forEach { Note(stringResource(R.string.import_off, it.file, money(kotlin.math.abs(it.off!!), i.currency))) }

            stmts.forEach { s ->
                Hairline()
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PhIcon(fileIcon(s.source), size = 18.dp)
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            KText(s.file, Modifier.weight(1f, fill = false), style = KoshaTheme.type.small, maxLines = 1)
                            if (s.byAi && s.source == "pdf") {
                                Sparkle(size = 11.dp)
                                KText(stringResource(R.string.reconcile_read_by_ai), style = KoshaTheme.type.xs, color = c.ink3, maxLines = 1)
                            }
                        }
                        KText(stmtLines(s.lines), style = KoshaTheme.type.xs, color = c.ink3)
                    }
                    IconButton("arrows-left-right", stringResource(R.string.import_move_label, s.file), { open(AcctSheet.Move(s.id)) })
                    IconButton("x", stringResource(R.string.import_remove, s.file), { model.remove(s) })
                }
                if (s.source == "csv" && s.layout != null && s.picked != null) {
                    Columns(
                        s.rows, s.layout, s.lines.size, columnsStatus(s.remembered, s.byAi, s.confident), s.byAi,
                        startOpen = !s.remembered && !s.confident,
                        onChange = { model.columns(s, it) },
                    )
                }
            }
        }
    }
}

// ---------- ImportPayees ----------

private const val PAGE = 60

private sealed interface PayeeSheet {
    data class Name(val group: String) : PayeeSheet
    data class Category(val group: String) : PayeeSheet
    data object Rest : PayeeSheet
}

@Composable
private fun ImportPayees(model: ImportModel) {
    val c = KoshaTheme.colors
    val m = meta() ?: return
    val plan = model.review ?: return
    val writes = canWrite()
    val list = plan.list
    val pairs = plan.pairs
    val total = plan.rows.size
    val open = list.filter { it.category.isEmpty() }
    var tab by rememberSaveable { mutableStateOf(if (open.isNotEmpty()) "check" else "all") }
    // Rows sorted while on "To check" stay in view until the tab changes, so a choice doesn't make its row jump away.
    var kept by remember { mutableStateOf(setOf<String>()) }
    var shown by rememberSaveable { mutableIntStateOf(PAGE) }
    var sheet by remember { mutableStateOf<PayeeSheet?>(null) }
    var rest by rememberSaveable { mutableStateOf("") }
    val categories = m.categories.filter { !it.isArchived && it.parentId == null && !Categories.isNeutral(it.id) }
    val accounts = model.live
    val nameOf = { acct: String -> accounts.firstOrNull { it.id == acct }?.let { model.info(it).name }.orEmpty() }
    val rows = when (tab) {
        "check" -> list.filter { g -> g.category.isEmpty() || g.raws.any { it in kept } }
        "all" -> list
        else -> emptyList()
    }
    fun go(t: String) {
        tab = t
        kept = emptySet()
        shown = PAGE
    }
    fun choose(g: Group, category: String) {
        if (tab == "check") kept = kept + g.raws
        model.edit(g, category = category)
    }
    val counted = list.filter { it.category != "skip" }.sumOf { it.lines.size } + pairs.size * 2

    Page(
        stringResource(R.string.import_check_payees),
        onBack = { model.step = ImportModel.Step.Accounts },
        subtitle = if (total == 0) stringResource(R.string.import_review_nothing)
        else listOfNotNull(
            pluralStringResource(R.plurals.app_imp_n_new, total, countText(total)),
            pluralStringResource(R.plurals.app_imp_n_payees, list.size, countText(list.size)),
            if (pairs.isNotEmpty()) pluralStringResource(R.plurals.app_imp_n_transfers, pairs.size, countText(pairs.size)) else null,
        ).joinToString(" · "),
        footer = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                KText(
                    when {
                        total == 0 -> stringResource(R.string.import_nothing_new)
                        open.isNotEmpty() -> pluralStringResource(R.plurals.import_without_category, open.size, countText(open.size))
                        else -> stringResource(R.string.import_ready)
                    },
                    Modifier.fillMaxWidth(),
                    style = KoshaTheme.type.xs,
                    color = c.ink3,
                    align = TextAlign.Center,
                )
                Button(
                    pluralStringResource(R.plurals.import_button, counted, countText(counted)),
                    { model.save() },
                    Modifier.fillMaxWidth(),
                    enabled = open.isEmpty() && counted > 0 && writes,
                )
            }
        },
    ) {
        if (total == 0) return@Page
        Segmented(
            listOfNotNull(
                "check" to stringResource(R.string.import_tab_check, countText(open.size)),
                "all" to stringResource(R.string.import_tab_all, countText(list.size)),
                if (pairs.isNotEmpty()) "transfers" to stringResource(R.string.import_tab_transfers, countText(pairs.size)) else null,
            ),
            tab,
            ::go,
            Modifier.fillMaxWidth(),
            small = true,
        )
        if (model.naming) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Sparkle(size = 12.dp)
                KText(stringResource(R.string.import_naming), style = KoshaTheme.type.xs, color = c.ink3)
            }
        }
        if (tab != "transfers") {
            if (rows.isEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PhIcon("check-circle", size = 16.dp, tint = c.pos)
                    KText(stringResource(R.string.import_all_sorted), style = KoshaTheme.type.small, color = c.ink3)
                }
            } else {
                Card(tight = true) {
                    rows.take(shown).forEachIndexed { n, g ->
                        if (n > 0) Hairline()
                        PayeeRow(g, m, nameOf, onName = { sheet = PayeeSheet.Name(g.id) }, onCategory = { sheet = PayeeSheet.Category(g.id) })
                    }
                    if (rows.size > shown) {
                        Hairline()
                        MoreRow(stringResource(R.string.import_show_more, countText(minOf(PAGE, rows.size - shown))), { shown += PAGE })
                    }
                }
            }
        }
        if (pairs.isNotEmpty() && tab != "all") {
            Section(stringResource(R.string.subs_group_transfers)) {
                pairs.groupBy { it.first.acct to it.second.acct }.forEach { (route, group) ->
                    TransferCard(model, m, route.first, route.second, group, nameOf)
                }
            }
        }
        if (tab == "check" && open.isNotEmpty()) {
            Card {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    KText(stringResource(R.string.import_rest), style = KoshaTheme.type.head)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        PickField(
                            categories.firstOrNull { it.id == rest }?.name ?: stringResource(R.string.import_rest_placeholder),
                            { sheet = PayeeSheet.Rest },
                            Modifier.weight(1f),
                            icon = categories.firstOrNull { it.id == rest }?.let { categoryIconName(it.icon) } ?: "squares-four",
                            muted = rest.isEmpty(),
                        )
                        Button(
                            stringResource(R.string.import_apply),
                            {
                                val cat = categories.firstOrNull { it.id == rest }
                                for (g in open) if (cat != null && cat.isIncome == g.income) choose(g, rest)
                                rest = ""
                            },
                            size = ButtonSize.Small,
                            enabled = rest.isNotEmpty(),
                        )
                    }
                    KText(
                        stringResource(R.string.import_leave_them_out),
                        Modifier.heightIn(min = 48.dp).clickable(role = Role.Button) { open.forEach { choose(it, "skip") } }.padding(vertical = 14.dp),
                        style = KoshaTheme.type.small,
                        color = c.ink2,
                    )
                }
            }
        }
    }

    when (val s = sheet) {
        is PayeeSheet.Name -> {
            val g = list.firstOrNull { it.id == s.group } ?: return
            TextSheet(stringResource(R.string.import_payee_for, g.lines[0].line.description), g.payee, { v -> if (v != g.payee) model.edit(g, payee = v) }) { sheet = null }
        }
        is PayeeSheet.Category -> {
            val g = list.firstOrNull { it.id == s.group } ?: return
            val label = { name: String -> if (g.income) R.string.import_transfer_from to name else R.string.import_transfer_to to name }
            Sheet(stringResource(R.string.import_category_for, g.payee), { sheet = null }) {
                ListPicker(
                    categories.filter { it.isIncome == g.income }.map { ListOption(it.id, it.name) } +
                        accounts.filter { a -> g.lines.none { it.acct == a.id } }.map { a ->
                            label(model.info(a).name).let { (id, n) -> ListOption("t:${a.id}", stringResource(id, n)) }
                        } +
                        model.wallets().filter { w -> accounts.none { it.target == w.id } }.map { w ->
                            label(w.name).let { (id, n) -> ListOption("w:${w.id}", stringResource(id, n)) }
                        } +
                        ListOption("skip", stringResource(R.string.import_leave_out)),
                    g.category,
                    { o -> choose(g, o.value); sheet = null },
                )
            }
        }
        PayeeSheet.Rest -> Sheet(stringResource(R.string.import_rest_label), { sheet = null }) {
            ListPicker(categories.map { ListOption(it.id, it.name) }, rest, { o -> rest = o.value; sheet = null })
        }
        null -> {}
    }
}

@Composable
private fun PayeeRow(g: Group, m: Meta, nameOf: (String) -> String, onName: () -> Unit, onCategory: () -> Unit) {
    val c = KoshaTheme.colors
    val r = span(g.lines.map { it.line })!!
    val sample = g.lines[0].line.description
    val cat = m.categories.firstOrNull { it.id == g.category }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            Modifier.weight(1f).clickable(role = Role.Button, onClick = onName),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                KText(g.payee, Modifier.weight(1f, fill = false), style = KoshaTheme.type.head, color = if (g.category == "skip") c.ink3 else c.ink)
                if (g.aiPayee) Sparkle(size = 11.dp)
            }
            if (!sample.equals(g.payee, ignoreCase = true)) {
                KText(sample, style = KoshaTheme.type.num.copy(fontSize = KoshaTheme.type.xs.fontSize), color = c.ink3, maxLines = 1)
            }
            KText(
                if (g.lines.size > 1) pluralStringResource(R.plurals.import_payments_range, g.lines.size, countText(g.lines.size), shortDay(r.first), shortDay(r.second))
                else shortDay(r.first),
                style = KoshaTheme.type.xs,
                color = c.ink3,
            )
        }
        val transferTo = when {
            g.category.startsWith("t:") -> nameOf(g.category.removePrefix("t:"))
            g.category.startsWith("w:") -> m.wallets.firstOrNull { it.id == g.category.removePrefix("w:") }?.name
            else -> null
        }
        when {
            g.category.isEmpty() -> WarnChip(stringResource(R.string.import_pick_category), onCategory)
            else -> Chip(
                when {
                    g.category == "skip" -> stringResource(R.string.import_leave_out)
                    transferTo != null -> stringResource(if (g.income) R.string.import_transfer_from else R.string.import_transfer_to, transferTo)
                    else -> cat?.name ?: g.category
                },
                Modifier.padding(top = 2.dp),
                small = true,
                onClick = onCategory,
                leading = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        PhIcon(
                            when {
                                g.category == "skip" -> "x"
                                transferTo != null -> "arrows-left-right"
                                else -> categoryIconName(cat?.icon)
                            },
                            size = 16.dp,
                        )
                    }
                },
            )
        }
        if (g.aiCategory && g.category.isNotEmpty()) Sparkle(Modifier.padding(top = 10.dp), size = 11.dp)
    }
}

/** "Pick a category", in warn, for a payee that still needs one. */
@Composable
private fun WarnChip(text: String, onClick: () -> Unit) {
    val c = KoshaTheme.colors
    Row(
        Modifier
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.height(32.dp).clip(CircleShape).border(1.dp, c.warn, CircleShape).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            PhIcon("plus", size = 16.dp, tint = c.warn)
            KText(text, style = KoshaTheme.type.small, color = c.warn, maxLines = 1)
        }
    }
}

/** The transfers found between two accounts: Not a transfer, or Leave out. */
@Composable
private fun TransferCard(model: ImportModel, m: Meta, from: String, to: String, pairs: List<Pair<Row, Row>>, nameOf: (String) -> String) {
    val c = KoshaTheme.colors
    val accts = model.live
    val r = span(pairs.map { it.first.line })!!
    fun colourOf(acct: String): Int {
        val a = accts.firstOrNull { it.id == acct } ?: return 0
        return model.info(a).wallet?.let { m.colour(it.id) } ?: (m.wallets.size + accts.indexOf(a))
    }
    val toCard = accts.firstOrNull { it.id == to }?.let { model.info(it).type } == AccountType.Credit
    Card {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.Center) {
                AccountDot(c.account(colourOf(from)), Modifier.align(Alignment.CenterVertically))
                KText(nameOf(from), style = KoshaTheme.type.head)
                PhIcon("arrow-right", Modifier.align(Alignment.CenterVertically), size = 14.dp, tint = c.ink3)
                AccountDot(c.account(colourOf(to)), Modifier.align(Alignment.CenterVertically))
                KText(nameOf(to), style = KoshaTheme.type.head)
            }
            KText(
                listOfNotNull(
                    if (pairs.size > 1) pluralStringResource(R.plurals.import_payments_range, pairs.size, countText(pairs.size), shortDay(r.first), shortDay(r.second))
                    else shortDay(r.first),
                    if (toCard) stringResource(R.string.app_imp_card_bill) else null,
                ).joinToString(" · "),
                style = KoshaTheme.type.xs,
                color = c.ink3,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(stringResource(R.string.import_not_transfer), { model.unpair(pairs) }, style = ButtonStyle.Secondary, size = ButtonSize.Small)
                Button(stringResource(R.string.import_leave_out), { model.leaveOut(pairs) }, style = ButtonStyle.Secondary, size = ButtonSize.Small)
            }
        }
    }
}

// ---------- saving ----------

@Composable
private fun ImportSaving(model: ImportModel) {
    val c = KoshaTheme.colors
    val error = model.error
    Page(stringResource(R.string.import_importing), onBack = null) {
        Card {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (error != null) {
                    Note(stringResource(R.string.import_failed, error.ifBlank { stringResource(R.string.auth_error_offline).trimEnd('.') }))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(stringResource(R.string.common_try_again), { model.save() }, size = ButtonSize.Medium)
                        Button(stringResource(R.string.import_back_to_review), { model.step = ImportModel.Step.Review }, style = ButtonStyle.Secondary, size = ButtonSize.Medium)
                    }
                } else {
                    KText(
                        when (val d = model.doing) {
                            is Doing.Setup -> stringResource(R.string.import_progress_setup, d.name)
                            is Doing.Adding -> pluralStringResource(R.plurals.import_progress_adding, d.count, countText(d.count), d.name)
                            Doing.Transfers -> stringResource(R.string.import_progress_transfers)
                            is Doing.Checking -> stringResource(R.string.import_progress_checking, d.name)
                            Doing.Finished, null -> stringResource(R.string.common_done)
                        } + "…",
                        style = KoshaTheme.type.small,
                    )
                    Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(c.soft)) {
                        Box(Modifier.fillMaxWidth(model.progress).height(6.dp).clip(CircleShape).background(c.brand))
                    }
                }
            }
        }
    }
}

// ---------- ImportDone ----------

@Composable
private fun ImportDone(model: ImportModel) {
    val nav = LocalNav.current
    val c = KoshaTheme.colors
    val m = meta() ?: return
    val result = model.result ?: return
    var recurring by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(c.bg).statusBarsPadding().navigationBarsPadding()) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.height(56.dp))
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(72.dp).clip(RoundedCornerShape(24.dp)).background(c.pos.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                    PhIcon("tray-arrow-down", size = 32.dp, tint = c.pos, fill = true)
                }
                KText(stringResource(R.string.import_done_title), style = KoshaTheme.type.title, align = TextAlign.Center)
                val lines = pluralStringResource(R.plurals.import_transactions, result.lines, countText(result.lines))
                val accounts = pluralStringResource(R.plurals.app_imp_n_accounts, result.accounts.size, countText(result.accounts.size))
                KText(
                    if (result.transfers > 0) stringResource(
                        R.string.app_imp_done_summary_transfers, lines, accounts,
                        pluralStringResource(R.plurals.app_imp_n_transfers, result.transfers, countText(result.transfers)),
                    ) else stringResource(R.string.app_imp_done_summary, lines, accounts),
                    Modifier.padding(horizontal = 24.dp),
                    style = KoshaTheme.type.small,
                    color = c.ink2,
                    align = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(12.dp))
            Card(tight = true) {
                result.accounts.forEachIndexed { i, a ->
                    if (i > 0) Hairline()
                    ListRow {
                        AccountDot(c.account(m.colour(a.walletId).takeIf { it >= 0 } ?: i), size = 10.dp)
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                KText(a.name, Modifier.weight(1f, fill = false), maxLines = 1)
                                if (a.isNew) Pill(stringResource(R.string.overview_chip_new))
                            }
                            when {
                                a.reconciled != null -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    PhIcon("seal-check", size = 14.dp, tint = c.pos)
                                    KText(stringResource(R.string.import_done_matches, shortDay(a.reconciled)), style = KoshaTheme.type.xs, color = c.pos)
                                }
                                a.checkPdf -> KText(stringResource(R.string.app_imp_check_pdf), style = KoshaTheme.type.xs, color = c.warn)
                                else -> KText(pluralStringResource(R.plurals.import_transactions, a.lines, countText(a.lines)), style = KoshaTheme.type.xs, color = c.ink3)
                            }
                        }
                        KText(countText(a.lines), style = KoshaTheme.type.num.copy(fontSize = KoshaTheme.type.small.fontSize), color = c.ink2)
                    }
                }
            }
            if (result.recurring.isNotEmpty()) {
                Card(Modifier.clickable(role = Role.Button) {
                    if (result.recurring.size == 1) nav.navigate(Route.RecurringForm(fromPayee = result.recurring[0])) else recurring = true
                }) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        IconTile("arrows-clockwise")
                        Column(Modifier.weight(1f)) {
                            KText(pluralStringResource(R.plurals.app_imp_recurring, result.recurring.size, countText(result.recurring.size)), style = KoshaTheme.type.head)
                            KText(result.recurring.take(3).joinToString(", "), style = KoshaTheme.type.xs, color = c.ink3, maxLines = 2)
                        }
                        PhIcon("caret-right", size = 16.dp, tint = c.ink3)
                    }
                }
            }
        }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(stringResource(R.string.import_done_overview), { if (!nav.popBackStack(Route.Overview, inclusive = false)) nav.navigate(Route.Overview) }, Modifier.fillMaxWidth())
            Button(
                stringResource(R.string.import_done_transactions),
                { nav.navigate(Route.Activity) { popUpTo(Route.Overview) } },
                Modifier.fillMaxWidth(),
                style = ButtonStyle.Secondary,
            )
        }
    }
    if (recurring) {
        Sheet(pluralStringResource(R.plurals.app_imp_recurring, result.recurring.size, countText(result.recurring.size)), { recurring = false }) {
            Column {
                result.recurring.forEach { payee ->
                    ListRow(onClick = { recurring = false; nav.navigate(Route.RecurringForm(fromPayee = payee)) }) {
                        KText(payee, Modifier.weight(1f))
                        PhIcon("caret-right", size = 16.dp, tint = c.ink3)
                    }
                }
            }
        }
    }
}
