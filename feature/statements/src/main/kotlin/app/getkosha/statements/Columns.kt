package app.getkosha.statements

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.getkosha.design.Chip
import app.getkosha.design.Hairline
import app.getkosha.design.KText
import app.getkosha.design.KoshaTheme
import app.getkosha.design.Pill
import app.getkosha.design.PhIcon
import app.getkosha.design.R
import app.getkosha.design.Sparkle
import app.getkosha.design.Switch

/** What a CSV's columns came from, for the Columns heading (statement-file.tsx columnsStatus). */
@Composable
fun columnsStatus(remembered: Boolean, byAi: Boolean, confident: Boolean): String = stringResource(
    when {
        remembered -> R.string.import_columns_remembered
        byAi -> R.string.import_columns_by_ai
        confident -> R.string.import_columns_confident
        else -> R.string.import_columns_check
    },
)

private const val IGNORE = ""

/**
 * A CSV's columns, each with a sample value and what it holds, to fix what Kosha guessed (statement-file.tsx
 * Columns). Open when Kosha wasn't sure. Every change re-reads the file on the server with the new layout.
 */
@Composable
fun Columns(
    rows: List<List<String>>,
    layout: Layout,
    count: Int,
    status: String,
    byAi: Boolean,
    startOpen: Boolean,
    onChange: (Layout) -> Unit,
) {
    val c = KoshaTheme.colors
    var open by rememberSaveable { mutableStateOf(startOpen) }
    var roleFor by rememberSaveable { mutableStateOf<Int?>(null) }
    var formats by rememberSaveable { mutableStateOf(false) }
    val roles = listOf(
        IGNORE to stringResource(R.string.import_role_ignore),
        "date" to stringResource(R.string.import_role_date),
        "description" to stringResource(R.string.import_role_description),
        "amount" to stringResource(R.string.import_role_amount),
        "debit" to stringResource(R.string.import_role_debit),
        "credit" to stringResource(R.string.import_role_credit),
        "balance" to stringResource(R.string.import_role_balance),
    )
    val dateFormats = listOf(
        DMY to stringResource(R.string.import_date_dmy),
        MDY to stringResource(R.string.import_date_mdy),
        YMD to stringResource(R.string.import_date_ymd),
        TEXT to stringResource(R.string.import_date_text),
    )
    fun roleOf(i: Int) = when (i) {
        layout.date -> "date"
        in layout.description -> "description"
        layout.amount -> "amount"
        layout.debit -> "debit"
        layout.credit -> "credit"
        layout.balance -> "balance"
        else -> IGNORE
    }
    fun set(i: Int, role: String) {
        var next = layout.copy(
            description = layout.description - i,
            amount = layout.amount.takeIf { it != i },
            debit = layout.debit.takeIf { it != i },
            credit = layout.credit.takeIf { it != i },
            balance = layout.balance.takeIf { it != i },
        )
        next = when (role) {
            "date" -> next.copy(date = i, dateFormat = guessDateFormat(rows.drop(layout.header + 1).map { it.getOrNull(i) ?: "" }) ?: next.dateFormat)
            "description" -> next.copy(description = (next.description + i).takeLast(2))
            "amount" -> next.copy(amount = i, debit = null, credit = null)
            "debit" -> next.copy(debit = i, amount = null)
            "credit" -> next.copy(credit = i, amount = null)
            "balance" -> next.copy(balance = i)
            else -> next
        }
        onChange(next)
    }
    val head = if (layout.header >= 0) rows.getOrNull(layout.header).orEmpty() else emptyList()
    val sample = rows.getOrNull(layout.header + 1).orEmpty()
    val width = maxOf(head.size, sample.size)

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button) { open = !open },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            KText(stringResource(R.string.import_columns), Modifier.weight(1f), style = KoshaTheme.type.small)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (byAi) Sparkle(size = 11.dp)
                Pill(status, color = if (!open && status == stringResource(R.string.import_columns_check)) c.warn else c.ink2)
            }
            PhIcon(if (open) "caret-up" else "caret-down", size = 16.dp, tint = c.ink3)
        }
        if (open) {
            val shape = RoundedCornerShape(12.dp)
            Column(Modifier.fillMaxWidth().clip(shape).border(1.dp, c.line, shape)) {
                (0 until width).forEach { i ->
                    if (i > 0) Hairline()
                    val role = roleOf(i)
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button) { roleFor = i }.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            head.getOrNull(i)?.takeIf { it.isNotBlank() }?.let { KText(it, style = KoshaTheme.type.xs, color = c.ink3, maxLines = 1) }
                            KText(sample.getOrNull(i).orEmpty(), style = KoshaTheme.type.num.copy(fontSize = KoshaTheme.type.xs.fontSize), color = c.ink2, maxLines = 1)
                        }
                        Chip(
                            roles.first { it.first == role }.second,
                            soft = true,
                            small = true,
                            leading = null,
                            onClick = { roleFor = i },
                        )
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button) { formats = true },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                KText(stringResource(R.string.import_columns_dates), Modifier.weight(1f), style = KoshaTheme.type.small, color = c.ink3)
                KText(dateFormats.firstOrNull { it.first == layout.dateFormat }?.second.orEmpty(), style = KoshaTheme.type.small)
                PhIcon("caret-down", Modifier.padding(start = 4.dp), size = 14.dp, tint = c.ink3)
            }
            if (layout.amount != null) {
                val invert = stringResource(R.string.import_columns_invert)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    KText(invert, Modifier.weight(1f), style = KoshaTheme.type.small)
                    Switch(layout.invert, { onChange(layout.copy(invert = it)) }, invert)
                }
            }
            KText(
                pluralStringResource(R.plurals.import_columns_lines_read, count, countText(count)),
                style = KoshaTheme.type.xs,
                color = c.ink3,
            )
        }
    }
    roleFor?.let { i ->
        ChoiceSheet(head.getOrNull(i)?.ifBlank { null } ?: stringResource(R.string.import_columns_column, (i + 1).toString()), roles, roleOf(i), { set(i, it) }, { roleFor = null })
    }
    if (formats) ChoiceSheet(stringResource(R.string.import_columns_dates), dateFormats, layout.dateFormat, { onChange(layout.copy(dateFormat = it)) }, { formats = false })
}
