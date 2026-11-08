@file:UseSerializers(RoundedLong::class)

package app.getkosha.ui

import kotlinx.serialization.UseSerializers
import app.getkosha.model.RoundedLong

import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.core.net.toUri
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import app.getkosha.data.Kosha
import app.getkosha.design.Button
import app.getkosha.design.ButtonSize
import app.getkosha.design.ButtonStyle
import app.getkosha.design.Card
import app.getkosha.design.Chip
import app.getkosha.design.Hairline
import app.getkosha.design.IconButton
import app.getkosha.design.KText
import app.getkosha.design.KoshaTheme
import app.getkosha.design.PhIcon
import app.getkosha.design.Pill
import app.getkosha.design.R
import app.getkosha.design.Radio
import app.getkosha.design.Segmented
import app.getkosha.design.Sheet
import app.getkosha.design.Switch
import app.getkosha.model.Dates
import app.getkosha.model.Money
import app.getkosha.model.Sign
import app.getkosha.model.convert
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Currency
import java.util.Locale
import kotlin.math.abs
import java.time.format.TextStyle as DateStyle

/**
 * The header of the main tabs. Overview, Activity and Recurring show the selected month (tap for the month
 * sheet); Accounts shows its title. Then the display currency pill (tap to step through the user's currencies,
 * touch and hold for the currency sheet), the Ask sparkle when Ask Kosha is on, an optional action, and the
 * avatar, which opens the account menu.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MainHeader(
    modifier: Modifier = Modifier,
    title: String? = null,
    subtitle: String? = null,
    showMonth: Boolean = title == null,
    showCurrency: Boolean = showMonth,
    action: (@Composable () -> Unit)? = null,
) {
    val kosha = rememberKosha()
    val nav = LocalNav.current
    val m = meta()
    val month by kosha.month.collectAsStateWithLifecycle()
    val currency by kosha.currency.collectAsStateWithLifecycle()
    val locale = KoshaTheme.locale
    var sheet by remember { mutableStateOf<String?>(null) }
    val previous = stringResource(R.string.shell_previous_month)
    val next = stringResource(R.string.shell_next_month)
    Row(
        modifier.fillMaxWidth().statusBarsPadding().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (showMonth) {
            val today = Dates.month(kosha.tz)
            val label = month.month.getDisplayName(DateStyle.FULL_STANDALONE, locale) +
                if (month.year != today.year) " ${month.year}" else ""
            Row(
                Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(role = Role.Button) { sheet = "month" }
                    .semantics {
                        contentDescription = month.format(DateTimeFormatter.ofPattern("LLLL yyyy", locale))
                        // The swipe that moves a month, for TalkBack.
                        customActions = listOf(
                            CustomAccessibilityAction(previous) { kosha.month.value = kosha.month.value.minusMonths(1); true },
                            CustomAccessibilityAction(next) { kosha.month.value = kosha.month.value.plusMonths(1); true },
                        )
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                KText(label, Modifier.weight(1f, fill = false).semantics { heading() }, style = KoshaTheme.type.page, maxLines = 1)
                PhIcon("caret-down", size = 18.dp, tint = KoshaTheme.colors.ink3)
            }
        } else {
            Column(Modifier.weight(1f)) {
                KText(title ?: "", Modifier.semantics { heading() }, style = KoshaTheme.type.page, maxLines = 1)
                if (subtitle != null) KText(subtitle, style = KoshaTheme.type.small, color = KoshaTheme.colors.ink3)
            }
        }
        if (showCurrency && m != null) {
            val list = (listOf(m.primary) + m.order).distinct()
            val shown = currency ?: m.primary
            if (list.size > 1) {
                val following = list[(list.indexOf(shown) + 1) % list.size]
                Box(
                    Modifier
                        .heightIn(min = 48.dp)
                        .combinedClickable(
                            role = Role.Button,
                            onClickLabel = stringResource(R.string.shell_currency_switch_to, following.uppercase()),
                            onLongClickLabel = stringResource(R.string.app_ov_show_totals_in),
                            onLongClick = { sheet = "currency" },
                            onClick = { kosha.currency.value = following.takeIf { it != m.primary } },
                        )
                        .semantics { contentDescription = shown.uppercase() },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier.height(36.dp).clip(CircleShape).border(1.dp, KoshaTheme.colors.line, CircleShape)
                            .background(KoshaTheme.colors.surface).padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) { KText(shown.uppercase(), style = KoshaTheme.type.num.copy(fontSize = 13.sp), color = KoshaTheme.colors.ink2) }
                }
            }
        }
        if (m?.ai?.ask == true) {
            IconButton("sparkle", stringResource(R.string.shell_ask_kosha), { nav.navigate(Route.Ask()) }, tint = KoshaTheme.colors.brand)
        }
        action?.invoke()
        Avatar(m?.user?.name ?: "", onClick = { sheet = "menu" })
    }
    when (sheet) {
        "month" -> MonthSheet(month, onPick = { kosha.month.value = it; sheet = null }, onDismiss = { sheet = null })
        "currency" -> CurrencySheet(onDismiss = { sheet = null })
        "menu" -> AccountMenu(onDismiss = { sheet = null })
    }
}

/** The avatar: the account's initial on the selection ground. 48dp to tap. */
@Composable
fun Avatar(name: String, onClick: () -> Unit, size: Int = 36) {
    val label = stringResource(R.string.shell_account_menu)
    val photo = profilePhoto()
    Box(Modifier.size(48.dp).clickable(role = Role.Button, onClickLabel = label, onClick = onClick).semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
        Box(Modifier.size(size.dp).clip(CircleShape).background(KoshaTheme.colors.sel), contentAlignment = Alignment.Center) {
            if (photo != null) {
                androidx.compose.foundation.Image(photo, null, Modifier.size(size.dp), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
            } else {
                KText(name.trim().take(1).uppercase().ifEmpty { "K" }, style = KoshaTheme.type.small.copy(fontSize = (size * 0.39).sp, fontWeight = FontWeight.SemiBold))
            }
        }
    }
}

/** Profile pictures already fetched, by their URL (user.image changes with each upload). */
private object Photos {
    val loaded = mutableMapOf<String, androidx.compose.ui.graphics.ImageBitmap>()
}

/** The account's profile picture from GET /api/app/avatar, or null when there is none or it can't load. */
@Composable
fun profilePhoto(): androidx.compose.ui.graphics.ImageBitmap? {
    val kosha = rememberKosha()
    val image = meta()?.user?.image ?: return null
    val photo by androidx.compose.runtime.produceState(Photos.loaded[image], image) {
        if (value != null) return@produceState
        value = runCatching {
            val bytes = kosha.api.bytes("/api/app/avatar")
            android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }.getOrNull()?.also { Photos.loaded[image] = it }
    }
    return photo
}

/** One month of get_month_overview's history: what MonthSheet shows under each month. */
@Serializable
private data class MonthHistory(val history: List<Item> = emptyList()) {
    @Serializable
    data class Item(val month: String, val spending: Long = 0)
}

/**
 * A year of months to jump to, each with what was spent in it (one get_month_overview for December gives the
 * year's twelve). "Swipe Overview, Activity or Recurring sideways to move one month." [under] replaces the
 * spending line, for screens that show something else.
 */
@Composable
fun MonthSheet(month: YearMonth, onPick: (YearMonth) -> Unit, onDismiss: () -> Unit, under: (@Composable (YearMonth) -> Unit)? = null) {
    val kosha = rememberKosha()
    val m = meta()
    var year by remember { mutableStateOf(month.year) }
    val locale = KoshaTheme.locale
    val c = KoshaTheme.colors
    val now = Dates.month(kosha.tz)
    val currency = kosha.currency.collectAsStateWithLifecycle().value ?: m?.primary ?: "eur"
    val version by kosha.version.collectAsStateWithLifecycle()
    val spent = if (under == null && year <= now.year) {
        remember(year, currency, version) {
            kosha.load("monthsheet/$year/$currency") {
                get<MonthHistory>("/api/v1/reports/month", mapOf("month" to "$year-12", "currency" to currency))
            }
        }.collectAsStateWithLifecycle(null).value?.value?.history?.associate { it.month to it.spending }.orEmpty()
    } else emptyMap()
    Sheet(stringResource(R.string.app_ov_pick_month), onDismiss) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Chip(stringResource(R.string.overview_this_month), soft = true, small = true, onClick = { onPick(now) })
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton("caret-left", stringResource(R.string.app_previous_year), { year-- })
            KText(year.toString(), Modifier.weight(1f), style = KoshaTheme.type.head.copy(fontWeight = FontWeight.SemiBold), align = TextAlign.Center)
            IconButton("caret-right", stringResource(R.string.app_next_year), { year++ })
        }
        (1..12).chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { n ->
                    val ym = YearMonth.of(year, n)
                    val on = ym == month
                    val future = ym > now
                    Column(
                        Modifier
                            .weight(1f)
                            .heightIn(min = 64.dp)
                            .alpha(if (future && !on) 0.55f else 1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (on) c.selection else c.soft)
                            .clickable(role = Role.RadioButton) { onPick(ym) }
                            .padding(4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
                    ) {
                        KText(
                            ym.month.getDisplayName(DateStyle.FULL_STANDALONE, locale),
                            style = KoshaTheme.type.body.let { if (on) it.copy(fontWeight = FontWeight.Medium) else it },
                            color = if (on) c.onSelection else c.ink,
                            maxLines = 1,
                        )
                        val tint = if (on) c.onSelection else c.ink3
                        when {
                            under != null -> under(ym)
                            future -> KText(stringResource(R.string.app_ov_not_started), style = KoshaTheme.type.xs, color = tint, maxLines = 1)
                            spent[ym.toString()] != null -> {
                                val amount = wholeMoney(spent.getValue(ym.toString()), currency, locale)
                                AmountText(
                                    AnnotatedString(if (ym == now) stringResource(R.string.app_ov_month_so_far, amount) else amount),
                                    style = KoshaTheme.type.num.copy(fontSize = 12.sp),
                                    color = tint,
                                )
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        KText(stringResource(R.string.app_month_swipe), Modifier.fillMaxWidth(), style = KoshaTheme.type.xs, color = c.ink3, align = TextAlign.Center)
    }
}

@Serializable
private data class NetWorthTotal(val currency: String = "", val total: Long = 0)

/**
 * "Show totals in": the user's currencies in their order, each with today's rate and net worth converted at it,
 * then a way to Settings › Currencies. Picking one sets the display currency for Overview, Activity and Recurring.
 */
@Composable
fun CurrencySheet(onDismiss: () -> Unit) {
    val kosha = rememberKosha()
    val nav = LocalNav.current
    val m = meta() ?: return
    val c = KoshaTheme.colors
    val locale = KoshaTheme.locale
    val shown = kosha.currency.collectAsStateWithLifecycle().value ?: m.primary
    val version by kosha.version.collectAsStateWithLifecycle()
    // ponytail: net worth in the main currency, converted at the server's rates for the others, as the web does.
    val worth = remember(version) {
        kosha.load("networth/${m.primary}") { get<NetWorthTotal>("/api/v1/reports/net-worth") }
    }.collectAsStateWithLifecycle(null).value?.value
    Sheet(stringResource(R.string.app_ov_show_totals_in), onDismiss) {
        Card(tight = true) {
            (listOf(m.primary) + m.order).distinct().forEachIndexed { i, code ->
                if (i > 0) Hairline()
                val on = code == shown
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = KoshaTheme.dims.row)
                        .clickable(role = Role.RadioButton) {
                            kosha.currency.value = code.takeIf { it != m.primary }
                            onDismiss()
                        }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Radio(on)
                    Column(Modifier.weight(1f)) {
                        val name = runCatching { Currency.getInstance(code.uppercase()).getDisplayName(locale) }.getOrDefault(code.uppercase())
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            KText(name, Modifier.weight(1f, fill = false), style = if (on) KoshaTheme.type.head else KoshaTheme.type.body)
                            KText(code.uppercase(), style = KoshaTheme.type.num, color = c.ink3)
                        }
                        KText(
                            if (code == m.primary) stringResource(R.string.app_ov_main_currency)
                            else "1 ${m.primary.uppercase()} = ${rateText(m.rates[code], m.rates[m.primary])} ${code.uppercase()}",
                            style = KoshaTheme.type.xs,
                            color = c.ink3,
                        )
                    }
                    if (worth != null) MoneyText(convert(worth.total, m.primary, code, m.rates), code, color = c.ink2)
                }
            }
        }
        KText(stringResource(R.string.app_ov_converted_today), Modifier.padding(start = 4.dp, end = 4.dp, top = 12.dp), style = KoshaTheme.type.xs, color = c.ink3)
        Spacer(Modifier.height(16.dp))
        Button(
            stringResource(R.string.app_ov_your_currencies),
            { onDismiss(); nav.navigate(Route.Settings("currencies")) },
            Modifier.fillMaxWidth(),
            style = ButtonStyle.Secondary,
            size = ButtonSize.Medium,
        )
    }
}

/** A rate the way the web writes one (core.ts fmtRate): 94.60, 1.1623, 0.01057. */
fun rateText(to: Double?, from: Double?): String {
    if (to == null || from == null || from == 0.0) return "…"
    val r = to / from
    return when {
        r >= 100 -> String.format(Locale.ROOT, "%.2f", r)
        r >= 1 -> String.format(Locale.ROOT, "%.4f", r)
        else -> BigDecimal(r).round(java.math.MathContext(4)).toPlainString()
    }
}

/** Sign-in methods, for the badge on Sign-in & security (GET /api/app/account). */
@Serializable
private data class SignIns(val hasPassword: Boolean = true, val passkeys: Int = 0, val twoFactor: Boolean = false)

/**
 * The account menu from the avatar: who is signed in, theme, Hide amounts, Settings, Sign-in & security (with a
 * badge for missing steps), Categories, AI, Language, and Sign out ("Signing out clears what this device saved.").
 */
@Composable
fun AccountMenu(onDismiss: () -> Unit) {
    val kosha = rememberKosha()
    val nav = LocalNav.current
    val m = meta()
    val waiting by kosha.outbox.items.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val c = KoshaTheme.colors
    val go = { page: String -> onDismiss(); nav.navigate(Route.Settings(page)) }
    val signIns = remember { kosha.load("account") { get<SignIns>("/api/app/account") } }.collectAsStateWithLifecycle(null).value?.value
    // Password, a passkey, and backup codes once two-factor is on: the steps Security's progress counts.
    val missing = signIns?.let {
        listOf(!it.hasPassword, it.passkeys == 0, it.twoFactor && m?.pref("backup_codes_saved_at", "").isNullOrEmpty()).count { b -> b }
    } ?: 0
    Sheet(m?.user?.name ?: "", onDismiss, subtitle = m?.user?.email) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Card(Modifier.fillMaxWidth()) {
                KText(stringResource(R.string.shell_theme), style = KoshaTheme.type.small, color = c.ink3)
                Spacer(Modifier.height(10.dp))
                Segmented(
                    listOf(
                        "light" to stringResource(R.string.shell_theme_light),
                        "dark" to stringResource(R.string.shell_theme_dark),
                        "system" to stringResource(R.string.shell_theme_system),
                    ),
                    m?.pref("theme", "system") ?: "system",
                    { kosha.setPreference("theme", it) },
                    Modifier.fillMaxWidth(),
                    small = true,
                )
            }
            Card(Modifier.fillMaxWidth(), tight = true) {
                val hideLabel = stringResource(R.string.shell_hide_amounts)
                MenuRow("eye-slash", hideLabel, onClick = null) {
                    Switch(m?.pref("hide_amounts", "0") == "1", { kosha.setPreference("hide_amounts", if (it) "1" else "0") }, hideLabel)
                }
            }
            Card(Modifier.fillMaxWidth(), tight = true) {
                MenuRow("gear", stringResource(R.string.shell_settings), { go("") })
                Hairline()
                MenuRow("shield-check", stringResource(R.string.shell_security), { go("security") }) {
                    if (missing > 0) {
                        Box(Modifier.heightIn(min = 20.dp).widthIn(min = 20.dp).clip(CircleShape).background(c.warn).padding(horizontal = 6.dp), contentAlignment = Alignment.Center) {
                            KText(missing.toString(), style = KoshaTheme.type.xs.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold), color = if (c.dark) Color(0xFF0B0B0C) else Color.White)
                        }
                    }
                }
                Hairline()
                MenuRow("tag", stringResource(R.string.shell_categories), { go("categories") })
                if (m?.ai?.any == true || m?.pref("ai_enabled", "0") == "1") {
                    Hairline()
                    MenuRow("sparkle", stringResource(R.string.shell_ai), { go("ai") }) { Pill(stringResource(R.string.shell_beta), color = c.brand) }
                }
                Hairline()
                MenuRow("translate", stringResource(R.string.common_language), { go("general") }) {
                    val lang = kosha.language
                    KText(lang.getDisplayLanguage(lang).replaceFirstChar { it.titlecase(lang) }, style = KoshaTheme.type.small, color = c.ink3)
                }
            }
            if (waiting.isNotEmpty()) {
                KText(pluralStringResource(R.plurals.app_signout_waiting, waiting.size, waiting.size), style = KoshaTheme.type.small, color = c.warn)
            }
            Card(Modifier.fillMaxWidth(), tight = true) {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = KoshaTheme.dims.settingsRow)
                        .clickable(role = Role.Button) { scope.launch { onDismiss(); kosha.signOut() } }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    PhIcon("sign-out", tint = c.bad)
                    KText(stringResource(R.string.shell_sign_out), color = c.bad)
                }
            }
            KText(stringResource(R.string.app_signout_clears), Modifier.fillMaxWidth(), style = KoshaTheme.type.xs, color = c.ink3, align = TextAlign.Center)
        }
    }
}

@Composable
private fun MenuRow(icon: String, label: String, onClick: (() -> Unit)?, end: @Composable () -> Unit = { PhIcon("caret-right", size = 16.dp, tint = KoshaTheme.colors.ink3) }) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = KoshaTheme.dims.settingsRow)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PhIcon(icon)
        KText(label, Modifier.weight(1f))
        end()
    }
}

// ---------- money that hides ----------

/**
 * Text holding money that follows Hide amounts like [MoneyText]: blurred (masked before Android 12), and a touch
 * and hold peeks. For figures [MoneyText] can't draw: the hero with its cents in ink3, or sentences with an amount.
 */
@Composable
fun AmountText(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    style: TextStyle = KoshaTheme.type.num,
    color: Color = KoshaTheme.colors.ink,
    align: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
) {
    val hidden = hideAmounts()
    var peek by remember { mutableStateOf(false) }
    val masked = hidden && !peek
    BasicText(
        // Same length, so the cents' ink3 span still lines up.
        if (masked) AnnotatedString(maskAmount(text.text), text.spanStyles, text.paragraphStyles) else text,
        modifier
            .then(if (masked) amountBlur(style) else Modifier)
            .then(
                if (hidden) Modifier.pointerInput(Unit) {
                    detectTapGestures(onPress = { peek = true; tryAwaitRelease(); peek = false })
                } else Modifier,
            ),
        style = style.copy(color = color).let { if (align != null) it.copy(textAlign = align) else it },
        maxLines = maxLines,
        overflow = if (maxLines == Int.MAX_VALUE) TextOverflow.Clip else TextOverflow.Ellipsis,
    )
}

/** The big figure: 40/600 with the cents in ink3 (`€1,312` + `.40`). [whole] drops the cents (Round amounts). */
@Composable
fun HeroMoney(
    minor: Long,
    currency: String,
    modifier: Modifier = Modifier,
    size: TextUnit = 40.sp,
    color: Color = KoshaTheme.colors.ink,
    sign: Sign = Sign.Natural,
    whole: Boolean = meta()?.pref("round_hero", "0") == "1",
) {
    val locale = KoshaTheme.locale
    val ink3 = KoshaTheme.colors.ink3
    val text = if (whole) wholeMoney(minor, currency, locale, sign) else Money.format(minor, currency, locale, sign)
    val decimals = Money.decimals(currency)
    val sep = DecimalFormatSymbols.getInstance(locale).monetaryDecimalSeparator
    val at = text.lastIndexOf(sep)
    val styled = buildAnnotatedString {
        if (whole || decimals == 0 || at < 0) append(text)
        else {
            append(text.substring(0, at))
            withStyle(SpanStyle(color = ink3)) { append(text.substring(at, (at + 1 + decimals).coerceAtMost(text.length))) }
            append(text.substring((at + 1 + decimals).coerceAtMost(text.length)))
        }
    }
    AmountText(styled, modifier, style = KoshaTheme.type.hero.copy(fontSize = size), color = color, maxLines = 1)
}

/** Money in whole units, such as `€2,480` under a month or on a chart. */
fun wholeMoney(minor: Long, currency: String, locale: Locale, sign: Sign = Sign.Natural): String {
    val f = NumberFormat.getCurrencyInstance(locale) as DecimalFormat
    runCatching { f.currency = Currency.getInstance(currency.uppercase()) }
    f.minimumFractionDigits = 0
    f.maximumFractionDigits = 0
    val text = f.format(BigDecimal.valueOf(abs(minor), 2).setScale(0, RoundingMode.HALF_UP))
    return when {
        minor < 0 && sign != Sign.None -> Money.MINUS + text
        minor > 0 && sign == Sign.Always -> "+$text"
        else -> text
    }
}

// ---------- shared by the tabs ----------

/**
 * A day in the language's own short form: "10 Nov", "10. Nov.", "11月10日"; with [weekday] "Wed 5 Nov", and with
 * [year] the year too.
 */
fun shortDay(day: java.time.LocalDate, locale: Locale, weekday: Boolean = false, year: Boolean = false): String {
    val skeleton = (if (weekday) "EEE" else "") + "dMMM" + (if (year) "y" else "")
    return day.format(DateTimeFormatter.ofPattern(android.text.format.DateFormat.getBestDateTimePattern(locale, skeleton), locale))
}

/** A share as a percentage in the language: "24%", with [signed] "+24%" or "−8%" (true minus). */
fun percent(value: Double, locale: Locale, digits: Int = 0, signed: Boolean = true): String {
    val f = NumberFormat.getPercentInstance(locale)
    f.minimumFractionDigits = digits
    f.maximumFractionDigits = digits
    val text = f.format(abs(value))
    return when {
        f.format(0.0) == text -> text
        value < 0 -> Money.MINUS + text
        signed -> "+$text"
        else -> text
    }
}

/** A search for Activity to open with, from Overview, Ask, Search or a transaction ("At this payee"). */
data class ActivitySearch(
    val query: String? = null,
    val categoryId: String? = null,
    val accountId: String? = null,
    val tag: String? = null,
    val eventId: String? = null,
    /** "expense", "income" or "transfer". */
    val type: String? = null,
    val minAmount: Long? = null,
    val maxAmount: Long? = null,
    /** Moves the shared month first, "YYYY-MM". */
    val month: String? = null,
)

/** Searches handed to Activity: set one, then open the Activity tab; Activity applies it and clears it. */
object ActivityRequests {
    val next = MutableStateFlow<ActivitySearch?>(null)
}

/** Opens a main tab the way the tab bar does, keeping each tab's state. */
fun NavController.openTab(route: Route) = navigate(route) {
    popUpTo(graph.startDestinationId) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

/** Opens Activity with a search applied. */
fun NavController.openActivity(search: ActivitySearch) {
    ActivityRequests.next.value = search
    openTab(Route.Activity)
}

/**
 * Swiping sideways moves the shared month by one: left for the next, right for the previous. For Overview,
 * Activity and Recurring. Rows that swipe themselves (delete, duplicate) take the gesture first.
 */
fun Modifier.monthSwipe(kosha: Kosha, threshold: Dp = 72.dp): Modifier = pointerInput(kosha) {
    var total = 0f
    detectHorizontalDragGestures(
        onDragStart = { total = 0f },
        onDragEnd = {
            val t = threshold.toPx()
            if (total > t) kosha.month.value = kosha.month.value.minusMonths(1)
            if (total < -t) kosha.month.value = kosha.month.value.plusMonths(1)
        },
    ) { change, amount ->
        total += amount
        change.consume()
    }
}

/**
 * The banners on the main tabs: offline (with the waiting count), and the demo account's, which offers
 * "Create your own account" on the server's sign-up page in a Custom Tab.
 */
@Composable
fun TabBanners(modifier: Modifier = Modifier) {
    val kosha = rememberKosha()
    val online by kosha.online.collectAsStateWithLifecycle()
    val waiting by kosha.outbox.items.collectAsStateWithLifecycle()
    val m = meta()
    val c = KoshaTheme.colors
    val context = LocalContext.current
    Column(modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!online) {
            Banner(
                icon = "cloud-slash",
                title = stringResource(R.string.common_offline_title),
                body = if (waiting.isEmpty()) stringResource(R.string.app_offline_none)
                else pluralStringResource(R.plurals.app_offline_waiting, waiting.size, waiting.size),
            )
        }
        if (m?.demo == true) {
            val shape = RoundedCornerShape(16.dp)
            Column(
                Modifier.fillMaxWidth().clip(shape)
                    .background(c.brand.copy(alpha = 0.09f).compositeOver(c.surface))
                    .border(1.dp, c.brand.copy(alpha = 0.3f).compositeOver(c.line), shape)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.semantics(mergeDescendants = true) { }) {
                    PhIcon("flask", size = 18.dp, tint = c.brand)
                    Column(Modifier.weight(1f)) {
                        KText(stringResource(R.string.shell_demo_title), style = KoshaTheme.type.head)
                        KText(stringResource(R.string.shell_demo_body), style = KoshaTheme.type.small, color = c.ink2)
                    }
                }
                Button(
                    stringResource(R.string.shell_demo_create),
                    {
                        val server = kosha.api.server ?: return@Button
                        CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(context, "$server/login?mode=up".toUri())
                    },
                    Modifier.fillMaxWidth(),
                    size = ButtonSize.Medium,
                    icon = "arrow-square-out",
                )
            }
        }
    }
}
