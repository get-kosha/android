@file:UseSerializers(RoundedLong::class)

package app.getkosha.activity

import kotlinx.serialization.UseSerializers
import app.getkosha.model.RoundedLong

import app.getkosha.model.Categories
import app.getkosha.model.KoshaJson
import app.getkosha.model.Transaction
import app.getkosha.model.TransactionList
import app.getkosha.model.convert
import app.getkosha.network.ApiException
import app.getkosha.network.KoshaApi
import app.getkosha.ui.ActivitySearch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.YearMonth

/** The dates Activity lists (core.ts Period): a month, a year, every date, or chosen days. */
@Serializable
sealed interface Period {
    @Serializable @SerialName("month")
    data class Month(val month: String) : Period

    @Serializable @SerialName("year")
    data class Year(val year: Int) : Period

    @Serializable @SerialName("all")
    data object All : Period

    @Serializable @SerialName("range")
    data class Range(val from: String, val to: String) : Period

    /** The first and last day it covers, or null for every date (core.ts periodDays). */
    val days: Pair<LocalDate, LocalDate>?
        get() = when (this) {
            is Month -> YearMonth.parse(month).let { it.atDay(1) to it.atEndOfMonth() }
            is Year -> LocalDate.of(year, 1, 1) to LocalDate.of(year, 12, 31)
            is Range -> LocalDate.parse(from) to LocalDate.parse(to)
            All -> null
        }

    /** The month or year before (-1) or after (1). Chosen days and every date have no neighbours. */
    fun shift(by: Int): Period? = when (this) {
        is Month -> Month(YearMonth.parse(month).plusMonths(by.toLong()).toString())
        is Year -> Year(year + by)
        else -> null
    }
}

/**
 * What Activity shows: search_transactions filters (GET /api/v1/transactions). Activity picks its own dates:
 * unset, a search, tag or event covers every date and anything else the current month, as on the web.
 */
@Serializable
data class Filters(
    val query: String = "",
    /** "expense", "income" or "transfer". */
    val type: String? = null,
    val accountId: String? = null,
    val categoryId: String? = null,
    /** Minor units by absolute value, in each entry's own currency. */
    val minAmount: Long? = null,
    val maxAmount: Long? = null,
    val tag: String? = null,
    val eventId: String? = null,
    val corrections: Boolean = false,
    val period: Period? = null,
) {
    /** Looking for something: then no period means every date. */
    val finding get() = query.isNotBlank() || tag != null || eventId != null

    /** The dates listed: the period, or the default for what's asked. */
    fun shown(current: YearMonth): Period = period ?: if (finding) Period.All else Period.Month(current.toString())

    /** Filters set in the Filters sheet, beyond the search and the segmented type. */
    val count get() = listOfNotNull(accountId, categoryId, minAmount, maxAmount, tag, eventId).size + (if (corrections) 1 else 0)

    fun params(current: YearMonth, offset: Int = 0, limit: Int = PAGE): Map<String, Any?> = mapOf(
        "month" to (shown(current) as? Period.Month)?.month,
        // Without dates the server lists every date only when finding something, so every date else starts at 1970.
        "from" to when (val p = shown(current)) {
            is Period.Month -> null
            Period.All -> if (finding) null else "1970-01-01"
            else -> p.days?.first?.toString()
        },
        "to" to shown(current).takeIf { it !is Period.Month }?.days?.second?.toString(),
        "query" to query.trim().take(40).ifEmpty { null },
        "type" to type,
        "accountId" to accountId,
        "categoryId" to categoryId,
        "minAmount" to minAmount,
        "maxAmount" to maxAmount,
        "tag" to tag,
        "eventId" to eventId,
        "includeCorrections" to corrections.takeIf { it },
        "limit" to limit,
        "offset" to offset,
    )

    /** A cache key for this list, so each search keeps its own saved copy. */
    fun key(current: YearMonth) = "activity/$current/" + KoshaJson.encodeToString(serializer(), this).hashCode()

    companion object {
        const val PAGE = 100

        fun of(s: ActivitySearch) = Filters(
            query = s.query.orEmpty(), type = s.type, accountId = s.accountId, categoryId = s.categoryId,
            minAmount = s.minAmount, maxAmount = s.maxAmount, tag = s.tag, eventId = s.eventId,
            period = s.month?.let { Period.Month(it) },
        )
    }
}

/** Entries grouped by day, newest first, as the list shows them (transactions.tsx). */
fun List<Transaction>.byDay(): List<Pair<String, List<Transaction>>> {
    val groups = mutableListOf<Pair<String, MutableList<Transaction>>>()
    for (t in this) {
        if (groups.lastOrNull()?.first != t.day) groups += t.day to mutableListOf()
        groups.last().second += t
    }
    return groups
}

/** A day's total in the display currency, leaving out neutral categories (transactions.tsx dayTotal). */
fun List<Transaction>.dayTotal(to: String, rates: Map<String, Double>): Long =
    filterNot { Categories.isNeutral(it.categoryId) }.sumOf { convert(it.amount, it.currency, to, rates) }

/** Out, in and moved for the whole filtered list, from the server's per-currency totals, converted. */
data class Summary(val out: Long, val inn: Long, val moved: Long) {
    val net get() = inn + out
}

fun List<TransactionList.Totals>.summary(to: String, rates: Map<String, Double>) = Summary(
    out = sumOf { convert(it.spent, it.currency, to, rates) },
    inn = sumOf { convert(it.received, it.currency, to, rates) },
    moved = sumOf { convert(it.transferred, it.currency, to, rates) },
)

/** A transfer's sent amount: the outgoing half plus its fee (transactions.tsx Row). */
val Transaction.sent: Long get() = Math.abs(amount) + if (amount < 0) transfer?.fee ?: 0 else 0

/** An entry dated outside its event's days was put in the event by hand. */
fun Transaction.outsideEvent(start: String?, end: String?) = (start != null && day < start) || (end != null && day > end)

/** One transaction by id (GET /api/app/transactions/{id}). */
suspend fun KoshaApi.findTransaction(id: String): Transaction = get("/api/app/transactions/$id")

/** Rows seen in lists this run, so a detail opens at once. */
internal object Seen {
    private val rows = mutableMapOf<String, Transaction>()

    fun remember(list: List<Transaction>) = synchronized(rows) { list.forEach { rows[it.id] = it } }

    operator fun get(id: String) = synchronized(rows) { rows[id] }
}
