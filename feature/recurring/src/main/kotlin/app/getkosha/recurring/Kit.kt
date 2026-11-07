package app.getkosha.recurring

import android.os.Build
import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.getkosha.data.Kosha
import app.getkosha.data.Loaded
import app.getkosha.design.AmountDisplay
import app.getkosha.design.AmountKeypad
import app.getkosha.design.Button
import app.getkosha.design.ButtonStyle
import app.getkosha.design.KText
import app.getkosha.design.KoshaTheme
import app.getkosha.design.MonthGrid
import app.getkosha.design.PhIcon
import app.getkosha.design.R
import app.getkosha.design.Sheet
import app.getkosha.design.Toast
import app.getkosha.design.ToastState
import app.getkosha.model.Dates
import app.getkosha.model.EntryKind
import app.getkosha.model.Keypad
import app.getkosha.model.Money
import app.getkosha.model.Sign
import app.getkosha.network.KoshaApi
import app.getkosha.ui.Banners
import app.getkosha.ui.LocalToasts
import app.getkosha.ui.hideAmounts
import app.getkosha.ui.meta
import app.getkosha.ui.rememberKosha
import app.getkosha.ui.write
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

// Small pieces Recurring and Accounts share: cached reads, writes, dates, percentages, the keypad and date
// sheets, the form scaffold and detail rows. Accounts depends on this module for them.

/** A cached read keyed on [key], read again after every change ([Kosha.changed]). */
@Composable
fun <T> rememberLoad(key: String, serializer: KSerializer<T>, fetch: suspend KoshaApi.() -> T): Loaded<T> {
    val kosha = rememberKosha()
    val version by kosha.version.collectAsStateWithLifecycle()
    val flow = remember(key, version) { kosha.load(key, serializer, fetch) }
    return flow.collectAsStateWithLifecycle(Loaded<T>(null, refreshing = true)).value
}

@Composable
inline fun <reified T> rememberLoad(key: String, noinline fetch: suspend KoshaApi.() -> T): Loaded<T> =
    rememberLoad(key, serializer<T>(), fetch)

/**
 * Runs writes: the demo account is told changes are off, the server's message shows when it refuses, and screens
 * read again when it works. [run] returns the block's result, or null when it didn't happen.
 */
class Writer(private val kosha: Kosha, private val toasts: ToastState, private val scope: CoroutineScope, private val demo: Boolean, private val offline: String, private val readOnly: String) {
    fun launch(block: suspend KoshaApi.() -> Unit) {
        scope.launch { run(block) }
    }

    suspend fun <T> run(block: suspend KoshaApi.() -> T): T? {
        if (demo) {
            toasts.show(Toast(readOnly))
            return null
        }
        return kosha.write(toasts, offline) { kosha.api.block() }
    }

    fun toast(message: String) = toasts.show(Toast(message))
}

@Composable
fun rememberWriter(): Writer {
    val kosha = rememberKosha()
    val toasts = LocalToasts.current
    val scope = rememberCoroutineScope()
    val demo = meta()?.demo == true
    val offline = stringResource(R.string.common_offline_title)
    val readOnly = stringResource(R.string.common_demo_read_only)
    return remember(demo, offline) { Writer(kosha, toasts, scope, demo, offline, readOnly) }
}

/** Today in the user's time zone from settings, never the phone's. */
@Composable
fun today(): LocalDate {
    val m = meta()
    return remember(m?.tz) { Dates.today(m?.tz ?: "UTC") }
}

/** A date in the language's own order for a skeleton such as "dMMM" (20 Nov), "EEEdMMM" (Thu 20 Nov) or "MMMyyyy". */
fun formatDay(day: LocalDate, skeleton: String, locale: Locale): String =
    DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale).format(day)

@Composable
fun dayText(day: String, skeleton: String = "dMMM"): String {
    val locale = KoshaTheme.locale
    return remember(day, skeleton, locale) { formatDay(LocalDate.parse(day), skeleton, locale) }
}

@Composable
fun monthText(month: YearMonth, skeleton: String = "MMMMyyyy"): String {
    val locale = KoshaTheme.locale
    return remember(month, skeleton, locale) { formatDay(month.atDay(1), skeleton, locale) }
}

/** A ratio as a percentage in the language: 0.124 → "12.4%" or "12,4 %". */
fun percent(ratio: Double, locale: Locale, digits: Int = 0, sign: Boolean = false): String {
    val f = NumberFormat.getPercentInstance(locale).apply {
        minimumFractionDigits = digits
        maximumFractionDigits = digits
    }
    val text = f.format(abs(ratio))
    return when {
        ratio < 0 -> Money.MINUS + text
        sign && ratio > 0 -> "+$text"
        else -> text
    }
}

/** Upper-cases the first letter, for message phrases that start a line ("every month" → "Every month"). */
fun String.capitalised(locale: Locale) = replaceFirstChar { it.titlecase(locale) }

/** A keypad input that shows an amount, such as `10.99` for 1099, or `1200` for ¥1,200 (stored as 120000). */
fun inputOf(minor: Long, currency: String): String {
    if (minor == 0L) return ""
    // The keypad has no minus key; a negative amount (an overdrawn balance, a loss) is typed as 0 − amount.
    if (minor < 0) return "0-" + inputOf(-minor, currency)
    val v = BigDecimal.valueOf(abs(minor), 2).setScale(Money.decimals(currency), RoundingMode.HALF_UP)
    return if (Money.decimals(currency) == 0) v.toPlainString() else v.toPlainString().removeSuffix(".00")
}

/**
 * What the keypad holds. It starts showing a known amount; the first digit replaces it, as a selected field
 * would, while backspace edits it.
 */
@Stable
class AmountInput(private val prefilled: String) {
    var input by mutableStateOf("")
        private set
    private var fresh by mutableStateOf(prefilled.isNotEmpty())

    val shown get() = if (fresh) prefilled else input

    /** For the keypad: empty while the prefilled amount waits to be replaced. */
    val keypad get() = if (fresh) "" else input

    fun fromKeypad(text: String) {
        fresh = false
        input = text
    }

    fun fromDisplay(text: String) {
        if (fresh) {
            fresh = false
        }
        input = text
    }

    /** The amount in minor units, ×100 for every currency, with its sign; null when it divides by zero. */
    fun minor(currency: String): Long? =
        Keypad.evaluate(shown, Money.decimals(currency))?.movePointRight(2)?.setScale(0, RoundingMode.HALF_UP)?.toLong()
}

@Composable
fun rememberAmountInput(minor: Long?, currency: String, vararg keys: Any?): AmountInput =
    remember(*keys) { AmountInput(minor?.let { inputOf(it, currency) } ?: "") }

/** The amount line and the 4×4 keypad. */
@Composable
fun AmountPad(state: AmountInput, currency: String, kind: EntryKind = EntryKind.Transfer, note: String? = null, keypad: Boolean = true) {
    AmountDisplay(state.shown, currency, kind, { state.fromDisplay(it) }, note = note)
    if (keypad) AmountKeypad(state.keypad, { state.fromKeypad(it) }, decimals = Money.decimals(currency))
}

/** A sheet to type one positive amount on the keypad. */
@Composable
fun KeypadSheet(
    title: String,
    minor: Long?,
    currency: String,
    onDone: (Long) -> Unit,
    onDismiss: () -> Unit,
    subtitle: String? = null,
    note: String? = null,
    kind: EntryKind = EntryKind.Transfer,
    clearLabel: String? = null,
    onClear: (() -> Unit)? = null,
    /** False for a balance, which can be zero or overdrawn (typed as 0 − amount). */
    positive: Boolean = true,
) {
    val state = rememberAmountInput(minor, currency, minor, currency)
    val value = state.minor(currency)
    Sheet(title, onDismiss, subtitle = subtitle) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AmountPad(state, currency, kind, note)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (onClear != null && clearLabel != null) {
                    Button(clearLabel, { onClear() }, Modifier.weight(1f), style = ButtonStyle.Secondary)
                }
                Button(stringResource(R.string.common_done), { value?.let(onDone) }, Modifier.weight(1f), enabled = value != null && (!positive || value > 0))
            }
        }
    }
}

/** A sheet with the month grid, to pick a day. [onClear] adds a button such as "Never". */
@Composable
fun DateSheet(
    title: String,
    selected: LocalDate?,
    onPick: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
    clearLabel: String? = null,
    onClear: (() -> Unit)? = null,
) {
    val today = today()
    var month by remember { mutableStateOf(YearMonth.from(selected ?: today)) }
    Sheet(title, onDismiss) {
        MonthGrid(month, selected, today, onPick, { month = it })
        if (onClear != null && clearLabel != null) {
            Spacer(Modifier.heightIn(min = 8.dp))
            Button(clearLabel, onClear, Modifier.fillMaxWidth(), style = ButtonStyle.Secondary)
        }
    }
}

/** Confirms a destructive action in a sheet with a danger button. */
@Composable
fun ConfirmSheet(title: String, body: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    Sheet(title, onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            KText(body, style = KoshaTheme.type.body, color = KoshaTheme.colors.ink2)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(stringResource(R.string.common_cancel), onDismiss, Modifier.weight(1f), style = ButtonStyle.Secondary)
                Button(confirm, onConfirm, Modifier.weight(1f), style = ButtonStyle.Danger)
            }
        }
    }
}

/**
 * A full-screen form: Cancel on the left, the title centred, scrolling fields with a 16dp gap, and a sticky
 * footer for the main button.
 */
@Composable
fun FormPage(title: String, onCancel: () -> Unit, footer: @Composable () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val c = KoshaTheme.colors
    Column(Modifier.fillMaxSize().background(c.bg).statusBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.widthIn(min = 72.dp).heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onCancel).padding(horizontal = 8.dp),
                contentAlignment = Alignment.CenterStart,
            ) { KText(stringResource(R.string.common_cancel), color = c.ink2) }
            KText(
                title,
                Modifier.weight(1f).semantics { heading() },
                style = KoshaTheme.type.head.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
                align = TextAlign.Center,
                maxLines = 2,
            )
            Spacer(Modifier.width(72.dp))
        }
        Banners()
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content,
        )
        Box(Modifier.fillMaxWidth().background(c.glass).navigationBarsPadding().padding(16.dp)) { footer() }
    }
}

/** A label and its value in a tight card: "Account   ● N26 Main  ›". */
@Composable
fun DetailRow(label: String, onClick: (() -> Unit)? = null, labelWidth: Dp = 92.dp, trailing: String? = if (onClick != null) "caret-right" else null, content: @Composable RowScope.() -> Unit) {
    val c = KoshaTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .heightIn(min = KoshaTheme.dims.settingsRow)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        KText(label, Modifier.width(labelWidth), style = KoshaTheme.type.small, color = c.ink3)
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), content = content)
        if (trailing != null) PhIcon(trailing, size = 16.dp, tint = c.ink3)
    }
}

/** A title and description with a switch, in a tight card. */
@Composable
fun SwitchRow(title: String, description: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = KoshaTheme.dims.settingsRow).padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            KText(title)
            if (description != null) KText(description, style = KoshaTheme.type.small, color = KoshaTheme.colors.ink3)
        }
        app.getkosha.design.Switch(checked, onChange, title)
    }
}

/**
 * Big money with the cents in ink3 (`€1,203` + `.47`), blurred with Hide amounts and peeked by touch and hold,
 * like MoneyText.
 */
@Composable
fun HeroMoney(minor: Long, currency: String, modifier: Modifier = Modifier, size: TextUnit = 40.sp) {
    val c = KoshaTheme.colors
    val locale = KoshaTheme.locale
    val text = Money.format(minor, currency, locale, Sign.Natural)
    val decimal = Money.separators(locale).second
    val cut = if (Money.decimals(currency) > 0) text.lastIndexOf(decimal) else -1
    val hidden = hideAmounts()
    var peek by remember { mutableStateOf(false) }
    val masked = hidden && !peek
    val styled = buildAnnotatedString {
        if (cut < 0) append(text) else {
            append(text.substring(0, cut))
            withStyle(SpanStyle(color = c.ink3)) { append(text.substring(cut)) }
        }
    }
    BasicText(
        if (masked && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) buildAnnotatedString { append("••••") } else styled,
        modifier
            .semantics { contentDescription = text }
            .then(if (masked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Modifier.blur(9.dp) else Modifier)
            .then(if (hidden) Modifier.pointerInput(Unit) { detectTapGestures(onPress = { peek = true; tryAwaitRelease(); peek = false }) } else Modifier),
        style = KoshaTheme.type.hero.copy(fontSize = size, color = c.ink),
        maxLines = 1,
    )
}

/** Swiping sideways moves one month, as on Overview and Activity. */
fun Modifier.swipeMonths(onMonth: (Int) -> Unit) = pointerInput(Unit) {
    var dx = 0f
    val threshold = 72.dp.toPx()
    detectHorizontalDragGestures(
        onDragStart = { dx = 0f },
        onDragEnd = {
            if (dx > threshold) onMonth(-1) else if (dx < -threshold) onMonth(1)
        },
        onHorizontalDrag = { _, d -> dx += d },
    )
}

/** A wide, soft hint line with an icon, as under the recurring form's dates. */
@Composable
fun HintLine(icon: String, text: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PhIcon(icon, size = 18.dp, tint = KoshaTheme.colors.ink3)
        KText(text, Modifier.weight(1f), style = KoshaTheme.type.small, color = KoshaTheme.colors.ink2)
    }
}
