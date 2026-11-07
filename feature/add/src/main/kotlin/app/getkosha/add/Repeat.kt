package app.getkosha.add

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import app.getkosha.design.AccountDot
import app.getkosha.design.Button
import app.getkosha.design.Chip
import app.getkosha.design.KText
import app.getkosha.design.KoshaTheme
import app.getkosha.design.MonthGrid
import app.getkosha.design.PhIcon
import app.getkosha.design.R
import app.getkosha.design.Radio
import app.getkosha.design.Segmented
import app.getkosha.design.Sheet
import app.getkosha.design.Stepper
import app.getkosha.design.Switch
import app.getkosha.model.EntryKind
import java.time.YearMonth

/** "Every month", "Every 2 weeks": the existing "every …" messages, with a capital to start a label. */
@Composable
internal fun frequency(r: Repeat): String {
    val res = when (r.unit) {
        "day" -> R.plurals.subs_every_day
        "week" -> R.plurals.subs_every_week
        "year" -> R.plurals.subs_every_year
        else -> R.plurals.subs_every_month
    }
    return pluralStringResource(res, r.every, r.every).replaceFirstChar { it.titlecase(KoshaTheme.locale) }
}

/**
 * Right under the amount: the account as a pill with its full name (cut off only when long), and Repeat, which
 * turns the entry into a bill, subscription, recurring income or recurring transfer. A transfer's accounts are
 * in its From and To rows, so it shows only Repeat.
 */
@Composable
internal fun Pills(m: AddModel) {
    val c = KoshaTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
        if (!m.transfer) {
            val account = m.account
            val spoken = when {
                account == null -> stringResource(R.string.add_chip_account_none)
                m.accountGuessed -> stringResource(R.string.app_add_account_guessed, account.name)
                else -> stringResource(R.string.add_chip_account, account.name)
            }
            Chip(
                account?.name ?: stringResource(R.string.add_no_account),
                Modifier.widthIn(max = 220.dp).tap(spoken) { m.sheet = AddSheet.Account },
                small = true,
                soft = m.accountGuessed,
                leading = {
                    if (c.accountColours && account != null) AccountDot(c.account(m.colorIndex(account.id)), size = 10.dp)
                    else PhIcon("wallet", size = 16.dp)
                },
            )
        }
        val r = m.repeat
        val label = if (r == null) stringResource(R.string.app_add_repeat_once) else frequency(r)
        val spoken = stringResource(R.string.app_add_repeat_label, label)
        Chip(
            label,
            Modifier.tap(spoken) { m.sheet = AddSheet.Repeat },
            small = true,
            on = r != null,
            soft = r == null,
            leading = { PhIcon("arrows-clockwise", size = 16.dp, tint = if (r != null) c.ink else c.ink2) },
        )
    }
}

/** Doesn't repeat, every week, month or year, or custom; for an expense, bill or subscription; an end; auto-record. */
@Composable
internal fun RepeatSheet(m: AddModel, close: () -> Unit) {
    val c = KoshaTheme.colors
    val autoDefault = m.meta.pref("default_auto_pay", "1") == "1"
    var draft by remember { mutableStateOf(m.repeat ?: Repeat(autoPay = autoDefault)) }
    var on by remember { mutableStateOf(m.repeat != null) }
    var custom by remember { mutableStateOf(m.repeat?.let { it.every != 1 || it.unit == "day" } ?: false) }
    var ending by remember { mutableStateOf(m.repeat?.endDay != null) }
    var month by remember { mutableStateOf(YearMonth.from(m.day)) }
    val presets = listOf("week", "month", "year")
    Sheet(stringResource(R.string.app_add_repeat), close) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.selectableGroup()) {
                Option(stringResource(R.string.app_add_repeat_once), !on) { on = false }
                presets.forEach { unit ->
                    val preset = draft.copy(every = 1, unit = unit)
                    Option(frequency(preset), on && !custom && draft.every == 1 && draft.unit == unit) {
                        on = true
                        custom = false
                        draft = preset
                    }
                }
                Option(stringResource(R.string.app_add_repeat_custom), on && custom) {
                    on = true
                    custom = true
                }
            }
            if (on && custom) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    KText(stringResource(R.string.subs_repeats_every), color = c.ink2)
                    Stepper(draft.every, { draft = draft.copy(every = it) }, max = 365)
                }
                Segmented(
                    listOf(
                        "day" to pluralStringResource(R.plurals.subs_interval_day, draft.every, draft.every),
                        "week" to pluralStringResource(R.plurals.subs_interval_week, draft.every, draft.every),
                        "month" to pluralStringResource(R.plurals.subs_interval_month, draft.every, draft.every),
                        "year" to pluralStringResource(R.plurals.subs_interval_year, draft.every, draft.every),
                    ),
                    draft.unit,
                    { draft = draft.copy(unit = it) },
                    small = true,
                )
            }
            if (on) {
                if (m.kind == EntryKind.Expense) {
                    Segmented(
                        listOf("repetitive" to stringResource(R.string.subs_kind_bill), "subscription" to stringResource(R.string.subs_kind_subscription)),
                        draft.kind,
                        { draft = draft.copy(kind = it) },
                    )
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    KText(stringResource(R.string.subs_ends), Modifier.weight(1f))
                    Segmented(
                        listOf(false to stringResource(R.string.subs_never), true to (draft.endDay?.let { dayLabel(it) } ?: stringResource(R.string.subs_ends))),
                        ending,
                        {
                            ending = it
                            if (!it) draft = draft.copy(endDay = null)
                        },
                        Modifier.widthIn(max = 220.dp),
                        small = true,
                    )
                }
                if (ending) {
                    MonthGrid(month, draft.endDay, m.today, { draft = draft.copy(endDay = it) }, { month = it })
                }
                val autoLabel = stringResource(R.string.subs_auto_pay)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    KText(autoLabel, Modifier.weight(1f))
                    Switch(draft.autoPay, { draft = draft.copy(autoPay = it) }, autoLabel)
                }
                KText(stringResource(R.string.app_add_repeat_note), style = KoshaTheme.type.small, color = c.ink3)
            }
            Spacer(Modifier.size(4.dp))
            Button(
                stringResource(R.string.common_done),
                {
                    m.repeat = if (on) draft.copy(endDay = draft.endDay.takeIf { ending }) else null
                    close()
                },
                Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun Option(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(selected, role = Role.RadioButton, onClick = onClick).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        KText(label, Modifier.weight(1f))
        Radio(selected)
    }
}

@Composable
private fun dayLabel(day: java.time.LocalDate): String =
    day.format(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy", KoshaTheme.locale))

/** A small pill's 48dp touch area (8dp above and below its 32), read as one button. */
private fun Modifier.tap(label: String, onClick: () -> Unit) =
    clip(CircleShape).clickable(role = Role.Button, onClick = onClick).padding(vertical = 8.dp).clearAndSetSemantics { contentDescription = label }
