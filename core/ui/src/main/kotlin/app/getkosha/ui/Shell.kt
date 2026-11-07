package app.getkosha.ui

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.getkosha.data.Kosha
import app.getkosha.design.Accent
import app.getkosha.design.Density
import app.getkosha.design.Figures
import app.getkosha.design.IconButton
import app.getkosha.design.KText
import app.getkosha.design.KoshaTheme
import app.getkosha.design.PhIcon
import app.getkosha.design.R
import app.getkosha.design.ToastState
import app.getkosha.model.Meta
import app.getkosha.model.Money
import app.getkosha.model.Sign
import androidx.compose.foundation.isSystemInDarkTheme

/** The app's toasts, shown at the top over every screen. */
val LocalToasts = staticCompositionLocalOf { ToastState() }

/** The session and shared state. */
@Composable
fun rememberKosha(): Kosha = remember { Kosha.get() }

/** The signed-in account's meta, or null before the first load. */
@Composable
fun meta(): Meta? = rememberKosha().meta.collectAsStateWithLifecycle().value

/** Kosha's theme with the account's appearance preferences: theme, accent, account colours, density and figures. */
@Composable
fun KoshaThemeFromPrefs(content: @Composable () -> Unit) {
    val m = meta()
    val theme = m?.pref("theme", "system") ?: "system"
    KoshaTheme(
        dark = if (theme == "system") isSystemInDarkTheme() else theme == "dark",
        accent = Accent.entries.firstOrNull { it.name.equals(m?.pref("accent", "teal"), ignoreCase = true) } ?: Accent.Teal,
        neutral = m?.pref("account_colours", "1") == "0",
        density = if (m?.pref("density", "comfortable") == "compact") Density.Compact else Density.Comfortable,
        figures = if (m?.pref("figures", "mono") == "proportional") Figures.Proportional else Figures.Mono,
        language = rememberKosha().language.language,
        content = content,
    )
}

/** Whether Settings › Appearance › Hide amounts is on. */
@Composable
fun hideAmounts(): Boolean = meta()?.pref("hide_amounts", "0") == "1"

/**
 * A money figure in the user's language, with a true minus. With Hide amounts on it is blurred (masked before
 * Android 12) and a touch and hold peeks at it. TalkBack reads the amount, or "hidden".
 */
@Composable
fun MoneyText(
    minor: Long,
    currency: String,
    modifier: Modifier = Modifier,
    sign: Sign = Sign.Natural,
    style: TextStyle = KoshaTheme.type.num,
    color: Color = KoshaTheme.colors.ink,
    align: TextAlign? = null,
) {
    val locale = KoshaTheme.locale
    val text = Money.format(minor, currency, locale, sign)
    val hidden = hideAmounts()
    val spoken = if (hidden) stringResource(R.string.shell_hide_amounts) else spokenMoney(minor, currency, locale, sign)
    var peek by remember { mutableStateOf(false) }
    val masked = hidden && !peek
    KText(
        if (masked && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) "••••" else text,
        modifier
            .semantics { contentDescription = spoken }
            .then(if (masked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Modifier.blur(7.dp) else Modifier)
            .then(
                if (hidden) Modifier.pointerInput(Unit) {
                    detectTapGestures(onPress = { peek = true; tryAwaitRelease(); peek = false })
                } else Modifier,
            ),
        style = style,
        color = color,
        align = align,
        maxLines = 1,
    )
}

/**
 * How TalkBack reads an amount: the currency by name ("minus 29.57 euros") rather than symbols, from ICU's
 * plural currency format in the app's language.
 */
fun spokenMoney(minor: Long, currency: String, locale: java.util.Locale, sign: Sign = Sign.Natural): String {
    val f = android.icu.text.NumberFormat.getInstance(locale, android.icu.text.NumberFormat.PLURALCURRENCYSTYLE)
    runCatching { f.currency = android.icu.util.Currency.getInstance(currency.uppercase()) }
    val digits = Money.decimals(currency)
    f.minimumFractionDigits = digits
    f.maximumFractionDigits = digits
    val value = java.math.BigDecimal.valueOf(minor, 2)
    return f.format(if (sign == Sign.None) value.abs() else value)
}

/**
 * A page below the tabs: back, a title (22/600) with an optional line under it, and actions on the right.
 * Content scrolls under a 16dp gutter.
 */
@Composable
fun Page(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    backLabel: String = stringResource(R.string.common_back),
    actions: @Composable RowScope.() -> Unit = {},
    scroll: Boolean = true,
    footer: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = KoshaTheme.colors
    Column(modifier.fillMaxSize().background(c.bg).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) IconButton("caret-left", backLabel, onBack) else Spacer(Modifier.padding(start = 8.dp))
            Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
                KText(title, Modifier.semantics { heading() }, style = KoshaTheme.type.title, maxLines = 2)
                if (subtitle != null) KText(subtitle, style = KoshaTheme.type.xs, color = c.ink3)
            }
            actions()
        }
        Banners()
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .then(if (scroll) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                .padding(horizontal = 16.dp)
                .padding(top = 8.dp, bottom = if (footer == null) 32.dp else 8.dp)
                .then(if (footer == null) Modifier.navigationBarsPadding() else Modifier),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
        if (footer != null) {
            Box(Modifier.fillMaxWidth().background(c.glass).navigationBarsPadding().padding(16.dp)) { footer() }
        }
    }
}

/** The offline and demo banners, shown under the header on main screens. */
@Composable
fun Banners(modifier: Modifier = Modifier) {
    val kosha = rememberKosha()
    val online by kosha.online.collectAsStateWithLifecycle()
    val waiting by kosha.outbox.items.collectAsStateWithLifecycle()
    val m = meta()
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
            Banner(icon = "flask", title = stringResource(R.string.shell_demo_title), body = stringResource(R.string.shell_demo_body))
        }
    }
}

@Composable
fun Banner(icon: String, title: String, body: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    val c = KoshaTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(c.soft)
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .semantics(mergeDescendants = true) { },
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        PhIcon(icon, size = 18.dp, tint = c.ink2)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            KText(title, style = KoshaTheme.type.small.copy(fontWeight = KoshaTheme.type.head.fontWeight))
            KText(body, style = KoshaTheme.type.small, color = c.ink2)
            action?.invoke()
        }
    }
}

/** "Showing what this phone saved…", for a screen whose saved copy is all it has while the server answers. */
@Composable
fun LoadingNote(modifier: Modifier = Modifier) {
    KText(stringResource(R.string.app_loading), modifier.semantics { contentDescription = "" }, style = KoshaTheme.type.small, color = KoshaTheme.colors.ink3)
}

/** Grey blocks where content will be, while there is nothing saved to show. */
@Composable
fun Skeleton(modifier: Modifier = Modifier, height: Dp = 16.dp) {
    Box(modifier.heightIn(min = height).clip(RoundedCornerShape(8.dp)).background(KoshaTheme.colors.soft))
}

/** The padding main screens leave for the tab bar. */
val TabBarSpace = PaddingValues(bottom = 112.dp)

/** Shows the server's message, or the demo's read-only message, after a write fails. Returns true when it worked. */
suspend fun <T> Kosha.write(toasts: ToastState, messageForOffline: String, block: suspend () -> T): T? = try {
    block().also { changed() }
} catch (e: java.io.IOException) {
    toasts.show(app.getkosha.design.Toast(messageForOffline))
    null
} catch (e: app.getkosha.network.ApiException) {
    toasts.show(app.getkosha.design.Toast(e.message ?: ""))
    null
}

/** Whether this account can change anything: false for the demo. */
@Composable
fun canWrite(): Boolean = meta()?.demo != true

@Composable
internal fun Centered(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}
