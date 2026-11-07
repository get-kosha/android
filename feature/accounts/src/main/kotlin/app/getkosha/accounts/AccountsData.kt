@file:UseSerializers(RoundedLong::class)

package app.getkosha.accounts

import kotlinx.serialization.UseSerializers
import app.getkosha.model.RoundedLong

import app.getkosha.model.AccountType
import app.getkosha.model.Wallet
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.Locale
import kotlin.math.abs

/**
 * GET /api/app/accounts-overview?month=&currency= (finance.accountsOverview): the net worth series by account in
 * the display currency, and per account its month in, out and moved and its last entry, in its own currency.
 * The server doesn't round converted figures, so they arrive as decimals.
 */
@Serializable
data class AccountsOverview(
    val currency: String,
    val month: String,
    val today: String,
    /** Unix seconds of the last exchange-rate refresh. */
    val ratesUpdated: Long = 0,
    val series: List<Point> = emptyList(),
    val accounts: Map<String, Flow> = emptyMap(),
) {
    @Serializable
    data class Point(val month: String, val netWorth: Double, val gains: Double = 0.0, val byAccount: Map<String, Double> = emptyMap())

    @Serializable
    data class Flow(
        @SerialName("in") val inflow: Double = 0.0,
        val out: Double = 0.0,
        val moved: Double = 0.0,
        val lastEntry: String? = null,
    )
}

/** GET /api/v1/exchanges (list_currency_exchanges). `sent` includes the fee. */
@Serializable
data class Exchanges(val exchanges: List<Exchange> = emptyList())

@Serializable
data class Exchange(
    val id: String,
    val day: String,
    val fromAccountId: String,
    val toAccountId: String,
    val sent: Long,
    val received: Long,
    val fee: Long = 0,
    val provider: String = "",
    val fromCurrency: String,
    val toCurrency: String,
    val marketRate: Double? = null,
)

/** GET /api/v1/gains?accountId= (list_gains), newest first. */
@Serializable
data class Gains(val gains: List<Gain> = emptyList())

@Serializable
data class Gain(val id: String, val accountId: String, val day: String, val gain: Long, val invested: Long, val note: String = "")

/** The answer of create_account that the app reads. */
@Serializable
data class CreatedAccount(val id: String)

/** Investments whose value is older than this ask to be updated (accounts.tsx STALE_DAYS). */
const val STALE_DAYS = 60

/** What a currency exchange cost against the market rate that day (core.ts exchange). Values in minor units. */
data class FxCost(val converted: Long, val rate: Double, val rateCost: Double?, val totalCost: Double, val costPct: Double)

/** The ways a net worth chart layer is drawn: an account's owned part, or all unrealized gains on top. */
data class Layer(val id: String?, val values: List<Double>)

object AccountsMath {
    /** [total] left the source account, [fee] of it was charged, and the rest became [received] (core.ts exchange). */
    fun exchange(total: Long, fee: Long, received: Long, marketRate: Double?): FxCost {
        val converted = abs(total) - abs(fee)
        val rate = if (converted > 0) abs(received).toDouble() / converted else 0.0
        // What the rate gap cost, in the source currency: the converted amount at market minus what arrived.
        val rateCost = marketRate?.takeIf { it > 0 }?.let { (converted * it - abs(received)) / it }
        val totalCost = if (rateCost == null) abs(fee).toDouble() else abs(fee) + rateCost
        return FxCost(converted, rate, rateCost, totalCost, if (total != 0L) totalCost / abs(total) * 100 else 0.0)
    }

    /** A rate with sensible precision (core.ts fmtRate): 93.85, 1.1623, 0.008640. */
    fun rate(r: Double, locale: Locale): String = when {
        r >= 100 -> String.format(locale, "%.2f", r)
        r >= 1 -> String.format(locale, "%.4f", r)
        else -> java.math.BigDecimal(r).round(java.math.MathContext(4)).toPlainString()
    }

    /** What an account is worth in lists, in its currency (core.ts accountWorth); a card shows what it owes. */
    fun shownWorth(w: Wallet): Long = when (w.type) {
        AccountType.Credit -> -maxOf(0, (w.creditLimit ?: 0) - w.balance)
        AccountType.Investment -> w.balance + (w.gain ?: 0)
        else -> w.balance
    }

    /** What an account adds to net worth (core.ts accountWorth). */
    fun worth(w: Wallet): Long = when (w.type) {
        AccountType.Credit -> w.balance - (w.creditLimit ?: 0)
        AccountType.Investment -> w.balance + (w.gain ?: 0)
        else -> w.balance
    }

    /** The last [months] points of the series: 13 for a year, 25 for two, all for null. */
    fun range(series: List<AccountsOverview.Point>, months: Int?) = if (months == null) series else series.takeLast(months)

    /** One layer per account in the given order (only those with any value), then the gains layer if there are gains. */
    fun layers(points: List<AccountsOverview.Point>, order: List<String>): List<Layer> {
        val ids = order + points.flatMap { it.byAccount.keys }.filter { it !in order }.distinct()
        val accounts = ids.map { id -> Layer(id, points.map { it.byAccount[id] ?: 0.0 }) }.filter { l -> l.values.any { Math.round(it) != 0L } }
        val gains = Layer(null, points.map { it.gains })
        return if (gains.values.any { Math.round(it) != 0L }) accounts + gains else accounts
    }

    /**
     * Stacks layers by sign, as recharts' stackOffset "sign": positive values pile up from zero, negative ones (card
     * debt) pile down from it. Returns each layer's (lower, upper) per point.
     */
    fun stack(layers: List<Layer>): List<List<Pair<Double, Double>>> {
        val n = layers.firstOrNull()?.values?.size ?: 0
        val up = DoubleArray(n)
        val down = DoubleArray(n)
        return layers.map { l ->
            l.values.mapIndexed { i, v ->
                if (v >= 0) (up[i] to up[i] + v).also { up[i] += v } else (down[i] + v to down[i]).also { down[i] += v }
            }
        }
    }
}
