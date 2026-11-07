@file:UseSerializers(RoundedLong::class)

package app.getkosha.statements

import kotlinx.serialization.UseSerializers
import app.getkosha.model.RoundedLong

import app.getkosha.model.AccountType
import app.getkosha.model.KoshaJson
import app.getkosha.network.KoshaApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.serializer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

// The shapes of /api/app/reconcile and /api/app/import (src/server/statements.server.ts, ai-tasks.server.ts and
// app-api.server.ts in the web repo), with only the fields these screens read. Rows keep the web's snake_case.

/** A CSV's columns (statement.ts Layout). Nullable columns are sent as null, never left out: the server needs them. */
@Serializable
data class Layout(
    val header: Int,
    val date: Int,
    val dateFormat: String,
    val description: List<Int>,
    val amount: Int?,
    val debit: Int?,
    val credit: Int?,
    val invert: Boolean,
    val balance: Int?,
)

/** JSON that keeps null fields, for the bodies whose schema requires them (a layout, finishing an import). */
internal val ExactJson = Json(KoshaJson) { explicitNulls = true }

internal fun Layout.json(): JsonElement = ExactJson.encodeToJsonElement(this)

/** POSTs a body as it is, for bodies built with [ExactJson]. */
internal suspend inline fun <reified T> KoshaApi.postExact(path: String, body: JsonElement): T =
    decode(serializer(), call("POST", path, emptyMap(), body.toString().toRequestBody("application/json".toMediaType())))

// ---------- reconcile ----------

/** GET /api/app/reconcile?walletId= (reconcileData). */
@Serializable
data class ReconcileData(
    val account: Acc,
    val history: List<History> = emptyList(),
    /** The balance the reconciled entries add up to. */
    val reconciled: Long = 0,
    /** Entries not yet reconciled: paid, or due within a week. */
    val open: List<Row> = emptyList(),
    /** Reconciled entries of the last two months, so a statement again finds them. */
    val done: List<Row> = emptyList(),
    /** CSV layouts remembered by their header, so a statement from a known bank reads straight away. */
    val layouts: Map<String, JsonElement> = emptyMap(),
    val aliases: Map<String, String> = emptyMap(),
) {
    @Serializable
    data class Acc(
        val id: String,
        val name: String,
        val currency: String,
        val type: AccountType = AccountType.Bank,
        @SerialName("credit_limit") val creditLimit: Long? = null,
        val balance: Long = 0,
    ) {
        val credit get() = type == AccountType.Credit
    }

    /** A past reconciliation. `printed` is the balance as the statement showed it (owed, for a card). */
    @Serializable
    data class History(
        val id: String,
        @SerialName("statement_day") val statementDay: String,
        val balance: Long = 0,
        val entries: Int = 0,
        val adjustment: Long = 0,
        val printed: Long = 0,
    )

    @Serializable
    data class Row(
        val id: String,
        val day: String,
        val amount: Long,
        val name: String = "",
        val note: String = "",
        @SerialName("category_id") val categoryId: String = "",
        val paid: Int = 1,
        @SerialName("transfer_id") val transferId: String? = null,
        @SerialName("counter_wallet_id") val counterWalletId: String? = null,
        @SerialName("bank_ref") val bankRef: String? = null,
    )
}

@Serializable
data class FinishBody(val walletId: String, val statementDay: String, val balance: Long, val ids: List<String>, val adjust: Boolean)

@Serializable
data class Finished(val id: String, val adjustment: Long = 0)

@Serializable
data class IdBody(val id: String)

@Serializable
data class ExplainBody(
    val statementDay: String,
    val diff: Long,
    val currency: String,
    val ticked: List<ExplainItem>,
    val unticked: List<ExplainItem>,
    val missing: List<ExplainLine>,
    val close: List<ExplainClose>,
)

@Serializable
data class ExplainItem(val id: String, val day: String, val amount: Long, val name: String)

@Serializable
data class ExplainLine(val day: String, val amount: Long, val text: String)

@Serializable
data class ExplainClose(val id: String, val statementAmount: Long)

/** What the AI model makes of a difference. */
@Serializable
data class Explained(val summary: String = "", val suggestions: List<Suggestion> = emptyList()) {
    @Serializable
    data class Suggestion(val text: String, val ids: List<String> = emptyList())
}

// ---------- reading a statement ----------

/** POST /api/app/import/read: text for CSV, OFX and QFX, base64 for a PDF. [layout] is a [Layout.json]. */
@Serializable
data class ReadBody(val name: String, val content: String, val format: String? = null, val card: Boolean = false, val layout: JsonElement? = null)

/** What /import/read gives: lines for every format, plus the CSV's rows and columns, or the OFX's or PDF's balances. */
@Serializable
data class Read(
    val format: String,
    val lines: List<Line> = emptyList(),
    val withdrawn: List<String> = emptyList(),
    // CSV
    val rows: List<List<String>> = emptyList(),
    val signature: String? = null,
    val layout: Layout? = null,
    val confident: Boolean = true,
    val accountHint: String? = null,
    // OFX and PDF
    val closing: Long? = null,
    val closingDay: String? = null,
    val currency: String? = null,
    val account: Account? = null,
    // PDF
    val opening: Long? = null,
    val off: Long? = null,
    val to: String? = null,
) {
    @Serializable
    data class Account(val id: String? = null, val name: String? = null, val type: String = "bank", val last4: String? = null)
}

/** The roles the AI model gave a CSV's columns (POST /import/layout). */
@Serializable
data class Roles(
    val header: Int,
    val date: Int,
    val description: List<Int> = emptyList(),
    val amount: Int? = null,
    val debit: Int? = null,
    val credit: Int? = null,
    val balance: Int? = null,
)

@Serializable
data class RowsBody(val rows: List<List<String>>)

@Serializable
data class TidyBody(val items: List<TidyItem>)

@Serializable
data class TidyItem(val key: String, val text: String, val amount: Long)

/** A payee name (and category) the AI model gave some bank text. */
@Serializable
data class Tidy(val payee: String, val categoryId: String? = null)

// ---------- import ----------

/** GET /import/memory: CSV layouts remembered by their header, and payees remembered for bank text. */
@Serializable
data class Memory(val layouts: Map<String, JsonElement> = emptyMap(), val aliases: Map<String, String> = emptyMap())

@Serializable
data class KnownEntry(val id: String, val day: String, val amount: Long, val name: String = "", val ref: String? = null)

/** One line to save with POST /import/lines: a category, or for a transfer the other account. */
@Serializable
data class NewLine(
    val day: String,
    val amount: Long,
    val name: String,
    val note: String = "",
    val categoryId: String? = null,
    val transferWith: String? = null,
    val ref: String,
)

@Serializable
data class Alias(val key: String, val payee: String)

@Serializable
data class LinesBody(val walletId: String, val layouts: List<JsonElement>, val aliases: List<Alias>, val lines: List<NewLine>)

@Serializable
data class PairBody(val pairs: List<TransferPair>)

@Serializable
data class TransferPair(
    val fromWalletId: String,
    val toWalletId: String,
    val sent: Long,
    val received: Long,
    val day: String,
    val refFrom: String,
    val refTo: String,
)

@Serializable
data class FinishImport(val walletId: String, val firstDay: String, val closing: Closing?, val keep: Long?) {
    @Serializable
    data class Closing(val day: String, val balance: Long)
}

@Serializable
data class ImportFinished(val opening: Long = 0, val reconciled: Boolean = false)

// ---------- public operations ----------

/** create_transactions with the statement line's bank id, and an id chosen here so a retry saves it once. */
@Serializable
data class BankEntry(
    val id: String,
    val accountId: String,
    val payee: String,
    val amount: Long,
    val categoryId: String,
    val day: String,
    val bankRef: String,
)

@Serializable
data class BankEntries(val transactions: List<BankEntry>)

@Serializable
data class AmountChange(val id: String, val amount: Long)

@Serializable
data class AmountChanges(val transactions: List<AmountChange>)

@Serializable
data class NewAccount(val name: String, val type: String, val currency: String, val creditLimit: Long? = null)

@Serializable
data class Created(val id: String)

/** list_recurring with includeSuggestions: payees paid on a steady rhythm that aren't set up yet. */
@Serializable
data class RecurringSuggestions(val suggestions: List<Suggestion> = emptyList()) {
    @Serializable
    data class Suggestion(val payee: String)
}
