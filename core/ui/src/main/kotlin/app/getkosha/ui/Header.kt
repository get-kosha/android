package app.getkosha.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.getkosha.design.Button
import app.getkosha.design.ButtonStyle
import app.getkosha.design.Hairline
import app.getkosha.design.IconButton
import app.getkosha.design.KText
import app.getkosha.design.KoshaTheme
import app.getkosha.design.PhIcon
import app.getkosha.design.R
import app.getkosha.design.Segmented
import app.getkosha.design.Sheet
import app.getkosha.design.Switch
import kotlinx.coroutines.launch
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as DateStyle

/**
 * The header of the main tabs. Overview, Activity and Recurring show the selected month (tap for the month
 * sheet); Accounts shows its title. Then the display currency pill (tap to step through the user's currencies),
 * the Ask sparkle when Ask Kosha is on, an optional action, and the avatar, which opens the account menu.
 */
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
    Row(
        modifier.fillMaxWidth().statusBarsPadding().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (showMonth) {
            val label = month.month.getDisplayName(DateStyle.FULL_STANDALONE, locale) +
                if (month.year != YearMonth.now().year) " ${month.year}" else ""
            Row(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(role = Role.Button) { sheet = "month" }
                    .semantics { contentDescription = month.format(DateTimeFormatter.ofPattern("LLLL yyyy", locale)) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                KText(label, Modifier.semantics { heading() }, style = KoshaTheme.type.page, maxLines = 1)
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
                Box(
                    Modifier
                        .heightIn(min = 48.dp)
                        .clickable(role = Role.Button) {
                            kosha.currency.value = list[(list.indexOf(shown) + 1) % list.size].takeIf { it != m.primary }
                        },
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
        "menu" -> AccountMenu(onDismiss = { sheet = null })
    }
}

/** The avatar: the account's initial on the selection ground. 48dp to tap. */
@Composable
fun Avatar(name: String, onClick: () -> Unit, size: Int = 36) {
    val label = stringResource(R.string.shell_account_menu)
    Box(Modifier.size(48.dp).clickable(role = Role.Button, onClickLabel = label, onClick = onClick).semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
        Box(Modifier.size(size.dp).clip(CircleShape).background(KoshaTheme.colors.sel), contentAlignment = Alignment.Center) {
            KText(name.trim().take(1).uppercase().ifEmpty { "K" }, style = KoshaTheme.type.small.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold))
        }
    }
}

/** A year of months to jump to. "Swipe Overview, Activity or Recurring sideways to move one month." */
@Composable
fun MonthSheet(month: YearMonth, onPick: (YearMonth) -> Unit, onDismiss: () -> Unit, under: (@Composable (YearMonth) -> Unit)? = null) {
    var year by remember { mutableStateOf(month.year) }
    val locale = KoshaTheme.locale
    val c = KoshaTheme.colors
    Sheet(year.toString(), onDismiss) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            IconButton("caret-left", stringResource(R.string.app_previous_year), { year-- })
            IconButton("caret-right", stringResource(R.string.app_next_year), { year++ })
        }
        (1..12).chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { n ->
                    val ym = YearMonth.of(year, n)
                    val on = ym == month
                    Column(
                        Modifier
                            .weight(1f)
                            .heightIn(min = 64.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (on) c.selection else c.soft)
                            .clickable(role = Role.RadioButton) { onPick(ym) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        KText(
                            ym.month.getDisplayName(DateStyle.SHORT_STANDALONE, locale),
                            style = KoshaTheme.type.body.let { if (on) it.copy(fontWeight = FontWeight.Medium) else it },
                            color = if (on) c.onSelection else c.ink,
                        )
                        under?.invoke(ym)
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        KText(stringResource(R.string.app_month_swipe), style = KoshaTheme.type.small, color = c.ink3)
    }
}

/**
 * The account menu from the avatar: who is signed in, theme, Hide amounts, Settings, Sign-in & security,
 * Categories, AI, Language, and Sign out ("Signing out clears what this device saved.").
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
    Sheet(m?.user?.name ?: "", onDismiss, subtitle = m?.user?.email) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Segmented(
                listOf(
                    "light" to stringResource(R.string.shell_theme_light),
                    "dark" to stringResource(R.string.shell_theme_dark),
                    "system" to stringResource(R.string.shell_theme_system),
                ),
                m?.pref("theme", "system") ?: "system",
                { kosha.setPreference("theme", it) },
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                PhIcon("eye-slash", size = 20.dp)
                Spacer(Modifier.width(12.dp))
                val hideLabel = stringResource(R.string.shell_hide_amounts)
                KText(hideLabel, Modifier.weight(1f))
                Switch(m?.pref("hide_amounts", "0") == "1", { kosha.setPreference("hide_amounts", if (it) "1" else "0") }, hideLabel)
            }
            Hairline()
            MenuRow("gear", stringResource(R.string.settings_title)) { go("") }
            MenuRow("shield-check", stringResource(R.string.settings_section_security)) { go("security") }
            MenuRow("tag", stringResource(R.string.settings_section_categories)) { go("categories") }
            if (m?.ai?.any == true || m?.pref("ai_enabled", "0") == "1") MenuRow("sparkle", stringResource(R.string.settings_section_ai)) { go("ai") }
            MenuRow("translate", stringResource(R.string.common_language)) { go("general") }
            Hairline()
            if (waiting.isNotEmpty()) {
                KText(pluralStringResource(R.plurals.app_signout_waiting, waiting.size, waiting.size), style = KoshaTheme.type.small, color = c.warn)
            }
            Button(
                stringResource(R.string.shell_sign_out),
                { scope.launch { onDismiss(); kosha.signOut() } },
                Modifier.fillMaxWidth(),
                style = ButtonStyle.Secondary,
                icon = "sign-out",
            )
            KText(stringResource(R.string.app_signout_clears), style = KoshaTheme.type.small, color = c.ink3)
        }
    }
}

@Composable
private fun MenuRow(icon: String, label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PhIcon(icon, size = 20.dp)
        KText(label, Modifier.weight(1f))
        PhIcon("caret-right", size = 16.dp, tint = KoshaTheme.colors.ink3)
    }
}
