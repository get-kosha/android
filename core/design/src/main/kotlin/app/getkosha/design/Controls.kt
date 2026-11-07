package app.getkosha.design

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** A white card with a hairline: radius 16, padding 16 (14 in compact). `tight` puts rows edge to edge. */
@Composable
fun Card(modifier: Modifier = Modifier, tight: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    val c = KoshaTheme.colors
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(c.surface)
            .border(1.dp, c.line, RoundedCornerShape(16.dp))
            .then(if (tight) Modifier else Modifier.padding(KoshaTheme.dims.cardPadding)),
        content = content,
    )
}

@Composable
fun Hairline(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(KoshaTheme.colors.line))
}

/** A list row: at least 52 high (48 compact), padding 10/16, gap 12. */
@Composable
fun ListRow(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    selected: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier
            .fillMaxWidth()
            .background(if (selected) KoshaTheme.colors.sel else Color.Transparent)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .heightIn(min = KoshaTheme.dims.row)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

/** A category or menu icon on a 36×36 soft tile. */
@Composable
fun IconTile(icon: String, modifier: Modifier = Modifier) {
    Box(
        modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(KoshaTheme.colors.soft),
        contentAlignment = Alignment.Center,
    ) { PhIcon(icon, size = 18.dp) }
}

/** An account's colour: an 8px rounded square. Hidden in neutral mode. */
@Composable
fun AccountDot(color: Color, modifier: Modifier = Modifier, size: Dp = 8.dp) {
    if (!KoshaTheme.colors.accountColours) return
    Box(modifier.size(size).clip(RoundedCornerShape(size * 3 / 8)).background(color))
}

/** A round 44dp icon button (48dp touch target). It always has a label for screen readers. */
@Composable
fun IconButton(
    icon: String,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    solid: Boolean = false,
    tint: Color = KoshaTheme.colors.ink2,
) {
    val c = KoshaTheme.colors
    Box(
        modifier.size(48.dp).clickable(onClickLabel = contentDescription, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .then(if (solid) Modifier.background(c.surface).border(1.dp, c.line, CircleShape) else Modifier),
            contentAlignment = Alignment.Center,
        ) { PhIcon(icon, size = 22.dp, tint = tint, contentDescription = contentDescription) }
    }
}

enum class ButtonStyle { Primary, Secondary, Soft, Danger }

enum class ButtonSize(val height: Dp, val radius: Dp, val fontSize: Int) {
    Large(52.dp, 14.dp, 16),
    Medium(44.dp, 12.dp, 15),
    Small(36.dp, 8.dp, 14),
}

/** Primary is ink with on-ink text; secondary is surface with a hairline; danger is bad text on a 10% tint. */
@Composable
fun Button(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: ButtonStyle = ButtonStyle.Primary,
    size: ButtonSize = ButtonSize.Large,
    enabled: Boolean = true,
    icon: String? = null,
) {
    val c = KoshaTheme.colors
    val shape = RoundedCornerShape(size.radius)
    val (ground, ink) = when (style) {
        ButtonStyle.Primary -> c.ink to c.onInk
        ButtonStyle.Secondary -> c.surface to c.ink
        ButtonStyle.Soft -> c.soft to c.ink
        ButtonStyle.Danger -> c.bad.copy(alpha = 0.1f) to c.bad
    }
    Row(
        modifier
            .defaultMinSize(minHeight = size.height)
            .alpha(if (enabled) 1f else 0.4f)
            .clip(shape)
            .background(ground)
            .then(if (style == ButtonStyle.Secondary) Modifier.border(1.dp, c.line, shape) else Modifier)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = if (size == ButtonSize.Small) 14.dp else 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) PhIcon(icon, size = 18.dp, tint = ink)
        KText(text, style = KoshaTheme.type.button.copy(fontSize = size.fontSize.sp), color = ink, maxLines = 1)
    }
}

/** A status pill: 22 high on soft, 12px text. */
@Composable
fun Pill(text: String, modifier: Modifier = Modifier, color: Color = KoshaTheme.colors.ink2) {
    Row(
        modifier.height(22.dp).clip(CircleShape).background(KoshaTheme.colors.soft).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { KText(text, style = KoshaTheme.type.xs, color = color, maxLines = 1) }
}

/**
 * A chip: 40 high (32 small), pill shaped, with a hairline. `on` gives it an ink outline; `soft` a soft ground
 * and no line. `leading` holds an icon or an account dot.
 */
@Composable
fun Chip(
    text: String,
    modifier: Modifier = Modifier,
    on: Boolean = false,
    soft: Boolean = false,
    small: Boolean = false,
    onClick: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
) {
    val c = KoshaTheme.colors
    Row(
        modifier
            .height(if (small) 32.dp else 40.dp)
            .clip(CircleShape)
            .background(if (soft) c.soft else c.surface)
            .then(
                when {
                    on -> Modifier.border(1.5.dp, c.ink, CircleShape)
                    soft -> Modifier
                    else -> Modifier.border(1.dp, c.line, CircleShape)
                },
            )
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = if (small) 12.dp else 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.invoke()
        KText(text, style = if (small) KoshaTheme.type.small else KoshaTheme.type.body, maxLines = 1)
    }
}

/** Segmented control: a soft track, 3dp inset, the picked segment on surface. Announces which one is picked. */
@Composable
fun <T> Segmented(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    small: Boolean = false,
) {
    val c = KoshaTheme.colors
    Row(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(c.soft)
            .padding(3.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEach { (value, label) ->
            val on = value == selected
            Box(
                Modifier
                    .weight(1f)
                    .height(if (small) 32.dp else 38.dp)
                    .then(if (on) Modifier.shadow(1.dp, RoundedCornerShape(9.dp)) else Modifier)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (on) c.surface else Color.Transparent)
                    .selectable(on, role = Role.Tab) { onSelect(value) },
                contentAlignment = Alignment.Center,
            ) {
                KText(
                    label,
                    style = (if (small) KoshaTheme.type.small else KoshaTheme.type.body)
                        .let { if (on) it.copy(fontWeight = KoshaTheme.type.head.fontWeight) else it },
                    color = if (on) c.ink else c.ink3,
                    maxLines = 1,
                )
            }
        }
    }
}

/** Switch: 46×28, knob 22, accent when on. Give it a label so TalkBack reads what it turns on. */
@Composable
fun Switch(checked: Boolean, onChange: (Boolean) -> Unit, label: String, modifier: Modifier = Modifier) {
    val c = KoshaTheme.colors
    val track by animateColorAsState(if (checked) c.brand else c.sel, label = "track")
    val knob by animateDpAsState(if (checked) 21.dp else 3.dp, label = "knob")
    Box(
        modifier
            .size(48.dp)
            .toggleable(checked, role = Role.Switch, onValueChange = onChange)
            .semantics { this.contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(46.dp, 28.dp).clip(CircleShape).background(track)) {
            Box(
                Modifier
                    .offset { IntOffset(knob.roundToPx(), 3.dp.roundToPx()) }
                    .size(22.dp)
                    .shadow(1.dp, CircleShape)
                    .clip(CircleShape)
                    .background(if (c.dark) Color(0xFFF3F3F4) else Color.White),
            )
        }
    }
}

/** Radio: 22 with a 12 dot in the selection colour. Usually drawn inside a selectable row. */
@Composable
fun Radio(selected: Boolean, modifier: Modifier = Modifier) {
    val c = KoshaTheme.colors
    Box(
        modifier.size(22.dp).clip(CircleShape).border(1.5.dp, if (selected) c.selection else c.line, CircleShape),
        contentAlignment = Alignment.Center,
    ) { if (selected) Box(Modifier.size(12.dp).clip(CircleShape).background(c.selection)) }
}

/** Tick box: 22, radius 7, filled with the selection colour and a check when on. */
@Composable
fun Tick(checked: Boolean, modifier: Modifier = Modifier) {
    val c = KoshaTheme.colors
    val shape = RoundedCornerShape(7.dp)
    Box(
        modifier
            .size(22.dp)
            .clip(shape)
            .background(if (checked) c.selection else Color.Transparent)
            .border(1.5.dp, if (checked) c.selection else c.line, shape),
        contentAlignment = Alignment.Center,
    ) { if (checked) PhIcon("check", size = 14.dp, tint = c.onSelection) }
}

/** A radio or tick that is its own 48dp touch target, for the kit and for rows without other content. */
@Composable
fun Toggle(checked: Boolean, onChange: (Boolean) -> Unit, label: String, tick: Boolean = false) {
    Box(
        Modifier
            .size(48.dp)
            .toggleable(checked, role = if (tick) Role.Checkbox else Role.RadioButton, onValueChange = onChange)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { if (tick) Tick(checked) else Radio(checked) }
}

/** − value + with a hairline, for "Repeats every N". */
@Composable
fun Stepper(value: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier, min: Int = 1, max: Int = 99) {
    val c = KoshaTheme.colors
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier.height(48.dp).clip(shape).background(c.surface).border(1.dp, c.line, shape),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepperKey("minus", stringResource(R.string.app_stepper_less), enabled = value > min) { onChange(value - 1) }
        KText(value.toString(), Modifier.widthIn(min = 24.dp), style = KoshaTheme.type.num, align = TextAlign.Center)
        StepperKey("plus", stringResource(R.string.app_stepper_more), enabled = value < max) { onChange(value + 1) }
    }
}

@Composable
private fun StepperKey(icon: String, label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(48.dp).alpha(if (enabled) 1f else 0.4f).clickable(enabled = enabled, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { PhIcon(icon, size = 20.dp, contentDescription = label) }
}

/** Small uppercase caption in ink3, for section headings. */
@Composable
fun Caption(text: String, modifier: Modifier = Modifier) {
    KText(text.uppercase(), modifier, style = KoshaTheme.type.caption, color = KoshaTheme.colors.ink3)
}
