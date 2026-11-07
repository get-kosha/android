package app.getkosha.add

import app.getkosha.model.Event
import app.getkosha.model.Keypad
import app.getkosha.model.Wallet
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import kotlin.math.abs
import kotlin.math.roundToLong

// Add's money and date rules, ported from the web (../kosha/src/lib/core.ts, add-defaults.ts and the transfer
// half of transaction-form.tsx). Pure, so they run in plain JUnit tests.

/**
 * The account Add starts on (add-defaults.ts startWallet): Settings › General › Default account when it names an
 * active account, else the last one used on this phone, else the first active one.
 */
fun startAccount(pref: String, active: List<Wallet>, last: String?): String? =
    pref.takeIf { it != "last" && active.any { w -> w.id == it } }
        ?: last?.takeIf { active.any { w -> w.id == it } }
        ?: active.firstOrNull()?.id

/**
 * The event a new entry on [day] joins by default (core.ts eventFor): the one whose dates cover the day. No end
 * means it is still going. When events overlap, the one that started last wins.
 */
fun eventFor(day: String, events: List<Event>): String? {
    var best: Event? = null
    for (e in events) {
        val start = e.startDay ?: continue
        if (day < start || (e.endDay != null && day > e.endDay!!)) continue
        if (best == null || start > best.startDay!!) best = e
    }
    return best?.id
}

/** An amount in minor units (×100) as keypad input: 2957 is "29.57", 1200 is "12", ¥1,200 (120000) is "1200". */
fun keypadInput(minor: Long, decimals: Int): String {
    if (minor == 0L) return ""
    return BigDecimal.valueOf(abs(minor), 2).setScale(decimals, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
}

/** A transfer's cost (core.ts exchange): `total` left From, `fee` of it was charged, the rest became `received`. */
data class Exchange(val converted: Long, val rate: Double, val rateCost: Double?, val totalCost: Double, val costPct: Double)

fun exchange(total: Long, fee: Long, received: Long, marketRate: Double?): Exchange {
    val converted = abs(total) - abs(fee)
    val rate = if (converted > 0) abs(received).toDouble() / converted else 0.0
    // What the rate gap cost, in the source currency: the converted amount at market minus what arrived, valued at market.
    val rateCost = marketRate?.takeIf { it != 0.0 }?.let { (converted * it - abs(received)) / it }
    val totalCost = if (rateCost == null) abs(fee).toDouble() else abs(fee) + rateCost
    return Exchange(converted, rate, rateCost, totalCost, if (total != 0L) totalCost / abs(total) * 100 else 0.0)
}

/** A rate as the web writes it (core.ts fmtRate): 2 decimals from 100, 4 from 1, else 4 significant digits. */
fun fmtRate(r: Double): BigDecimal = when {
    r >= 100 -> BigDecimal(r).setScale(2, RoundingMode.HALF_UP)
    r >= 1 -> BigDecimal(r).setScale(4, RoundingMode.HALF_UP)
    r <= 0 -> BigDecimal.ZERO
    else -> BigDecimal(r).round(MathContext(4, RoundingMode.HALF_UP))
}

/** Units of [to] per unit of [from] at the server's rates (units per 1 USD). 1 when a rate is missing, as on the web. */
fun marketRate(from: String, to: String, perUsd: Map<String, Double>): Double {
    val f = perUsd[from] ?: return 1.0
    val t = perUsd[to] ?: return 1.0
    return if (f == 0.0 || t == 0.0) 1.0 else t / f
}

/** What the fee sheet holds as typed: keypad text for the fee, rate and amount received, and the provider. */
data class FeeInput(
    val fee: String = "",
    val received: String = "",
    val rate: String = "",
    /** Which of rate and received was typed last; the other follows from it. Null: the market rate. */
    val typed: Typed? = null,
    val provider: String = "",
) {
    enum class Typed { Rate, Received }
}

/**
 * A transfer's sides from what was typed (transaction-form.tsx useExchange). [sent] leaves From and includes the
 * fee. Between currencies you type the rate or the amount received and the other follows; otherwise the market rate.
 */
data class TransferSides(val fee: Long, val converted: Long, val received: Long, val market: Double, val cross: Boolean) {
    val rate get() = if (cross && converted > 0) received.toDouble() / converted else 1.0
    val cost get() = exchange(converted + fee, fee, received, if (cross) market else null)
}

/** Decimals the rate field takes on the keypad. */
const val RATE_DECIMALS = 6

fun transferSides(sent: Long, input: FeeInput, from: String, to: String?, fromDecimals: Int, toDecimals: Int, perUsd: Map<String, Double>): TransferSides {
    val cross = to != null && to != from
    val fee = Keypad.minorUnits(input.fee, fromDecimals) ?: 0
    val converted = sent - fee
    val market = if (cross) marketRate(from, to, perUsd) else 1.0
    val received = when {
        !cross -> converted
        input.typed == FeeInput.Typed.Received -> Keypad.minorUnits(input.received, toDecimals) ?: 0
        input.typed == FeeInput.Typed.Rate -> (converted * (Keypad.evaluate(input.rate, RATE_DECIMALS)?.toDouble() ?: 0.0)).roundToLong()
        else -> (converted * market).roundToLong()
    }
    return TransferSides(fee, converted, received, market, cross)
}
