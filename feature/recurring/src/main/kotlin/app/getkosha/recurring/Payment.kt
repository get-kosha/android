package app.getkosha.recurring

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import app.getkosha.ui.filesAuthority
import app.getkosha.design.AccountDot
import app.getkosha.design.Button
import app.getkosha.design.ButtonStyle
import app.getkosha.design.Card
import app.getkosha.design.Chip
import app.getkosha.design.Hairline
import app.getkosha.design.KText
import app.getkosha.design.KoshaTheme
import app.getkosha.design.MonthGrid
import app.getkosha.design.PhIcon
import app.getkosha.design.R
import app.getkosha.design.Sheet
import app.getkosha.design.Tick
import app.getkosha.model.Sign
import app.getkosha.ui.LocalToasts
import app.getkosha.ui.MoneyText
import app.getkosha.ui.Page
import app.getkosha.ui.meta
import app.getkosha.ui.rememberKosha
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.abs

/** A file picked to attach: its name, type and bytes. */
private class Picked(val name: String, val type: String, val bytes: ByteArray)

private fun read(context: Context, uri: Uri): Picked? = runCatching {
    val resolver = context.contentResolver
    val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null } ?: "bill"
    val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
    Picked(name, resolver.getType(uri) ?: "application/octet-stream", bytes)
}.getOrNull()

/**
 * Enter one payment, or change it before it is due: the amount on the keypad ("Usually about €84.00"), its
 * day, a bill to attach, Reset to regular, and Record payment. Only this payment changes.
 */
@Composable
fun PaymentSheet(s: Sub, instance: Instance, pay: Boolean, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val m = meta()
    val c = KoshaTheme.colors
    val writer = rememberWriter()
    val scope = rememberCoroutineScope()
    // A variable amount starts empty so it can't be saved as the estimate by accident.
    val start = if (pay && s.isVariable && !instance.edited) null else abs(instance.amount)
    val state = rememberAmountInput(start, s.currency, instance.dueDay)
    var day by remember { mutableStateOf(LocalDate.parse(instance.day)) }
    var calendar by remember { mutableStateOf(false) }
    var month by remember { mutableStateOf(YearMonth.from(day)) }
    var file by remember { mutableStateOf<Picked?>(null) }
    var busy by remember { mutableStateOf(false) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { file = read(context, it) } }
    val wallets = m?.wallets.orEmpty()
    val account = wallets.indexOfFirst { it.id == s.walletId }
    val minor = state.minor(s.currency)
    val recorded = stringResource(R.string.subs_recorded)
    val updated = stringResource(R.string.subs_payment_updated)
    val attachFailed = stringResource(R.string.tx_attach_failed)

    val save = save@{
        val value = minor ?: return@save
        busy = true
        scope.launch {
            val amount = abs(value) * (if (s.amount < 0) -1 else 1)
            val result = writer.run {
                patch<JsonObject, PaymentResult>(
                    "/api/v1/recurring/${s.id}/payments/${instance.dueDay}",
                    buildJsonObject {
                        put("status", if (pay) "paid" else "scheduled")
                        put("amount", amount)
                        put("day", day.toString())
                    },
                )
            }
            val bill = file
            if (result != null && bill != null) {
                // A payment in the past comes back as null once paid, so find its row in the history.
                val ok = writer.run {
                    val tx = result.payment?.transactionId
                        ?: get<SubscriptionsData>("/api/app/subscriptions").subscriptions.find { it.id == s.id }?.history?.find { it.dueDay == instance.dueDay }?.id
                    tx?.let { upload("/api/app/transactions/$it/attachments", bill.name, bill.type, bill.bytes) }
                }
                if (ok == null) writer.toast(attachFailed)
            }
            busy = false
            if (result != null) {
                if (bill == null) writer.toast(if (pay) recorded else updated)
                onDismiss()
            }
        }
    }

    Sheet(
        "${s.title(m)} · ${dayText(instance.dueDay)}",
        onDismiss,
        subtitle = stringResource(
            when {
                !pay -> R.string.subs_edit_once_description
                s.isTransfer -> R.string.subs_pay_transfer_description
                else -> R.string.subs_pay_description
            },
        ),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AmountPad(
                state,
                s.currency,
                note = stringResource(R.string.app_rec_usually, money(abs(s.amount), s.currency)),
                keypad = !calendar,
            )
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip(wallets.getOrNull(account)?.name ?: "", leading = { AccountDot(c.account(account)) })
                Chip(dayText(day.toString(), "EEEdMMM"), on = calendar, onClick = { calendar = !calendar }, leading = { PhIcon("calendar-blank", size = 18.dp) })
                if (pay && m?.attachments == true && !s.isTransfer) {
                    Chip(
                        file?.name ?: stringResource(R.string.app_rec_attach_bill),
                        onClick = { pick.launch(arrayOf("image/*", "application/pdf")) },
                        leading = { PhIcon("paperclip", size = 18.dp) },
                    )
                }
            }
            if (calendar) {
                MonthGrid(month, day, today(), { day = it; calendar = false }, { month = it })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (instance.edited || instance.status == "skipped") {
                    Button(stringResource(R.string.subs_reset_regular), {
                        scope.launch {
                            val ok = writer.run { send("PATCH", "/api/v1/recurring/${s.id}/payments/${instance.dueDay}", buildJsonObject { put("status", "scheduled") }) }
                            if (ok != null) onDismiss()
                        }
                    }, Modifier.weight(1f), style = ButtonStyle.Secondary)
                }
                Button(
                    stringResource(if (pay) R.string.app_rec_record_payment else R.string.subs_save_this_payment),
                    { save() },
                    Modifier.weight(1.4f),
                    enabled = !busy && minor != null && minor > 0,
                )
            }
        }
    }
}

/**
 * Documents by month for a schedule's payments: tick months and share them as one zip through the share sheet.
 * Kosha downloads each file, zips them in the cache and hands the zip to other apps through its FileProvider.
 */
@Composable
fun DocumentsPage(s: Sub, files: List<ScheduleFile>, onBack: () -> Unit) {
    val kosha = rememberKosha()
    val context = LocalContext.current
    val toasts = LocalToasts.current
    val c = KoshaTheme.colors
    val scope = rememberCoroutineScope()
    var picked by remember { mutableStateOf(emptySet<String>()) }
    var busy by remember { mutableStateOf(false) }
    val all = picked.size == files.size
    val zipName = stringResource(R.string.tx_docs_zip_name, s.name)
    val failed = stringResource(R.string.tx_zip_failed)
    val offline = stringResource(R.string.common_offline_title)
    val sorted = files.sortedByDescending { it.dueDay ?: it.day }

    Page(
        stringResource(R.string.tx_documents),
        onBack = onBack,
        subtitle = "${s.name} · ${files.size}",
        actions = {
            KText(
                stringResource(if (all) R.string.tx_select_none else R.string.tx_select_all),
                Modifier.heightIn(min = 48.dp).clickable(role = Role.Button) { picked = if (all) emptySet() else files.map { it.id }.toSet() }.padding(horizontal = 8.dp, vertical = 14.dp),
                style = KoshaTheme.type.small,
                color = c.ink2,
            )
        },
        footer = {
            Button(
                pluralStringResource(R.plurals.app_rec_share_zip, picked.size, picked.size),
                {
                    busy = true
                    scope.launch {
                        val chosen = sorted.filter { it.id in picked }
                        runCatching {
                            val names = Schedule.archiveNames(chosen.map { (it.dueDay ?: it.day) to it.name })
                            val parts = chosen.map { kosha.api.bytes("/api/app/attachments/${it.id}") }
                            val zip = withContext(Dispatchers.IO) {
                                val dir = File(context.cacheDir, "shared").apply { mkdirs() }
                                File(dir, zipName.replace(Regex("""[\\/:*?"<>|]"""), "-") + ".zip").also { f ->
                                    ZipOutputStream(f.outputStream()).use { out ->
                                        // Photos and PDFs are compressed already, so the zip only stores them.
                                        out.setLevel(Deflater.NO_COMPRESSION)
                                        names.zip(parts).forEach { (name, bytes) ->
                                            out.putNextEntry(ZipEntry(name))
                                            out.write(bytes)
                                            out.closeEntry()
                                        }
                                    }
                                }
                            }
                            val uri = FileProvider.getUriForFile(context, context.filesAuthority(), zip)
                            val send = Intent(Intent.ACTION_SEND).setType("application/zip").putExtra(Intent.EXTRA_STREAM, uri)
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            context.startActivity(Intent.createChooser(send, zip.name))
                        }.onFailure { toasts.show(app.getkosha.design.Toast(if (it is java.io.IOException) offline else failed)) }
                        busy = false
                    }
                },
                Modifier.fillMaxWidth(),
                enabled = picked.isNotEmpty() && !busy,
                icon = "export",
            )
        },
    ) {
        Card(Modifier.fillMaxWidth(), tight = true) {
            sorted.forEachIndexed { i, f ->
                if (i > 0) Hairline()
                val on = f.id in picked
                val payment = s.history.find { it.id == f.transactionId }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .toggleable(on, role = Role.Checkbox) { picked = if (it) picked + f.id else picked - f.id }
                        .heightIn(min = KoshaTheme.dims.settingsRow)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Tick(on)
                    Column(Modifier.weight(1f)) {
                        KText(monthText(YearMonth.from(LocalDate.parse(f.dueDay ?: f.day))))
                        KText(
                            "${f.name} · ${android.text.format.Formatter.formatShortFileSize(context, f.size)}",
                            style = KoshaTheme.type.small,
                            color = c.ink3,
                            maxLines = 1,
                        )
                    }
                    if (payment != null) {
                        MoneyText(payment.amount, s.currency, sign = Sign.Always, color = c.ink2, style = KoshaTheme.type.num.copy(fontSize = KoshaTheme.type.small.fontSize))
                    }
                }
            }
        }
        KText(stringResource(R.string.app_rec_zip_hint), style = KoshaTheme.type.xs, color = c.ink3)
    }
}
