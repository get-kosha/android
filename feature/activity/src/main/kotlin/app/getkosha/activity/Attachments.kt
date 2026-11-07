@file:UseSerializers(RoundedLong::class)

package app.getkosha.activity

import kotlinx.serialization.UseSerializers
import app.getkosha.model.RoundedLong

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import app.getkosha.ui.filesAuthority
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.getkosha.design.Button
import app.getkosha.design.ButtonStyle
import app.getkosha.design.Card
import app.getkosha.design.Hairline
import app.getkosha.design.IconButton
import app.getkosha.design.IconTile
import app.getkosha.design.KText
import app.getkosha.design.KoshaTheme
import app.getkosha.design.PhIcon
import app.getkosha.design.R
import app.getkosha.design.Sheet
import app.getkosha.design.Toast
import app.getkosha.model.Transaction
import app.getkosha.network.ApiException
import app.getkosha.ui.LocalToasts
import app.getkosha.ui.rememberKosha
import app.getkosha.ui.write
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.io.File
import java.io.IOException

/** A file attached to a transaction (attachments.server.ts listAttachments). */
@Serializable
internal data class Attachment(val id: String, val name: String, val type: String = "", val size: Long = 0, @SerialName("transaction_id") val transactionId: String = "")

/** Up to 10 MB each, as the server takes them. */
private const val LIMIT = 10L * 1024 * 1024


/** "240 KB", "1.2 MB". */
internal fun fileSize(bytes: Long, context: Context): String = android.text.format.Formatter.formatShortFileSize(context, bytes)

/** Attachments: each file with its size, opened in another app on tap, removed after a check; Attach another. */
@Composable
internal fun Attachments(t: Transaction, writable: Boolean, onAttach: () -> Unit) {
    val kosha = rememberKosha()
    val toasts = LocalToasts.current
    val context = LocalContext.current
    val res = LocalResources.current
    val c = KoshaTheme.colors
    val scope = rememberCoroutineScope()
    val version by kosha.version.collectAsStateWithLifecycle()
    val files = remember(t.id, version) {
        kosha.load("attachments/${t.id}") { get<List<Attachment>>("/api/app/transactions/${t.id}/attachments") }
    }.collectAsStateWithLifecycle(null).value?.value.orEmpty()
    var removing by remember { mutableStateOf<Attachment?>(null) }
    val offline = stringResource(R.string.common_offline_title)
    if (files.isEmpty() && !writable) return
    Card(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                KText(stringResource(R.string.tx_attachments), Modifier.weight(1f), style = KoshaTheme.type.small, color = c.ink3)
                if (writable) {
                    Row(
                        Modifier.heightIn(min = 44.dp).clickable(role = Role.Button, onClick = onAttach).padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        PhIcon("paperclip", size = 16.dp)
                        KText(stringResource(if (files.isEmpty()) R.string.tx_attach_receipt else R.string.tx_attach_another), style = KoshaTheme.type.small, color = c.ink2)
                    }
                }
            }
            files.forEach { a ->
                Row(
                    Modifier.fillMaxWidth().clickable(role = Role.Button) {
                        scope.launch {
                            try {
                                open(context, a, kosha.api.bytes("/api/app/attachments/${a.id}"))
                            } catch (e: IOException) {
                                toasts.show(Toast(res.getString(R.string.tx_fetch_failed, a.name)))
                            } catch (e: ApiException) {
                                toasts.show(Toast(e.message ?: ""))
                            }
                        }
                    },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        Modifier.size(56.dp, 72.dp).clip(RoundedCornerShape(8.dp)).background(c.soft).border(1.dp, c.line, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center,
                    ) { PhIcon(if (a.type == "application/pdf") "file-pdf" else "file-image", size = 22.dp, tint = c.ink3) }
                    Column(Modifier.weight(1f)) {
                        KText(a.name, maxLines = 1)
                        KText(fileSize(a.size, context), style = KoshaTheme.type.num.copy(fontSize = KoshaTheme.type.xs.fontSize), color = c.ink3)
                    }
                    if (writable) IconButton("trash", stringResource(R.string.tx_remove_file, a.name), { removing = a })
                }
            }
        }
    }
    removing?.let { a ->
        Sheet(stringResource(R.string.tx_remove_file_title, a.name), { removing = null }) {
            KText(stringResource(R.string.tx_remove_file_body), style = KoshaTheme.type.small, color = c.ink2)
            Spacer(Modifier.height(14.dp))
            Button(stringResource(R.string.common_remove), {
                removing = null
                scope.launch {
                    if (kosha.write(toasts, offline) { kosha.api.send("DELETE", "/api/app/attachments/${a.id}") } != null) {
                        toasts.show(Toast(res.getString(R.string.tx_removed)))
                    }
                }
            }, Modifier.fillMaxWidth(), style = ButtonStyle.Danger)
            Spacer(Modifier.height(10.dp))
            Button(stringResource(R.string.common_cancel), { removing = null }, Modifier.fillMaxWidth(), style = ButtonStyle.Secondary)
        }
    }
}

/** Saves the file where another app can read it, then opens it there. */
private suspend fun open(context: Context, a: Attachment, bytes: ByteArray) {
    val file = withContext(Dispatchers.IO) {
        File(context.cacheDir, "attachments").apply { mkdirs() }.resolve(a.id + "-" + a.name.replace(Regex("[^A-Za-z0-9._-]"), "_")).apply { writeBytes(bytes) }
    }
    val uri = FileProvider.getUriForFile(context, context.filesAuthority(), file)
    val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, a.type.ifEmpty { "*/*" }).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    try {
        context.startActivity(Intent.createChooser(intent, a.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
    }
}

/**
 * Attach receipt or document: take a photo, choose from photos, or choose a file (PDF or image). "Up to 10 MB
 * each." A row shows the file while it uploads.
 */
@Composable
internal fun AttachSheet(transactionId: String, onDismiss: () -> Unit) {
    val kosha = rememberKosha()
    val toasts = LocalToasts.current
    val context = LocalContext.current
    val res = LocalResources.current
    val c = KoshaTheme.colors
    val scope = rememberCoroutineScope()
    var uploading by remember { mutableStateOf<Pair<String, Long>?>(null) }
    var attached by remember { mutableIntStateOf(0) }
    val offline = stringResource(R.string.common_offline_title)
    val upload = { uri: Uri ->
        scope.launch {
            val (name, size) = describe(context, uri)
            if (size > LIMIT) {
                toasts.show(Toast(res.getString(R.string.tx_file_too_big, name)))
                return@launch
            }
            uploading = name to size
            val type = context.contentResolver.getType(uri) ?: "image/jpeg"
            val bytes = withContext(Dispatchers.IO) { runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull() }
            val done = bytes != null && kosha.write(toasts, offline) { kosha.api.upload("/api/app/transactions/$transactionId/attachments", name, type, bytes) } != null
            uploading = null
            if (done) {
                attached++
                toasts.show(Toast(res.getQuantityString(R.plurals.tx_attached, attached, attached)))
                onDismiss()
            } else if (bytes == null) {
                toasts.show(Toast(res.getString(R.string.tx_fetch_failed, name)))
            }
        }
    }
    var photo by remember { mutableStateOf<Uri?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok) photo?.let { upload(it) } }
    val takePhoto = {
        val file = File(context.cacheDir, "camera").apply { mkdirs() }.resolve("photo-${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(context, context.filesAuthority(), file)
        photo = uri
        camera.launch(uri)
    }
    // When the app declares the camera permission (for receipt scanning), the camera intent needs it granted too.
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) takePhoto() }
    val photos = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { it?.let(upload) }
    val documents = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(upload) }
    Sheet(stringResource(R.string.tx_attach_receipt), onDismiss) {
        Card(Modifier.fillMaxWidth(), tight = true) {
            Choice("camera", stringResource(R.string.app_act_take_photo)) {
                val declared = runCatching {
                    context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS).requestedPermissions?.contains(Manifest.permission.CAMERA) == true
                }.getOrDefault(false)
                if (declared && ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    permission.launch(Manifest.permission.CAMERA)
                } else takePhoto()
            }
            Hairline()
            Choice("images", stringResource(R.string.app_act_choose_photos)) {
                photos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
            Hairline()
            Choice("file-pdf", stringResource(R.string.server_choose_file), stringResource(R.string.app_act_file_hint)) {
                documents.launch(arrayOf("application/pdf", "image/*"))
            }
        }
        KText(stringResource(R.string.app_act_up_to), Modifier.padding(start = 4.dp, end = 4.dp, top = 12.dp, bottom = 4.dp), style = KoshaTheme.type.xs, color = c.ink3)
        uploading?.let { (name, size) ->
            val spin by rememberInfiniteTransition(label = "upload").animateFloat(0f, 360f, infiniteRepeatable(tween(900), RepeatMode.Restart), label = "angle")
            Card(Modifier.fillMaxWidth().padding(top = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    KText(name, Modifier.weight(1f), style = KoshaTheme.type.small, maxLines = 1)
                    KText(fileSize(size, context), style = KoshaTheme.type.xs, color = c.ink3)
                    PhIcon("spinner-gap", Modifier.rotate(spin), size = 14.dp, tint = c.brand)
                    KText(stringResource(R.string.tx_attaching), style = KoshaTheme.type.xs, color = c.ink2)
                }
            }
        }
    }
}

@Composable
private fun Choice(icon: String, title: String, sub: String? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).heightIn(min = KoshaTheme.dims.settingsRow).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconTile(icon)
        Column(Modifier.weight(1f)) {
            KText(title, style = KoshaTheme.type.head)
            if (sub != null) KText(sub, style = KoshaTheme.type.small, color = KoshaTheme.colors.ink3)
        }
    }
}

/** A picked file's name and size. */
private fun describe(context: Context, uri: Uri): Pair<String, Long> {
    var name = uri.lastPathSegment ?: "file"
    var size = 0L
    runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cur ->
            if (cur.moveToFirst()) {
                cur.getString(0)?.let { name = it }
                if (!cur.isNull(1)) size = cur.getLong(1)
            }
        }
    }
    if (size == 0L) size = runCatching { context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: 0L }.getOrDefault(0L)
    return name to size
}
