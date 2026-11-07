package app.getkosha.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.getkosha.design.Button
import app.getkosha.design.ButtonSize
import app.getkosha.design.ButtonStyle
import app.getkosha.design.Caption
import app.getkosha.design.Card
import app.getkosha.design.Field
import app.getkosha.design.KText
import app.getkosha.design.KoshaTheme
import app.getkosha.design.ListOption
import app.getkosha.design.ListPicker
import app.getkosha.design.PhIcon
import app.getkosha.design.Pill
import app.getkosha.design.R
import app.getkosha.design.Sheet
import app.getkosha.design.dayLabel
import app.getkosha.model.Dates
import app.getkosha.ui.LoadingNote
import app.getkosha.ui.LocalNav
import app.getkosha.ui.Page
import app.getkosha.ui.meta
import app.getkosha.ui.rememberKosha
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

// Settings › AI: the master switch, providers, features, what AI can see and the request log. All optional;
// with AI off nothing below the switch shows, and nothing is sent.

val AI_PROVIDERS = listOf("anthropic", "openai", "gemini", "openrouter", "ollama", "compatible")

/** Ollama and OpenAI-compatible endpoints are reached at a URL the person gives, with an optional key. */
fun usesBaseUrl(id: String) = id == "ollama" || id == "compatible"

@Composable
private fun providerKind(id: String): String = stringResource(
    when (id) {
        "anthropic" -> R.string.settings_ai_kind_anthropic
        "openai" -> R.string.settings_ai_kind_openai
        "gemini" -> R.string.settings_ai_kind_gemini
        "openrouter" -> R.string.settings_ai_kind_openrouter
        "ollama" -> R.string.settings_ai_kind_ollama
        else -> R.string.settings_ai_kind_compatible
    },
)

private fun placeholder(id: String) = when (id) {
    "anthropic" -> "sk-ant-…"
    "openai" -> "sk-…"
    "gemini" -> "AIza…"
    "openrouter" -> "sk-or-…"
    "ollama" -> "https://ollama.example.com"
    else -> "https://…/v1"
}

@Composable
fun featureTitle(f: AiFeature): String = stringResource(
    when (f) {
        AiFeature.Categorise -> R.string.settings_ai_categorise_title
        AiFeature.QuickAdd -> R.string.settings_ai_quick_add_title
        AiFeature.Receipts -> R.string.settings_ai_receipts_title
        AiFeature.Recap -> R.string.settings_ai_recap_title
        AiFeature.Ask -> R.string.settings_ai_ask_title
        AiFeature.Statements -> R.string.settings_ai_statements_title
    },
)

@Composable
private fun featureDescription(f: AiFeature): String = stringResource(
    when (f) {
        AiFeature.Categorise -> R.string.settings_ai_categorise_description
        AiFeature.QuickAdd -> R.string.settings_ai_quick_add_description
        AiFeature.Receipts -> R.string.settings_ai_receipts_description
        AiFeature.Recap -> R.string.settings_ai_recap_description
        AiFeature.Ask -> R.string.settings_ai_ask_description
        AiFeature.Statements -> R.string.settings_ai_statements_description
    },
)

@Composable
private fun featureSends(f: AiFeature): String = stringResource(
    when (f) {
        AiFeature.Categorise -> R.string.settings_ai_categorise_sends
        AiFeature.QuickAdd -> R.string.settings_ai_quick_add_sends
        AiFeature.Receipts -> R.string.settings_ai_receipts_sends
        AiFeature.Recap -> R.string.settings_ai_recap_sends
        AiFeature.Ask -> R.string.settings_ai_ask_sends
        AiFeature.Statements -> R.string.settings_ai_statements_sends
    },
)

private val SHARES = listOf(
    Triple("ai_share_amount", R.string.settings_ai_share_amount, R.string.settings_ai_share_amount_description),
    Triple("ai_share_payee", R.string.settings_ai_share_payee, R.string.settings_ai_share_payee_description),
    Triple("ai_share_date", R.string.settings_ai_share_date, R.string.settings_ai_share_date_description),
    Triple("ai_share_note", R.string.settings_ai_share_note, R.string.settings_ai_share_note_description),
    Triple("ai_share_account", R.string.settings_ai_share_account, R.string.settings_ai_share_account_description),
)

@Composable
private fun loadProviders() = loaded("ai-providers") { get<List<AiProvider>>("/api/app/ai/providers") }

/** AI: Use AI features, then (when on) the providers, each feature's switch, What AI can see and the request log. */
@Composable
fun AiSettingsPage() {
    val nav = LocalNav.current
    val open = go()
    val m = meta()
    val c = KoshaTheme.colors
    val w = rememberWriter()
    val on = m.prefOf("ai_enabled") == "1"
    val providers = loadProviders().value
    var adding by remember { mutableStateOf(false) }

    Page(
        stringResource(R.string.settings_section_ai),
        onBack = { nav.popBackStack() },
        actions = { Pill(stringResource(R.string.settings_badge_beta), Modifier.padding(end = 12.dp)) },
    ) {
        KText(stringResource(R.string.app_set_ai_intro), style = KoshaTheme.type.small, color = c.ink3)
        Rows({
            SwitchRow(
                stringResource(R.string.settings_ai_enabled_title),
                stringResource(R.string.settings_ai_enabled_description),
                on,
                { w.pref("ai_enabled", if (it) "1" else "0") },
            )
        })
        if (!on) return@Page

        GroupCaption(stringResource(R.string.settings_ai_providers))
        val connected = (providers ?: emptyList()).filter { it.connected }.sortedByDescending { it.isDefault }
        if (providers == null) LoadingNote()
        Rows(
            *connected.map { p ->
                @Composable {
                    NavRow(
                        providerName(p.id),
                        description = stringResource(R.string.settings_ai_connected, p.model),
                        icon = "plug",
                        titleExtra = if (p.isDefault) {
                            { Pill(stringResource(R.string.settings_ai_default), color = c.brand) }
                        } else null,
                    ) { open("ai-provider", p.id) }
                }
            }.toTypedArray(),
            {
                NavRow(stringResource(R.string.app_set_ai_add_provider), description = stringResource(R.string.app_set_ai_add_provider_desc), icon = "plus") { adding = true }
            },
        )
        val anyDefault = connected.any { it.isDefault }
        if (providers != null && !anyDefault) Hint(stringResource(R.string.settings_ai_features_note))

        GroupCaption(stringResource(R.string.settings_ai_features))
        val needs = mapOf(
            "ai_share_payee" to stringResource(R.string.settings_ai_needs_payee),
            "ai_share_amount" to stringResource(R.string.settings_ai_needs_amount),
            "suggest_category" to stringResource(R.string.settings_ai_needs_suggest),
        )
        Rows(*AiFeature.entries.map { f ->
            @Composable {
                val value = m.prefOf(f.pref) == "1"
                val blocked = f.needs.filter { m.prefOf(it) != "1" }.mapNotNull { needs[it] }
                SwitchRow(
                    featureTitle(f),
                    featureDescription(f),
                    value,
                    { w.pref(f.pref, if (it) "1" else "0") },
                    extra = if (value && blocked.isNotEmpty()) {
                        { KText(blocked.joinToString(" "), Modifier.padding(top = 4.dp), style = KoshaTheme.type.xs, color = c.warn) }
                    } else null,
                )
            }
        }.toTypedArray())

        val shared = SHARES.filter { m.prefOf(it.first) == "1" }.map { stringResource(it.second) }
        Rows(
            {
                NavRow(
                    stringResource(R.string.settings_ai_visibility),
                    description = shared.joinToString(", ").ifEmpty { null },
                    icon = "eye",
                ) { open("ai-privacy", null) }
            },
            {
                NavRow(stringResource(R.string.settings_ai_log_dialog_title), description = stringResource(R.string.settings_ai_log_dialog_description), icon = "list-magnifying-glass") {
                    open("ai-log", null)
                }
            },
        )
    }

    if (adding) {
        Sheet(stringResource(R.string.app_set_ai_add_provider), { adding = false }) {
            val list = AI_PROVIDERS.filter { id -> providers?.firstOrNull { it.id == id }?.connected != true }
            Rows(*list.map { id ->
                @Composable {
                    NavRow(providerName(id), description = providerKind(id)) {
                        adding = false
                        open("ai-provider", id)
                    }
                }
            }.toTypedArray())
        }
    }
}

/**
 * One provider: whether it's connected, the API key (masked, with Replace; the server never sends it back), the
 * base URL for self-hosted endpoints, the model from the provider's own list, Test connection, Make default and
 * Disconnect. A test that works saves the key and model, so there is no Save button.
 */
@Composable
fun AiProviderPage(id: String) {
    val nav = LocalNav.current
    val kosha = rememberKosha()
    val c = KoshaTheme.colors
    val w = rememberWriter()
    val p = loadProviders().value?.firstOrNull { it.id == id } ?: AiProvider(id)
    val name = providerName(id)
    var key by rememberSaveable { mutableStateOf("") }
    var replacing by rememberSaveable { mutableStateOf(false) }
    var baseUrl by rememberSaveable(p.baseUrl) { mutableStateOf(p.baseUrl ?: "") }
    var test by remember { mutableStateOf<AiTest?>(null) }
    var testing by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }
    val models = test?.models?.takeIf { it.isNotEmpty() } ?: listOfNotNull(p.model.ifEmpty { null })
    val saved = stringResource(R.string.common_saved)

    fun body(model: String?) = buildJsonObject {
        if (key.isNotBlank()) put("key", JsonPrimitive(key.trim()))
        if (usesBaseUrl(id) && baseUrl.isNotBlank()) put("baseUrl", JsonPrimitive(baseUrl.trim()))
        if (model != null) put("model", JsonPrimitive(model))
    }

    fun save(model: String) = w.run({ kosha.api.send("PUT", "/api/app/ai/providers/$id", body(model)) }) {
        key = ""
        replacing = false
        w.toast(saved)
    }

    fun runTest() {
        if (w.refused()) return
        testing = true
        w.run({ kosha.api.post<JsonObject, AiTest>("/api/app/ai/providers/$id/test", body(null)) }, always = { testing = false }) { r ->
            test = r
            // A working connection saves what was typed, keeping the model (or taking the provider's first one).
            val model = p.model.takeIf { it.isNotEmpty() && it in r.models } ?: r.models.firstOrNull()
            if (r.ok && model != null && (!p.connected || key.isNotBlank() || (usesBaseUrl(id) && baseUrl != (p.baseUrl ?: "")) || model != p.model)) save(model)
        }
    }
    val disconnected = stringResource(R.string.settings_ai_disconnected, name)

    Page(
        name,
        onBack = { nav.popBackStack() },
        subtitle = if (p.isDefault) stringResource(R.string.settings_ai_default) else null,
        footer = if (p.connected) {
            {
                Button(
                    stringResource(R.string.app_set_ai_disconnect_named, name),
                    { w.run({ kosha.api.send("DELETE", "/api/app/ai/providers/$id") }) { w.toast(disconnected); nav.popBackStack() } },
                    Modifier.fillMaxWidth(),
                    style = ButtonStyle.Danger,
                )
            }
        } else null,
    ) {
        val t = test
        when {
            t != null && t.ok -> Pill(stringResource(R.string.settings_ai_reached, name) + " · ${t.ms} ms", color = c.pos)
            t != null -> KText(t.error ?: "", style = KoshaTheme.type.small, color = c.bad)
            p.connected -> Pill(stringResource(R.string.settings_ai_connected, p.model), color = c.pos)
            else -> Pill(stringResource(R.string.settings_ai_not_connected))
        }
        KText(providerKind(id), style = KoshaTheme.type.small, color = c.ink3)

        if (usesBaseUrl(id)) {
            Column {
                FieldLabel(stringResource(R.string.settings_ai_base_url))
                Field(baseUrl, { baseUrl = it }, placeholder = placeholder(id), keyboard = KeyboardOptions(keyboardType = KeyboardType.Uri))
            }
        }
        Column {
            FieldLabel(stringResource(if (usesBaseUrl(id)) R.string.settings_ai_api_key_optional else R.string.settings_ai_api_key))
            if (p.keyHint != null && !replacing) {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(14.dp)).background(c.surface)
                        .then(Modifier).padding(start = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    PhIcon("key", size = 18.dp, tint = c.ink3)
                    KText("…${p.keyHint}", Modifier.weight(1f), style = KoshaTheme.type.num, color = c.ink3)
                    Button(stringResource(R.string.app_set_ai_replace), { if (!w.refused()) replacing = true }, style = ButtonStyle.Soft, size = ButtonSize.Small)
                }
            } else {
                Field(
                    key,
                    { key = it },
                    placeholder = placeholder(id).takeUnless { usesBaseUrl(id) } ?: "",
                    icon = "key",
                    keyboard = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
            }
            Hint(stringResource(if (usesBaseUrl(id)) R.string.settings_ai_url_note else R.string.app_set_ai_key_stored), Modifier.padding(top = 6.dp))
        }
        Column {
            FieldLabel(stringResource(R.string.settings_ai_model))
            PickField(
                null,
                p.model.ifEmpty { stringResource(if (models.isEmpty()) R.string.settings_ai_test_to_load else R.string.settings_ai_pick_model) },
                placeholder = p.model.isEmpty(),
            ) { if (models.isNotEmpty() && !w.refused()) picking = true }
        }
        Button(
            stringResource(
                when {
                    testing -> R.string.settings_ai_testing
                    test?.ok == true -> R.string.settings_ai_test_again
                    else -> R.string.settings_ai_test
                },
            ),
            { runTest() },
            Modifier.fillMaxWidth(),
            style = ButtonStyle.Secondary,
            size = ButtonSize.Medium,
            icon = "plugs-connected",
            enabled = !testing && (p.connected || key.isNotBlank() || (usesBaseUrl(id) && baseUrl.isNotBlank())),
        )
        if (p.connected && !p.isDefault) {
            Button(
                stringResource(R.string.settings_ai_make_default),
                { w.run({ kosha.api.send("POST", "/api/app/ai/providers/$id/default") }) },
                Modifier.fillMaxWidth(),
                style = ButtonStyle.Soft,
                size = ButtonSize.Medium,
            )
        }
    }

    if (picking) {
        Sheet(stringResource(R.string.settings_ai_model), { picking = false }) {
            ListPicker(models.map { ListOption(it, it) }, p.model, { o -> picking = false; save(o.value) })
        }
    }
}

/**
 * What AI can see: the five fields Kosha may send, a live example of one transaction as it would go, what each
 * feature sends, and the request log switch.
 */
@Composable
fun AiPrivacyPage() {
    val nav = LocalNav.current
    val m = meta()
    val c = KoshaTheme.colors
    val w = rememberWriter()
    val shares = mapOf(
        "amount" to (m.prefOf("ai_share_amount") == "1"),
        "payee" to (m.prefOf("ai_share_payee") == "1"),
        "date" to (m.prefOf("ai_share_date") == "1"),
        "note" to (m.prefOf("ai_share_note") == "1"),
        "account" to (m.prefOf("ai_share_account") == "1"),
    )
    val sample = SampleTx(
        payee = "Pizza Hut",
        amount = -2957,
        currency = m?.primary ?: "eur",
        day = "2026-11-04",
        note = stringResource(R.string.settings_ai_sample_note),
        account = m?.wallets?.firstOrNull()?.name ?: "Visa Card",
    )
    val lines = samplePayload(sample, shares, m?.user?.id ?: "")

    Page(stringResource(R.string.settings_ai_visibility), onBack = { nav.popBackStack() }) {
        KText(stringResource(R.string.app_set_ai_privacy_intro), style = KoshaTheme.type.small, color = c.ink3)
        Rows(*SHARES.map { (pref, title, description) ->
            @Composable { SwitchRow(stringResource(title), stringResource(description), m.prefOf(pref) == "1", { w.pref(pref, if (it) "1" else "0") }) }
        }.toTypedArray())

        GroupCaption(stringResource(R.string.settings_ai_example))
        Card(Modifier.fillMaxWidth()) {
            val mono = KoshaTheme.type.num.copy(fontSize = 12.5.sp, lineHeight = 21.sp)
            KText("{", style = mono)
            lines.forEachIndexed { i, (field, value, shown) ->
                Row(Modifier.padding(start = 14.dp)) {
                    KText("\"$field\": ", style = mono, color = c.ink3)
                    KText(
                        value + if (i < lines.lastIndex) "," else "",
                        Modifier.weight(1f, fill = false),
                        style = mono.copy(textDecoration = if (shown == Shown.Off) TextDecoration.LineThrough else null),
                        color = when (shown) {
                            Shown.On -> c.pos
                            Shown.Masked -> c.brand
                            Shown.Off -> c.ink3
                        },
                    )
                    if (shown == Shown.Off) KText("  " + stringResource(R.string.settings_ai_not_sent), style = mono, color = c.ink3)
                }
            }
            KText("}", style = mono)
        }
        Note("mask-happy", stringResource(R.string.settings_ai_masked_note))

        GroupCaption(stringResource(R.string.app_set_ai_each_sends))
        Rows(*AiFeature.entries.map { f ->
            @Composable {
                Row(Modifier.fillMaxWidth().heightIn(min = KoshaTheme.dims.settingsRow).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    KText(featureTitle(f), Modifier.width(120.dp), style = KoshaTheme.type.small)
                    KText(featureSends(f), Modifier.weight(1f), style = KoshaTheme.type.xs, color = c.ink3)
                }
            }
        }.toTypedArray())
        Rows({
            SwitchRow(
                stringResource(R.string.settings_ai_log_title),
                stringResource(R.string.settings_ai_log_description),
                m.prefOf("ai_log_enabled") == "1",
                { w.pref("ai_log_enabled", if (it) "1" else "0") },
            )
        })
    }
}

private fun featureIcon(feature: String) = when (feature) {
    "ask" -> "chat-circle-text"
    "categorise" -> "tag"
    "receipts" -> "receipt"
    "quick_add" -> "text-aa"
    "recap" -> "article"
    else -> "file-text"
}

/** Request log: this month's usage, then every request of the last 30 days; open one to see exactly what went and came back. */
@Composable
fun AiLogPage() {
    val nav = LocalNav.current
    val kosha = rememberKosha()
    val c = KoshaTheme.colors
    val locale = KoshaTheme.locale
    val load = loaded("ai-log") { get<AiUsage>("/api/app/ai/log") }
    val usage = load.value
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    val number = NumberFormat.getIntegerInstance(locale)
    val zone = runCatching { ZoneId.of(kosha.tz) }.getOrDefault(ZoneId.of("UTC"))
    val today = Dates.today(kosha.tz)
    val time = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale)

    Page(stringResource(R.string.settings_ai_log_dialog_title), onBack = { nav.popBackStack() }, subtitle = stringResource(R.string.settings_ai_log_dialog_description)) {
        if (usage == null) {
            LoadingNote()
            return@Page
        }
        Card(Modifier.fillMaxWidth()) {
            Caption(stringResource(R.string.settings_ai_usage))
            Spacer(Modifier.height(10.dp))
            Row {
                Stat(stringResource(R.string.settings_ai_requests), number.format(usage.requests), Modifier.weight(1f))
                Stat(stringResource(R.string.settings_ai_tokens), number.format(usage.tokens), Modifier.weight(1f))
            }
        }
        if (usage.log.isEmpty()) KText(stringResource(R.string.settings_ai_log_empty), color = c.ink3)
        Rows(*usage.log.map { e ->
            @Composable {
                val at = Instant.ofEpochSecond(e.at).atZone(zone)
                val f = AiFeature.entries.firstOrNull { it.id == e.feature }
                val expanded = open == e.id
                Column {
                    Row(
                        Modifier.fillMaxWidth().clickable(role = Role.Button) { open = if (expanded) null else e.id }
                            .heightIn(min = KoshaTheme.dims.row).padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        PhIcon(featureIcon(e.feature), size = 18.dp)
                        Column(Modifier.weight(1f)) {
                            KText(if (f != null) featureTitle(f) else e.feature, style = KoshaTheme.type.small)
                            KText(
                                listOf(dayLabel(at.toLocalDate(), today) + " " + at.format(time), providerName(e.provider), e.model).joinToString(" · "),
                                style = KoshaTheme.type.xs,
                                color = c.ink3,
                            )
                        }
                        KText(number.format(e.tokensIn + e.tokensOut), style = KoshaTheme.type.num.copy(fontSize = 12.sp), color = c.ink3)
                        PhIcon(if (expanded) "caret-up" else "caret-down", size = 16.dp, tint = c.ink3)
                    }
                    if (expanded) {
                        Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Exchange(stringResource(R.string.app_set_ai_sent), e.request)
                            Exchange(stringResource(R.string.app_set_ai_returned), e.response)
                        }
                    }
                }
            }
        }.toTypedArray())
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        KText(label, style = KoshaTheme.type.xs, color = KoshaTheme.colors.ink3)
        KText(value, style = KoshaTheme.type.num.copy(fontSize = 20.sp))
    }
}

/** One side of a request, word for word, in a soft box. */
@Composable
private fun Exchange(label: String, text: String) {
    val c = KoshaTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        KText(label.uppercase(), style = KoshaTheme.type.caption.copy(fontSize = 11.sp), color = c.ink3)
        KText(
            text,
            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(c.soft).padding(horizontal = 10.dp, vertical = 8.dp),
            style = TextStyle(fontFamily = KoshaTheme.type.num.fontFamily, fontSize = 12.sp, lineHeight = 18.sp),
            color = c.ink2,
        )
    }
}
