package app.getkosha.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.BasicText
import androidx.core.content.FileProvider
import app.getkosha.ui.filesAuthority
import app.getkosha.design.Button
import app.getkosha.design.ButtonSize
import app.getkosha.design.ButtonStyle
import app.getkosha.design.Card
import app.getkosha.design.Field
import app.getkosha.design.IconButton
import app.getkosha.design.IconTile
import app.getkosha.design.KText
import app.getkosha.design.KoshaTheme
import app.getkosha.design.R
import app.getkosha.design.Segmented
import app.getkosha.design.Sheet
import app.getkosha.model.Dates
import app.getkosha.network.ApiException
import app.getkosha.ui.LocalNav
import app.getkosha.ui.Page
import app.getkosha.ui.Route
import app.getkosha.ui.meta
import app.getkosha.ui.rememberKosha
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.io.File
import java.text.NumberFormat

// Import & export, API & MCP, and Delete & reset.

/** The share sheet for a file this app wrote to its cache, through the module's FileProvider. */
private fun shareFile(context: Context, file: File, type: String) {
    val uri = FileProvider.getUriForFile(context, context.filesAuthority(), file)
    val send = Intent(Intent.ACTION_SEND).setType(type).putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(send, file.name))
}

private fun shareText(context: Context, text: String) {
    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), null))
}

private fun copy(context: Context, label: String, text: String) {
    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(label, text))
}

/** Import & export: Import statements, and Transactions (CSV) and Everything (JSON) through the share sheet. */
@Composable
fun DataExportPage() {
    val nav = LocalNav.current
    val m = meta()
    val c = KoshaTheme.colors
    Page(stringResource(R.string.settings_section_data), onBack = { nav.popBackStack() }) {
        GroupCaption(stringResource(R.string.settings_data_import))
        Card(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    IconTile("file-arrow-up")
                    KText(stringResource(R.string.settings_row_statements), Modifier.weight(1f), style = KoshaTheme.type.head)
                }
                KText(
                    stringResource(if (m?.ai?.statements == true) R.string.settings_data_statements_desc_pdf else R.string.settings_data_statements_desc),
                    style = KoshaTheme.type.small,
                    color = c.ink2,
                )
                Button(stringResource(R.string.settings_data_import_statements), { nav.navigate(Route.Import) }, Modifier.fillMaxWidth(), size = ButtonSize.Medium)
            }
        }
        GroupCaption(stringResource(R.string.settings_row_export))
        Rows(
            { ExportRow("file-csv", R.string.settings_data_transactions, R.string.settings_data_transactions_sub, "/api/app/export/transactions.csv", "kosha-transactions.csv", "text/csv") },
            { ExportRow("file-code", R.string.settings_data_everything, R.string.settings_data_everything_sub, "/api/app/export/kosha.json", "kosha.json", "application/json") },
        )
        Hint(stringResource(R.string.app_set_export_note))
    }
}

/** One export: Download fetches it with the session, writes it to the cache and opens the share sheet. */
@Composable
private fun ExportRow(icon: String, title: Int, sub: Int, path: String, fileName: String, type: String) {
    val kosha = rememberKosha()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val w = rememberWriter()
    var state by remember { mutableStateOf("idle") }
    val failed = stringResource(R.string.settings_data_export_failed, "%s")
    SRow(stringResource(title), description = stringResource(sub), icon = icon) {
        Button(
            stringResource(
                when (state) {
                    "busy" -> R.string.settings_data_preparing
                    "done" -> R.string.settings_data_downloaded
                    else -> R.string.settings_data_download
                },
            ),
            {
                state = "busy"
                scope.launch {
                    try {
                        val bytes = kosha.api.bytes(path)
                        val file = withContext(Dispatchers.IO) {
                            File(context.cacheDir, "exports").apply { mkdirs() }.resolve(fileName).apply { writeBytes(bytes) }
                        }
                        state = "done"
                        shareFile(context, file, type)
                    } catch (e: Exception) {
                        state = "idle"
                        w.toast(failed.replace("%s", (e as? ApiException)?.message ?: e.javaClass.simpleName))
                    }
                }
            },
            style = ButtonStyle.Secondary,
            size = ButtonSize.Small,
            icon = if (state == "idle") "export" else null,
            enabled = state != "busy",
        )
    }
}

/**
 * API & MCP: the MCP server URL with Copy, apps connected through OAuth (with Disconnect), API keys (a new key is
 * shown once, with Copy and Share), and the API reference.
 */
@Composable
fun ApiMcpPage() {
    val nav = LocalNav.current
    val kosha = rememberKosha()
    val c = KoshaTheme.colors
    val w = rememberWriter()
    val context = LocalContext.current
    val locale = KoshaTheme.locale
    val today = Dates.today(kosha.tz)
    val access = loaded("api-access") { get<ApiAccess>("/api/app/api-access") }.value
    var creating by remember { mutableStateOf(false) }
    var made by remember { mutableStateOf<Pair<String, NewKey>?>(null) }
    var confirm by remember { mutableStateOf<Pair<String, String>?>(null) } // kind ("key" or "app") to id
    val urlCopied = stringResource(R.string.settings_api_url_copied)
    val keyCopied = stringResource(R.string.settings_api_key_copied)

    Page(stringResource(R.string.settings_section_api), onBack = { nav.popBackStack() }) {
        KText(stringResource(R.string.settings_section_api_desc), style = KoshaTheme.type.small, color = c.ink3)
        GroupCaption(stringResource(R.string.settings_api_assistants))
        Card(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                KText(stringResource(R.string.settings_api_mcp_title), style = KoshaTheme.type.head)
                val url = access?.mcpUrl ?: ((kosha.api.server ?: "https://getkosha.app") + "/mcp")
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KText(
                        url,
                        Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(c.soft).heightIn(min = 44.dp).padding(horizontal = 12.dp, vertical = 12.dp),
                        style = KoshaTheme.type.num.copy(fontSize = 14.sp),
                        maxLines = 1,
                    )
                    Button(stringResource(R.string.settings_sec_copy), { copy(context, "MCP", url); w.toast(urlCopied) }, style = ButtonStyle.Secondary, size = ButtonSize.Small, icon = "copy")
                }
                KText(stringResource(R.string.settings_api_assistants_note), style = KoshaTheme.type.xs, color = c.ink3)
            }
        }

        GroupCaption(stringResource(R.string.settings_api_connected_apps))
        val apps = access?.apps ?: emptyList()
        if (access != null && apps.isEmpty()) Hint(stringResource(R.string.settings_api_none))
        Rows(*apps.map { a ->
            @Composable {
                SRow(
                    a.name,
                    description = listOfNotNull(
                        stringResource(if (a.write) R.string.settings_api_can_write else R.string.settings_api_app_read_only),
                        stringResource(R.string.settings_api_connected_on, shortDate(dayOf(a.connectedAt, kosha.tz), locale, today)),
                        a.host.ifEmpty { null },
                    ).joinToString(" · "),
                ) {
                    Button(stringResource(R.string.settings_api_disconnect), { if (!w.refused()) confirm = "app" to a.clientId }, style = ButtonStyle.Secondary, size = ButtonSize.Small)
                }
            }
        }.toTypedArray())

        GroupCaption(stringResource(R.string.settings_api_keys)) {
            Button(stringResource(R.string.settings_api_new_key), { if (!w.refused()) creating = true }, style = ButtonStyle.Soft, size = ButtonSize.Small, icon = "plus")
        }
        val keys = access?.keys ?: emptyList()
        if (access != null && keys.isEmpty() && made == null) Hint(stringResource(R.string.settings_api_no_keys))
        Rows(
            *keys.map { k ->
                @Composable {
                    SRow(
                        k.name,
                        description = listOf(
                            stringResource(if (k.scope == "write") R.string.settings_api_read_write else R.string.settings_api_read_only),
                            stringResource(R.string.settings_api_made_on, shortDate(dayOf(k.createdAt * 1000, kosha.tz), locale, today)),
                            k.lastUsedAt?.let { stringResource(R.string.settings_api_used_on, shortDate(dayOf(it * 1000, kosha.tz), locale, today)) }
                                ?: stringResource(R.string.settings_api_never_used),
                        ).joinToString(" · "),
                    ) {
                        IconButton("trash", stringResource(R.string.settings_api_delete_key, k.name), { if (!w.refused()) confirm = "key" to k.id })
                    }
                }
            }.toTypedArray(),
            made?.let { (name, key) ->
                {
                    // The key is shown this once; only its hash is kept on the server.
                    Column(
                        Modifier.fillMaxWidth().background(c.brand.copy(alpha = 0.06f)).padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        KText(stringResource(R.string.settings_api_created, name), style = KoshaTheme.type.small.copy(fontWeight = KoshaTheme.type.head.fontWeight))
                        KText(
                            key.key,
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.surface).border(1.dp, c.line, RoundedCornerShape(12.dp)).padding(12.dp),
                            style = KoshaTheme.type.num.copy(fontSize = 13.sp),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(stringResource(R.string.settings_sec_copy), { copy(context, name, key.key); w.toast(keyCopied) }, Modifier.weight(1f), size = ButtonSize.Small, icon = "copy")
                            Button(stringResource(R.string.app_set_share), { shareText(context, key.key) }, Modifier.weight(1f), style = ButtonStyle.Secondary, size = ButtonSize.Small, icon = "share-network")
                        }
                    }
                }
            },
        )
        KText(stringResource(R.string.settings_api_keys_note), Modifier.padding(horizontal = 2.dp), style = KoshaTheme.type.xs, color = c.ink3)
        Rows({
            NavRow(
                stringResource(R.string.settings_api_docs_title),
                description = stringResource(R.string.settings_api_docs_description),
                icon = "book-open",
                external = true,
            ) {
                val docs = access?.docsUrl?.ifEmpty { null }
                if (docs != null) openUrl(context, docs) else openWeb(context, kosha.api.server, "/api/v1/docs")
            }
        })
    }

    if (creating) NewKeySheet({ creating = false }) { name, key -> creating = false; made = name to key }
    confirm?.let { (kind, id) ->
        val key = access?.keys?.firstOrNull { it.id == id }
        val app = access?.apps?.firstOrNull { it.clientId == id }
        val name = key?.name ?: app?.name ?: ""
        val doneMessage = stringResource(if (kind == "key") R.string.settings_api_key_deleted else R.string.settings_api_app_disconnected, name)
        Sheet(stringResource(if (kind == "key") R.string.settings_api_delete_title else R.string.settings_api_disconnect_title, name), { confirm = null }) {
            KText(stringResource(if (kind == "key") R.string.settings_api_delete_body else R.string.settings_api_disconnect_body), color = c.ink2)
            Spacer(Modifier.height(16.dp))
            Button(
                stringResource(if (kind == "key") R.string.common_delete else R.string.settings_api_disconnect),
                {
                    confirm = null
                    val path = if (kind == "key") "/api/app/api-keys/$id" else "/api/app/connected-apps/$id"
                    w.run({ kosha.api.send("DELETE", path) }) { w.toast(doneMessage) }
                },
                Modifier.fillMaxWidth(),
                style = ButtonStyle.Danger,
            )
        }
    }
}

/** New key: a name and read-only or read & write. */
@Composable
private fun NewKeySheet(onDismiss: () -> Unit, onMade: (String, NewKey) -> Unit) {
    val kosha = rememberKosha()
    val w = rememberWriter()
    var name by rememberSaveable { mutableStateOf("") }
    var scope by rememberSaveable { mutableStateOf("read") }
    var busy by remember { mutableStateOf(false) }
    Sheet(stringResource(R.string.settings_api_new_key), onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column {
                FieldLabel(stringResource(R.string.settings_api_name))
                Field(name, { name = it }, placeholder = stringResource(R.string.settings_api_name_placeholder), keyboard = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
            }
            Column {
                FieldLabel(stringResource(R.string.settings_api_scope))
                Segmented(
                    listOf("read" to stringResource(R.string.settings_api_read_only), "write" to stringResource(R.string.settings_api_read_write)),
                    scope,
                    { scope = it },
                    Modifier.fillMaxWidth(),
                )
            }
            Button(
                stringResource(R.string.settings_api_create),
                {
                    busy = true
                    val body = buildJsonObject { put("name", JsonPrimitive(name.trim())); put("scope", JsonPrimitive(scope)) }
                    w.run({ kosha.api.post<JsonObject, NewKey>("/api/app/api-keys", body) }, always = { busy = false }) { onMade(name.trim(), it) }
                },
                Modifier.fillMaxWidth(),
                enabled = name.isNotBlank() && !busy,
            )
        }
    }
}

/**
 * Delete & reset: Delete all transactions after typing DELETE (with the count and what stays), and Delete account,
 * which opens the server's page, where the person confirms with their email and password.
 */
@Composable
fun DeleteResetPage() {
    val nav = LocalNav.current
    val kosha = rememberKosha()
    val c = KoshaTheme.colors
    val w = rememberWriter()
    val context = LocalContext.current
    val locale = KoshaTheme.locale
    val account = loaded("account") { get<AccountSummary>("/api/app/account") }.value
    var typed by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val count = account?.transactions ?: 0
    val n = NumberFormat.getIntegerInstance(locale).format(count)
    val done = pluralStringResource(R.plurals.settings_danger_reset_done, count, n)

    Page(stringResource(R.string.settings_section_delete), onBack = { nav.popBackStack() }) {
        KText(stringResource(R.string.app_set_delete_intro), style = KoshaTheme.type.small, color = c.ink3)
        Card(Modifier.fillMaxWidth().border(1.dp, c.bad.copy(alpha = 0.4f), RoundedCornerShape(16.dp))) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                KText(stringResource(R.string.settings_danger_reset_title), style = KoshaTheme.type.head)
                KText(
                    if (account != null) pluralStringResource(R.plurals.settings_danger_reset_body, count, n) else stringResource(R.string.settings_danger_transactions_desc),
                    style = KoshaTheme.type.small,
                    color = c.ink2,
                )
                Column {
                    BasicText(
                        styledArg(R.string.settings_danger_type_to_confirm, "DELETE", SpanStyle(fontFamily = KoshaTheme.type.num.fontFamily, color = c.ink, background = c.soft)),
                        Modifier.padding(bottom = 6.dp),
                        style = KoshaTheme.type.small.copy(color = c.ink3),
                    )
                    Field(typed, { typed = it }, keyboard = KeyboardOptions(capitalization = KeyboardCapitalization.Characters))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(stringResource(R.string.common_cancel), { typed = "" }, Modifier.width(110.dp), style = ButtonStyle.Secondary, size = ButtonSize.Medium)
                    Button(
                        if (busy) stringResource(R.string.settings_danger_deleting) else pluralStringResource(R.plurals.settings_danger_reset_button, count, n),
                        {
                            busy = true
                            // The word stays DELETE in every language, as on the web.
                            w.run({ kosha.api.send("DELETE", "/api/app/transactions") }, always = { busy = false }) {
                                typed = ""
                                w.toast(done)
                            }
                        },
                        Modifier.weight(1f),
                        style = ButtonStyle.Danger,
                        size = ButtonSize.Medium,
                        enabled = typed == "DELETE" && !busy && count > 0,
                    )
                }
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                KText(stringResource(R.string.settings_row_delete_account), style = KoshaTheme.type.head)
                KText(stringResource(R.string.settings_danger_account_desc), style = KoshaTheme.type.small, color = c.ink2)
                Button(
                    stringResource(R.string.settings_danger_account_button),
                    { if (!w.refused()) openWeb(context, kosha.api.server, "/settings/delete#delete-account") },
                    Modifier.fillMaxWidth(),
                    style = ButtonStyle.Danger,
                    size = ButtonSize.Medium,
                    icon = "arrow-square-out",
                )
                KText(stringResource(R.string.app_set_delete_account_note, serverHost(kosha.api.server)), style = KoshaTheme.type.xs, color = c.ink3)
            }
        }
    }
}
