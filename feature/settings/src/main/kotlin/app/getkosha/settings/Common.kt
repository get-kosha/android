package app.getkosha.settings

import android.content.Context
import android.graphics.BitmapFactory
import androidx.core.net.toUri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.getkosha.data.Kosha
import app.getkosha.data.Loaded
import app.getkosha.design.Caption
import app.getkosha.design.Card
import app.getkosha.design.Hairline
import app.getkosha.design.IconTile
import app.getkosha.design.KText
import app.getkosha.design.KoshaTheme
import app.getkosha.design.PhIcon
import app.getkosha.design.R
import app.getkosha.design.Switch
import app.getkosha.design.Toast
import app.getkosha.design.ToastState
import app.getkosha.model.Meta
import app.getkosha.network.KoshaApi
import app.getkosha.ui.LocalToasts
import app.getkosha.ui.meta
import app.getkosha.ui.rememberKosha
import app.getkosha.ui.write
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.serializer
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// What every settings page shares: reads through the cache, writes with the demo lock, rows, groups and the
// browser hand-off.

/** A read through the cache (cached copy first, then the server's), fetched again after every change. */
@Composable
inline fun <reified T> loaded(key: String, noinline fetch: suspend KoshaApi.() -> T): Loaded<T> {
    val kosha = rememberKosha()
    val version by kosha.version.collectAsStateWithLifecycle()
    val flow = remember(key, version) { kosha.load(key, serializer<T>(), fetch) }
    return flow.collectAsStateWithLifecycle(Loaded<T>(null, refreshing = true)).value
}

/** A preference as text, with Kosha's default when the server hasn't sent it. */
fun Meta?.prefOf(key: String): String = this?.pref(key, PREF_DEFAULTS[key] ?: "") ?: PREF_DEFAULTS[key] ?: ""

/**
 * Runs writes for a page. The demo account is read-only, so every write there shows its message instead. Failures
 * show the server's message (or the offline one) and success refreshes what every screen reads.
 */
class Writer(
    private val kosha: Kosha,
    private val toasts: ToastState,
    private val scope: CoroutineScope,
    val demo: Boolean,
    private val demoMessage: String,
    private val offlineMessage: String,
) {
    fun refused(): Boolean {
        if (demo) toasts.show(Toast(demoMessage))
        return demo
    }

    /** A write; [done] runs with its result when it worked, [always] once it has finished either way. */
    fun <T> run(block: suspend () -> T, always: () -> Unit = {}, done: (T) -> Unit = {}) {
        if (refused()) return always()
        scope.launch {
            val r = runCatching { kosha.write(toasts, offlineMessage, block) }.getOrNull()
            always()
            if (r != null) done(r)
        }
    }

    /** Saves a synced preference, applied at once. */
    fun pref(key: String, value: String?) {
        if (!refused()) kosha.setPreference(key, value)
    }

    fun toast(message: String) = toasts.show(Toast(message))
}

@Composable
fun rememberWriter(): Writer {
    val kosha = rememberKosha()
    val toasts = LocalToasts.current
    val scope = rememberCoroutineScope()
    val demo = meta()?.demo == true
    val demoMessage = stringResource(R.string.common_demo_read_only)
    val offline = stringResource(R.string.common_offline_title)
    return remember(demo, demoMessage, offline) { Writer(kosha, toasts, scope, demo, demoMessage, offline) }
}

/** The server's web pages for what the app leaves to the web (password, passkeys, account deletion…), in a Custom Tab. */
fun openWeb(context: Context, server: String?, path: String) = openUrl(context, (server ?: "https://getkosha.app").trimEnd('/') + path)

fun openUrl(context: Context, url: String) {
    CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(context, url.toUri())
}

/** The server's host, as the footer and the hand-off notes name it: "getkosha.app". */
fun serverHost(server: String?): String = server?.let { it.toUri().host } ?: "getkosha.app"

/** A section caption above a card. */
@Composable
fun GroupCaption(text: String, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(start = 2.dp, end = 2.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Caption(text, Modifier.weight(1f))
        trailing?.invoke()
    }
}

/** A tight card of rows with hairlines between them. Null rows are left out. */
@Composable
fun Rows(vararg rows: (@Composable () -> Unit)?, modifier: Modifier = Modifier) {
    val shown = rows.filterNotNull()
    if (shown.isEmpty()) return
    Card(modifier.fillMaxWidth(), tight = true) {
        shown.forEachIndexed { i, row ->
            if (i > 0) Hairline()
            row()
        }
    }
}

/**
 * A settings row: an optional icon tile, a title (15/500) with a description under it (13, ink3), and whatever
 * sits on the right. At least 56 high (48 compact).
 */
@Composable
fun SRow(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    icon: String? = null,
    onClick: (() -> Unit)? = null,
    descriptionColor: androidx.compose.ui.graphics.Color = KoshaTheme.colors.ink3,
    titleExtra: (@Composable RowScope.() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .heightIn(min = KoshaTheme.dims.settingsRow)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (icon != null) IconTile(icon)
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                KText(title, Modifier.weight(1f, fill = false), style = KoshaTheme.type.head)
                titleExtra?.invoke(this)
            }
            if (description != null) {
                KText(description, Modifier.padding(top = 2.dp), style = KoshaTheme.type.small, color = descriptionColor)
            }
        }
        trailing?.invoke(this)
    }
}

/** A row that opens another page: a caret on the right. */
@Composable
fun NavRow(title: String, description: String? = null, icon: String? = null, value: String? = null, external: Boolean = false, badge: Int = 0, titleExtra: (@Composable RowScope.() -> Unit)? = null, onClick: () -> Unit) {
    SRow(title, description = description, icon = icon, onClick = onClick, titleExtra = titleExtra) {
        if (value != null) KText(value, style = KoshaTheme.type.small, color = KoshaTheme.colors.ink2, maxLines = 1)
        if (badge > 0) Badge(badge)
        PhIcon(if (external) "arrow-square-out" else "caret-right", size = 16.dp, tint = KoshaTheme.colors.ink3)
    }
}

/** A row whose switch is the whole row: one tap anywhere toggles it, and TalkBack reads it as a switch. */
@Composable
fun SwitchRow(title: String, description: String?, checked: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean = true, extra: (@Composable () -> Unit)? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onChange)
            .alpha(if (enabled) 1f else 0.4f)
            .heightIn(min = KoshaTheme.dims.settingsRow)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            KText(title, style = KoshaTheme.type.head)
            if (description != null) KText(description, Modifier.padding(top = 2.dp), style = KoshaTheme.type.small, color = KoshaTheme.colors.ink3)
            extra?.invoke()
        }
        // The row is the toggle; the switch only draws it.
        Box(Modifier.clearAndSetSemantics { }) { Switch(checked, {}, title) }
    }
}

/** The warning-coloured count of open steps. */
@Composable
fun Badge(count: Int) {
    val c = KoshaTheme.colors
    Box(
        Modifier.defaultMinSize(minWidth = 20.dp).heightIn(min = 20.dp).clip(CircleShape).background(c.warn).padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        KText(count.toString(), style = KoshaTheme.type.xs.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold), color = if (c.dark) c.onInk else androidx.compose.ui.graphics.Color.White)
    }
}

/** A quiet note on soft: an icon and a line of text. */
@Composable
fun Note(icon: String, text: String, modifier: Modifier = Modifier) {
    val c = KoshaTheme.colors
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.soft).padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PhIcon(icon, size = 16.dp, tint = c.ink2)
        KText(text, style = KoshaTheme.type.small, color = c.ink2)
    }
}

/** A line of small text under a card. */
@Composable
fun Hint(text: String, modifier: Modifier = Modifier) {
    KText(text, modifier.padding(horizontal = 2.dp), style = KoshaTheme.type.xs, color = KoshaTheme.colors.ink3)
}

/** A label above a field. */
@Composable
fun FieldLabel(text: String) {
    KText(text, Modifier.padding(bottom = 6.dp), style = KoshaTheme.type.small, color = KoshaTheme.colors.ink3)
}

/**
 * The profile picture, or the name's initial on the accent. The picture comes from GET /api/app/avatar with the
 * session's token, read again whenever meta's image changes.
 * ponytail: held in memory only, so offline it shows the initial until the next launch online.
 */
@Composable
fun Avatar(size: Dp, modifier: Modifier = Modifier) {
    val kosha = rememberKosha()
    val m = meta()
    val c = KoshaTheme.colors
    val image = m?.user?.image
    val photo by produceState<ImageBitmap?>(null, image) {
        value = if (image == null) null else runCatching {
            val bytes = kosha.api.bytes("/api/app/avatar")
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }.getOrNull()
    }
    Box(modifier.size(size).clip(CircleShape).background(c.brand), contentAlignment = Alignment.Center) {
        val p = photo
        if (p != null) {
            Image(p, null, Modifier.size(size), contentScale = ContentScale.Crop)
        } else {
            KText(
                (m?.user?.name ?: "").trim().take(1).uppercase().ifEmpty { "K" },
                style = KoshaTheme.type.body.copy(fontSize = (size.value * 0.38f).sp, fontWeight = FontWeight.SemiBold),
                color = c.onBrand,
            )
        }
    }
}

/** "3 Aug", or "3 Aug 2025" in another year, in the app's language. */
fun shortDate(day: LocalDate, locale: Locale, today: LocalDate): String {
    val skeleton = if (day.year == today.year) "dMMM" else "dMMMyyyy"
    return day.format(DateTimeFormatter.ofPattern(android.text.format.DateFormat.getBestDateTimePattern(locale, skeleton), locale))
}

fun dayOf(millis: Long, tz: String): LocalDate =
    Instant.ofEpochMilli(millis).atZone(runCatching { ZoneId.of(tz) }.getOrDefault(ZoneId.of("UTC"))).toLocalDate()

/** Marks a control as unusable for this account without taking it off the screen. */
fun Modifier.dimmed(off: Boolean) = if (off) alpha(0.4f) else this

/** For content descriptions built from parts. */
fun Modifier.described(text: String) = semantics { contentDescription = text }
