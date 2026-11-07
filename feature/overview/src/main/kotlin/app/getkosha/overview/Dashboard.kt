package app.getkosha.overview

import app.getkosha.model.AccountType
import app.getkosha.model.Wallet
import app.getkosha.model.convert
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.roundToLong

// GET /api/app/dashboard?month=&currency=: the web's own Overview read (finance.server.ts dashboard()), already
// converted to the display currency. The phone only picks and compares its figures, with the formulas from
// ../kosha/src/routes/_app/index.tsx cited on each function below; it never adds up rows itself.

@Serializable
data class Dashboard(
    val primary: String,
    val month: String,
    val current: Flows = Flows(),
    val previous: Flows = Flows(),
    val series: List<SeriesPoint> = emptyList(),
    val categories: List<DashCategory> = emptyList(),
    val pace: Pace = Pace(),
    val income: Income? = null,
    val upcoming: List<Upcoming> = emptyList(),
) {
    @Serializable
    data class Flows(val income: Long = 0, val expense: Long = 0, val net: Long = 0)

    @Serializable
    data class SeriesPoint(val month: String, val income: Long = 0, val expense: Long = 0, val netWorth: Long = 0, val gains: Long = 0)

    /** Spending in a category this month; [usualToDate] is the average by the same day over the 6 months before. */
    @Serializable
    data class DashCategory(val id: String, val total: Long, val count: Int = 0, val fixed: Boolean = false, val usualToDate: Double? = null)

    /**
     * Cumulative spending by day: [current] up to today, [usual] the average month (null without history).
     * The usual curve is an average, so the server sends fractions of a cent; they are rounded for display only.
     */
    @Serializable
    data class Pace(val daysInMonth: Int = 30, val today: Int? = null, val current: List<Long> = emptyList(), val usual: List<Double>? = null)

    @Serializable
    data class Income(val name: String, val day: String, val amount: Long)

    /** A payment due in the next 30 days: a recurring one (key "id|dueDay") or a one-off upcoming entry. */
    @Serializable
    data class Upcoming(
        val key: String,
        val name: String,
        val day: String,
        val amount: Long,
        val currency: String,
        val kind: String,
        @SerialName("subscription_id") val subscriptionId: String? = null,
        @SerialName("category_id") val categoryId: String = "",
        @SerialName("to_wallet_id") val toWalletId: String? = null,
    ) {
        val dueDay get() = key.substringAfter('|', "")
    }
}

/** Net worth from get_net_worth. */
@Serializable
data class NetWorth(val currency: String = "", val total: Long = 0)

/** GET /api/app/recap: the recap Overview shows, if any (ai-tasks.server.ts getRecap). */
@Serializable
data class RecapView(val month: String, val auto: Boolean = false, val recap: Recap? = null)

/** A month's recap, from the recap read or recap_month. */
@Serializable
data class Recap(
    val month: String? = null,
    val headline: String = "",
    val summary: String = "",
    val points: List<Point> = emptyList(),
    val writtenDay: String = "",
) {
    @Serializable
    data class Point(val text: String, val tone: String = "neutral")
}

private fun Double.cents() = roundToLong()

/** The last day of the dashboard's month, "YYYY-MM-DD". */
fun Dashboard.monthEnd() = "$month-${pace.daysInMonth.toString().padStart(2, '0')}"

/** The day the figures run to: today in this month, the last day in a past one. */
fun Dashboard.dayN() = pace.today ?: pace.daysInMonth

/** Usual spending by [dayN] and over the whole month (PaceCard: usualBy, usualMonth). */
fun Dashboard.usualBy(): Long? = pace.usual?.getOrNull(dayN() - 1)?.cents()
fun Dashboard.usualMonth(): Long? = pace.usual?.getOrNull(pace.daysInMonth - 1)?.cents()

/** PaceCard: projected = spent + max(0, usual whole month − usual by now), only while the month is live. */
fun Dashboard.projected(): Long? {
    val by = usualBy() ?: return null
    val whole = usualMonth() ?: return null
    if (pace.today == null) return null
    return current.expense + maxOf(0, whole - by)
}

/** PaceCard: how far under (positive) or over (negative) the usual by now, or null without history. */
fun Dashboard.underUsual(): Long? = usualBy()?.let { it - current.expense }

/** Overview: money a still-unpaid bill takes out, converted; incoming money and transfers count nothing. */
fun Dashboard.Upcoming.outflow(to: String, rates: Map<String, Double>): Long =
    if (amount < 0 && toWalletId == null) -convert(amount, currency, to, rates) else 0

/** Overview: what is still to pay before month end; only the current month has anything still to pay. */
fun Dashboard.dueThisMonth(currentMonth: String): List<Dashboard.Upcoming> =
    if (month == currentMonth) upcoming.filter { it.day <= monthEnd() } else emptyList()

fun Dashboard.stillToPay(currentMonth: String, rates: Map<String, Double>) = dueThisMonth(currentMonth).sumOf { it.outflow(primary, rates) }

/** Bills among what is still to pay, for "6 bills before 30 Nov". */
fun Dashboard.billsDue(currentMonth: String, rates: Map<String, Double>) = dueThisMonth(currentMonth).count { it.outflow(primary, rates) > 0 }

/**
 * ComingUp: what is due before the cutoff (month end, or the next 30 days with Settings › Recurring › Coming up),
 * and its total. A past month on screen uses this month's end.
 */
fun Dashboard.comingUp(today: LocalDate, thirtyDays: Boolean): Pair<String, List<Dashboard.Upcoming>> {
    val end = if (month >= today.toString().take(7)) (if (thirtyDays) today.plusDays(30).toString() else monthEnd()) else "${today.toString().take(7)}-31"
    return end to upcoming.filter { it.day <= end }
}

fun List<Dashboard.Upcoming>.outflowTotal(to: String, rates: Map<String, Double>) = sumOf { it.outflow(to, rates) }

/** A category's pill in Where it went (vsUsual): fixed, new, or the change against the usual by now. */
sealed interface VsUsual {
    data object Fixed : VsUsual
    data object New : VsUsual
    /** The change as a ratio, e.g. 0.24 for +24%. Warn above +20%, positive below −5%. */
    data class Change(val ratio: Double) : VsUsual {
        val warn get() = ratio > 0.2
        val good get() = ratio < -0.05
    }
}

fun vsUsual(total: Long, usual: Double?, fixed: Boolean = false): VsUsual = when {
    fixed -> VsUsual.Fixed
    // "new", not "+3766%", when the baseline is under a tenth of this month's spend.
    usual == null || usual == 0.0 || usual * 10 < total -> VsUsual.New
    else -> VsUsual.Change((total - usual) / usual)
}

/** One item for Needs attention, in the web's order: overdue bills, cards owed, categories above usual, old values. */
sealed interface Attention {
    val key: String

    data class Overdue(val payment: Dashboard.Upcoming) : Attention {
        override val key get() = payment.key
    }

    data class CardOwed(val card: Wallet, val owed: Long) : Attention {
        override val key get() = card.id
    }

    data class AboveUsual(val category: Dashboard.DashCategory, val usual: Long) : Attention {
        override val key get() = category.id
        val ratio get() = category.total.toDouble() / usual - 1
    }

    /** An investment whose value is older than [STALE_DAYS], or was never entered ([age] null). */
    data class Stale(val account: Wallet, val age: Long?) : Attention {
        override val key get() = account.id
    }
}

const val STALE_DAYS = 60

/** NeedsAttention in index.tsx, with its thresholds. */
fun Dashboard.attention(today: LocalDate, wallets: List<Wallet>): List<Attention> {
    val items = mutableListOf<Attention>()
    val todayText = today.toString()
    upcoming.filter { it.day < todayText && it.subscriptionId != null }.forEach { items += Attention.Overdue(it) }
    for (card in wallets.filter { !it.isArchived && it.type == AccountType.Credit }) {
        val owed = (card.creditLimit ?: 0) - card.balance
        if (owed > 0) items += Attention.CardOwed(card, owed)
    }
    // A category over 20% above its usual pace. Skip tiny baselines and overspends under 2% of the usual month so far.
    val usualSoFar = pace.usual?.getOrNull(dayN() - 1) ?: 0.0
    for (c in categories) {
        val u = c.usualToDate
        if (c.fixed || u == null || u == 0.0 || u * 10 < c.total || c.total <= u * 1.2 || c.total - u < usualSoFar * 0.02) continue
        items += Attention.AboveUsual(c, u.cents())
    }
    for (w in wallets.filter { !it.isArchived && it.type == AccountType.Investment }) {
        val age = w.gainDay?.let { ChronoUnit.DAYS.between(LocalDate.parse(it), today) }
        if (age != null && age <= STALE_DAYS) continue
        items += Attention.Stale(w, age)
    }
    return items
}

/** NetWorthCard: the change in net worth this month (null for a future month) and over the last 12 months. */
fun Dashboard.worthChange(): Pair<Long?, Double?> {
    val at = { m: String -> series.firstOrNull { it.month == m }?.netWorth }
    val prevMonth = java.time.YearMonth.parse(month).minusMonths(1).toString()
    val thisMonth = at(month)?.let { it - (at(prevMonth) ?: 0) }
    val line = series.filter { it.month <= month }.takeLast(12)
    val yearAgo = if (line.size > 1) line.first().netWorth else null
    val growth = if (yearAgo != null && yearAgo != 0L) (line.last().netWorth - yearAgo).toDouble() / abs(yearAgo) else null
    return thisMonth to growth
}

/** What an account adds to net worth, in its own currency (core.ts accountWorth). */
fun Wallet.worth(): Long = when (type) {
    AccountType.Credit -> balance - (creditLimit ?: 0)
    AccountType.Investment -> balance + (gain ?: 0)
    else -> balance
}
