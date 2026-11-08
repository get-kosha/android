@file:UseSerializers(RoundedLong::class)

package app.getkosha.model

import kotlinx.serialization.UseSerializers

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

// The shapes Kosha's API speaks. Public operations (/api/v1, camelCase) follow ../kosha/src/lib/api-contract.ts;
// /api/app endpoints that mirror the web's own server functions keep the web's shapes (snake_case rows).
// Money is integer minor units, ×100 for every currency; the sign is the direction. Days are "YYYY-MM-DD" and
// months "YYYY-MM" in the user's time zone.

/** One JSON configuration for every request and cache file: unknown fields are ignored, so a newer server is fine. */
val KoshaJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
    coerceInputValues = true
}

/** An error as the API sends it: a message written for people, and invalid fields by path ("transactions.3.accountId"). */
@Serializable
data class ApiError(val code: String = "error", val message: String = "", val issues: List<Issue> = emptyList()) {
    @Serializable
    data class Issue(val path: String, val message: String)
}

@Serializable
data class ErrorBody(val error: ApiError)

// ---------- public operations ----------

@Serializable
data class Account(
    val id: String,
    val name: String,
    val type: AccountType,
    val currency: String,
    val balance: Long,
    val worth: Long,
    val creditLimit: Long? = null,
    val gain: Long? = null,
    val gainDay: String? = null,
    val archived: Boolean = false,
    val transactionCount: Int = 0,
    val reconciledTo: String? = null,
)

@Serializable
enum class AccountType {
    @SerialName("bank") Bank,
    @SerialName("cash") Cash,
    @SerialName("credit") Credit,
    @SerialName("investment") Investment,
}

@Serializable
data class Category(
    val id: String,
    val name: String,
    val icon: String? = null,
    val type: String = "expense",
    val parentId: String? = null,
    val archived: Boolean = false,
    val transactionCount: Int = 0,
    val recurringCount: Int = 0,
    val ruleCount: Int = 0,
) {
    val income get() = type == "income"
}

@Serializable
data class Transaction(
    val id: String,
    val day: String,
    val time: String = "12:00",
    val payee: String,
    val amount: Long,
    val currency: String,
    val accountId: String,
    val accountName: String = "",
    val categoryId: String,
    val categoryName: String = "",
    val subcategoryId: String? = null,
    val note: String = "",
    val tags: List<String> = emptyList(),
    val eventId: String? = null,
    val status: String = "paid",
    val recurringId: String? = null,
    val dueDay: String? = null,
    val transfer: TransferLink? = null,
    val reconciled: Boolean = false,
    val bankRef: String? = null,
    val attachmentCount: Int = 0,
) {
    @Serializable
    data class TransferLink(
        val id: String,
        val counterAccountId: String? = null,
        val counterAmount: Long? = null,
        val fee: Long = 0,
        /** Wise, Revolut, your bank… "" if not given. */
        val provider: String = "",
        /** The day's mid-market rate between the two currencies, when they differ and it's known. */
        val marketRate: Double? = null,
    )
}

@Serializable
data class TransactionList(
    val transactions: List<Transaction>,
    val total: Int = 0,
    val nextOffset: Int? = null,
    val totals: List<Totals> = emptyList(),
    /** Only with a tag or eventId: paid spending and income per category and currency, net, most spent first. */
    val byCategory: List<CategoryTotal> = emptyList(),
) {
    @Serializable
    data class CategoryTotal(val categoryId: String, val categoryName: String = "", val currency: String, val amount: Long, val count: Int = 0)

    /** Money out, in and moved per currency for the filtered list. */
    @Serializable
    data class Totals(val currency: String, val spent: Long, val received: Long, val transferred: Long)
}

/** A new entry for create_transactions. [id] is chosen here so a retry is saved once. */
@Serializable
data class NewTransaction(
    val id: String,
    val accountId: String,
    val payee: String,
    val amount: Long,
    val categoryId: String? = null,
    val subcategoryId: String? = null,
    val day: String? = null,
    val time: String? = null,
    val note: String = "",
    val tags: List<String> = emptyList(),
    val eventId: String? = null,
)

@Serializable
data class Transactions(val transactions: List<Transaction>)

@Serializable
data class Payee(
    val payee: String,
    val count: Int,
    val categoryId: String? = null,
    val accountId: String? = null,
    val currency: String? = null,
    val averageAmount: Long? = null,
)

@Serializable
data class Payees(val payees: List<Payee>)

@Serializable
data class Settings(
    val user: User,
    val primaryCurrency: String,
    val timeZone: String,
    val currencyOrder: List<String> = emptyList(),
    val preferences: Map<String, JsonElement?> = emptyMap(),
    val exchangeRates: Rates = Rates(),
) {
    @Serializable
    data class User(val name: String, val email: String)

    @Serializable
    data class Rates(val updatedAt: Long = 0, val perUsd: Map<String, Double> = emptyMap())
}

@Serializable
data class Recurring(
    val id: String,
    val name: String,
    val accountId: String,
    val categoryId: String,
    val amount: Long,
    val currency: String,
    val note: String = "",
    val every: Int = 1,
    val unit: String = "month",
    val startDay: String,
    val endDay: String? = null,
    val autoPay: Boolean = true,
    val kind: String = "subscription",
    val toAccountId: String? = null,
    val toAmount: Long? = null,
    val variable: Boolean = false,
    val ended: Boolean = false,
    val monthlyCost: Long = 0,
    val next: Payment? = null,
    val payments: List<Payment> = emptyList(),
) {
    @Serializable
    data class Payment(
        val dueDay: String,
        val day: String,
        val amount: Long,
        val note: String = "",
        val status: String,
        val edited: Boolean = false,
        val transactionId: String? = null,
        /** The account it's paid from: the schedule's, unless changed for this payment. */
        val accountId: String = "",
    )
}

// ---------- /api/app ----------

/** What every screen needs, from GET /api/app/meta (the web's getMeta). */
@Serializable
data class Meta(
    val wallets: List<Wallet> = emptyList(),
    val categories: List<MetaCategory> = emptyList(),
    val rules: List<Rule> = emptyList(),
    val titles: List<String> = emptyList(),
    val events: List<Event> = emptyList(),
    val tags: List<String> = emptyList(),
    /** Units per 1 USD. */
    val rates: Map<String, Double> = emptyMap(),
    val primary: String = "eur",
    val order: List<String> = emptyList(),
    val tz: String = "UTC",
    val prefs: Map<String, JsonElement?> = emptyMap(),
    val ratesUpdated: Long = 0,
    /** Which AI features are live. Off means no trace in the UI. */
    val ai: AiFlags = AiFlags(),
    val aiModel: AiModel? = null,
    val user: User? = null,
    /** The demo account: read-only. */
    val demo: Boolean = false,
    val avatars: Boolean = false,
    val attachments: Boolean = false,
    val version: Int = 0,
) {
    @Serializable
    data class User(val id: String, val name: String, val email: String, val image: String? = null)

    @Serializable
    data class AiModel(val provider: String, val model: String)

    /** A preference as text, or its default. */
    fun pref(key: String, default: String): String =
        (prefs[key] as? kotlinx.serialization.json.JsonPrimitive)?.takeIf { it.isString }?.content ?: default
}

@Serializable
data class AiFlags(
    val categorise: Boolean = false,
    @SerialName("quick_add") val quickAdd: Boolean = false,
    val receipts: Boolean = false,
    val recap: Boolean = false,
    val ask: Boolean = false,
    val statements: Boolean = false,
) {
    val any get() = categorise || quickAdd || receipts || recap || ask || statements
}

/** An account as the web's own reads give it (snake_case). */
@Serializable
data class Wallet(
    val id: String,
    val name: String,
    val currency: String,
    val archived: Int = 0,
    val balance: Long = 0,
    val type: AccountType = AccountType.Bank,
    @SerialName("credit_limit") val creditLimit: Long? = null,
    val gain: Long? = null,
    @SerialName("gain_day") val gainDay: String? = null,
    @SerialName("tx_count") val txCount: Int = 0,
    @SerialName("entry_count") val entryCount: Int = 0,
    @SerialName("reconciled_day") val reconciledDay: String? = null,
) {
    val isArchived get() = archived != 0
}

@Serializable
data class MetaCategory(
    val id: String,
    val name: String,
    val icon: String? = null,
    val income: Int = 0,
    @SerialName("parent_id") val parentId: String? = null,
    val uses: Int = 0,
    val archived: Int = 0,
) {
    val isIncome get() = income != 0
    val isArchived get() = archived != 0
}

@Serializable
data class Rule(val title: String, @SerialName("category_id") val categoryId: String, val exact: Int = 0)

@Serializable
data class Event(
    val id: String,
    val name: String,
    @SerialName("start_day") val startDay: String? = null,
    @SerialName("end_day") val endDay: String? = null,
    @SerialName("tx_count") val txCount: Int = 0,
)

/** Days of a month with entries (GET /api/app/days). */
@Serializable
data class Days(val days: List<String> = emptyList())

/** An untyped JSON object, for screens that read a few fields of a larger answer. */
typealias JsonMap = JsonObject
