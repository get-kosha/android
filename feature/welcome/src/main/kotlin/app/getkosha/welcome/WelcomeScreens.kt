@file:UseSerializers(RoundedLong::class)

package app.getkosha.welcome

import kotlinx.serialization.UseSerializers
import app.getkosha.model.RoundedLong

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import app.getkosha.design.AccountDot
import app.getkosha.design.AmountDisplay
import app.getkosha.design.AmountKeypad
import app.getkosha.design.Button
import app.getkosha.design.ButtonStyle
import app.getkosha.design.Card
import app.getkosha.design.Chip
import app.getkosha.design.Field
import app.getkosha.design.IconButton
import app.getkosha.design.IconTile
import app.getkosha.design.KText
import app.getkosha.design.KoshaTheme
import app.getkosha.design.ListOption
import app.getkosha.design.ListPicker
import app.getkosha.design.PhIcon
import app.getkosha.design.R
import app.getkosha.design.Radio
import app.getkosha.design.Sheet
import app.getkosha.design.Toast
import app.getkosha.model.Account
import app.getkosha.model.AccountType
import app.getkosha.model.Dates
import app.getkosha.model.EntryKind
import app.getkosha.model.KoshaJson
import app.getkosha.model.Money
import app.getkosha.model.convert
import app.getkosha.network.ApiException
import app.getkosha.ui.Banners
import app.getkosha.ui.LocalNav
import app.getkosha.ui.LocalToasts
import app.getkosha.ui.MoneyText
import app.getkosha.ui.Route
import app.getkosha.ui.hideAmounts
import app.getkosha.ui.meta
import app.getkosha.ui.rememberKosha
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.io.IOException
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.ZoneId
import java.util.Currency
import java.util.Locale
import java.util.UUID

/** The welcome screens, registered in the app's NavHost. */
fun NavGraphBuilder.welcomeScreens() {
    composable<Route.Welcome> { Welcome() }
}

@Serializable
private data class NetWorth(val currency: String, val total: Long)

private val RowsSaver = Saver<MutableState<List<NewAccount>>, String>(
    save = { KoshaJson.encodeToString(ListSerializer(NewAccount.serializer()), it.value) },
    restore = { mutableStateOf(KoshaJson.decodeFromString(ListSerializer(NewAccount.serializer()), it)) },
)

/** The accounts Welcome suggests: the first three start the list, the rest are one tap away (welcome.tsx suggested). */
@Composable
private fun suggestions(): List<Pair<String, AccountType>> = listOf(
    stringResource(R.string.welcome_suggest_current) to AccountType.Bank,
    stringResource(R.string.welcome_type_credit_card) to AccountType.Credit,
    stringResource(R.string.welcome_suggest_investments) to AccountType.Investment,
    stringResource(R.string.welcome_suggest_savings) to AccountType.Bank,
    stringResource(R.string.welcome_type_cash) to AccountType.Cash,
    stringResource(R.string.welcome_suggest_pension) to AccountType.Investment,
)

/** A currency's name in the app's language, or its code. */
private fun currencyName(code: String, locale: Locale) =
    runCatching { Currency.getInstance(code.uppercase()).getDisplayName(locale) }.getOrDefault(code.uppercase())

/**
 * First run, shown while the account has no accounts: (1) name, main currency and time zone; (2) the accounts,
 * each with the figures people know today, and a live net worth; (3) done, with Import statements next. Every step
 * can be skipped. The demo account can't save, so it goes straight to Overview.
 */
@Composable
private fun Welcome() {
    val nav = LocalNav.current
    val kosha = rememberKosha()
    val m = meta()
    val toasts = LocalToasts.current
    val scope = rememberCoroutineScope()
    val locale = KoshaTheme.locale
    val finish = { then: Route? ->
        nav.navigate(Route.Overview) { popUpTo(Route.Welcome) { inclusive = true } }
        if (then != null) nav.navigate(then)
    }
    LaunchedEffect(m?.demo) { if (m?.demo == true) finish(null) }
    if (m == null || m.demo) return

    var step by rememberSaveable { mutableStateOf("you") }
    // A new account still has the server's defaults, so start from the phone's zone and region instead (welcome.tsx).
    val fresh = m.wallets.isEmpty()
    var name by rememberSaveable { mutableStateOf(m.user?.name ?: "") }
    var currency by rememberSaveable {
        mutableStateOf(
            if (!fresh) m.primary
            else runCatching { Currency.getInstance(Locale.getDefault()).currencyCode.lowercase() }.getOrNull()?.takeIf { it in m.rates } ?: m.primary,
        )
    }
    var tz by rememberSaveable { mutableStateOf(if (fresh) ZoneId.systemDefault().id else m.tz) }
    var rows by rememberSaveable(saver = RowsSaver) { mutableStateOf(emptyList()) }
    var added by rememberSaveable { mutableStateOf(listOf<String>()) } // the new accounts' currencies
    var busy by remember { mutableStateOf(false) }
    var checked by remember { mutableStateOf(false) }
    val offline = stringResource(R.string.common_offline_title)
    val starting = suggestions().take(3)

    suspend fun attempt(block: suspend () -> Unit) {
        busy = true
        try {
            block()
        } catch (e: IOException) {
            toasts.show(Toast(offline))
        } catch (e: ApiException) {
            toasts.show(Toast(e.message ?: ""))
        } finally {
            busy = false
        }
    }

    BackHandler(step == "accounts") { step = "you" }

    when (step) {
        "you" -> Step(
            at = 1,
            onSkip = { finish(null) },
            footer = {
                KText(stringResource(R.string.welcome_change_later), Modifier.fillMaxWidth(), style = KoshaTheme.type.xs, color = KoshaTheme.colors.ink3, align = TextAlign.Center)
                Button(
                    stringResource(if (busy) R.string.welcome_saving else R.string.common_continue),
                    {
                        if (name.isBlank()) {
                            checked = true
                            return@Button
                        }
                        scope.launch {
                            attempt {
                                if (name.trim() != m.user?.name) {
                                    kosha.api.send("PATCH", "/api/app/profile", buildJsonObject { put("name", JsonPrimitive(name.trim())) })
                                }
                                kosha.api.send(
                                    "PATCH",
                                    "/api/v1/settings",
                                    buildJsonObject {
                                        put("primaryCurrency", JsonPrimitive(currency))
                                        put("timeZone", JsonPrimitive(tz))
                                        put("currencyOrder", JsonArray((listOf(currency) + m.order.filter { it != currency }).map(::JsonPrimitive)))
                                    },
                                )
                                kosha.changed()
                                // Rows still on the old main currency follow the new one.
                                rows = if (rows.isEmpty()) {
                                    starting.filter { s -> m.wallets.none { it.name.equals(s.first, ignoreCase = true) } }
                                        .map { NewAccount(UUID.randomUUID().toString(), it.first, it.second, currency) }
                                } else rows.map { if (it.currency == m.primary) it.copy(currency = currency) else it }
                                checked = false
                                step = "accounts"
                            }
                        }
                    },
                    Modifier.fillMaxWidth(),
                    enabled = !busy,
                )
            },
        ) {
            KText(stringResource(R.string.welcome_title), Modifier.semantics { heading() }, style = KoshaTheme.type.page.copy(fontSize = 30.sp))
            KText(stringResource(R.string.welcome_sub), style = KoshaTheme.type.small, color = KoshaTheme.colors.ink2)
            YouStep(name, { name = it }, checked, currency, { currency = it }, tz, { tz = it })
        }

        "accounts" -> {
            val total = previewNetWorth(rows, currency, m.rates) + m.wallets.filterNot { it.isArchived }.sumOf {
                convert(accountWorth(it.type, it.balance, it.creditLimit, it.gain), it.currency, currency, m.rates)
            }
            Step(
                at = 2,
                onSkip = null,
                footer = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(stringResource(R.string.welcome_skip), { finish(null) }, Modifier.width(120.dp), style = ButtonStyle.Secondary)
                        Button(
                            when {
                                busy -> stringResource(R.string.welcome_adding)
                                rows.isEmpty() -> stringResource(R.string.welcome_add_an_account)
                                else -> pluralStringResource(R.plurals.welcome_add_accounts, rows.size, rows.size)
                            },
                            {
                                if (rows.any { it.name.isBlank() }) {
                                    checked = true
                                    return@Button
                                }
                                scope.launch {
                                    attempt {
                                        val today = Dates.text(Dates.today(tz))
                                        val gains = mutableListOf<JsonObject>()
                                        // One at a time, dropping each row once saved, so a retry never adds one twice.
                                        for (row in rows) {
                                            val o = row.opening()
                                            val account = kosha.api.post<JsonObject, Account>(
                                                "/api/v1/accounts",
                                                buildJsonObject {
                                                    put("name", JsonPrimitive(row.name.trim()))
                                                    put("type", JsonPrimitive(row.type.name.lowercase()))
                                                    put("currency", JsonPrimitive(row.currency))
                                                    o.creditLimit?.let { put("creditLimit", JsonPrimitive(it)) }
                                                    put("balance", JsonPrimitive(o.balance))
                                                },
                                            )
                                            o.gain?.let { g ->
                                                gains += buildJsonObject {
                                                    put("accountId", JsonPrimitive(account.id))
                                                    put("day", JsonPrimitive(today))
                                                    put("gain", JsonPrimitive(g))
                                                }
                                            }
                                            added = added + row.currency
                                            rows = rows.filterNot { it.key == row.key }
                                        }
                                        if (gains.isNotEmpty()) kosha.api.send("POST", "/api/v1/gains", buildJsonObject { put("gains", JsonArray(gains)) })
                                        kosha.changed()
                                        step = "done"
                                    }
                                    if (added.isNotEmpty() && step != "done") kosha.changed()
                                }
                            },
                            Modifier.weight(1f),
                            enabled = !busy && rows.isNotEmpty(),
                        )
                    }
                },
            ) {
                KText(stringResource(R.string.welcome_accounts_title), Modifier.semantics { heading() }, style = KoshaTheme.type.title)
                KText(stringResource(R.string.welcome_accounts_sub), style = KoshaTheme.type.small, color = KoshaTheme.colors.ink2)
                Card(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        KText(stringResource(R.string.welcome_net_worth), Modifier.weight(1f), style = KoshaTheme.type.small, color = KoshaTheme.colors.ink3)
                        MoneyText(total, currency, style = KoshaTheme.type.num.copy(fontWeight = KoshaTheme.type.head.fontWeight))
                    }
                }
                if (rows.isEmpty()) KText(stringResource(R.string.welcome_rows_empty), Modifier.fillMaxWidth(), style = KoshaTheme.type.small, color = KoshaTheme.colors.ink3, align = TextAlign.Center)
                rows.forEachIndexed { i, row ->
                    AccountCard(
                        row,
                        index = m.wallets.size + i,
                        primary = currency,
                        rates = m.rates,
                        checked = checked,
                        onChange = { next -> rows = rows.map { if (it.key == row.key) next else it } },
                        onRemove = { rows = rows.filterNot { it.key == row.key } },
                    )
                }
                AddChips(suggestions(), rows.map { it.name } + m.wallets.map { it.name }) { s ->
                    rows = rows + NewAccount(UUID.randomUUID().toString(), s.first, s.second, currency)
                }
            }
        }

        else -> Done(added, onImport = { finish(Route.Import) }, onSkip = { finish(null) })
    }
}

/** The frame of a step: where you are (step N of 2), the content, and the actions at the bottom. */
@Composable
private fun Step(at: Int, onSkip: (() -> Unit)?, footer: @Composable ColumnScope.() -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val c = KoshaTheme.colors
    Column(Modifier.fillMaxSize().background(c.bg).statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 12.dp).heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val label = stringResource(R.string.welcome_progress_step, at.toString())
            KText(label, style = KoshaTheme.type.xs, color = c.ink3)
            Row(Modifier.weight(1f).semantics { contentDescription = label }, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(2) { n -> Box(Modifier.weight(1f).height(4.dp).clip(CircleShape).background(if (n < at) c.brand else c.sel)) }
            }
            if (onSkip != null) {
                Box(Modifier.heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onSkip).padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                    KText(stringResource(R.string.welcome_skip), style = KoshaTheme.type.small, color = c.ink3)
                }
            }
        }
        Banners()
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 18.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
        Column(
            Modifier.fillMaxWidth().background(c.glass).navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = footer,
        )
    }
}

/** A field-like button that opens a picker. */
@Composable
private fun PickField(onClick: () -> Unit, content: @Composable () -> Unit) {
    val c = KoshaTheme.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(14.dp)).background(c.surface)
            .border(1.dp, c.line, RoundedCornerShape(14.dp)).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.weight(1f)) { content() }
        PhIcon("caret-right", size = 16.dp, tint = c.ink3)
    }
}

/** Step 1: name, main currency and time zone. */
@Composable
private fun YouStep(name: String, onName: (String) -> Unit, checked: Boolean, currency: String, onCurrency: (String) -> Unit, tz: String, onTz: (String) -> Unit) {
    val c = KoshaTheme.colors
    val m = meta() ?: return
    val locale = KoshaTheme.locale
    var sheet by remember { mutableStateOf<String?>(null) }
    Spacer(Modifier.height(4.dp))
    Column {
        Label(stringResource(R.string.welcome_name_title))
        Field(
            name,
            onName,
            error = if (checked && name.isBlank()) stringResource(R.string.welcome_name_error) else null,
            keyboard = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
        )
        Under(stringResource(R.string.welcome_name_description))
    }
    Column {
        Label(stringResource(R.string.welcome_currency_title))
        PickField({ sheet = "currency" }) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                KText(currency.uppercase(), style = KoshaTheme.type.num.copy(fontWeight = KoshaTheme.type.head.fontWeight))
                KText(currencyName(currency, locale), color = c.ink3, maxLines = 1)
            }
        }
        Under(stringResource(R.string.welcome_currency_description))
    }
    Column {
        Label(stringResource(R.string.welcome_tz_title))
        PickField({ sheet = "zone" }) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                KText(zoneCity(tz))
                KText(tz, style = KoshaTheme.type.small, color = c.ink3, maxLines = 1)
            }
        }
        Under(stringResource(R.string.welcome_tz_description))
    }
    when (sheet) {
        "currency" -> CurrencySheet(m.rates.keys, currency, { onCurrency(it); sheet = null }, { sheet = null })
        "zone" -> Sheet(stringResource(R.string.welcome_tz_title), { sheet = null }) {
            val options = remember { zoneOptions() }
            ListPicker(options, tz, { onTz(it.value); sheet = null })
        }
    }
}

private fun zoneCity(id: String) = id.substringAfterLast('/').replace('_', ' ')

/** Region/city zones, the phone's first. */
private fun zoneOptions(): List<ListOption> {
    val phone = ZoneId.systemDefault().id
    val all = ZoneId.getAvailableZoneIds().filter { '/' in it && !it.startsWith("Etc/") && !it.startsWith("SystemV/") && !it.startsWith("US/") && it.first().isUpperCase() }
    return (listOf(phone) + all.filter { it != phone }.sortedBy(::zoneCity)).map { ListOption(it, zoneCity(it), it) }
}

/** Every currency with an exchange rate that the phone can name, searchable. */
@Composable
private fun CurrencySheet(codes: Set<String>, selected: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val locale = KoshaTheme.locale
    val options = remember(codes, locale) {
        codes.filter { it.length == 3 && runCatching { Currency.getInstance(it.uppercase()) }.isSuccess }
            .map { ListOption(it, currencyName(it, locale), it.uppercase()) }
            .sortedBy { it.label }
    }
    Sheet(stringResource(R.string.welcome_currency_label), onDismiss) { ListPicker(options, selected, { onPick(it.value) }) }
}

@Composable
private fun Label(text: String) = KText(text, Modifier.padding(bottom = 6.dp), style = KoshaTheme.type.small, color = KoshaTheme.colors.ink3)

@Composable
private fun Under(text: String) = KText(text, Modifier.padding(top = 6.dp), style = KoshaTheme.type.xs, color = KoshaTheme.colors.ink3)

/** A number as the field shows it: grouped, with the currency's decimals, no symbol. */
private fun plain(minor: Long, currency: String, locale: Locale): String =
    NumberFormat.getNumberInstance(locale).apply {
        val d = Money.decimals(currency)
        minimumFractionDigits = d
        maximumFractionDigits = d
    }.format(BigDecimal.valueOf(minor, 2))

/**
 * One account to add: its colour, name (typed in place), type, currency and remove; then its figures, each on the
 * keypad; and a line on what a card has left, what an investment has made, or its value in the main currency.
 */
@Composable
private fun AccountCard(
    row: NewAccount,
    index: Int,
    primary: String,
    rates: Map<String, Double>,
    checked: Boolean,
    onChange: (NewAccount) -> Unit,
    onRemove: () -> Unit,
) {
    val c = KoshaTheme.colors
    val locale = KoshaTheme.locale
    var sheet by remember { mutableStateOf<String?>(null) }
    val o = row.opening()
    val (first, second) = when (row.type) {
        AccountType.Credit -> R.string.welcome_field_owed to R.string.welcome_field_credit_limit
        AccountType.Investment -> R.string.welcome_field_put_in to R.string.welcome_field_value_today
        else -> R.string.welcome_field_balance to null
    }
    val typeLabel = stringResource(
        when (row.type) {
            AccountType.Bank -> R.string.welcome_type_bank
            AccountType.Cash -> R.string.welcome_type_cash
            AccountType.Credit -> R.string.welcome_type_card
            AccountType.Investment -> R.string.welcome_type_investment
        },
    )
    val named = row.name.trim()
    val nameLabel = stringResource(R.string.welcome_account_name_label)
    Card(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AccountDot(c.account(index), size = 10.dp)
                BasicTextField(
                    row.name,
                    { onChange(row.copy(name = it)) },
                    Modifier.weight(1f).semantics { contentDescription = nameLabel },
                    textStyle = KoshaTheme.type.head.copy(color = c.ink),
                    cursorBrush = SolidColor(c.selection),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    decorationBox = { inner ->
                        Box(Modifier.heightIn(min = 40.dp), contentAlignment = Alignment.CenterStart) {
                            if (row.name.isEmpty()) KText(stringResource(R.string.welcome_account_name_placeholder), style = KoshaTheme.type.head, color = c.ink3, maxLines = 1)
                            inner()
                        }
                    },
                )
                Chip(typeLabel, small = true, soft = true, onClick = { sheet = "type" }, leading = { PhIcon("caret-down", size = 14.dp) })
                Chip(row.currency.uppercase(), small = true, soft = true, onClick = { sheet = "currency" })
                IconButton(
                    "x",
                    if (named.isNotEmpty()) stringResource(R.string.welcome_remove, named) else stringResource(R.string.welcome_remove_this_account),
                    onRemove,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FigureField(stringResource(first), row.amount, row.currency, Modifier.weight(1f)) { sheet = "amount" }
                if (second != null) FigureField(stringResource(second), row.extra, row.currency, Modifier.weight(1f)) { sheet = "extra" }
            }
            val notes = buildList {
                if (row.type == AccountType.Credit) {
                    add(
                        (if (o.creditLimit == null) stringResource(R.string.welcome_credit_left_hint)
                        else stringResource(R.string.welcome_credit_left, Money.format(maxOf(0, o.balance), row.currency, locale))) to c.ink3,
                    )
                }
                if (row.type == AccountType.Investment) {
                    val g = o.gain
                    add(
                        when {
                            g == null -> stringResource(R.string.welcome_gain_hint) to c.ink3
                            g >= 0 -> stringResource(R.string.welcome_gain, Money.format(g, row.currency, locale)) to c.pos
                            else -> stringResource(R.string.welcome_loss, Money.format(-g, row.currency, locale)) to c.bad
                        },
                    )
                }
                val worth = accountWorth(row.type, o.balance, o.creditLimit, o.gain)
                if (row.currency != primary && worth != 0L) {
                    add("≈ " + Money.format(convert(worth, row.currency, primary, rates), primary, locale) to c.ink3)
                }
            }
            if (checked && named.isEmpty()) {
                KText(stringResource(R.string.welcome_error_name), style = KoshaTheme.type.xs, color = c.bad)
            } else if (notes.isNotEmpty() && !hideAmounts()) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    notes.forEach { (text, color) -> KText(text, style = KoshaTheme.type.xs, color = color) }
                }
            }
        }
    }
    when (sheet) {
        "type" -> Sheet(stringResource(if (named.isNotEmpty()) R.string.welcome_type_of else R.string.welcome_type_of_account, named), { sheet = null }) {
            listOf(
                AccountType.Bank to R.string.welcome_type_bank_account,
                AccountType.Cash to R.string.welcome_type_cash,
                AccountType.Credit to R.string.welcome_type_credit_card,
                AccountType.Investment to R.string.welcome_type_investment_long,
            ).forEach { (type, label) ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 52.dp).selectable(row.type == type, role = Role.RadioButton) {
                        // The second figure means something else for another type, so it starts over.
                        onChange(row.copy(type = type, extra = if (type == row.type) row.extra else ""))
                        sheet = null
                    },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    KText(stringResource(label), Modifier.weight(1f))
                    Radio(row.type == type)
                }
            }
        }
        "currency" -> CurrencySheet(meta()?.rates?.keys ?: emptySet(), row.currency, { onChange(row.copy(currency = it)); sheet = null }, { sheet = null })
        "amount", "extra" -> {
            val which = sheet!!
            var input by remember(which) { mutableStateOf(if (which == "amount") row.amount else row.extra) }
            Sheet(stringResource(if (which == "amount") first else second!!), { sheet = null }) {
                AmountDisplay(input, row.currency, EntryKind.Transfer)
                AmountKeypad(input, { input = it }, decimals = Money.decimals(row.currency))
                Spacer(Modifier.height(12.dp))
                Button(
                    stringResource(R.string.common_done),
                    {
                        // A sum like 1200+300 is kept as its result.
                        val value = figure(input)?.let { BigDecimal.valueOf(it, 2).stripTrailingZeros().toPlainString() } ?: ""
                        onChange(if (which == "amount") row.copy(amount = value) else row.copy(extra = value))
                        sheet = null
                    },
                    Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** A figure's field: its label on the left, the number on the right; tapping opens the keypad. */
@Composable
private fun FigureField(label: String, input: String, currency: String, modifier: Modifier, onClick: () -> Unit) {
    val c = KoshaTheme.colors
    val locale = KoshaTheme.locale
    val minor = figure(input)
    Row(
        modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(14.dp)).background(c.surface).border(1.dp, c.line, RoundedCornerShape(14.dp))
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        KText(label, Modifier.weight(1f, fill = false), style = KoshaTheme.type.xs, color = c.ink3)
        Spacer(Modifier.weight(1f))
        if (minor == null) KText(stringResource(R.string.welcome_amount_placeholder), style = KoshaTheme.type.num.copy(fontSize = 18.sp), color = c.ink3)
        else MoneyNumber(plain(minor, currency, locale))
    }
}

@Composable
private fun MoneyNumber(text: String) {
    // Hide amounts blurs figures everywhere, these included.
    if (hideAmounts()) KText("••••", style = KoshaTheme.type.num.copy(fontSize = 18.sp)) else KText(text, style = KoshaTheme.type.num.copy(fontSize = 18.sp), maxLines = 1)
}

/** Suggested accounts not added yet, then Another account. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddChips(all: List<Pair<String, AccountType>>, have: List<String>, onAdd: (Pair<String, AccountType>) -> Unit) {
    val missing = all.filter { s -> have.none { it.trim().equals(s.first, ignoreCase = true) } }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        (missing + ("" to AccountType.Bank)).forEach { s ->
            Chip(
                s.first.ifEmpty { stringResource(R.string.welcome_another_account) },
                small = true,
                onClick = { onAdd(s) },
                leading = { PhIcon("plus", size = 14.dp) },
                modifier = Modifier.heightIn(min = 48.dp),
            )
        }
    }
}

/** Step 3: what was added, net worth from the server's report, and Import statements or Skip for now. */
@Composable
private fun Done(added: List<String>, onImport: () -> Unit, onSkip: () -> Unit) {
    val kosha = rememberKosha()
    val c = KoshaTheme.colors
    val locale = KoshaTheme.locale
    val version by kosha.version.collectAsStateWithLifecycle()
    val worth = remember(version) { kosha.load("net-worth", NetWorth.serializer()) { get<NetWorth>("/api/v1/reports/net-worth") } }
        .collectAsStateWithLifecycle(null).value?.value
    val currencies = added.distinct().size
    val count = added.size
    Column(Modifier.fillMaxSize().background(c.bg).statusBarsPadding()) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 96.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.size(72.dp).clip(RoundedCornerShape(24.dp)).background(c.pos.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                PhIcon("check-circle", size = 32.dp, tint = c.pos, fill = true)
            }
            KText(stringResource(R.string.welcome_done_title), Modifier.semantics { heading() }, style = KoshaTheme.type.title)
            val addedLine = if (currencies > 1) pluralStringResource(R.plurals.welcome_done_added_currencies, count, count, currencies)
            else pluralStringResource(R.plurals.welcome_done_added, count, count)
            KText(
                addedLine + if (worth != null && !hideAmounts()) " " + stringResource(R.string.app_wel_net_worth_starts, Money.format(worth.total, worth.currency, locale)) else "",
                style = KoshaTheme.type.small,
                color = c.ink2,
                align = TextAlign.Center,
            )
            Spacer(Modifier.height(14.dp))
            Card(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        IconTile("clock-counter-clockwise")
                        KText(stringResource(R.string.welcome_history_title), Modifier.weight(1f), style = KoshaTheme.type.head)
                    }
                    KText(stringResource(R.string.welcome_history_body), style = KoshaTheme.type.small, color = c.ink2)
                    KText(stringResource(R.string.app_wel_formats), style = KoshaTheme.type.xs, color = c.ink3)
                }
            }
        }
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)) {
                PhIcon("info", size = 14.dp, tint = c.ink3)
                KText(stringResource(R.string.app_wel_import_later), style = KoshaTheme.type.xs, color = c.ink3, align = TextAlign.Center)
            }
            Button(stringResource(R.string.welcome_import), onImport, Modifier.fillMaxWidth(), icon = "file-arrow-up")
            Button(stringResource(R.string.welcome_skip), onSkip, Modifier.fillMaxWidth(), style = ButtonStyle.Secondary)
        }
    }
}
