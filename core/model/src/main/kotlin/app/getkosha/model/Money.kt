package app.getkosha.model

import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/** How an amount's direction is shown. */
enum class Sign {
    /** −€12.50 for money out, €12.50 for money in: lists of balances. */
    Natural,
    /** −€12.50 and +€3,240.00: entries, where the direction matters. */
    Always,
    /** €12.50 either way: transfers and neutral amounts. */
    None,
}

/**
 * Formats amounts held as integer minor units: ×100 for every currency, as the web stores them, so ¥1,200
 * is 120000. Currency symbol, grouping and decimals follow the language, and the currency sets how many
 * decimals show (none for JPY). Negative amounts take a true minus (U+2212), never a hyphen.
 */
object Money {
    const val MINUS = "−"

    /** The web formats English as British English (core.ts intlLocale): 07/11/2026, and yen as JP¥. */
    fun locale(language: String): Locale = if (language == "en") Locale.UK else Locale.forLanguageTag(language)

    /** How many decimals the currency shows, such as 2 for EUR and 0 for JPY. */
    fun decimals(currency: String): Int =
        runCatching { Currency.getInstance(currency.uppercase()).defaultFractionDigits }.getOrDefault(2).coerceAtLeast(0)

    private fun formatter(currency: String, locale: Locale): DecimalFormat {
        val f = NumberFormat.getCurrencyInstance(locale) as DecimalFormat
        runCatching { f.currency = Currency.getInstance(currency.uppercase()) }
        val digits = decimals(currency)
        f.minimumFractionDigits = digits
        f.maximumFractionDigits = digits
        return f
    }

    fun format(minor: Long, currency: String, locale: Locale, sign: Sign = Sign.Natural): String {
        val text = formatter(currency, locale).format(BigDecimal.valueOf(Math.abs(minor), 2))
        return when {
            minor < 0 && sign != Sign.None -> MINUS + text
            minor > 0 && sign == Sign.Always -> "+$text"
            else -> text
        }
    }

    /**
     * What goes before and after the number for this currency and language: ("€", "") in English, ("", " €")
     * in German. The keypad wraps the digits as typed in them.
     */
    fun affixes(currency: String, locale: Locale): Pair<String, String> =
        formatter(currency, locale).let { it.positivePrefix to it.positiveSuffix }

    /** The grouping and decimal characters the keypad shows numbers with. */
    fun separators(locale: Locale): Pair<Char, Char> =
        DecimalFormatSymbols.getInstance(locale).let { it.groupingSeparator to it.decimalSeparator }
}
