package app.getkosha.add

import android.icu.text.DateIntervalFormat
import android.icu.util.DateInterval
import android.icu.util.TimeZone
import android.icu.util.ULocale
import android.text.Annotation
import android.text.Spanned
import android.text.format.DateFormat
import android.text.format.Formatter
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import app.getkosha.ui.filesAuthority
import java.io.File
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import app.getkosha.data.Loaded
import app.getkosha.design.AccountList
import app.getkosha.design.AccountOption
import app.getkosha.design.AmountKeypad
import app.getkosha.design.Button
import app.getkosha.design.ButtonSize
import app.getkosha.design.ButtonStyle
import app.getkosha.design.Caption
import app.getkosha.design.Card
import app.getkosha.design.CategoryGrid
import app.getkosha.design.CategoryTile
import app.getkosha.design.Chip
import app.getkosha.design.Field
import app.getkosha.design.Hairline
import app.getkosha.design.HoldKey
import app.getkosha.design.KText
import app.getkosha.design.KoshaTheme
import app.getkosha.design.MonthGrid
import app.getkosha.design.PhIcon
import app.getkosha.design.R
import app.getkosha.design.Sheet
import app.getkosha.design.TimeWheel
import app.getkosha.design.categoryIconName
import app.getkosha.design.dayLabel
import app.getkosha.model.AccountType
import app.getkosha.model.Dates
import app.getkosha.model.Days
import app.getkosha.model.EntryKind
import app.getkosha.model.Event
import app.getkosha.model.Keypad
import app.getkosha.model.MetaCategory
import app.getkosha.model.Money
import app.getkosha.model.Sign
import app.getkosha.model.cleanTag
import app.getkosha.model.parseTags
import app.getkosha.ui.Route
import app.getkosha.ui.hideAmounts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DecimalFormat
import java.text.NumberFormat
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** The sheet Add has open, if any. Each is a Kosha sheet: drag down, tap outside or back to close. */
@Composable
internal fun Sheets(m: AddModel, ui: AddUi) {
    val close = {
        m.sheet = null
        m.viaNext = false
    }
    when (m.sheet) {
        AddSheet.Account -> AccountSheet(m, to = false, close)
        AddSheet.To -> AccountSheet(m, to = true, close)
        AddSheet.Date -> DateSheet(m, close)
        AddSheet.Time -> TimeSheet(m, close)
        AddSheet.Note -> NoteSheet(m, ui, close)
        AddSheet.Category -> CategorySheet(m, ui, close)
        AddSheet.Fee -> FeeSheet(m, close)
        AddSheet.Describe -> DescribeSheet(m, ui, close)
        AddSheet.Repeat -> RepeatSheet(m, close)
        null -> {}
    }
}

/** A time as the phone writes it: 13:05, or 1:05 PM where the phone uses 12-hour time. */
@Composable
internal fun timeText(t: LocalTime): String =
    t.format(DateTimeFormatter.ofPattern(if (DateFormat.is24HourFormat(LocalContext.current)) "HH:mm" else "h:mm a", KoshaTheme.locale))

// ---------- AccountSheet ----------

/** Accounts grouped as Bank & cash, Credit cards and Investments; archived ones left out (AccountSheet). */
@Composable
private fun AccountSheet(m: AddModel, to: Boolean, close: () -> Unit) {
    val title = stringResource(if (!m.transfer) R.string.add_account else if (to) R.string.add_to else R.string.add_from)
    val hidden = hideAmounts()
    val locale = KoshaTheme.locale
    val accounts = if (to) m.active.filter { it.id != m.account?.id } else m.active
    val selected = if (to) m.toAccount?.id else m.account?.id
    val typeName = mapOf(
        AccountType.Bank to stringResource(R.string.accounts_type_bank),
        AccountType.Cash to stringResource(R.string.accounts_type_cash),
        AccountType.Credit to stringResource(R.string.accounts_type_credit),
        AccountType.Investment to stringResource(R.string.accounts_type_investment),
    )
    val groups = listOf(
        R.string.accounts_group_bank_cash to listOf(AccountType.Bank, AccountType.Cash),
        R.string.accounts_group_credit to listOf(AccountType.Credit),
        R.string.accounts_group_investment to listOf(AccountType.Investment),
    )
    val fmt = { minor: Long, cur: String -> if (hidden) "••••" else Money.format(minor, cur, locale, Sign.Natural) }
    val availableOf = stringResource(R.string.app_add_available_of)
    Sheet(title, close) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            groups.forEach { (label, types) ->
                val rows = accounts.filter { it.type in types }
                if (rows.isEmpty()) return@forEach
                Caption(stringResource(label), Modifier.padding(top = 6.dp))
                AccountList(
                    rows.map { w ->
                        val cur = w.currency.uppercase()
                        val limit = w.creditLimit
                        if (w.type == AccountType.Credit && limit != null) {
                            // Cards show the credit left: the limit plus the (negative) balance.
                            AccountOption(w.id, w.name, availableOf.format(fmt(limit + w.balance, w.currency), fmt(limit, w.currency)), "", m.colorIndex(w.id))
                        } else {
                            val balance = fmt(w.balance, w.currency) + if (w.currency != m.meta.primary && !hidden) " $cur" else ""
                            AccountOption(w.id, w.name, "${typeName[w.type]} · $cur", balance, m.colorIndex(w.id))
                        }
                    },
                    selected,
                    { m.pickAccount(it.id, to) },
                )
            }
        }
    }
}

// ---------- DateSheet and TimeSheet ----------

@Composable
private fun DateSheet(m: AddModel, close: () -> Unit) {
    val c = KoshaTheme.colors
    val kosha = m.kosha
    val today = m.today
    val locale = KoshaTheme.locale
    var month by remember { mutableStateOf(YearMonth.from(m.day)) }
    val version by kosha.version.collectAsState()
    val days by remember(month, version) {
        kosha.load("days/$month") { get<Days>("/api/app/days", mapOf("month" to month.toString())) }
    }.collectAsState(Loaded(null, refreshing = true))
    val marked = remember(days) { days.value?.days?.toSet() ?: emptySet() }
    val start = YearMonth.from(today).atDay(1)
    val quick = listOf(
        stringResource(R.string.add_today) to today,
        stringResource(R.string.add_yesterday) to today.minusDays(1),
        stringResource(R.string.add_start_of_month) to start,
        stringResource(R.string.add_end_of_month) to YearMonth.from(today).atEndOfMonth(),
    )
    Sheet(stringResource(R.string.add_date_and_time), close) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            quick.forEach { (label, d) ->
                Chip(label, small = true, soft = m.day != d, on = m.day == d, onClick = {
                    m.day = d
                    month = YearMonth.from(d)
                })
            }
        }
        Spacer(Modifier.size(8.dp))
        MonthGrid(month, m.day, today, { m.day = it }, { month = it }, hasEntries = { it.toString() in marked })
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(4.dp).clip(CircleShape).background(c.ink3))
            KText(stringResource(R.string.app_add_days_with_entries), Modifier.weight(1f), style = KoshaTheme.type.small, color = c.ink3)
            KText(m.day.format(DateTimeFormatter.ofPattern("EEEE d MMMM", locale)), style = KoshaTheme.type.small, color = c.ink3)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            val shown = timeText(m.time ?: Dates.now(m.meta.tz))
            val label = stringResource(R.string.app_add_time_row, shown)
            Row(
                Modifier
                    .weight(1f)
                    .heightIn(min = 52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .border(1.dp, c.line, RoundedCornerShape(14.dp))
                    .clickable(role = Role.Button, onClickLabel = label) { m.sheet = AddSheet.Time }
                    .semantics(mergeDescendants = true) { contentDescription = label }
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                PhIcon("clock", size = 18.dp, tint = c.ink2)
                KText(stringResource(R.string.add_time), Modifier.weight(1f))
                KText(shown, style = KoshaTheme.type.num)
            }
            Button(stringResource(R.string.common_done), close, Modifier.width(120.dp))
        }
    }
}

@Composable
private fun TimeSheet(m: AddModel, close: () -> Unit) {
    val c = KoshaTheme.colors
    val twelve = !DateFormat.is24HourFormat(LocalContext.current)
    val shown = m.time ?: Dates.now(m.meta.tz)
    Sheet(stringResource(R.string.add_time), close) {
        Row(
            Modifier
                .heightIn(min = 48.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable(role = Role.Button, onClickLabel = stringResource(R.string.app_add_back_to_date)) { m.sheet = AddSheet.Date }
                .padding(end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            PhIcon("caret-left", size = 18.dp, tint = c.ink2)
            KText(dayLabel(m.day, m.today), color = c.ink2)
        }
        TimeWheel(
            shown.hour,
            shown.minute,
            { h, min ->
                // The wheels report where they rest when they open; only a real change sets the time.
                if (h != shown.hour || min != shown.minute) m.time = LocalTime.of(h, min)
            },
            { m.time = Dates.now(m.meta.tz) },
            twelveHour = twelve,
        )
        KText(
            stringResource(if (twelve) R.string.app_add_time_help_12 else R.string.app_add_time_help_24),
            Modifier.fillMaxWidth().padding(vertical = 12.dp),
            style = KoshaTheme.type.small,
            color = c.ink3,
            align = TextAlign.Center,
        )
        Button(stringResource(R.string.common_done), close, Modifier.fillMaxWidth())
    }
}

// ---------- NoteSheet ----------

/** Note, tags, event and files (NoteSheet). A transfer takes a note only. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NoteSheet(m: AddModel, ui: AddUi, close: () -> Unit) {
    val c = KoshaTheme.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var tagText by remember { mutableStateOf("") }
    var naming by remember { mutableStateOf<String?>(null) }
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val cr = context.contentResolver
            val name = cr.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            } ?: "file"
            val type = cr.getType(uri) ?: "application/octet-stream"
            val bytes = withContext(Dispatchers.IO) { runCatching { cr.openInputStream(uri)?.use { it.readBytes() } }.getOrNull() }
            when {
                bytes == null -> ui.error(ui.s(R.string.tx_attach_failed))
                bytes.size > ATTACHMENT_LIMIT -> ui.error(ui.s(R.string.tx_file_too_big, name))
                else -> m.files.add(Staged(name, type, bytes))
            }
        }
    }
    // A photo from the phone's camera app (a JPEG in the cache), staged like a picked file; no AI needed.
    var photo by remember { mutableStateOf<File?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val file = photo ?: return@rememberLauncherForActivityResult
        photo = null
        if (!ok) return@rememberLauncherForActivityResult
        scope.launch {
            val bytes = withContext(Dispatchers.IO) { runCatching { file.readBytes().also { file.delete() } }.getOrNull() }
            when {
                bytes == null || bytes.isEmpty() -> ui.error(ui.s(R.string.tx_attach_failed))
                bytes.size > ATTACHMENT_LIMIT -> ui.error(ui.s(R.string.tx_file_too_big, file.name))
                else -> m.files.add(Staged(file.name, "image/jpeg", bytes))
            }
        }
    }
    val takePhoto = {
        val file = File(context.cacheDir, "camera").apply { mkdirs() }.resolve("photo-${System.currentTimeMillis()}.jpg")
        photo = file
        camera.launch(FileProvider.getUriForFile(context, context.filesAuthority(), file))
    }
    // Add declares the camera permission (receipt scanning), so the camera app's intent needs it granted too.
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) takePhoto() }
    val addTags = { text: String ->
        m.tags = (m.tags + parseTags(text)).distinct().take(app.getkosha.model.MAX_TAGS)
        tagText = ""
    }
    Sheet(stringResource(if (m.transfer) R.string.add_note else R.string.add_note_tags_event), close) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Field(m.note, { m.note = it.take(2000) }, label = stringResource(R.string.add_note), singleLine = false,
                keyboard = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
            if (!m.transfer) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    KText(stringResource(R.string.add_tags), style = KoshaTheme.type.small, color = c.ink3)
                    if (m.tags.isNotEmpty()) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            m.tags.forEach { t ->
                                val remove = stringResource(R.string.tx_remove_file, "#$t")
                                Chip("#$t", Modifier.semantics { contentDescription = remove }, soft = true, onClick = { m.tags = m.tags - t },
                                    leading = null)
                            }
                        }
                    }
                    Field(
                        tagText,
                        { v ->
                            // A space or comma ends a tag, as on the web's tag input.
                            if (v.any { it == ' ' || it == ',' }) addTags(v) else tagText = v
                        },
                        placeholder = stringResource(R.string.tx_type_a_tag),
                        keyboard = KeyboardOptions(imeAction = ImeAction.Done),
                    )
                    val before = m.meta.tags.filter { it !in m.tags }.take(8)
                    if (before.isNotEmpty()) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                            KText(stringResource(R.string.app_add_used_before), style = KoshaTheme.type.small, color = c.ink3)
                            before.forEach { t -> Chip("#$t", small = true, onClick = { m.tags = (m.tags + cleanTag(t)).distinct() }) }
                        }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    KText(stringResource(R.string.add_event), style = KoshaTheme.type.small, color = c.ink3)
                    EventList(m, ui, naming, { naming = it })
                }
                if (m.meta.attachments) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        KText(stringResource(R.string.app_add_files), style = KoshaTheme.type.small, color = c.ink3)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            m.files.forEach { f ->
                                val remove = stringResource(R.string.tx_remove_file, f.name)
                                Chip(
                                    "${f.name}  ${Formatter.formatShortFileSize(context, f.bytes.size.toLong())}",
                                    Modifier.semantics { contentDescription = remove },
                                    onClick = { m.files.remove(f) },
                                    leading = { PhIcon(if (f.type.startsWith("image/")) "image" else "file-pdf", size = 18.dp) },
                                )
                            }
                            Chip(stringResource(R.string.app_act_take_photo), onClick = {
                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) takePhoto()
                                else permission.launch(Manifest.permission.CAMERA)
                            }, leading = { PhIcon("camera", size = 18.dp) })
                            Chip(stringResource(R.string.app_add_attach), onClick = {
                                pickFile.launch(arrayOf("image/jpeg", "image/png", "image/webp", "image/heic", "image/heif", "application/pdf"))
                            }, leading = { PhIcon("paperclip", size = 18.dp) })
                        }
                    }
                }
            }
            Button(stringResource(R.string.common_done), {
                if (tagText.isNotBlank()) addTags(tagText)
                close()
            }, Modifier.fillMaxWidth())
        }
    }
}

/** No event, the event the day falls in plus recent ones, and New event (named inline). */
@Composable
private fun EventList(m: AddModel, ui: AddUi, naming: String?, onNaming: (String?) -> Unit) {
    val c = KoshaTheme.colors
    val auto = eventFor(m.day.toString(), m.meta.events)
    val current = m.eventId
    val shown = (listOfNotNull(m.event) + m.meta.events.take(4)).distinctBy { it.id }
    Card(Modifier.fillMaxWidth(), tight = true) {
        EventRow("prohibit", stringResource(R.string.tx_no_event), null, current == null) { m.pickEvent(null) }
        shown.forEach { e ->
            Hairline()
            val dates = eventDates(e)
            val detail = if (e.id == auto && !m.eventPicked) stringResource(R.string.app_add_picked_by_date, dates) else dates.ifEmpty { null }
            EventRow("calendar-star", e.name, detail, current == e.id) { m.pickEvent(e.id) }
        }
        Hairline()
        if (naming == null) {
            EventRow("plus", stringResource(R.string.tx_new_event), null, false) { onNaming("") }
        } else {
            val focus = remember { FocusRequester() }
            LaunchedEffect(Unit) { focus.requestFocus() }
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Field(
                    naming,
                    { onNaming(it.take(80)) },
                    Modifier.focusRequester(focus),
                    label = stringResource(R.string.tx_name),
                    placeholder = stringResource(R.string.tx_event_placeholder),
                    keyboard = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                )
                KText(stringResource(R.string.tx_event_from, eventDates(Event("", "", startDay = m.day.toString(), endDay = m.day.toString()))), style = KoshaTheme.type.small, color = c.ink3)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(stringResource(R.string.common_cancel), { onNaming(null) }, Modifier.weight(1f), style = ButtonStyle.Secondary, size = ButtonSize.Medium)
                    Button(stringResource(R.string.tx_create_event), { m.createEvent(ui, naming) { onNaming(null) } }, Modifier.weight(1f), size = ButtonSize.Medium)
                }
            }
        }
    }
}

@Composable
private fun EventRow(icon: String, name: String, detail: String?, on: Boolean, onClick: () -> Unit) {
    val c = KoshaTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (on) c.sel else c.surface)
            .selectable(on, role = Role.RadioButton, onClick = onClick)
            .heightIn(min = KoshaTheme.dims.row)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PhIcon(icon, size = 20.dp, tint = c.ink2)
        Column(Modifier.weight(1f)) {
            KText(name)
            if (detail != null) KText(detail, style = KoshaTheme.type.small, color = c.ink3)
        }
        if (on) PhIcon("check", size = 20.dp, tint = c.selection)
    }
}

/** "4 – 9 Nov", "From 20 Dec", "Until 3 Jan", or "" for an undated event (tags-events.tsx eventDates). */
@Composable
private fun eventDates(e: Event): String {
    val locale = KoshaTheme.locale
    val start = e.startDay
    val end = e.endDay
    fun ms(day: String) = LocalDate.parse(day).atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
    fun short(day: String) = LocalDate.parse(day).format(DateTimeFormatter.ofPattern("d MMM", locale))
    return when {
        start == null && end == null -> ""
        start == null -> stringResource(R.string.tx_event_until, short(end!!))
        end == null -> stringResource(R.string.tx_event_from, short(start))
        else -> remember(start, end, locale) {
            DateIntervalFormat.getInstance("dMMM", ULocale.forLocale(locale)).apply { timeZone = TimeZone.getTimeZone("UTC") }
                .format(DateInterval(ms(start), ms(end)), StringBuffer(), java.text.FieldPosition(0)).toString()
        }
    }
}

// ---------- CategorySheet ----------

/**
 * Pick a category: search, Most used, All (subcategories under their parent), New. From the tile it picks; from
 * Next ("Pick a category" with the reason) a tap saves the entry at once.
 */
@Composable
private fun CategorySheet(m: AddModel, ui: AddUi, close: () -> Unit) {
    val c = KoshaTheme.colors
    var query by remember { mutableStateOf("") }
    val all = m.candidates
    val tops = m.tops
    val payee = m.payee.trim()
    val income = m.kind == EntryKind.Income
    val reason = when {
        payee.isNotEmpty() -> stringResource(if (income) R.string.app_add_why_income_new else R.string.app_add_why_expense_new, payee)
        else -> stringResource(if (income) R.string.app_add_why_income else R.string.app_add_why_expense)
    }
    val title = stringResource(if (m.viaNext) R.string.tx_pick_category else R.string.add_category)
    fun tile(cat: MetaCategory) = CategoryTile(cat.id, categoryIconName(cat.icon), cat.name)
    val pick = { t: CategoryTile ->
        val cat = all.first { it.id == t.id }
        val top = cat.parentId?.takeIf { p -> tops.any { it.id == p } } ?: cat.id
        val sub = cat.id.takeIf { it != top }
        if (m.viaNext) m.save(ui, again = false, top = top, sub = sub) else m.pickCategory(top, sub)
    }
    val picked = if (m.categoryId != null) m.subcategoryId ?: m.categoryId else null
    val guess = if (m.categoryId == null) m.chosen else null
    val guessLabel = stringResource(R.string.add_suggested, m.chosenCategory?.name ?: "")
    Sheet(title, close, subtitle = if (m.viaNext) reason else stringResource(R.string.app_add_most_used_first)) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Field(query, { query = it }, placeholder = stringResource(R.string.add_find_category), icon = "magnifying-glass")
            val q = query.trim()
            if (q.isNotEmpty()) {
                val found = all.filter { cat ->
                    val parent = all.firstOrNull { it.id == cat.parentId }
                    (listOfNotNull(parent?.name, cat.name).joinToString(" › ")).contains(q, ignoreCase = true)
                }
                if (found.isEmpty()) KText(stringResource(R.string.add_no_category_match), color = c.ink3)
                CategoryGrid(found.map(::tile), picked, pick, guess = guess, aiGuess = m.chosenIsAi, guessLabel = guessLabel)
            } else {
                val most = tops.sortedByDescending { it.uses }.take(10)
                Caption(stringResource(R.string.app_most_used))
                CategoryGrid(most.map(::tile), picked, pick, guess = guess, aiGuess = m.chosenIsAi, guessLabel = guessLabel)
                // The rest, each with its subcategories after it (category-picker.tsx).
                val rest = tops.filter { it !in most }.flatMap { t -> listOf(t) + all.filter { it.parentId == t.id } } +
                    most.flatMap { t -> all.filter { it.parentId == t.id } }
                if (rest.isNotEmpty()) {
                    Caption(stringResource(R.string.add_all))
                    CategoryGrid(rest.map(::tile), picked, pick, guess = guess, aiGuess = m.chosenIsAi, guessLabel = guessLabel)
                }
            }
            Button(
                stringResource(R.string.settings_cat_new),
                {
                    close()
                    ui.nav.navigate(Route.Settings("category"))
                },
                style = ButtonStyle.Secondary,
                size = ButtonSize.Medium,
                icon = "plus",
            )
        }
    }
}

// ---------- FeeSheet ----------

private enum class FeeField { Fee, Received, Rate }

/**
 * Fee and rate for a transfer (FeeSheet): the fee in From's currency, what arrived in To's, the provider, and the
 * rate, with what it cost against the market rate. Typing the rate works out what arrived, and the other way round.
 */
@Composable
private fun FeeSheet(m: AddModel, close: () -> Unit) {
    val c = KoshaTheme.colors
    val locale = KoshaTheme.locale
    val focus = LocalFocusManager.current
    val from = m.account ?: return
    val to = m.toAccount
    val s = m.sides
    val (grouping, decimal) = Money.separators(locale)
    var field by remember { mutableStateOf(FeeField.Fee) }
    val toDecimals = Money.decimals(to?.currency ?: from.currency)
    fun show(text: String) = if (text.isEmpty()) "" else Keypad.group(text, grouping, decimal)
    fun rate(r: Double): String = (NumberFormat.getNumberInstance(locale) as DecimalFormat).apply {
        val bd = fmtRate(r)
        minimumFractionDigits = bd.scale().coerceAtLeast(0)
        maximumFractionDigits = bd.scale().coerceAtLeast(0)
    }.format(fmtRate(r))
    val f = m.fee
    Sheet(stringResource(if (s.cross) R.string.add_fee_and_rate else R.string.add_fee), close) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                app.getkosha.design.AccountDot(c.account(m.colorIndex(from.id)), size = 10.dp)
                KText(from.name, maxLines = 1)
                if (to != null) {
                    PhIcon("arrow-right", size = 16.dp, tint = c.ink3)
                    app.getkosha.design.AccountDot(c.account(m.colorIndex(to.id)), size = 10.dp)
                    KText(to.name, Modifier.weight(1f), maxLines = 1)
                } else Spacer(Modifier.weight(1f))
                KText(money(m.minor ?: 0, from.currency), style = KoshaTheme.type.num, color = c.ink2)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                KeyField(stringResource(R.string.tx_fee_currency, from.currency.uppercase()), show(f.fee), field == FeeField.Fee, Modifier.weight(1f),
                    onDelete = { m.fee = f.copy(fee = Keypad.press(f.fee, Keypad.Key.Delete)) }, onClear = { m.fee = f.copy(fee = "") }) {
                    focus.clearFocus()
                    field = FeeField.Fee
                }
                if (s.cross && to != null) {
                    val typed = f.typed == FeeInput.Typed.Received
                    KeyField(stringResource(R.string.tx_received_currency, to.currency.uppercase()),
                        if (typed) show(f.received) else show(keypadInput(s.received, toDecimals)), field == FeeField.Received, Modifier.weight(1f),
                        onDelete = { m.fee = f.copy(received = Keypad.press(if (typed) f.received else "", Keypad.Key.Delete), typed = FeeInput.Typed.Received) },
                        onClear = { m.fee = f.copy(received = "", typed = FeeInput.Typed.Received) }) {
                        focus.clearFocus()
                        field = FeeField.Received
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
                Field(f.provider, { m.fee = f.copy(provider = it.take(60)) }, Modifier.weight(1f), label = stringResource(R.string.tx_via),
                    keyboard = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done))
                if (s.cross && to != null) {
                    val typed = f.typed == FeeInput.Typed.Rate
                    KeyField(stringResource(R.string.tx_rate_label, from.currency.uppercase()), if (typed) show(f.rate) else rate(s.rate), field == FeeField.Rate, Modifier.weight(1f),
                        onDelete = { m.fee = f.copy(rate = Keypad.press(if (typed) f.rate else "", Keypad.Key.Delete), typed = FeeInput.Typed.Rate) },
                        onClear = { m.fee = f.copy(rate = "", typed = FeeInput.Typed.Rate) }) {
                        focus.clearFocus()
                        field = FeeField.Rate
                    }
                }
            }
            val sentence = buildList {
                if (s.converted > 0 && (s.fee > 0 || s.cross)) {
                    add(
                        if (s.cross && to != null) stringResource(R.string.tx_fx_becomes, money(s.converted, from.currency), money(s.received, to.currency), rate(s.market))
                        else stringResource(R.string.tx_fx_arrives, money(s.converted, from.currency)),
                    )
                    val cost = s.cost
                    if (cost.totalCost > 0.5) {
                        add(stringResource(R.string.add_fx_cost, money(Math.round(cost.totalCost), from.currency),
                            NumberFormat.getNumberInstance(locale).apply { minimumFractionDigits = 2; maximumFractionDigits = 2 }.format(cost.costPct)))
                    }
                }
            }
            if (sentence.isNotEmpty()) {
                KText(sentence.joinToString(" "), Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.soft).padding(14.dp), style = KoshaTheme.type.small, color = c.ink2)
            }
            when (field) {
                FeeField.Fee -> AmountKeypad(f.fee, { m.fee = f.copy(fee = it) }, decimals = Money.decimals(from.currency))
                FeeField.Received -> AmountKeypad(
                    if (f.typed == FeeInput.Typed.Received) f.received else "",
                    { m.fee = f.copy(received = it, typed = FeeInput.Typed.Received) },
                    decimals = toDecimals,
                )
                FeeField.Rate -> AmountKeypad(
                    if (f.typed == FeeInput.Typed.Rate) f.rate else "",
                    { m.fee = f.copy(rate = it, typed = FeeInput.Typed.Rate) },
                    decimals = RATE_DECIMALS,
                )
            }
            Button(stringResource(R.string.common_done), close, Modifier.fillMaxWidth())
        }
    }
}

/** A number typed on Kosha's keypad, never the system keyboard: tap to make it the one the keypad fills. */
@Composable
private fun KeyField(label: String, text: String, on: Boolean, modifier: Modifier, onDelete: () -> Unit, onClear: () -> Unit, onClick: () -> Unit) {
    val c = KoshaTheme.colors
    val shape = RoundedCornerShape(14.dp)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        KText(label, style = KoshaTheme.type.small, color = c.ink3, maxLines = 1)
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .clip(shape)
                .background(c.surface)
                .border(if (on) 2.dp else 1.dp, if (on) c.ink else c.line, shape)
                .selectable(on, role = Role.Button, onClick = onClick)
                .semantics(mergeDescendants = true) { contentDescription = "$label $text" }
                .padding(start = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            KText(text, Modifier.weight(1f), style = KoshaTheme.type.num.copy(fontSize = KoshaTheme.type.body.fontSize), maxLines = 1)
            if (on && text.isNotEmpty()) {
                HoldKey(label = stringResource(R.string.app_key_delete), onClick = onDelete, onHold = onClear) {
                    PhIcon("backspace", size = 20.dp, tint = c.ink3)
                }
            } else Spacer(Modifier.size(48.dp))
        }
    }
}

// ---------- DescribeSheet ----------

/** Describe it (AI): one sentence, Fill in, or Scan a receipt. The system keyboard is right here: these are words. */
@Composable
private fun DescribeSheet(m: AddModel, ui: AddUi, close: () -> Unit) {
    val c = KoshaTheme.colors
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Sheet(stringResource(R.string.add_describe), close) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Field(
                m.described,
                { m.described = it.take(500) },
                Modifier.focusRequester(focus),
                label = stringResource(R.string.add_describe_label),
                placeholder = stringResource(R.string.add_describe_placeholder),
                singleLine = false,
                keyboard = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            )
            KText(stringResource(R.string.add_describe_help), style = KoshaTheme.type.small, color = c.ink3)
            PrivacyLine(m, ui)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (m.meta.ai.receipts) {
                    Button(stringResource(R.string.add_scan_receipt), {
                        close()
                        m.camera = true
                    }, style = ButtonStyle.Secondary, icon = "camera")
                }
                Button(stringResource(R.string.add_fill_in), { m.describe(ui) }, Modifier.weight(1f), enabled = m.described.isNotBlank())
            }
        }
    }
}

/** "Kosha sends only what you share with Anthropic", with "what you share" opening What AI can see. */
@Composable
internal fun PrivacyLine(m: AddModel, ui: AddUi, modifier: Modifier = Modifier) {
    val c = KoshaTheme.colors
    val model = m.meta.aiModel ?: return
    val provider = when (model.provider) {
        "anthropic" -> "Anthropic"
        "openai" -> "OpenAI"
        "gemini" -> "Gemini"
        "openrouter" -> "OpenRouter"
        "ollama" -> "Ollama"
        "compatible" -> stringResource(R.string.ask_your_endpoint)
        else -> model.provider
    }
    val raw = LocalResources.current.getText(R.string.app_add_privacy)
    val spanned = raw as? Spanned
    val link = spanned?.getSpans(0, raw.length, Annotation::class.java)?.firstOrNull()
    val text = raw.toString()
    val start = link?.let { spanned.getSpanStart(it) } ?: -1
    val end = link?.let { spanned.getSpanEnd(it) } ?: -1
    val annotated = buildAnnotatedString {
        fun plain(part: String) = append(part.replace("%1\$s", provider))
        if (start < 0) plain(text) else {
            plain(text.substring(0, start))
            withLink(LinkAnnotation.Clickable("privacy", TextLinkStyles(SpanStyle(textDecoration = TextDecoration.Underline))) {
                ui.nav.navigate(Route.Settings("ai-privacy"))
            }) { plain(text.substring(start, end)) }
            plain(text.substring(end))
        }
    }
    BasicText(annotated, modifier, style = KoshaTheme.type.small.copy(color = c.ink3))
}
