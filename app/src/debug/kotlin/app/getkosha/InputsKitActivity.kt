package app.getkosha

import android.os.Bundle
import android.text.format.DateFormat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.getkosha.design.Accent
import app.getkosha.design.AccountDot
import app.getkosha.design.AccountList
import app.getkosha.design.AccountOption
import app.getkosha.design.AmountDisplay
import app.getkosha.design.AmountKeypad
import app.getkosha.design.Button
import app.getkosha.design.ButtonStyle
import app.getkosha.design.Card
import app.getkosha.design.CategoryGrid
import app.getkosha.design.CategoryTile
import app.getkosha.design.Chip
import app.getkosha.design.Field
import app.getkosha.design.FieldTile
import app.getkosha.design.Figures
import app.getkosha.design.KText
import app.getkosha.design.KoshaTheme
import app.getkosha.design.ListOption
import app.getkosha.design.ListPicker
import app.getkosha.design.MonthGrid
import app.getkosha.design.PhIcon
import app.getkosha.design.Pick
import app.getkosha.design.R as D
import app.getkosha.design.Segmented
import app.getkosha.design.Sheet
import app.getkosha.design.Stepper
import app.getkosha.design.Switch
import app.getkosha.design.TileRow
import app.getkosha.design.TimeWheel
import app.getkosha.design.Toast
import app.getkosha.design.ToastHost
import app.getkosha.design.ToastState
import app.getkosha.design.Toggle
import app.getkosha.design.dayLabel
import app.getkosha.model.EntryKind
import app.getkosha.model.Money
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

/** Debug only: every control in core:design on one screen, to compare with design/screens/light/00-start-InputsKit.png. */
class InputsKitActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { Kit() }
    }
}

// Sample data, as on the design board. The kit never shows real account data.
private data class SampleAccount(val id: String, val name: String, val currency: String, val balance: Long, val colour: Int)

private val ACCOUNTS = listOf(
    SampleAccount("n26", "N26 Main", "eur", 241830, 0),
    SampleAccount("visa", "Visa Card", "eur", -68357, 1),
    SampleAccount("cash", "Cash", "eur", 8500, 4),
    SampleAccount("hdfc", "HDFC Savings", "inr", 41285000, 2),
)

private val CATEGORIES = listOf(
    CategoryTile("groceries", "shopping-cart", "Groceries"), CategoryTile("dining", "fork-knife", "Dining"),
    CategoryTile("coffee", "coffee", "Coffee"), CategoryTile("transit", "tram", "Transit"),
    CategoryTile("shopping", "bag", "Shopping"), CategoryTile("social", "martini", "Social"),
    CategoryTile("takeaway", "pizza", "Takeaway"), CategoryTile("health", "bandaids", "Health"),
    CategoryTile("utilities", "lightning", "Utilities"), CategoryTile("rent", "house", "Rent"),
    CategoryTile("internet", "globe", "Internet"), CategoryTile("gifts", "gift", "Gifts"),
    CategoryTile("travel", "airplane-tilt", "Travel"), CategoryTile("fun", "popcorn", "Entertainment"),
    CategoryTile("bills", "receipt", "Bills & Fees"),
)

private enum class Open { None, Account, List, Date, Time, Category }

@Composable
private fun Kit() {
    var dark by rememberSaveable { mutableStateOf(false) }
    var neutral by rememberSaveable { mutableStateOf(false) }
    var accent by rememberSaveable { mutableStateOf(Accent.Teal) }
    var figures by rememberSaveable { mutableStateOf(Figures.Mono) }
    KoshaTheme(dark = dark, accent = accent, neutral = neutral, figures = figures) {
        val toasts = remember { ToastState() }
        Box(Modifier.fillMaxSize().background(KoshaTheme.colors.bg)) {
            Column(
                Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).navigationBarsPadding()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                KText(stringResource(R.string.kit_title), Modifier.semantics { heading() }, style = KoshaTheme.type.page)
                KText(stringResource(R.string.kit_lead), color = KoshaTheme.colors.ink2)
                Segmented(listOf(false to stringResource(R.string.kit_light), true to stringResource(R.string.kit_dark)), dark, { dark = it })
                Segmented(listOf(false to stringResource(R.string.kit_more_colour), true to stringResource(R.string.kit_neutral)), neutral, { neutral = it })
                Segmented(Accent.entries.map { it to it.name }, accent, { accent = it }, small = true)
                Segmented(listOf(Figures.Mono to stringResource(R.string.kit_mono), Figures.Proportional to stringResource(R.string.kit_proportional)), figures, { figures = it })
                KeypadCard()
                TilesCard()
                DateCard()
                TimeCard()
                AccountCard()
                CategoryCard()
                ControlsCard()
                FieldsCard()
                SheetsCard(toasts)
            }
            ToastHost(toasts, Modifier.align(Alignment.TopCenter))
        }
    }
}

@Composable
private fun KitCard(title: Int, lead: Int, notes: Int? = null, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                KText(stringResource(title), Modifier.semantics { heading() }, style = KoshaTheme.type.head.copy(fontSize = KoshaTheme.type.sheetTitle.fontSize))
                KText(stringResource(lead), style = KoshaTheme.type.small.copy(fontSize = KoshaTheme.type.body.fontSize), color = KoshaTheme.colors.ink2)
            }
            content()
            if (notes != null) KText(stringResource(notes), style = KoshaTheme.type.small, color = KoshaTheme.colors.ink2)
        }
    }
}

@Composable
private fun KeypadCard() {
    var input by rememberSaveable { mutableStateOf("12.50+4") }
    var kind by rememberSaveable { mutableStateOf(EntryKind.Expense) }
    val locale = KoshaTheme.locale
    KitCard(R.string.kit_keypad, R.string.kit_keypad_lead, R.string.kit_keypad_notes) {
        Segmented(
            listOf(
                EntryKind.Expense to stringResource(D.string.add_kind_expense),
                EntryKind.Income to stringResource(D.string.add_kind_income),
                EntryKind.Transfer to stringResource(D.string.add_kind_transfer),
            ),
            kind, { kind = it },
        )
        AmountDisplay(input, "eur", kind, { input = it }, note = "≈ " + Money.format(156090, "inr", locale) + " · HDFC Savings")
        AmountKeypad(input, { input = it })
    }
}

@Composable
private fun TilesCard() {
    val today = LocalDate.now()
    KitCard(R.string.kit_tiles, R.string.kit_tiles_lead) {
        TileRow {
            FieldTile("Visa Card", {}, cell, leading = { AccountDot(KoshaTheme.colors.account(1), size = 12.dp) }, icon = "wallet")
            FieldTile("Dining", {}, cell, icon = "fork-knife", pick = Pick.Guess, aiGuess = true)
            FieldTile(dayLabel(today, today), {}, cell, icon = "calendar-blank")
            FieldTile(stringResource(D.string.add_note), {}, cell, icon = "note-pencil", pick = Pick.Empty)
        }
        TileRow {
            FieldTile(stringResource(D.string.add_category), {}, cell, icon = "squares-four", pick = Pick.Empty)
            FieldTile(dayLabel(today.minusDays(1), today), {}, cell, icon = "calendar-blank")
        }
    }
}

@Composable
private fun DateCard() {
    val today = LocalDate.now()
    var picked by rememberSaveable { mutableStateOf(today) }
    var month by rememberSaveable { mutableStateOf(YearMonth.from(today)) }
    var open by rememberSaveable { mutableStateOf(false) }
    val has = { d: LocalDate -> !d.isAfter(today) && d.dayOfMonth % 3 != 1 }
    KitCard(R.string.kit_date, R.string.kit_date_lead, R.string.kit_date_notes) {
        QuickDays(today, picked) { picked = it; month = YearMonth.from(it) }
        MonthGrid(month, picked, today, { picked = it }, { month = it }, hasEntries = has)
        Button(stringResource(R.string.kit_open_date), { open = true }, Modifier.fillMaxWidth(), style = ButtonStyle.Secondary)
    }
    if (open) {
        Sheet(stringResource(D.string.add_date), { open = false }) {
            QuickDays(today, picked) { picked = it; open = false }
            MonthGrid(month, picked, today, { picked = it; open = false }, { month = it }, hasEntries = has)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuickDays(today: LocalDate, picked: LocalDate, onPick: (LocalDate) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(today, today.minusDays(1), today.minusDays(2), today.withDayOfMonth(1)).distinct().forEachIndexed { i, d ->
            val label = if (i == 3) stringResource(D.string.add_start_of_month) else dayLabel(d, today)
            Chip(label, on = d == picked, onClick = { onPick(d) })
        }
    }
}

@Composable
private fun TimeCard() {
    var hour by rememberSaveable { mutableIntStateOf(14) }
    var minute by rememberSaveable { mutableIntStateOf(30) }
    var open by rememberSaveable { mutableStateOf(false) }
    val twelve = !DateFormat.is24HourFormat(LocalContext.current)
    val now = { LocalTime.now().let { hour = it.hour; minute = it.minute } }
    KitCard(R.string.kit_time, R.string.kit_time_lead, R.string.kit_time_notes) {
        TimeWheel(hour, minute, { h, m -> hour = h; minute = m }, now, twelveHour = twelve)
        Button(stringResource(R.string.kit_open_time), { open = true }, Modifier.fillMaxWidth(), style = ButtonStyle.Secondary)
    }
    if (open) {
        Sheet(stringResource(D.string.add_time), { open = false }) {
            TimeWheel(hour, minute, { h, m -> hour = h; minute = m }, now, twelveHour = twelve)
        }
    }
}

@Composable
private fun accountOptions(): List<AccountOption> {
    val locale = KoshaTheme.locale
    return ACCOUNTS.map { AccountOption(it.id, it.name, null, Money.format(it.balance, it.currency, locale), it.colour) }
}

@Composable
private fun AccountCard() {
    var picked by rememberSaveable { mutableStateOf("n26") }
    KitCard(R.string.kit_account, R.string.kit_account_lead, R.string.kit_account_notes) {
        AccountList(accountOptions(), picked, { picked = it.id })
    }
}

@Composable
private fun CategoryCard() {
    var picked by rememberSaveable { mutableStateOf<String?>("groceries") }
    var open by rememberSaveable { mutableStateOf(false) }
    KitCard(R.string.kit_category, R.string.kit_category_lead, R.string.kit_category_notes) {
        CategoryGrid(CATEGORIES.take(5), picked, { picked = it.id }, guess = "coffee", aiGuess = true)
        Button(stringResource(R.string.kit_open_category), { open = true }, Modifier.fillMaxWidth(), style = ButtonStyle.Secondary)
    }
    if (open) {
        Sheet(stringResource(D.string.add_category), { open = false }, subtitle = stringResource(D.string.app_most_used)) {
            CategoryGrid(CATEGORIES, picked, { picked = it.id; open = false }, guess = "coffee", aiGuess = true)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ControlsCard() {
    var kind by rememberSaveable { mutableStateOf(EntryKind.Expense) }
    var on by rememberSaveable { mutableStateOf(true) }
    var off by rememberSaveable { mutableStateOf(false) }
    var radio by rememberSaveable { mutableIntStateOf(0) }
    var tickA by rememberSaveable { mutableStateOf(true) }
    var tickB by rememberSaveable { mutableStateOf(false) }
    var every by rememberSaveable { mutableIntStateOf(1) }
    KitCard(R.string.kit_controls, R.string.kit_controls_lead) {
        Segmented(
            listOf(
                EntryKind.Expense to stringResource(D.string.add_kind_expense),
                EntryKind.Income to stringResource(D.string.add_kind_income),
                EntryKind.Transfer to stringResource(D.string.add_kind_transfer),
            ),
            kind, { kind = it },
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Switch(on, { on = it }, "Switch on")
            Switch(off, { off = it }, "Switch off")
            Toggle(radio == 0, { radio = 0 }, "Radio 1")
            Toggle(radio == 1, { radio = 1 }, "Radio 2")
            Toggle(tickA, { tickA = it }, "Tick 1", tick = true)
            Toggle(tickB, { tickB = it }, "Tick 2", tick = true)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("N26 Main", onClick = {}, leading = { AccountDot(KoshaTheme.colors.account(0)) })
            Chip(stringResource(D.string.add_today), on = true, onClick = {}, leading = { PhIcon("calendar-blank", size = 18.dp) })
            Chip("#lisbon", soft = true, onClick = {}, leading = { PhIcon("tag", size = 18.dp) })
        }
        Stepper(every, { every = it })
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(stringResource(R.string.kit_primary), {})
            Button(stringResource(R.string.kit_secondary), {}, style = ButtonStyle.Secondary)
            Button(stringResource(D.string.common_delete), {}, style = ButtonStyle.Danger)
            Button(stringResource(R.string.kit_primary), {}, enabled = false)
        }
    }
}

@Composable
private fun FieldsCard() {
    var payee by rememberSaveable { mutableStateOf("") }
    var typed by rememberSaveable { mutableStateOf("Bonanza Co") }
    var server by rememberSaveable { mutableStateOf("kosha.home") }
    KitCard(R.string.kit_fields, R.string.kit_fields_lead, R.string.kit_fields_notes) {
        Field(payee, { payee = it }, placeholder = stringResource(D.string.add_payee))
        Field(typed, { typed = it })
        Field(server, { server = it }, error = stringResource(R.string.kit_server_error))
    }
}

@Composable
private fun SheetsCard(toasts: ToastState) {
    var open by rememberSaveable { mutableStateOf(Open.None) }
    var account by rememberSaveable { mutableStateOf("n26") }
    var currency by rememberSaveable { mutableStateOf("EUR") }
    val undo = stringResource(D.string.common_undo)
    val message = stringResource(R.string.kit_toast)
    KitCard(R.string.kit_sheets, R.string.kit_sheets_lead, R.string.kit_sheets_notes) {
        Button(stringResource(R.string.kit_open_account), { open = Open.Account }, Modifier.fillMaxWidth(), style = ButtonStyle.Secondary)
        Button(stringResource(R.string.kit_open_list), { open = Open.List }, Modifier.fillMaxWidth(), style = ButtonStyle.Secondary)
        Button(stringResource(R.string.kit_show_toast), { toasts.show(Toast(message, undo) {}) }, Modifier.fillMaxWidth())
    }
    when (open) {
        Open.Account -> Sheet(stringResource(D.string.add_account), { open = Open.None }) {
            AccountList(accountOptions(), account, { account = it.id; open = Open.None })
        }
        Open.List -> Sheet(stringResource(D.string.accounts_currency), { open = Open.None }) {
            val options = remember {
                java.util.Currency.getAvailableCurrencies().sortedBy { it.currencyCode }
                    .map { ListOption(it.currencyCode, it.currencyCode, it.displayName) }
            }
            ListPicker(options, currency, { currency = it.value; open = Open.None })
        }
        else -> Unit
    }
}
