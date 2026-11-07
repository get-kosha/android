package app.getkosha.design

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.getkosha.model.EntryKind
import app.getkosha.model.Keypad
import app.getkosha.model.Keypad.Key
import app.getkosha.model.Keypad.Op
import app.getkosha.model.Money
import app.getkosha.model.Sign

/** What the amount line reads for a keypad input: `−€29.57`, `€0`, or `…` when it divides by zero. */
fun amountText(input: String, currency: String, kind: EntryKind, locale: java.util.Locale): String {
    val sign = when (kind) {
        EntryKind.Expense -> Money.MINUS
        EntryKind.Income -> "+"
        EntryKind.Transfer -> ""
    }
    val (prefix, suffix) = Money.affixes(currency, locale)
    if (input.isEmpty()) return prefix + "0" + suffix
    val decimals = Money.decimals(currency)
    if (Keypad.hasOperator(input)) {
        val value = Keypad.evaluate(input, decimals) ?: return "…"
        val minor = value.movePointRight(2).toLong()
        if (minor == 0L) return prefix + "0" + suffix
        return Money.format(minor, currency, locale, Sign.None).let { if (minor > 0) sign + it else Money.MINUS + it }
    }
    val (grouping, decimal) = Money.separators(locale)
    return sign + prefix + Keypad.group(input, grouping, decimal) + suffix
}

/**
 * The amount being entered: the expression above it when it has an operator, the amount in the middle
 * (60, or 46 when longer than 8 characters), backspace on the right (hold to clear), and a note below.
 */
@Composable
fun AmountDisplay(
    input: String,
    currency: String,
    kind: EntryKind,
    onInput: (String) -> Unit,
    modifier: Modifier = Modifier,
    note: String? = null,
) {
    val c = KoshaTheme.colors
    val locale = KoshaTheme.locale
    val text = amountText(input, currency, kind, locale)
    val (grouping, decimal) = Money.separators(locale)
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        KText(
            if (Keypad.hasOperator(input)) Keypad.pretty(input, grouping, decimal) else "",
            Modifier.heightIn(min = 20.dp),
            style = KoshaTheme.type.expr,
            color = c.ink3,
            maxLines = 1,
        )
        Box(Modifier.fillMaxWidth().padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
            KText(
                text,
                Modifier.padding(horizontal = 48.dp).semantics { liveRegion = LiveRegionMode.Polite },
                style = KoshaTheme.type.amount.let { if (text.length > 8) it.copy(fontSize = 46.sp) else it },
                color = when {
                    input.isEmpty() -> c.ink3
                    kind == EntryKind.Income -> c.pos
                    else -> c.ink
                },
                align = TextAlign.Center,
                maxLines = 1,
            )
            if (input.isNotEmpty()) {
                HoldKey(
                    Modifier.align(Alignment.CenterEnd),
                    label = stringResource(R.string.app_key_delete),
                    onClick = { onInput(Keypad.press(input, Key.Delete)) },
                    onHold = { onInput("") },
                ) { PhIcon("backspace", size = 26.dp, tint = c.ink3) }
            }
        }
        if (note != null) KText(note, style = KoshaTheme.type.small, color = c.ink2, maxLines = 2, align = TextAlign.Center)
    }
}

/**
 * The 4×4 keypad: digits, `.`, `0` and `00`, and a column of ÷ × − + on soft keys. A light haptic tick on
 * each press. The decimal key follows the currency: hidden for currencies without decimals.
 */
@Composable
fun AmountKeypad(input: String, onInput: (String) -> Unit, modifier: Modifier = Modifier, decimals: Int = 2) {
    val view = LocalView.current
    val pending = Keypad.pendingOperator(input)
    val press = { key: Key ->
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        onInput(Keypad.press(input, key, decimals))
    }
    val rows = listOf(
        listOf(Key.Digit(1), Key.Digit(2), Key.Digit(3), Key.Operator(Op.Divide)),
        listOf(Key.Digit(4), Key.Digit(5), Key.Digit(6), Key.Operator(Op.Multiply)),
        listOf(Key.Digit(7), Key.Digit(8), Key.Digit(9), Key.Operator(Op.Subtract)),
        listOf(Key.Dot, Key.Digit(0), Key.DoubleZero, Key.Operator(Op.Add)),
    )
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { key ->
                    Box(Modifier.weight(1f)) {
                        when {
                            key is Key.Operator -> OperatorKey(key.op, on = pending == key.op) { press(key) }
                            key == Key.Dot && decimals == 0 -> Spacer(Modifier.height(60.dp))
                            else -> DigitKey(key, press)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DigitKey(key: Key, press: (Key) -> Unit) {
    val label = when (key) {
        is Key.Digit -> key.digit.toString()
        Key.DoubleZero -> "00"
        else -> "."
    }
    val spoken = when (key) {
        Key.Dot -> stringResource(R.string.add_key_decimal)
        Key.DoubleZero -> stringResource(R.string.app_key_double_zero)
        else -> label
    }
    Box(
        Modifier
            .fillMaxWidth()
            .height(60.dp)
            .clip(RoundedCornerShape(14.dp))
            .combinedClickable(role = Role.Button, onClick = { press(key) })
            .clearAndSetSemantics { contentDescription = spoken },
        contentAlignment = Alignment.Center,
    ) {
        KText(label, style = KoshaTheme.type.key.let { if (key == Key.DoubleZero) it.copy(fontSize = 24.sp) else it })
    }
}

@Composable
private fun OperatorKey(op: Op, on: Boolean, onClick: () -> Unit) {
    val c = KoshaTheme.colors
    val label = stringResource(
        when (op) {
            Op.Divide -> R.string.app_key_divide
            Op.Multiply -> R.string.app_key_multiply
            Op.Subtract -> R.string.app_key_subtract
            Op.Add -> R.string.app_key_add
        },
    )
    Box(
        Modifier
            .fillMaxWidth()
            .height(60.dp)
            .padding(start = 6.dp, top = 3.dp, bottom = 3.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (on) c.selection else c.soft)
            .combinedClickable(role = Role.Button, onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = label
                selected = on
            },
        contentAlignment = Alignment.Center,
    ) {
        KText(
            op.shown,
            style = KoshaTheme.type.body.copy(fontSize = 26.sp, fontWeight = FontWeight.Normal),
            color = if (on) c.onSelection else c.ink2,
        )
    }
}

/** A 48dp key that does one thing on tap and another on a long press, such as backspace and clear. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HoldKey(
    modifier: Modifier = Modifier,
    label: String,
    onClick: () -> Unit,
    onHold: () -> Unit,
    content: @Composable () -> Unit,
) {
    val view = LocalView.current
    Box(
        modifier
            .size(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Color.Transparent)
            .combinedClickable(
                role = Role.Button,
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onClick()
                },
                onLongClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    onHold()
                },
            )
            .clearAndSetSemantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { content() }
}
