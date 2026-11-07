package app.getkosha.model

import app.getkosha.model.Keypad.Key
import app.getkosha.model.Keypad.Op
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KeypadTest {
    private fun type(keys: String, decimals: Int = 2) = keys.fold("") { acc, c ->
        val key = when (c) {
            '.' -> Key.Dot
            '<' -> Key.Delete
            '+' -> Key.Operator(Op.Add)
            '-' -> Key.Operator(Op.Subtract)
            '*' -> Key.Operator(Op.Multiply)
            '/' -> Key.Operator(Op.Divide)
            else -> Key.Digit(c.digitToInt())
        }
        Keypad.press(acc, key, decimals)
    }

    @Test fun digits() {
        assertEquals("12", type("12"))
        assertEquals("5", type("05"))
        assertEquals("0", type("0"))
        assertEquals("0", type("00"))
        assertEquals("100", type("100"))
        assertEquals("12345678", type("123456789"))
    }

    @Test fun decimals() {
        assertEquals("0.", type("."))
        assertEquals("12.5", type("12.5."))
        assertEquals("12.50", type("12.50"))
        assertEquals("12.05", type("12.057"))
        assertEquals("12", type("12.", decimals = 0))
        assertEquals("0.0", type(".0"))
    }

    @Test fun operators() {
        assertEquals("", type("+"))
        assertEquals("12+", type("12-+"))
        assertEquals("12*", type("12.*"))
        assertEquals("24.07+5.5", type("24.07+5.5"))
        assertEquals("24.07+0.", type("24.07+."))
        assertEquals("1+0", type("1+00+<"))
        assertEquals("1", type("12<"))
        assertEquals(Op.Add, Keypad.pendingOperator("12+"))
        assertNull(Keypad.pendingOperator("12+3"))
        assertEquals("", Keypad.press("12+3", Key.Clear))
        assertEquals("12+", Keypad.press("12+3", Key.Delete))
    }

    @Test fun evaluates() {
        assertEquals("29.57", Keypad.evaluate("24.07+5.5").toString())
        assertEquals("14.00", Keypad.evaluate("2+3*4").toString())
        assertEquals("16.50", Keypad.evaluate("12.50+4").toString())
        assertEquals("10.00", Keypad.evaluate("10/3*3").toString())
        assertEquals("3.33", Keypad.evaluate("10/3").toString())
        assertEquals("12.00", Keypad.evaluate("12+").toString())
        assertEquals("0", Keypad.evaluate("").toString())
        assertEquals("1200", Keypad.evaluate("1199.6", decimals = 0).toString())
        assertNull(Keypad.evaluate("5/0"))
        assertNull(Keypad.evaluate("5/0.0+1"))
    }

    @Test fun minorUnits() {
        assertEquals(2957L, Keypad.minorUnits("24.07+5.5"))
        // Every currency is stored ×100: ¥1,200 is 120000.
        assertEquals(120000L, Keypad.minorUnits("1200", decimals = 0))
        assertEquals(9999999999L, Keypad.minorUnits("99999999.99"))
        assertNull(Keypad.minorUnits(""))
        assertNull(Keypad.minorUnits("5-10"))
        assertNull(Keypad.minorUnits("4-4"))
        assertNull(Keypad.minorUnits("1/0"))
    }

    @Test fun display() {
        assertEquals("1,204.5", Keypad.group("1204.5"))
        assertEquals("0", Keypad.group(""))
        assertEquals("1.204,5", Keypad.group("1204.5", '.', ','))
        assertEquals("24.07 + 5.50", Keypad.pretty("24.07+5.50"))
        assertEquals("1,000 × 3 ÷ 2 − 1 +", Keypad.pretty("1000*3/2-1+"))
    }

    @Test fun money() {
        val uk = Money.locale("en")
        assertEquals("−€29.57", Money.format(-2957, "eur", uk))
        assertEquals("+€3,240.00", Money.format(324000, "EUR", uk, Sign.Always))
        assertEquals("€200.00", Money.format(-20000, "eur", uk, Sign.None))
        // CLDR British English writes yen as JP¥, like the web's Intl.NumberFormat.
        assertEquals("JP¥1,200", Money.format(120000, "jpy", uk))
        assertEquals(0, Money.decimals("jpy"))
        assertEquals("−1.312,40 €", Money.format(-131240, "eur", Locale.GERMANY))
        assertEquals('.' to ',', Money.separators(Locale.GERMANY))
        assertEquals("€" to "", Money.affixes("eur", uk))
        assertEquals("" to "\u00a0€", Money.affixes("eur", Locale.GERMANY))
    }
}
