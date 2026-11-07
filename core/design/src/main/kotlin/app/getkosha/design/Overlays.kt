package app.getkosha.design

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * A text field for words only (payee, note, names, search, server address, keys): Kosha never opens the system
 * keyboard for amounts. At rest a hairline; focused a 2dp ink border with a caret in the selection colour; with
 * an [error], a red border and the message below.
 */
@Composable
fun Field(
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    label: String? = null,
    error: String? = null,
    icon: String? = null,
    keyboard: KeyboardOptions = KeyboardOptions.Default,
    singleLine: Boolean = true,
) {
    val c = KoshaTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(14.dp)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (label != null) KText(label, style = KoshaTheme.type.small, color = c.ink3)
        BasicTextField(
            value = value,
            onValueChange = onChange,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { if (error != null) error(error) },
            textStyle = KoshaTheme.type.body.copy(fontSize = 16.sp, color = c.ink),
            cursorBrush = SolidColor(c.selection),
            keyboardOptions = keyboard,
            singleLine = singleLine,
            interactionSource = interaction,
            decorationBox = { inner ->
                Row(
                    Modifier
                        .heightIn(min = 48.dp)
                        .clip(shape)
                        .background(c.surface)
                        .border(
                            width = if (focused || error != null) 2.dp else 1.dp,
                            color = when {
                                error != null -> c.bad
                                focused -> c.ink
                                else -> c.line
                            },
                            shape = shape,
                        )
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (icon != null) PhIcon(icon, size = 18.dp, tint = c.ink3)
                    Box(Modifier.weight(1f)) {
                        if (value.isEmpty()) KText(placeholder, style = KoshaTheme.type.body.copy(fontSize = 16.sp), color = c.ink3, maxLines = 1)
                        inner()
                    }
                }
            },
        )
        if (error != null) KText(error, style = KoshaTheme.type.small, color = c.bad)
    }
}

/**
 * The one sheet pattern for every picker: radius 24 on top, a grab handle, a title with a close button, sized
 * to its content, dragged down or tapped outside to close. Material provides only the drag, insets and back.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Sheet(
    title: String,
    onDismiss: () -> Unit,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = KoshaTheme.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = c.surface,
        contentColor = c.ink,
        scrimColor = c.scrim,
        tonalElevation = 0.dp,
        dragHandle = {
            Box(Modifier.padding(top = 8.dp, bottom = 10.dp).size(36.dp, 5.dp).clip(CircleShape).background(c.sel))
        },
    ) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 18.dp).navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    KText(title, Modifier.semantics { heading() }, style = KoshaTheme.type.sheetTitle)
                    if (subtitle != null) KText(subtitle, style = KoshaTheme.type.small, color = c.ink3)
                }
                IconButton("x", stringResource(R.string.common_close), onDismiss)
            }
            Box(Modifier.height(8.dp))
            content()
        }
    }
}

/** One option of a [ListPicker]: what is saved, what is shown, and an optional line under it. */
data class ListOption(val value: String, val label: String, val detail: String? = null)

/**
 * The one searchable radio list, for time zones, currencies, languages and "Move to…". Search matches the
 * label, the detail and the value.
 */
@Composable
fun ListPicker(
    options: List<ListOption>,
    selected: String?,
    onPick: (ListOption) -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }
    val shown = remember(query, options) {
        if (query.isBlank()) options
        else options.filter { o -> listOfNotNull(o.label, o.detail, o.value).any { it.contains(query.trim(), ignoreCase = true) } }
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Field(query, { query = it }, placeholder = stringResource(R.string.common_search), icon = "magnifying-glass")
        LazyColumn(Modifier.heightIn(max = 420.dp)) {
            items(shown, key = { it.value }) { o ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = KoshaTheme.dims.row)
                        .selectable(o.value == selected, role = Role.RadioButton) { onPick(o) }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        KText(o.label)
                        if (o.detail != null) KText(o.detail, style = KoshaTheme.type.small, color = KoshaTheme.colors.ink3)
                    }
                    Radio(o.value == selected)
                }
            }
        }
    }
}

/** An account in the account picker: its colour, name, type and currency, balance, and a radio. */
data class AccountOption(val id: String, val name: String, val detail: String?, val balance: String, val colorIndex: Int)

/**
 * The account picker's rows, inside a tight card. Recent first; archived accounts are left out by the caller.
 * Long names wrap rather than push the balance off.
 */
@Composable
fun AccountList(accounts: List<AccountOption>, selected: String?, onPick: (AccountOption) -> Unit, modifier: Modifier = Modifier) {
    val c = KoshaTheme.colors
    Card(modifier, tight = true) {
        accounts.forEachIndexed { i, a ->
            if (i > 0) Hairline()
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = KoshaTheme.dims.row + 16.dp)
                    .selectable(a.id == selected, role = Role.RadioButton) { onPick(a) }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AccountDot(c.account(a.colorIndex), size = 12.dp)
                Column(Modifier.weight(1f)) {
                    KText(a.name)
                    if (a.detail != null) KText(a.detail, style = KoshaTheme.type.small, color = c.ink3)
                }
                KText(a.balance, style = KoshaTheme.type.num, color = c.ink2)
                Radio(a.id == selected)
            }
        }
    }
}

/** What the toast shows, and what Undo does. */
data class Toast(val message: String, val undoLabel: String? = null, val onUndo: (() -> Unit)? = null)

/** Holds the toast on screen. Each toast stays five seconds, or until Undo. */
@Stable
class ToastState {
    var current by mutableStateOf<Toast?>(null)
        private set

    fun show(toast: Toast) {
        current = toast
    }

    fun dismiss() {
        current = null
    }
}

/** Toasts at the top: 16 from the edges, ink ground, min height 52, radius 14, with Undo inside. */
@Composable
fun ToastHost(state: ToastState, modifier: Modifier = Modifier) {
    val c = KoshaTheme.colors
    val toast = state.current
    LaunchedEffect(toast) {
        if (toast != null) {
            delay(5_000)
            state.dismiss()
        }
    }
    AnimatedVisibility(
        visible = toast != null,
        modifier = modifier.statusBarsPadding().padding(horizontal = 16.dp, vertical = 4.dp),
        enter = slideInVertically { -it } + fadeIn(),
        exit = slideOutVertically { -it } + fadeOut(),
    ) {
        val shown = remember(toast) { toast } ?: return@AnimatedVisibility
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .shadow(12.dp, RoundedCornerShape(14.dp))
                .clip(RoundedCornerShape(14.dp))
                .background(c.ink)
                .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            KText(shown.message, Modifier.weight(1f), style = KoshaTheme.type.body.copy(fontSize = 14.sp), color = c.onInk)
            if (shown.onUndo != null && shown.undoLabel != null) {
                Box(
                    Modifier
                        .heightIn(min = 48.dp)
                        .clickable(role = Role.Button) {
                            shown.onUndo.invoke()
                            state.dismiss()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier.height(36.dp).clip(RoundedCornerShape(9.dp)).background(c.onInk.copy(alpha = 0.14f)).padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        KText(shown.undoLabel, style = KoshaTheme.type.body.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium), color = c.onInk)
                    }
                }
            }
        }
    }
}
