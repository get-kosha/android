@file:UseSerializers(RoundedLong::class)

package app.getkosha.settings

import kotlinx.serialization.UseSerializers
import app.getkosha.model.RoundedLong

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// The answers Settings reads, with only the fields it shows. /api/app shapes follow the web's server functions
// (src/server/app-api.server.ts); /api/v1 shapes follow src/lib/api-contract.ts.

/** GET /api/app/account (accountOverview): sign-in methods and what Delete & reset would remove. */
@Serializable
data class AccountSummary(
    val transactions: Int = 0,
    val accounts: Int = 0,
    val recurring: Int = 0,
    val categories: Int = 0,
    val rules: Int = 0,
    val hasPassword: Boolean = false,
    /** Null when the server has no Google sign-in; [Google.accountId] is the connected login, if any. */
    val google: Google? = null,
    val twoFactor: Boolean = false,
    val passkeys: Int = 0,
) {
    @Serializable
    data class Google(val accountId: String? = null)
}

/** One AI provider from GET /api/app/ai/providers. The key never comes back, only its last four characters. */
@Serializable
data class AiProvider(
    val id: String,
    val connected: Boolean = false,
    val model: String = "",
    val keyHint: String? = null,
    val baseUrl: String? = null,
    val isDefault: Boolean = false,
)

/** POST /api/app/ai/providers/{id}/test: the provider's models, or why it couldn't be reached. */
@Serializable
data class AiTest(val ok: Boolean = false, val ms: Long = 0, val models: List<String> = emptyList(), val error: String? = null)

/** GET /api/app/ai/log: this month's usage and the last 30 days of requests. */
@Serializable
data class AiUsage(val requests: Int = 0, val tokens: Long = 0, val log: List<Entry> = emptyList()) {
    @Serializable
    data class Entry(
        val id: String,
        val at: Long,
        val provider: String,
        val model: String,
        val feature: String,
        val request: String = "",
        val response: String = "",
        @SerialName("tokens_in") val tokensIn: Long = 0,
        @SerialName("tokens_out") val tokensOut: Long = 0,
        val day: String = "",
    )
}

/** GET /api/app/api-access: API keys, apps connected through OAuth, and where MCP and the API docs live. */
@Serializable
data class ApiAccess(
    val keys: List<Key> = emptyList(),
    val apps: List<App> = emptyList(),
    val mcpUrl: String = "",
    val docsUrl: String = "",
) {
    @Serializable
    data class Key(
        val id: String,
        val name: String,
        val hint: String = "",
        val scope: String = "read",
        /** Unix seconds. */
        @SerialName("created_at") val createdAt: Long = 0,
        @SerialName("last_used_at") val lastUsedAt: Long? = null,
    )

    @Serializable
    data class App(
        val clientId: String,
        val name: String,
        val host: String = "",
        val write: Boolean = false,
        /** Milliseconds. */
        val connectedAt: Long = 0,
        val lastUsedAt: Long? = null,
    )
}

/** POST /api/app/api-keys: the new key, shown this once. */
@Serializable
data class NewKey(val id: String, val key: String)

@Serializable
data class CategoryList(val categories: List<app.getkosha.model.Category>)

/** An event from list_events. */
@Serializable
data class KEvent(
    val id: String,
    val name: String,
    val startDay: String? = null,
    val endDay: String? = null,
    val transactionCount: Int = 0,
)

@Serializable
data class Events(val events: List<KEvent>)

/** A payee rule from list_payee_rules. `match` is "exact" or "contains"; `source` is "manual" or "learned". */
@Serializable
data class PayeeRule(
    val payee: String,
    val categoryId: String,
    val match: String = "contains",
    val source: String = "manual",
    val matches: Int = 0,
)

@Serializable
data class PayeeRules(val rules: List<PayeeRule>, val total: Int = 0, val nextOffset: Int? = null)
