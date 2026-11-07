package app.getkosha.settings

import android.app.LocaleManager
import android.os.Build
import android.os.LocaleList
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.getkosha.design.Accent
import app.getkosha.design.AccountDot
import app.getkosha.design.Card
import app.getkosha.design.KText
import app.getkosha.design.KoshaColors
import app.getkosha.design.KoshaTheme
import app.getkosha.design.ListOption
import app.getkosha.design.ListPicker
import app.getkosha.design.R
import app.getkosha.design.Segmented
import app.getkosha.design.Sheet
import app.getkosha.design.Switch
import app.getkosha.model.Money
import app.getkosha.model.Sign
import app.getkosha.ui.LocalNav
import app.getkosha.ui.MoneyText
import app.getkosha.ui.Page
import app.getkosha.ui.meta
import app.getkosha.ui.rememberKosha
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.time.ZoneId
import java.util.Locale

// General, Appearance and Recurring: preferences that save as they change.

/** General: language, time zone, round amounts, and what Add starts with. */
@Composable
fun GeneralPage() {
    val nav = LocalNav.current
    val kosha = rememberKosha()
    val m = meta()
    val w = rememberWriter()
    val context = LocalContext.current
    val locale = KoshaTheme.locale
    var sheet by remember { mutableStateOf<String?>(null) }
    val language = m.prefOf("language").ifEmpty { Locale.getDefault().language }.takeIf { it in LANGUAGES } ?: "en"
    val wallets = m?.wallets?.filterNot { it.isArchived } ?: emptyList()
    val walletPref = m.prefOf("default_wallet")
    val lastUsed = stringResource(R.string.settings_general_last_used)

    Page(stringResource(R.string.settings_section_general), onBack = { nav.popBackStack() }) {
        GroupCaption(stringResource(R.string.settings_general_region))
        Rows(
            { NavRow(stringResource(R.string.settings_row_language), stringResource(R.string.settings_general_language_desc), value = languageName(language)) { sheet = "language" } },
            { NavRow(stringResource(R.string.settings_row_time_zone), stringResource(R.string.settings_general_time_zone_desc), value = zoneCity(m?.tz ?: "UTC")) { sheet = "zone" } },
        )
        GroupCaption(stringResource(R.string.settings_general_month))
        val full = Money.format(143600, m?.primary ?: "eur", locale)
        Rows({
            SwitchRow(
                stringResource(R.string.settings_row_round_amounts),
                stringResource(R.string.settings_general_round_desc, full.replace(Regex("[.,]\\d{2}(?=\\D*$)"), ""), full),
                m.prefOf("round_hero") == "1",
                { w.pref("round_hero", if (it) "1" else "0") },
            )
        })
        GroupCaption(stringResource(R.string.settings_general_adding))
        Rows(
            {
                NavRow(
                    stringResource(R.string.settings_row_default_account),
                    stringResource(R.string.settings_general_default_account_desc),
                    value = wallets.firstOrNull { it.id == walletPref }?.name ?: lastUsed,
                ) { sheet = "wallet" }
            },
            {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    KText(stringResource(R.string.settings_row_add_kind), style = KoshaTheme.type.head)
                    KText(stringResource(R.string.settings_general_add_kind_desc), style = KoshaTheme.type.small, color = KoshaTheme.colors.ink3)
                    Segmented(
                        listOf("expense" to stringResource(R.string.settings_general_expense), "income" to stringResource(R.string.settings_general_income)),
                        m.prefOf("add_kind"),
                        { w.pref("add_kind", it) },
                        Modifier.fillMaxWidth(),
                        small = true,
                    )
                }
            },
            {
                SwitchRow(
                    stringResource(R.string.settings_row_suggest_category),
                    stringResource(R.string.settings_general_suggest_desc),
                    m.prefOf("suggest_category") == "1",
                    { w.pref("suggest_category", if (it) "1" else "0") },
                )
            },
        )
    }

    when (sheet) {
        "language" -> Sheet(stringResource(R.string.settings_row_language), { sheet = null }) {
            ListPicker(LANGUAGES.map { ListOption(it, languageName(it)) }, language, { o ->
                sheet = null
                w.pref("language", o.value)
                // The app's strings follow the app locale. Android 13 and later switch it per app; older phones keep
                // the phone's language for words, while money and dates follow the saved setting.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags(o.value)
                }
            })
        }
        "zone" -> Sheet(stringResource(R.string.settings_row_time_zone), { sheet = null }) {
            val phone = remember { ZoneId.systemDefault().id }
            val options = remember {
                (listOf(phone) + pickableZones().filter { it != phone }).map { ListOption(it, zoneCity(it), "$it · ${zoneOffset(it)}") }
            }
            ListPicker(options, m?.tz, { o ->
                sheet = null
                w.run({ kosha.api.send("PATCH", "/api/v1/settings", buildJsonObject { put("timeZone", JsonPrimitive(o.value)) }) })
            })
        }
        "wallet" -> Sheet(stringResource(R.string.settings_row_default_account), { sheet = null }) {
            ListPicker(
                listOf(ListOption("last", lastUsed)) + wallets.map { ListOption(it.id, it.name, it.currency.uppercase()) },
                walletPref,
                { o -> sheet = null; w.pref("default_wallet", o.value) },
            )
        }
    }
}

/** Appearance: a live preview, then theme, accent, density, account colours, number font, hide amounts, animations. */
@Composable
fun AppearancePage() {
    val nav = LocalNav.current
    val m = meta()
    val c = KoshaTheme.colors
    val w = rememberWriter()
    val primary = m?.primary ?: "eur"

    Page(stringResource(R.string.settings_section_appearance), onBack = { nav.popBackStack() }) {
        Card(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    KText(stringResource(R.string.settings_appearance_preview), Modifier.weight(1f), style = KoshaTheme.type.small, color = c.ink3)
                    KText(stringResource(R.string.settings_appearance_preview_hint), style = KoshaTheme.type.xs, color = c.ink3)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    KText(stringResource(R.string.settings_appearance_net_worth), Modifier.weight(1f))
                    MoneyText(2829692, primary)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AccountDot(c.account(0))
                    KText(stringResource(R.string.settings_appearance_salary), Modifier.weight(1f))
                    MoneyText(324000, primary, sign = Sign.Always, color = c.pos)
                    Box(Modifier.semantics(mergeDescendants = true) { }) { Switch(true, {}, stringResource(R.string.settings_appearance_sample)) }
                }
            }
        }

        GroupCaption(stringResource(R.string.settings_appearance_look))
        Card(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Choice(
                    stringResource(R.string.settings_row_theme),
                    stringResource(R.string.settings_appearance_theme_desc),
                    listOf(
                        "light" to stringResource(R.string.settings_appearance_light),
                        "dark" to stringResource(R.string.settings_appearance_dark),
                        "system" to stringResource(R.string.settings_appearance_system),
                    ),
                    m.prefOf("theme"),
                ) { w.pref("theme", it) }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    KText(stringResource(R.string.settings_row_accent), style = KoshaTheme.type.head)
                    AccentPicker(m.prefOf("accent")) { w.pref("accent", it) }
                    KText(stringResource(R.string.settings_appearance_accent_desc), style = KoshaTheme.type.xs, color = c.ink3)
                }
                Choice(
                    stringResource(R.string.settings_row_density),
                    stringResource(R.string.settings_appearance_density_desc),
                    listOf("comfortable" to stringResource(R.string.settings_appearance_comfortable), "compact" to stringResource(R.string.settings_appearance_compact)),
                    m.prefOf("density"),
                ) { w.pref("density", it) }
                InlineSwitch(stringResource(R.string.app_set_account_colours), stringResource(R.string.app_set_account_colours_desc), m.prefOf("account_colours") == "1") {
                    w.pref("account_colours", if (it) "1" else "0")
                }
            }
        }

        GroupCaption(stringResource(R.string.settings_appearance_behaviour))
        Card(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Choice(
                    stringResource(R.string.settings_row_number_font),
                    stringResource(R.string.settings_appearance_figures_desc),
                    listOf("mono" to stringResource(R.string.settings_appearance_mono), "proportional" to stringResource(R.string.settings_appearance_proportional)),
                    m.prefOf("figures"),
                ) { w.pref("figures", it) }
                InlineSwitch(stringResource(R.string.settings_row_hide_amounts), stringResource(R.string.app_set_hide_desc), m.prefOf("hide_amounts") == "1") {
                    w.pref("hide_amounts", if (it) "1" else "0")
                }
                Choice(
                    stringResource(R.string.settings_row_animations),
                    stringResource(R.string.settings_appearance_motion_desc),
                    listOf(
                        "on" to stringResource(R.string.settings_appearance_on),
                        "reduced" to stringResource(R.string.settings_appearance_reduced),
                        "off" to stringResource(R.string.settings_appearance_off),
                    ),
                    m.prefOf("motion"),
                ) { w.pref("motion", it) }
            }
        }
    }
}

/** A title, a segmented choice and a line under it, inside a card. */
@Composable
private fun Choice(title: String, description: String?, options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        KText(title, style = KoshaTheme.type.head)
        Segmented(options, selected, onSelect, Modifier.fillMaxWidth(), small = true)
        if (description != null) KText(description, style = KoshaTheme.type.xs, color = KoshaTheme.colors.ink3)
    }
}

/** A switch with its title and description, inside a card. */
@Composable
private fun InlineSwitch(title: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            KText(title, style = KoshaTheme.type.head)
            KText(description, style = KoshaTheme.type.xs, color = KoshaTheme.colors.ink3)
        }
        Switch(checked, onChange, title)
    }
}

/** The five accents as swatches in their own colour for the current theme, the picked one ringed. */
@Composable
private fun AccentPicker(selected: String, onPick: (String) -> Unit) {
    val c = KoshaTheme.colors
    val names = mapOf(
        Accent.Teal to stringResource(R.string.settings_appearance_teal),
        Accent.Blue to stringResource(R.string.settings_appearance_blue),
        Accent.Amber to stringResource(R.string.settings_appearance_amber),
        Accent.Rose to stringResource(R.string.settings_appearance_rose),
        Accent.Graphite to stringResource(R.string.settings_appearance_graphite),
    )
    Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Accent.entries.forEach { accent ->
            val value = accent.name.lowercase()
            val on = value == selected
            val color = KoshaColors.of(c.dark, accent).brand
            Box(
                Modifier
                    .size(48.dp)
                    .selectable(on, role = Role.RadioButton) { onPick(value) }
                    .semantics { contentDescription = names.getValue(accent) },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier.size(30.dp).then(if (on) Modifier.border(1.5.dp, color, CircleShape) else Modifier),
                    contentAlignment = Alignment.Center,
                ) { Box(Modifier.size(24.dp).clip(CircleShape).background(color)) }
            }
        }
    }
}

/** Recurring: record payments automatically by default, and how far ahead Coming up looks. */
@Composable
fun RecurringDefaultsPage() {
    val nav = LocalNav.current
    val m = meta()
    val w = rememberWriter()
    Page(stringResource(R.string.settings_section_recurring), onBack = { nav.popBackStack() }) {
        GroupCaption(stringResource(R.string.settings_rec_group))
        Rows({
            SwitchRow(
                stringResource(R.string.settings_rec_auto_title),
                stringResource(R.string.settings_rec_auto_description),
                m.prefOf("default_auto_pay") == "1",
                { w.pref("default_auto_pay", if (it) "1" else "0") },
            )
        })
        Card(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    KText(stringResource(R.string.settings_rec_coming_up_title), style = KoshaTheme.type.head)
                    KText(stringResource(R.string.settings_rec_coming_up_description), style = KoshaTheme.type.small, color = KoshaTheme.colors.ink3)
                }
                Segmented(
                    listOf("month" to stringResource(R.string.settings_rec_until_month_end), "30d" to stringResource(R.string.settings_rec_next_30_days)),
                    m.prefOf("upcoming_horizon"),
                    { w.pref("upcoming_horizon", it) },
                    Modifier.fillMaxWidth(),
                    small = true,
                )
            }
        }
    }
}
