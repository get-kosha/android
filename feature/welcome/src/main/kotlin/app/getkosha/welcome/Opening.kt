package app.getkosha.welcome

import app.getkosha.model.AccountType
import app.getkosha.model.Keypad
import app.getkosha.model.convert
import kotlinx.serialization.Serializable

// The welcome screen's arithmetic, ported from the web (core.ts openingFigures, accountWorth, netWorth). It is a
// preview before any account exists, so it can't come from the server's reports yet.

/** An account being set up on Welcome: the two figures people know, as typed on the keypad. */
@Serializable
data class NewAccount(
    val key: String,
    val name: String,
    val type: AccountType,
    val currency: String,
    /** The balance, what a card owes, or what was put into an investment. */
    val amount: String = "",
    /** A card's credit limit or an investment's value today. */
    val extra: String = "",
)

/** What a new account stores. A card keeps the credit left; an investment keeps the money put in and its gain. */
data class Opening(val balance: Long, val creditLimit: Long?, val gain: Long?)

/** A typed figure in minor units (×100), or null when nothing was typed. */
fun figure(input: String): Long? {
    if (input.isEmpty()) return null
    val value = Keypad.evaluate(input) ?: return null
    return value.movePointRight(2).setScale(0, java.math.RoundingMode.HALF_UP).longValueExact()
}

/**
 * core.ts openingFigures: a card's balance is the credit left (limit minus what is owed; without a limit, minus
 * what is owed). An investment with only a value today counts it all as money put in.
 */
fun openingFigures(type: AccountType, amount: Long?, extra: Long?): Opening = when (type) {
    AccountType.Credit -> Opening((extra ?: 0) - Math.abs(amount ?: 0), extra, null)
    AccountType.Investment -> {
        val put = amount ?: extra ?: 0
        Opening(put, null, extra?.let { it - put })
    }
    else -> Opening(amount ?: 0, null, null)
}

fun NewAccount.opening(): Opening =
    openingFigures(type, figure(amount), if (type == AccountType.Credit || type == AccountType.Investment) figure(extra) else null)

/** core.ts accountWorth: a card counts what it owes as debt, an investment its money in plus its gain. */
fun accountWorth(type: AccountType, balance: Long, creditLimit: Long?, gain: Long?): Long = when (type) {
    AccountType.Credit -> balance - (creditLimit ?: 0)
    AccountType.Investment -> balance + (gain ?: 0)
    else -> balance
}

/** core.ts netWorth(...).total for accounts not saved yet, converted at the server's rates (units per 1 USD). */
fun previewNetWorth(accounts: List<NewAccount>, to: String, perUsd: Map<String, Double>): Long =
    accounts.sumOf { a ->
        val o = a.opening()
        convert(accountWorth(a.type, o.balance, o.creditLimit, o.gain), a.currency, to, perUsd)
    }
