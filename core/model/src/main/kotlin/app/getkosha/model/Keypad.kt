package app.getkosha.model

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/**
 * The amount keypad's input: an expression such as `24.07+5.5`, built one key at a time. Ported from the
 * Add prototype (design/source/AddPrototype.dc.html): group, lastSeg, evalExpr, prettyExpr and press.
 *
 * The text always uses `.` for decimals and `+ - * /` for operators; [pretty] and [group] turn it into what
 * the person reads. `decimals` is how many the currency shows (2 for most, 0 for JPY and KRW). Amounts are
 * still stored ×100 for every currency, as the web does, so [minorUnits] multiplies by 100 whatever `decimals` is.
 */
object Keypad {
    /** Digits allowed before the decimal point in one number: up to 99,999,999. */
    const val MAX_WHOLE_DIGITS = 8

    sealed interface Key {
        data class Digit(val digit: Int) : Key
        data object Dot : Key
        data class Operator(val op: Op) : Key
        /** Backspace. */
        data object Delete : Key
        /** Holding backspace. */
        data object Clear : Key
    }

    enum class Op(val char: Char, val shown: String) {
        Divide('/', "÷"),
        Multiply('*', "×"),
        Subtract('-', "−"),
        Add('+', "+"),
    }

    private const val OPS = "+-*/"

    /** The number being typed: the text after the last operator. */
    fun lastSegment(input: String): String = input.substring(input.indexOfLast { it in OPS } + 1)

    fun hasOperator(input: String) = input.any { it in OPS }

    /** The operator the input ends with, which the keypad highlights. */
    fun pendingOperator(input: String): Op? = Op.entries.firstOrNull { input.endsWith(it.char) }

    fun press(input: String, key: Key, decimals: Int = 2): String {
        val seg = lastSegment(input)
        return when (key) {
            Key.Delete -> input.dropLast(1)
            Key.Clear -> ""
            is Key.Operator -> when {
                input.isEmpty() -> input
                input.last() in OPS -> input.dropLast(1) + key.op.char
                else -> input.removeSuffix(".") + key.op.char
            }
            Key.Dot -> when {
                decimals == 0 || '.' in seg -> input
                seg.isEmpty() -> "${input}0."
                else -> "$input."
            }
            is Key.Digit -> {
                val add = key.digit.toString()
                if (add == "0" && (seg.isEmpty() || seg == "0")) {
                    // A first 0 types "0"; another zero on "0" does nothing.
                    return if (seg.isEmpty()) input + "0" else input
                }
                val dot = seg.indexOf('.')
                if (dot >= 0) {
                    val room = decimals - (seg.length - dot - 1)
                    if (room <= 0) input else input + add.take(room)
                } else {
                    if (seg.length + add.length > MAX_WHOLE_DIGITS) input
                    else (if (seg == "0") input.dropLast(1) else input) + add
                }
            }
        }
    }

    /**
     * The value of the input, × and ÷ before + and −, ignoring a trailing operator or dot, rounded to `decimals`.
     * Zero for no input; null when it divides by zero, so the amount shows "…" and nothing can be saved.
     */
    fun evaluate(input: String, decimals: Int = 2): BigDecimal? {
        val expr = input.trimEnd { it in OPS || it == '.' }
        if (expr.isEmpty()) return BigDecimal.ZERO
        val tokens = Regex("""\d*\.?\d+|[+\-*/]""").findAll(expr).map { it.value }.toList()
        val terms = mutableListOf<BigDecimal>()
        val signs = mutableListOf<Char>()
        var current = BigDecimal(tokens[0])
        var i = 1
        while (i + 1 < tokens.size) {
            val op = tokens[i][0]
            val value = BigDecimal(tokens[i + 1])
            when (op) {
                '*' -> current = current.multiply(value)
                '/' -> {
                    if (value.signum() == 0) return null
                    current = current.divide(value, MathContext.DECIMAL64)
                }
                else -> {
                    terms += current
                    signs += op
                    current = value
                }
            }
            i += 2
        }
        terms += current
        var result = terms[0]
        signs.forEachIndexed { n, op -> result = if (op == '+') result + terms[n + 1] else result - terms[n + 1] }
        return result.setScale(decimals, RoundingMode.HALF_UP)
    }

    /**
     * The amount to save, in minor units (×100 for every currency) and positive, or null when it isn't above zero
     * or can't be worked out. The kind of entry gives it its sign.
     */
    fun minorUnits(input: String, decimals: Int = 2): Long? {
        val value = evaluate(input, decimals) ?: return null
        if (value.signum() <= 0) return null
        return value.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact()
    }

    /** One typed number with grouping, as typed: `1204.5` reads `1,204.5`. */
    fun group(segment: String, grouping: Char = ',', decimal: Char = '.'): String {
        if (segment.isEmpty()) return "0"
        val whole = segment.substringBefore('.').trimStart('0').ifEmpty { "0" }
        val grouped = whole.reversed().chunked(3).joinToString(grouping.toString()).reversed()
        return if ('.' in segment) grouped + decimal + segment.substringAfter('.') else grouped
    }

    /** The expression as shown above the amount: `24.07 + 5.50` with ×, ÷ and a true minus. */
    fun pretty(input: String, grouping: Char = ',', decimal: Char = '.'): String =
        Regex("""[+\-*/]|[^+\-*/]+""").findAll(input).joinToString("") { m ->
            val op = Op.entries.firstOrNull { it.char.toString() == m.value }
            if (op != null) " ${op.shown} " else group(m.value, grouping, decimal)
        }.trim()
}
