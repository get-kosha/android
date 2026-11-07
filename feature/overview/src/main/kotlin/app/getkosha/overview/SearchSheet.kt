package app.getkosha.overview

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.getkosha.data.Queued
import app.getkosha.design.AccountDot
import app.getkosha.design.Caption
import app.getkosha.design.Field
import app.getkosha.design.IconTile
import app.getkosha.design.KText
import app.getkosha.design.KoshaTheme
import app.getkosha.design.PhIcon
import app.getkosha.design.R
import app.getkosha.design.Sparkle
import app.getkosha.design.Toast
import app.getkosha.design.categoryIconName
import app.getkosha.design.dayLabel
import app.getkosha.model.Categories
import app.getkosha.model.Dates
import app.getkosha.model.Meta
import app.getkosha.model.Money
import app.getkosha.model.NewTransaction
import app.getkosha.model.Payees
import app.getkosha.model.Sign
import app.getkosha.model.Transactions
import app.getkosha.model.guessCategory
import app.getkosha.network.ApiException
import app.getkosha.ui.ActivitySearch
import app.getkosha.ui.AmountText
import app.getkosha.ui.LocalNav
import app.getkosha.ui.LocalToasts
import app.getkosha.ui.Route
import app.getkosha.ui.canWrite
import app.getkosha.ui.meta
import app.getkosha.ui.openActivity
import app.getkosha.ui.openTab
import app.getkosha.ui.rememberKosha
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.io.IOException
import java.util.UUID

@Serializable
private data class Ids(val ids: List<String>)

/**
 * Search and quick add from one field, full screen over Overview: "12.50 rewe yesterday" becomes a Quick add row
 * (saved through create_transactions with its own id, or queued offline), then payees, categories, accounts and
 * pages to go to, "Search activity for …", and "Ask …" when Ask Kosha is on. Ported from search-palette.tsx.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchSheet(onDismiss: () -> Unit) {
    val kosha = rememberKosha()
    val nav = LocalNav.current
    val toasts = LocalToasts.current
    val res = LocalResources.current
    val m = meta() ?: return
    val c = KoshaTheme.colors
    val locale = KoshaTheme.locale
    val scope = rememberCoroutineScope()
    val writable = canWrite()
    var q by remember { mutableStateOf("") }
    var payees by remember { mutableStateOf<List<app.getkosha.model.Payee>>(emptyList()) }
    val text = q.trim()
    val low = text.lowercase()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    LaunchedEffect(text) {
        if (text.isEmpty()) {
            payees = emptyList()
            return@LaunchedEffect
        }
        delay(150)
        payees = runCatching { kosha.api.get<Payees>("/api/v1/payees", mapOf("query" to text.take(40), "limit" to 5)).payees }.getOrDefault(payees)
    }
    val wallets = m.wallets.filter { !it.isArchived }
    val categories = m.categories.filter { !it.isArchived && !Categories.isSystem(it.id) }
    val parsed = remember(text, m) {
        if (text.isEmpty()) null else QuickAdd.parse(text, wallets.map { Named(it.id, it.name) }, categories.map { Named(it.id, it.name) }, locale.language)
    }
    val tz = kosha.tz
    val today = Dates.today(tz)
    val close = { route: () -> Unit -> onDismiss(); route() }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(Modifier.fillMaxSize().background(c.surface).statusBarsPadding().navigationBarsPadding().imePadding()) {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Field(
                    q, { q = it }, Modifier.weight(1f).focusRequester(focus),
                    placeholder = stringResource(R.string.shell_search_placeholder), icon = "magnifying-glass",
                    keyboard = KeyboardOptions(imeAction = ImeAction.Search),
                )
                Box(Modifier.heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onDismiss).padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
                    KText(stringResource(R.string.common_cancel), color = c.ink2)
                }
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 8.dp)) {
                val p = parsed
                if (p?.amount != null) {
                    val account = wallets.firstOrNull { it.id == p.walletId } ?: defaultAccount(m, kosha.local.lastAccount)
                    val categoryId = p.categoryId ?: guessCategory(p.payee, m.rules)
                        ?: payees.firstOrNull { it.payee.equals(p.payee, ignoreCase = true) }?.categoryId
                    val category = m.categories.firstOrNull { it.id == categoryId }
                    val day = if (p.date == QuickDate.Yesterday) today.minusDays(1) else today
                    val signed = if (p.income) p.amount else -p.amount
                    if (account != null) {
                        Group(stringResource(R.string.shell_search_quick_add))
                        val amountText = Money.format(signed, account.currency, locale, Sign.Always)
                        Column(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.soft)
                                .clickable(role = Role.Button) {
                                    if (!writable) {
                                        toasts.show(Toast(res.getString(R.string.common_demo_read_only)))
                                        return@clickable
                                    }
                                    val entry = NewTransaction(
                                        id = UUID.randomUUID().toString(), accountId = account.id, payee = p.payee, amount = signed,
                                        categoryId = categoryId, day = Dates.text(day), tags = p.tags,
                                    )
                                    scope.launch {
                                        try {
                                            kosha.api.post<Map<String, List<NewTransaction>>, Transactions>("/api/v1/transactions", mapOf("transactions" to listOf(entry)))
                                            kosha.local.lastAccount = account.id
                                            kosha.changed()
                                            toasts.show(
                                                Toast(res.getString(R.string.tx_added_toast, amountText, p.payee), res.getString(R.string.common_undo)) {
                                                    scope.launch {
                                                        runCatching { kosha.api.post<Ids, kotlinx.serialization.json.JsonObject>("/api/v1/transactions/delete", Ids(listOf(entry.id))) }
                                                        kosha.changed()
                                                    }
                                                },
                                            )
                                            onDismiss()
                                        } catch (e: IOException) {
                                            kosha.outbox.add(Queued(entry, account.currency, account.name, category?.name ?: ""))
                                            kosha.sendOutbox()
                                            toasts.show(
                                                Toast(res.getString(R.string.app_ov_saved_offline, amountText, p.payee), res.getString(R.string.common_undo)) {
                                                    scope.launch { kosha.outbox.remove(entry.id) }
                                                },
                                            )
                                            onDismiss()
                                        } catch (e: ApiException) {
                                            toasts.show(Toast(e.message ?: ""))
                                        }
                                    }
                                }
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(Modifier.heightIn(min = 36.dp).clip(RoundedCornerShape(10.dp)).background(c.surface).padding(9.dp)) { PhIcon("plus", size = 18.dp) }
                                AmountText(
                                    AnnotatedString(stringResource(R.string.app_ov_quick_add_row, amountText, p.payee.ifEmpty { category?.name ?: "" })),
                                    Modifier.weight(1f), style = KoshaTheme.type.body.copy(fontWeight = FontWeight.Medium),
                                )
                            }
                            FlowRow(Modifier.padding(start = 44.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (category != null) Tag(category.name) { PhIcon(categoryIconName(category.icon), size = 14.dp) }
                                Tag(account.name) { AccountDot(c.account(m.wallets.indexOf(account))) }
                                Tag(dayLabel(day, today)) { PhIcon("calendar-blank", size = 14.dp) }
                                p.tags.forEach { Tag("#$it") {} }
                            }
                        }
                    }
                }
                if (payees.isNotEmpty()) {
                    Group(stringResource(R.string.shell_search_payees))
                    payees.forEach { py ->
                        val cat = m.categories.firstOrNull { it.id == py.categoryId }
                        Result(
                            icon = { IconTile(categoryIconName(cat?.icon)) },
                            title = py.payee,
                            sub = py.averageAmount?.let { avg ->
                                stringResource(R.string.shell_search_payee_usually, py.count.toString(), Money.format(kotlin.math.abs(avg), py.currency ?: m.primary, locale))
                            },
                            end = "arrow-up-right",
                        ) { close { nav.openActivity(ActivitySearch(query = py.payee)) } }
                    }
                }
                val cats = if (text.isEmpty()) emptyList() else categories.filter { it.name.lowercase().contains(low) }.take(4)
                val accounts = if (text.isEmpty()) emptyList() else wallets.filter { it.name.lowercase().contains(low) }.take(3)
                val pages = listOf(
                    Triple("arrows-clockwise", stringResource(R.string.shell_nav_recurring)) { nav.openTab(Route.Recurring) },
                    Triple("bank", stringResource(R.string.shell_nav_accounts)) { nav.openTab(Route.Accounts) },
                    Triple("tray-arrow-down", stringResource(R.string.shell_page_import)) { nav.navigate(Route.Import) },
                    Triple("scales", stringResource(R.string.shell_page_reconcile)) { nav.navigate(Route.Reconcile()) },
                    Triple("gear", stringResource(R.string.shell_settings)) { nav.navigate(Route.Settings()) },
                ).filter { low.isEmpty() || it.second.lowercase().contains(low) }
                if (cats.isNotEmpty() || accounts.isNotEmpty() || pages.isNotEmpty() || text.isNotEmpty()) Group(stringResource(R.string.shell_search_go_to))
                if (text.isNotEmpty()) {
                    Result(icon = { IconTile("magnifying-glass") }, title = stringResource(R.string.shell_search_activity, text)) {
                        close { nav.openActivity(ActivitySearch(query = text.take(40))) }
                    }
                }
                cats.forEach { cat ->
                    Result(icon = { IconTile(categoryIconName(cat.icon)) }, title = cat.name, sub = stringResource(R.string.shell_nav_activity)) {
                        close { nav.openActivity(ActivitySearch(categoryId = cat.id)) }
                    }
                }
                accounts.forEach { a ->
                    Result(icon = { IconTile("wallet") }, title = a.name, sub = stringResource(R.string.shell_nav_activity)) {
                        close { nav.openActivity(ActivitySearch(accountId = a.id)) }
                    }
                }
                pages.forEach { (icon, label, go) -> Result(icon = { IconTile(icon) }, title = label, end = "arrow-right") { close(go) } }
                if (text.isNotEmpty() && m.ai.ask) {
                    Result(
                        icon = {
                            Box(Modifier.heightIn(min = 36.dp).clip(RoundedCornerShape(10.dp)).background(c.soft).padding(9.dp)) { Sparkle(size = 18.dp) }
                        },
                        title = stringResource(R.string.shell_search_ask, text),
                        sub = stringResource(R.string.shell_search_ask_hint),
                    ) { close { nav.navigate(Route.Ask(text)) } }
                }
            }
        }
    }
}

/** The account a quick add uses when the line names none: Settings › General › Default account, else the last used. */
internal fun defaultAccount(m: Meta, last: String?) = m.wallets.filter { !it.isArchived }.let { list ->
    val pick = m.pref("default_wallet", "last").let { if (it == "last") last else it }
    list.firstOrNull { it.id == pick } ?: list.firstOrNull()
}

@Composable
private fun Group(label: String) {
    Caption(label, Modifier.padding(start = 8.dp, end = 8.dp, top = 14.dp, bottom = 6.dp).semantics { heading() })
}

@Composable
private fun Result(icon: @Composable () -> Unit, title: String, sub: String? = null, end: String? = null, onClick: () -> Unit) {
    val c = KoshaTheme.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button, onClick = onClick).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        icon()
        Column(Modifier.weight(1f)) {
            KText(title, maxLines = 2)
            if (sub != null) AmountText(AnnotatedString(sub), style = KoshaTheme.type.xs, color = c.ink3)
        }
        if (end != null) PhIcon(end, size = 16.dp, tint = c.ink3)
    }
}

@Composable
private fun Tag(text: String, leading: @Composable () -> Unit) {
    val c = KoshaTheme.colors
    Row(
        Modifier.height(22.dp).clip(CircleShape).background(c.surface).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        leading()
        KText(text, style = KoshaTheme.type.xs, color = c.ink2, maxLines = 1)
    }
}
