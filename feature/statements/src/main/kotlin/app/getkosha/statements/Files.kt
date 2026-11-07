package app.getkosha.statements

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Base64
import androidx.core.content.IntentCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.Charset

/** The kinds of file the picker offers. A PDF only when AI reads statements. */
fun statementTypes(pdf: Boolean): Array<String> = buildList {
    // Banks' CSV and OFX files come with all sorts of types, so text and unknown binaries are allowed too.
    addAll(listOf("text/csv", "text/comma-separated-values", "application/x-ofx", "application/vnd.intu.qfx", "text/plain",
        "application/octet-stream", "application/vnd.ms-excel"))
    if (pdf) add("application/pdf")
}.toTypedArray()

/** A file the person chose or shared, read for POST /import/read. */
class Picked(val name: String, val pdf: Boolean, val content: String)

/** The file's name as the picker or the sharing app gives it. */
fun fileName(context: Context, uri: Uri): String = runCatching {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0) else null
    }
}.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/') ?: "statement"

/** Whether a file is a PDF, by its type, name or first bytes. */
fun isPdf(context: Context, uri: Uri, name: String): Boolean =
    context.contentResolver.getType(uri) == "application/pdf" || name.endsWith(".pdf", ignoreCase = true)

/**
 * Reads a file: text for CSV, OFX and QFX (UTF-8, falling back to Windows-1252 for older bank exports, as the web's
 * fileText does), base64 for a PDF, which the server hands to the AI model.
 */
suspend fun readFile(context: Context, uri: Uri): Picked = withContext(Dispatchers.IO) {
    val name = fileName(context, uri)
    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("unreadable")
    val pdf = isPdf(context, uri, name) || (bytes.size > 4 && String(bytes, 0, 4, Charsets.ISO_8859_1) == "%PDF")
    val content = if (pdf) Base64.encodeToString(bytes, Base64.NO_WRAP) else text(bytes)
    Picked(name, pdf, content)
}

private fun text(bytes: ByteArray): String = runCatching {
    Charsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(bytes)).toString()
}.getOrElse { String(bytes, Charset.forName("windows-1252")) }

/**
 * Statements shared to Kosha from another app, waiting for Import to take them. The share activity puts them here
 * and opens the app; Import takes them when it opens, or at once when it is already open.
 */
object ImportInbox {
    private val waiting = MutableStateFlow<List<Uri>>(emptyList())
    val files: StateFlow<List<Uri>> = waiting.asStateFlow()

    fun put(uris: List<Uri>) {
        waiting.value = waiting.value + uris
    }

    fun take(): List<Uri> = waiting.value.also { waiting.value = emptyList() }
}

/**
 * The share target for statements (CSV, OFX, QFX, PDF): it keeps the shared files in [ImportInbox] and opens the
 * app on Import. The files go along on the intent too, so the app keeps its permission to read them after this
 * activity finishes.
 */
class ShareStatementsActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uris = when (intent.action) {
            Intent.ACTION_SEND -> listOfNotNull(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java))
            Intent.ACTION_SEND_MULTIPLE -> IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
            else -> emptyList()
        }
        if (uris.isNotEmpty()) {
            ImportInbox.put(uris)
            val open = Intent().setClassName(packageName, "app.getkosha.MainActivity")
                .putExtra(ROUTE_EXTRA, ROUTE_IMPORT)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            open.clipData = ClipData.newRawUri(null, uris[0]).apply { uris.drop(1).forEach { addItem(ClipData.Item(it)) } }
            startActivity(open)
        }
        finish()
    }

    companion object {
        /** The extra MainActivity reads to open a screen, and its value for Import. */
        const val ROUTE_EXTRA = "kosha.route"
        const val ROUTE_IMPORT = "import"
    }
}
