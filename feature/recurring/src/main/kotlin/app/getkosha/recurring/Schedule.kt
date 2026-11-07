@file:UseSerializers(RoundedLong::class)

package app.getkosha.recurring

import kotlinx.serialization.UseSerializers
import app.getkosha.model.RoundedLong

import app.getkosha.model.Categories
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.YearMonth

// The shapes behind Recurring. GET /api/app/subscriptions is the web's own read (finance.listSubscriptions plus
// recentIncome), so it keeps the web's snake_case rows; the public /api/v1/recurring operations are camelCase.

/** GET /api/app/subscriptions. */
@Serializable
data class SubscriptionsData(val subscriptions: List<Sub> = emptyList(), val income: List<Income> = emptyList()) {
    /** Income over the last 365 days, per account currency (recentIncome). */
    @Serializable
    data class Income(val currency: String, val total: Long)
}

/** One schedule as db.server loadSubscriptions returns it (SubscriptionView). */
@Serializable
data class Sub(
    val id: String,
    val name: String,
    @SerialName("wallet_id") val walletId: String,
    @SerialName("category_id") val categoryId: String,
    val amount: Long,
    val note: String = "",
    val every: Int = 1,
    val unit: String = "month",
    @SerialName("start_day") val startDay: String,
    @SerialName("end_day") val endDay: String? = null,
    @SerialName("auto_pay") val autoPay: Int = 0,
    val kind: String = "subscription",
    @SerialName("to_wallet_id") val toWalletId: String? = null,
    @SerialName("to_amount") val toAmount: Long? = null,
    val variable: Int = 0,
    val currency: String,
    val ended: Boolean = false,
    /** Amount normalised to one month; the server doesn't round it. */
    val monthly: Double = 0.0,
    val instances: List<Instance> = emptyList(),
    val next: Instance? = null,
    /** Paid payments, newest first. */
    val history: List<Paid> = emptyList(),
) {
    val isTransfer get() = toWalletId != null
    val isVariable get() = variable != 0
    val auto get() = autoPay != 0

    /** core.ts monthlyCost, rounded to whole minor units for display. */
    val perMonth get() = Math.round(monthly)
}

/** One upcoming (or overdue, or edited) payment of a schedule (core.ts Instance). */
@Serializable
data class Instance(
    @SerialName("due_day") val dueDay: String,
    val day: String,
    val amount: Long,
    val note: String = "",
    /** paid, skipped, overdue, due or upcoming. */
    val status: String,
    val edited: Boolean = false,
    @SerialName("tx_id") val txId: String? = null,
)

/** A paid payment: the transaction row. */
@Serializable
data class Paid(
    val id: String,
    @SerialName("due_day") val dueDay: String? = null,
    val day: String,
    val amount: Long,
    val note: String = "",
    @SerialName("wallet_id") val walletId: String = "",
    @SerialName("transfer_id") val transferId: String? = null,
)

/** GET /api/v1/recurring?includeSuggestions=true, for "Looks recurring". */
@Serializable
data class RecurringList(val suggestions: List<Suggestion> = emptyList())

@Serializable
data class Suggestion(
    val payee: String,
    val amount: Long,
    val variable: Boolean = false,
    val every: Int = 1,
    val unit: String = "month",
    val nextDay: String,
    val lastDay: String,
    val count: Int = 0,
    val categoryId: String,
    val accountId: String,
    val transactionIds: List<String> = emptyList(),
)

/** A file attached to one of a schedule's payments (attachments.server scheduleAttachments). */
@Serializable
data class ScheduleFile(
    val id: String,
    @SerialName("transaction_id") val transactionId: String,
    val name: String,
    val type: String = "",
    val size: Long = 0,
    val day: String,
    val dueDay: String? = null,
)

/** The answer of create_recurring and update_recurring_payment that the app reads. */
@Serializable
data class Created(val id: String)

@Serializable
data class PaymentResult(val payment: Payment? = null) {
    @Serializable
    data class Payment(val transactionId: String? = null)
}

/** Where a schedule is listed, as the web groups them (subscriptions.tsx groupOf). */
enum class Group { Bills, Subscriptions, Income, Transfers }

fun Sub.group(): Group = when {
    toWalletId != null -> Group.Transfers
    amount > 0 -> Group.Income
    kind == "repetitive" -> Group.Bills
    else -> Group.Subscriptions
}

/** Scheduled money going out: bills and subscriptions, never transfers or neutral categories. */
fun Sub.isCost() = amount < 0 && toWalletId == null && !Categories.isNeutral(categoryId)

/** The four kinds the form offers, and how each is stored (subscription-form.tsx). */
enum class FormKind { Bill, Subscription, Income, Transfer }

/**
 * Schedule maths ported from ../kosha/src/lib/core.ts (occurrence, occurrencesBetween, nextOccurrence). Days are
 * plain dates in the user's time zone; month steps keep the start's day, clamped to short months.
 */
object Schedule {
    /** The n-th payment day, where 0 is [start]. */
    fun occurrence(start: LocalDate, every: Int, unit: String, n: Int): LocalDate = when (unit) {
        "day" -> start.plusDays(n.toLong() * every)
        "week" -> start.plusWeeks(n.toLong() * every)
        else -> {
            val months = n.toLong() * every * (if (unit == "year") 12 else 1)
            val ym = YearMonth.from(start).plusMonths(months)
            ym.atDay(minOf(start.dayOfMonth, ym.lengthOfMonth()))
        }
    }

    /** Payment days within [from, to], never past [end]. */
    fun between(start: LocalDate, every: Int, unit: String, end: LocalDate?, from: LocalDate, to: LocalDate, limit: Int = 2000): List<LocalDate> {
        val out = mutableListOf<LocalDate>()
        for (n in 0 until limit) {
            val day = occurrence(start, every, unit, n)
            if (day > to || (end != null && day > end)) break
            if (day >= from) out += day
        }
        return out
    }

    /** The next payment on or after [from], or null once the schedule has ended. */
    fun next(start: LocalDate, every: Int, unit: String, end: LocalDate?, from: LocalDate): LocalDate? =
        between(start, every, unit, end, from, from.plusDays(366L * maxOf(1, if (unit == "year") every else 1)), 5000).firstOrNull()

    /**
     * How many payments Kosha records at once for a new schedule that starts in the past: settle() in db.server
     * books every day from the start until today when payments are recorded automatically.
     */
    fun backfill(start: LocalDate, every: Int, unit: String, end: LocalDate?, today: LocalDate): Int =
        if (start >= today) 0 else between(start, every, unit, end, start, today).size

    /** The days of [month] a schedule pays on. */
    fun inMonth(s: Sub, month: YearMonth): List<LocalDate> =
        between(LocalDate.parse(s.startDay), s.every, s.unit, s.endDay?.let(LocalDate::parse), month.atDay(1), month.atEndOfMonth())

    /** Zip entry names: "2026-10 payslip.pdf", numbered when two would clash (core.ts archiveNames). */
    fun archiveNames(files: List<Pair<String, String>>): List<String> {
        val seen = HashMap<String, Int>()
        return files.map { (day, name) ->
            val base = "${day.take(7)} ${name.replace(Regex("""[\\/:*?"<>|]"""), "-")}"
            val n = (seen[base] ?: 0) + 1
            seen[base] = n
            if (n == 1) return@map base
            val dot = base.lastIndexOf('.')
            if (dot > 8) "${base.substring(0, dot)} ($n)${base.substring(dot)}" else "$base ($n)"
        }
    }
}
