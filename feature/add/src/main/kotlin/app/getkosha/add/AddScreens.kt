package app.getkosha.add

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import app.getkosha.design.AccountDot
import app.getkosha.design.AmountDisplay
import app.getkosha.design.AmountKeypad
import app.getkosha.design.Button
import app.getkosha.design.ButtonStyle
import app.getkosha.design.Caption
import app.getkosha.design.Card
import app.getkosha.design.FieldTile
import app.getkosha.design.Hairline
import app.getkosha.design.IconButton
import app.getkosha.design.IconTile
import app.getkosha.design.KText
import app.getkosha.design.KoshaTheme
import app.getkosha.design.PhIcon
import app.getkosha.design.Pick
import app.getkosha.design.R
import app.getkosha.design.Segmented
import app.getkosha.design.Sparkle
import app.getkosha.design.TileRow
import app.getkosha.design.categoryIconName
import app.getkosha.design.dayLabel
import app.getkosha.model.Dates
import app.getkosha.model.EntryKind
import app.getkosha.model.Money
import app.getkosha.model.Payee
import app.getkosha.model.Sign
import app.getkosha.model.Wallet
import app.getkosha.ui.LocalNav
import app.getkosha.ui.LocalToasts
import app.getkosha.ui.ActivitySearch
import app.getkosha.ui.Route
import app.getkosha.ui.openActivity
import app.getkosha.ui.hideAmounts
import kotlin.math.abs

/** The add screens, registered in the app's NavHost. */
fun NavGraphBuilder.addScreens() {
    composable<Route.Add> { entry ->
        val route = entry.toRoute<Route.Add>()
        AddScreen(viewModel { AddModel(route) })
    }
}

/** Money as text for sentences and rows, masked while Hide amounts is on. */
@Composable
internal fun money(minor: Long, currency: String, sign: Sign = Sign.None): String =
    if (hideAmounts()) "••••" else Money.format(minor, currency, KoshaTheme.locale, sign)

/**
 * Add (02-add-*): the amount first with the keypad under the thumb, the kind, the tile row, the payee with its
 * suggestions, or the two accounts and the fee for a transfer, then Add another and the primary button.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AddScreen(m: AddModel) {
    val c = KoshaTheme.colors
    val nav = LocalNav.current
    val toasts = LocalToasts.current
    val context = LocalContext.current
    val view = LocalView.current
    val ui = remember(nav, toasts) { AddUi(context, view, toasts, nav) }
    val focus = LocalFocusManager.current
    var payeeFocused by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    // Back closes the camera before Add; sheets close themselves first.
    BackHandler(enabled = m.camera) { m.camera = false }
    // Back with the keyboard up hides it; the keypad comes back with it.
    val imeVisible = WindowInsets.isImeVisible
    LaunchedEffect(imeVisible) { if (!imeVisible) focus.clearFocus() }

    BoxWithConstraints(Modifier.fillMaxSize().background(c.bg).statusBarsPadding().navigationBarsPadding().imePadding()) {
        val total = maxHeight
        var top by remember { mutableStateOf(0.dp) }
        var bottom by remember { mutableStateOf(0.dp) }
        // The amount takes the room the rest leaves; on short screens or with big fonts the whole screen scrolls.
        val amountHeight = (total - top - bottom).coerceAtLeast(if (payeeFocused) 76.dp else 184.dp)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Header(m, ui, Modifier.onSizeChanged { top = with(density) { it.height.toDp() } })
            Box(Modifier.fillMaxWidth().height(amountHeight).padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
                Amount(m, compact = payeeFocused)
            }
            Column(
                Modifier.fillMaxWidth().onSizeChanged { bottom = with(density) { it.height.toDp() } }.padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Tiles(m)
                if (m.transfer) {
                    TransferRows(m)
                } else {
                    PayeeField(m, payeeFocused) { payeeFocused = it }
                    if (payeeFocused && m.payee.isNotBlank()) Suggestions(m, ui) { focus.clearFocus() }
                }
                if (!payeeFocused) {
                    AmountKeypad(m.input, { m.input = it }, Modifier.padding(horizontal = 16.dp), decimals = m.decimals)
                    Buttons(m, ui)
                }
            }
        }
    }
    Sheets(m, ui)
    if (m.camera) ReceiptCamera(m, ui)
}

@Composable
private fun Header(m: AddModel, ui: AddUi, modifier: Modifier) {
    val ai = m.meta.ai
    Row(modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton("x", stringResource(R.string.common_close), { ui.close() })
        Segmented(
            listOf(
                EntryKind.Expense to stringResource(R.string.add_kind_expense),
                EntryKind.Income to stringResource(R.string.add_kind_income),
                EntryKind.Transfer to stringResource(R.string.add_kind_transfer),
            ),
            m.kind,
            m::pickKind,
            Modifier.weight(1f).padding(horizontal = 4.dp),
        )
        if (ai.quickAdd || ai.receipts) {
            // With only receipts on, the sparkle goes straight to the camera.
            val label = stringResource(if (ai.quickAdd) R.string.add_describe_or_scan else R.string.add_scan_receipt)
            Box(
                Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .clickable(enabled = m.reading == null, role = Role.Button, onClickLabel = label) {
                        if (ai.quickAdd) m.sheet = AddSheet.Describe else m.camera = true
                    }
                    .semantics { contentDescription = label },
                contentAlignment = Alignment.Center,
            ) { Sparkle(size = 24.dp) }
        } else {
            Spacer(Modifier.width(48.dp))
        }
    }
}

@Composable
private fun Amount(m: AddModel, compact: Boolean) {
    val c = KoshaTheme.colors
    val reading = m.reading
    if (reading != null) {
        Row(
            Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spinner()
            KText(stringResource(if (reading == Filled.Receipt) R.string.add_hint_reading_receipt else R.string.add_reading_text), color = c.ink2)
        }
        return
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val filled = m.filled
        if (filled != null && !compact) FilledNote(m, filled)
        val note = when {
            compact -> null
            m.transfer -> stringResource(R.string.app_add_transfer_note)
            m.input.isEmpty() -> stringResource(if (m.savedOnce) R.string.app_add_ready_next else R.string.app_add_start)
            m.converted != null -> stringResource(R.string.app_add_converted, money(m.converted!!, m.meta.primary))
            m.kind == EntryKind.Income -> stringResource(R.string.app_add_income_note)
            else -> null
        }
        AmountDisplay(m.input, m.currency, m.kind, note = note)
        if (!compact) Pills(m)
    }
}

/** "Read from your receipt · Check it before adding." with the photo, after AI filled the form (AddFilledAI). */
@Composable
private fun FilledNote(m: AddModel, filled: Filled) {
    val c = KoshaTheme.colors
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.soft).padding(12.dp).semantics(mergeDescendants = true) { },
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        m.receipt?.let {
            Image(it, null, Modifier.size(36.dp, 44.dp).clip(RoundedCornerShape(6.dp)).border(1.dp, c.line, RoundedCornerShape(6.dp)), contentScale = ContentScale.Crop)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Sparkle(size = 14.dp)
                KText(stringResource(if (filled == Filled.Receipt) R.string.app_add_filled_receipt else R.string.app_add_filled_ai), style = KoshaTheme.type.small.copy(fontWeight = FontWeight.Medium))
            }
            val photo = filled == Filled.Receipt && m.files.any { it.name == "receipt.jpg" }
            KText(stringResource(if (photo) R.string.app_add_check_photo else R.string.app_add_check_it), style = KoshaTheme.type.small, color = c.ink2)
        }
    }
}

@Composable
private fun Tiles(m: AddModel) {
    val c = KoshaTheme.colors
    val today = m.today
    val day = dayLabel(m.day, today)
    val shownTime = timeText(m.time ?: Dates.now(m.meta.tz))
    TileRow(Modifier.padding(horizontal = 16.dp)) {
        // The account is the pill under the amount; three tiles stay: category, date, note.
        if (!m.transfer) {
            val cat = m.chosenCategory
            val pick = when {
                m.categoryId != null -> Pick.Picked
                m.chosen != null -> Pick.Guess
                else -> Pick.Empty
            }
            FieldTile(
                label = cat?.name ?: stringResource(R.string.add_category),
                onClick = {
                    m.viaNext = false
                    m.sheet = AddSheet.Category
                },
                modifier = cell,
                icon = if (cat != null) categoryIconName(cat.icon) else "squares-four",
                pick = pick,
                aiGuess = pick == Pick.Guess && m.chosenIsAi,
                description = when {
                    cat == null -> stringResource(R.string.app_add_category_none)
                    pick == Pick.Picked -> stringResource(R.string.app_add_category_picked, cat.name)
                    m.chosenIsAi -> stringResource(R.string.app_add_category_ai, cat.name)
                    else -> stringResource(R.string.app_add_category_usual, cat.name)
                },
            )
        }
        FieldTile(
            label = day,
            onClick = { m.sheet = AddSheet.Date },
            modifier = cell,
            icon = "calendar-blank",
            description = stringResource(R.string.add_chip_date, day, shownTime),
        )
        // The note tile names what is set: the note, else the event, else the first tag, else the files.
        val details = buildList {
            m.note.trim().lineSequence().firstOrNull()?.takeIf { it.isNotEmpty() }?.let(::add)
            if (!m.transfer) {
                m.event?.name?.let(::add)
                m.tags.firstOrNull()?.let { add("#$it") }
                if (m.files.isNotEmpty()) add(pluralStringResource(R.plurals.add_files, m.files.size, m.files.size.toString()))
            }
        }
        val onlyFiles = details.size == 1 && m.files.isNotEmpty() && m.note.isBlank() && m.event == null && m.tags.isEmpty()
        FieldTile(
            label = details.firstOrNull()?.let { if (details.size > 1) "$it +${details.size - 1}" else it } ?: stringResource(R.string.add_note),
            onClick = { m.sheet = AddSheet.Note },
            modifier = cell,
            icon = if (onlyFiles) "paperclip" else "note-pencil",
            pick = if (details.isEmpty()) Pick.Guess else Pick.Picked,
            description = details.firstOrNull()?.let { "${stringResource(R.string.add_note)}, $it" }
                ?: stringResource(if (m.transfer) R.string.add_note else R.string.add_note_tags_event),
        )
    }
}

/** The payee: the only text field on Add. "usually €28" or AI's sparkle sit on the right, a clear button while typing. */
@Composable
private fun PayeeField(m: AddModel, focused: Boolean, onFocus: (Boolean) -> Unit) {
    val c = KoshaTheme.colors
    val focus = LocalFocusManager.current
    val shape = RoundedCornerShape(14.dp)
    val usual = m.match?.usualAmount?.takeIf { it != 0L }
    val label = stringResource(R.string.add_payee)
    BasicTextField(
        value = m.payee,
        onValueChange = m::typePayee,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .onFocusChanged { onFocus(it.isFocused) }
            .semantics { contentDescription = label },
        textStyle = KoshaTheme.type.body.copy(fontSize = 16.sp, color = c.ink),
        cursorBrush = SolidColor(c.selection),
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
        decorationBox = { inner ->
            Row(
                Modifier
                    .heightIn(min = 48.dp)
                    .clip(shape)
                    .background(c.surface)
                    .border(if (focused) 2.dp else 1.dp, if (focused) c.ink else c.line, shape)
                    .padding(start = 14.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(Modifier.weight(1f).padding(vertical = 12.dp)) {
                    if (m.payee.isEmpty()) KText(stringResource(R.string.add_payee), style = KoshaTheme.type.body.copy(fontSize = 16.sp), color = c.ink3, maxLines = 1)
                    inner()
                }
                when {
                    focused && m.payee.isNotEmpty() ->
                        IconButton("x-circle", stringResource(R.string.app_add_clear_payee), { m.typePayee("") }, tint = c.ink3)
                    m.payeeByAi -> Sparkle(Modifier.padding(end = 10.dp), size = 16.dp)
                    usual != null && m.match?.currency != null ->
                        KText(
                            stringResource(R.string.add_usually, money(abs(usual), m.match!!.currency!!)),
                            Modifier.padding(end = 10.dp),
                            style = KoshaTheme.type.small,
                            color = c.ink3,
                            maxLines = 1,
                        )
                }
            }
        },
    )
}

/** Payees matching what's typed, then a way to search Activity for it (AddPayee). */
@Composable
private fun Suggestions(m: AddModel, ui: AddUi, done: () -> Unit) {
    val c = KoshaTheme.colors
    val nav = ui.nav
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp), tight = true) {
        if (m.suggestions.isNotEmpty()) {
            Caption(stringResource(R.string.add_section_payees), Modifier.padding(start = 16.dp, top = 14.dp, bottom = 6.dp))
            m.suggestions.forEach { p -> SuggestionRow(m, p) { m.pickPayee(p); done() } }
            Hairline(Modifier.padding(horizontal = 16.dp))
        }
        Caption(stringResource(R.string.add_section_go_to), Modifier.padding(start = 16.dp, top = 14.dp, bottom = 6.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button) {
                    nav.popBackStack()
                    nav.openActivity(ActivitySearch(query = m.payee.trim()))
                }
                .heightIn(min = KoshaTheme.dims.row)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconTile("magnifying-glass")
            KText(stringResource(R.string.add_search_activity, m.payee.trim()), Modifier.weight(1f))
        }
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun SuggestionRow(m: AddModel, p: Payee, onClick: () -> Unit) {
    val c = KoshaTheme.colors
    val cat = m.meta.categories.firstOrNull { it.id == p.categoryId }
    val account = m.meta.wallets.firstOrNull { it.id == p.accountId }
    val typed = m.payee.trim()
    val detail = listOfNotNull(
        cat?.name,
        account?.name,
        if (p.count > 0) pluralStringResource(R.plurals.app_add_times, p.count, p.count.toString()) else null,
    ).joinToString(" · ")
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = KoshaTheme.dims.row + 8.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(categoryIconName(cat?.icon))
        Column(Modifier.weight(1f)) {
            // The typed part in bold, as the design does.
            val at = p.payee.indexOf(typed, ignoreCase = true)
            BasicText(
                buildAnnotatedString {
                    if (at < 0 || typed.isEmpty()) append(p.payee) else {
                        append(p.payee.substring(0, at))
                        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(p.payee.substring(at, at + typed.length)) }
                        append(p.payee.substring(at + typed.length))
                    }
                },
                style = KoshaTheme.type.body.copy(color = c.ink),
            )
            if (detail.isNotEmpty()) KText(detail, style = KoshaTheme.type.small, color = c.ink3)
        }
        val usual = p.usualAmount
        if (usual != null && usual != 0L && p.currency != null) {
            KText(stringResource(R.string.add_usually, money(abs(usual), p.currency!!)), Modifier.width(96.dp), style = KoshaTheme.type.small, color = c.ink3, align = TextAlign.End)
        }
    }
}

/** Transfer: From and To with a swap button, then "Fee and rate" (AddTransfer). */
@Composable
private fun TransferRows(m: AddModel) {
    val c = KoshaTheme.colors
    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Card(Modifier.fillMaxWidth(), tight = true) {
            SideRow(m, stringResource(R.string.add_from), m.account) { m.sheet = AddSheet.Account }
            Hairline()
            SideRow(m, stringResource(R.string.add_to), m.toAccount) { m.sheet = AddSheet.To }
        }
        if (m.toAccount != null) {
            IconButton(
                "arrows-down-up",
                stringResource(R.string.app_add_swap),
                m::swap,
                Modifier.align(Alignment.CenterEnd).offset(x = (-36).dp),
                solid = true,
            )
        }
    }
    val s = m.sides
    val to = m.toAccount
    val summary = when {
        s.cross && to != null -> listOfNotNull(
            s.fee.takeIf { it > 0 }?.let { stringResource(R.string.app_add_fee_amount, money(it, m.currency)) },
            stringResource(R.string.add_arrives, money(s.received, to.currency)),
        ).joinToString(" · ")
        s.fee > 0 -> stringResource(R.string.app_add_fee_amount, money(s.fee, m.currency))
        else -> stringResource(R.string.add_fee_none)
    }
    val title = stringResource(if (s.cross) R.string.add_fee_and_rate else R.string.add_fee)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(c.surface)
            .border(1.dp, c.line, RoundedCornerShape(14.dp))
            .clickable(role = Role.Button, onClickLabel = title) { m.sheet = AddSheet.Fee }
            .heightIn(min = 48.dp)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KText(title, color = c.ink2)
        KText(summary, Modifier.weight(1f), style = KoshaTheme.type.small, color = c.ink3, align = TextAlign.End)
        PhIcon("caret-right", size = 16.dp, tint = c.ink3)
    }
}

@Composable
private fun SideRow(m: AddModel, side: String, account: Wallet?, onClick: () -> Unit) {
    val c = KoshaTheme.colors
    val description = if (account != null) stringResource(R.string.add_side_account, side, account.name) else stringResource(R.string.add_side_pick, side)
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = description }
            .heightIn(min = 60.dp)
            .padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KText(side, Modifier.width(44.dp), style = KoshaTheme.type.small, color = c.ink3)
        if (account != null) {
            AccountDot(c.account(m.colorIndex(account.id)), size = 10.dp)
            Column(Modifier.weight(1f)) {
                KText(account.name, style = KoshaTheme.type.head)
                KText("${money(account.balance, account.currency, Sign.Natural)} · ${account.currency.uppercase()}", style = KoshaTheme.type.xs, color = c.ink3)
            }
        } else {
            KText(stringResource(R.string.add_pick_one), Modifier.weight(1f), color = c.ink3)
        }
        PhIcon("caret-right", size = 16.dp, tint = c.ink3)
    }
}

@Composable
private fun Buttons(m: AddModel, ui: AddUi) {
    val has = m.minor != null
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            stringResource(R.string.add_another),
            { m.save(ui, again = true) },
            // Sized to its label, so it never cuts off at large font sizes; the primary button takes the rest.
            Modifier,
            style = ButtonStyle.Secondary,
            enabled = m.ready,
        )
        val label = when {
            m.needsCategory -> stringResource(R.string.app_next)
            m.kind == EntryKind.Income -> stringResource(R.string.add_add_income)
            m.kind == EntryKind.Transfer -> stringResource(R.string.add_add_transfer)
            else -> stringResource(R.string.add_add_expense)
        }
        Button(label, { m.primary(ui) }, Modifier.weight(1f), enabled = has)
    }
}

/** A turning spinner for AI reads. */
@Composable
internal fun Spinner(size: Dp = 20.dp, tint: Color = KoshaTheme.colors.brand) {
    val turn = rememberInfiniteTransition(label = "spin")
    val angle by turn.animateFloat(0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "angle")
    PhIcon("spinner-gap", Modifier.rotate(angle), size = size, tint = tint)
}
