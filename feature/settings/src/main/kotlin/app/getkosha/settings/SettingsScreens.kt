package app.getkosha.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import app.getkosha.design.Card
import app.getkosha.design.Field
import app.getkosha.design.KText
import app.getkosha.design.KoshaTheme
import app.getkosha.design.PhIcon
import app.getkosha.design.Pill
import app.getkosha.design.R
import app.getkosha.ui.LocalNav
import app.getkosha.ui.Page
import app.getkosha.ui.Route
import app.getkosha.ui.meta
import app.getkosha.ui.rememberKosha

/** The settings screens, registered in the app's NavHost. Route.Settings.page picks the page. */
fun NavGraphBuilder.settingsScreens() {
    composable<Route.Settings> { entry ->
        val r = entry.toRoute<Route.Settings>()
        when (r.page) {
            "profile" -> ProfilePage()
            "security" -> SecurityPage()
            "general" -> GeneralPage()
            "appearance" -> AppearancePage()
            "currencies" -> CurrenciesPage()
            "categories" -> CategoriesPage()
            "category" -> CategoryEditPage(r.id ?: "new")
            "events" -> EventsPage()
            "rules" -> RulesPage()
            "recurring" -> RecurringDefaultsPage()
            "ai" -> AiSettingsPage()
            "ai-provider" -> AiProviderPage(r.id ?: "anthropic")
            "ai-privacy" -> AiPrivacyPage()
            "ai-log" -> AiLogPage()
            "data" -> DataExportPage()
            "api" -> ApiMcpPage()
            "delete" -> DeleteResetPage()
            else -> SettingsList()
        }
    }
}

/** Opens another settings page. */
@Composable
fun go(): (String, String?) -> Unit {
    val nav = LocalNav.current
    return { page, id -> nav.navigate(Route.Settings(page, id)) }
}

/** One searchable row of a section (registry.tsx): its title, description and extra search words. */
private class Searchable(@StringRes val title: Int, @StringRes val description: Int, @StringRes val keywords: Int)

private class Section(val page: String, @StringRes val title: Int, val icon: String, val rows: List<Searchable>)

private fun s(t: Int, d: Int, k: Int) = Searchable(t, d, k)

// The sections in the list's order, with the rows search finds (registry.tsx GROUPS; page width is web-only).
private val GROUPS: List<Pair<Int, List<Section>>> = listOf(
    R.string.settings_group_you to listOf(
        Section("profile", R.string.settings_section_profile, "user", listOf(
            s(R.string.settings_row_photo, R.string.settings_row_photo_desc, R.string.settings_row_photo_kw),
            s(R.string.settings_row_name, R.string.settings_row_name_desc, R.string.settings_row_name_kw),
            s(R.string.settings_row_email, R.string.settings_row_email_desc, R.string.settings_row_email_kw),
        )),
        Section("security", R.string.settings_section_security, "shield-check", listOf(
            s(R.string.settings_row_password, R.string.settings_row_password_desc, R.string.settings_row_password_kw),
            s(R.string.settings_row_passkeys, R.string.settings_row_passkeys_desc, R.string.settings_row_passkeys_kw),
            s(R.string.settings_row_google, R.string.settings_row_google_desc, R.string.settings_row_google_kw),
            s(R.string.settings_row_two_factor, R.string.settings_row_two_factor_desc, R.string.settings_row_two_factor_kw),
            s(R.string.settings_row_devices, R.string.settings_row_devices_desc, R.string.settings_row_devices_kw),
            s(R.string.app_set_unlock, R.string.app_set_unlock_desc, R.string.app_set_unlock_kw),
        )),
    ),
    R.string.settings_group_preferences to listOf(
        Section("general", R.string.settings_section_general, "sliders-horizontal", listOf(
            s(R.string.settings_row_language, R.string.settings_row_language_desc, R.string.settings_row_language_kw),
            s(R.string.settings_row_time_zone, R.string.settings_row_time_zone_desc, R.string.settings_row_time_zone_kw),
            s(R.string.settings_row_round_amounts, R.string.settings_row_round_amounts_desc, R.string.settings_row_round_amounts_kw),
            s(R.string.settings_row_default_account, R.string.settings_row_default_account_desc, R.string.settings_row_default_account_kw),
            s(R.string.settings_row_add_kind, R.string.settings_row_add_kind_desc, R.string.settings_row_add_kind_kw),
            s(R.string.settings_row_suggest_category, R.string.settings_row_suggest_category_desc, R.string.settings_row_suggest_category_kw),
        )),
        Section("appearance", R.string.settings_section_appearance, "paint-brush", listOf(
            s(R.string.settings_row_theme, R.string.settings_row_theme_desc, R.string.settings_row_theme_kw),
            s(R.string.settings_row_accent, R.string.settings_row_accent_desc, R.string.settings_row_accent_kw),
            s(R.string.settings_row_density, R.string.settings_row_density_desc, R.string.settings_row_density_kw),
            s(R.string.settings_row_number_font, R.string.settings_row_number_font_desc, R.string.settings_row_number_font_kw),
            s(R.string.settings_row_hide_amounts, R.string.settings_row_hide_amounts_desc, R.string.settings_row_hide_amounts_kw),
            s(R.string.settings_row_animations, R.string.settings_row_animations_desc, R.string.settings_row_animations_kw),
        )),
    ),
    R.string.settings_group_money to listOf(
        Section("currencies", R.string.settings_section_currencies, "currency-circle-dollar", listOf(
            s(R.string.settings_row_currencies, R.string.settings_row_currencies_desc, R.string.settings_row_currencies_kw),
            s(R.string.settings_row_rates_source, R.string.settings_row_rates_source_desc, R.string.settings_row_rates_source_kw),
        )),
        Section("categories", R.string.settings_section_categories, "tag", listOf(
            s(R.string.settings_row_categories, R.string.settings_row_categories_desc, R.string.settings_row_categories_kw),
        )),
        Section("events", R.string.settings_section_events, "calendar-star", listOf(
            s(R.string.settings_row_events, R.string.settings_row_events_desc, R.string.settings_row_events_kw),
        )),
        Section("rules", R.string.settings_section_rules, "funnel", listOf(
            s(R.string.settings_row_learn_rules, R.string.settings_row_learn_rules_desc, R.string.settings_row_learn_rules_kw),
            s(R.string.settings_row_rules, R.string.settings_row_rules_desc, R.string.settings_row_rules_kw),
        )),
        Section("recurring", R.string.settings_section_recurring, "arrows-clockwise", listOf(
            s(R.string.settings_row_auto_record, R.string.settings_row_auto_record_desc, R.string.settings_row_auto_record_kw),
            s(R.string.settings_row_coming_up, R.string.settings_row_coming_up_desc, R.string.settings_row_coming_up_kw),
        )),
    ),
    R.string.settings_group_intelligence to listOf(
        Section("ai", R.string.settings_section_ai, "sparkle", listOf(
            s(R.string.settings_row_ai_enabled, R.string.settings_row_ai_enabled_desc, R.string.settings_row_ai_enabled_kw),
            s(R.string.settings_row_ai_providers, R.string.settings_row_ai_providers_desc, R.string.settings_row_ai_providers_kw),
            s(R.string.settings_row_ai_features, R.string.settings_row_ai_features_desc, R.string.settings_row_ai_features_kw),
            s(R.string.settings_row_ai_categorise, R.string.settings_row_ai_categorise_desc, R.string.settings_row_ai_categorise_kw),
            s(R.string.settings_row_ai_quick_add, R.string.settings_row_ai_quick_add_desc, R.string.settings_row_ai_quick_add_kw),
            s(R.string.settings_row_ai_receipts, R.string.settings_row_ai_receipts_desc, R.string.settings_row_ai_receipts_kw),
            s(R.string.settings_row_ai_recap, R.string.settings_row_ai_recap_desc, R.string.settings_row_ai_recap_kw),
            s(R.string.settings_row_ai_ask, R.string.settings_row_ai_ask_desc, R.string.settings_row_ai_ask_kw),
            s(R.string.settings_row_ai_statements, R.string.settings_row_ai_statements_desc, R.string.settings_row_ai_statements_kw),
            s(R.string.settings_row_ai_privacy, R.string.settings_row_ai_privacy_desc, R.string.settings_row_ai_privacy_kw),
            s(R.string.settings_row_ai_log, R.string.settings_row_ai_log_desc, R.string.settings_row_ai_log_kw),
            s(R.string.settings_row_ai_usage, R.string.settings_row_ai_usage_desc, R.string.settings_row_ai_usage_kw),
        )),
    ),
    R.string.settings_group_data to listOf(
        Section("data", R.string.settings_section_data, "download-simple", listOf(
            s(R.string.settings_row_statements, R.string.settings_row_statements_desc, R.string.settings_row_statements_kw),
            s(R.string.settings_row_export, R.string.settings_row_export_desc, R.string.settings_row_export_kw),
        )),
        Section("api", R.string.settings_section_api, "plugs-connected", listOf(
            s(R.string.settings_row_mcp_url, R.string.settings_row_mcp_url_desc, R.string.settings_row_mcp_url_kw),
            s(R.string.settings_row_connected_apps, R.string.settings_row_connected_apps_desc, R.string.settings_row_connected_apps_kw),
            s(R.string.settings_row_api_keys, R.string.settings_row_api_keys_desc, R.string.settings_row_api_keys_kw),
            s(R.string.settings_row_api_docs, R.string.settings_row_api_docs_desc, R.string.settings_row_api_docs_kw),
        )),
        Section("delete", R.string.settings_section_delete, "warning", listOf(
            s(R.string.settings_row_delete_transactions, R.string.settings_row_delete_transactions_desc, R.string.settings_row_delete_transactions_kw),
            s(R.string.settings_row_delete_account, R.string.settings_row_delete_account_desc, R.string.settings_row_delete_account_kw),
        )),
    ),
)

/** The provider names, as the web writes them; the self-hosted kind is a translated phrase. */
@Composable
fun providerName(id: String): String = when (id) {
    "anthropic" -> "Anthropic"
    "openai" -> "OpenAI"
    "gemini" -> "Google Gemini"
    "openrouter" -> "OpenRouter"
    "ollama" -> "Ollama"
    else -> stringResource(R.string.settings_ai_compatible_name)
}

/**
 * Settings: search across every section's rows, the profile card, then the sections grouped You, Preferences,
 * Money, Intelligence and Data, each with a line about where it stands. Footer: version and server.
 */
@Composable
private fun SettingsList() {
    val nav = LocalNav.current
    val open = go()
    val kosha = rememberKosha()
    val m = meta()
    val c = KoshaTheme.colors
    val context = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }
    val account = loaded("account") { get<AccountSummary>("/api/app/account") }.value
    val access = loaded("api-access") { get<ApiAccess>("/api/app/api-access") }.value
    val res = LocalResources.current

    Page(stringResource(R.string.settings_title), onBack = { nav.popBackStack() }) {
        Field(query, { query = it }, placeholder = stringResource(R.string.settings_search_placeholder), icon = "magnifying-glass")

        if (query.isNotBlank()) {
            // Every row and section whose words match, in the list's order.
            val hits = GROUPS.flatMap { it.second }.flatMap { section ->
                val sectionTitle = res.getString(section.title)
                val own = if (matchesAll(query, sectionTitle)) listOf(section to null) else emptyList()
                own + section.rows.filter {
                    matchesAll(query, res.getString(it.title), res.getString(it.description), res.getString(it.keywords), sectionTitle)
                }.map { section to it }
            }
            if (hits.isEmpty()) {
                KText(stringResource(R.string.settings_no_match, query.trim()), Modifier.fillMaxWidth().padding(vertical = 24.dp), color = c.ink3, align = TextAlign.Center)
            } else {
                Hint(pluralStringResource(R.plurals.settings_results, hits.size, hits.size))
                Rows(*hits.map { (section, row) ->
                    @Composable {
                        NavRow(
                            if (row != null) stringResource(row.title) else stringResource(section.title),
                            description = if (row != null) stringResource(section.title) else null,
                            icon = section.icon,
                        ) { open(section.page, null) }
                    }
                }.toTypedArray())
            }
            return@Page
        }

        // Who is signed in.
        Card(Modifier.fillMaxWidth(), tight = true) {
            Row(
                Modifier.fillMaxWidth().clickable(role = Role.Button) { open("profile", null) }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Avatar(48.dp)
                Column(Modifier.weight(1f)) {
                    KText(m?.user?.name ?: "", style = KoshaTheme.type.head)
                    KText(m?.user?.email ?: "", style = KoshaTheme.type.xs, color = c.ink3)
                }
                PhIcon("caret-right", size = 16.dp, tint = c.ink3)
            }
        }

        val open2 = account?.let { a -> securitySteps(a, m.prefOf("backup_codes_saved_at").isNotEmpty()).count { !it.second } } ?: 0
        val currencies = m?.let { currencyOrder(it.primary, it.order, it.wallets.filterNot { w -> w.isArchived }.map { w -> w.currency }) } ?: emptyList()
        val aiOn = m.prefOf("ai_enabled") == "1"
        val aiCount = AiFeature.entries.count { m.prefOf(it.pref) == "1" }
        val describe: Map<String, String?> = mapOf(
            "profile" to stringResource(R.string.app_set_profile_desc),
            "security" to stringResource(R.string.app_set_security_desc),
            "general" to stringResource(R.string.app_set_general_desc),
            "appearance" to stringResource(R.string.app_set_appearance_desc),
            "currencies" to currencies.joinToString(" · ") { it.uppercase() },
            "categories" to stringResource(R.string.app_set_categories_desc),
            "events" to stringResource(R.string.app_set_events_desc),
            "rules" to listOfNotNull(
                account?.let { pluralStringResource(R.plurals.settings_rules_count, it.rules, it.rules) },
                if (m.prefOf("learn_rules") == "1") stringResource(R.string.app_set_rules_learning) else null,
            ).joinToString(" · ").ifEmpty { null },
            "recurring" to stringResource(R.string.app_set_recurring_desc),
            "ai" to if (!aiOn) stringResource(R.string.settings_appearance_off) else listOfNotNull(
                stringResource(R.string.settings_appearance_on),
                m?.aiModel?.provider?.let { providerName(it) },
                pluralStringResource(R.plurals.app_set_ai_features_on, aiCount, aiCount),
            ).joinToString(" · "),
            "data" to stringResource(R.string.app_set_data_desc),
            "api" to access?.let {
                pluralStringResource(R.plurals.app_set_apps_count, it.apps.size, it.apps.size) + " · " +
                    pluralStringResource(R.plurals.app_set_keys_count, it.keys.size, it.keys.size)
            },
            "delete" to stringResource(R.string.app_set_delete_desc),
        )
        GROUPS.forEach { (group, sections) ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                GroupCaption(stringResource(group))
                Rows(*sections.map { section ->
                    @Composable {
                        NavRow(
                            stringResource(section.title),
                            description = describe[section.page],
                            icon = section.icon,
                            badge = if (section.page == "security") open2 else 0,
                            titleExtra = if (section.page == "ai") {
                                { Pill(stringResource(R.string.settings_badge_beta)) }
                            } else null,
                        ) { open(section.page, null) }
                    }
                }.toTypedArray())
            }
        }

        val version = remember {
            runCatching {
                val p = context.packageManager.getPackageInfo(context.packageName, 0)
                p.versionName.orEmpty() to p.longVersionCode.toString()
            }.getOrDefault("" to "")
        }
        KText(
            stringResource(R.string.app_set_footer, version.first, version.second, serverHost(kosha.api.server)),
            Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 12.dp),
            style = KoshaTheme.type.xs,
            color = c.ink3,
            align = TextAlign.Center,
        )
    }
}
