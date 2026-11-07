package app.getkosha.design

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** A tab: its label and Phosphor icon (regular when idle, fill in the selection colour when picked). */
data class Tab<T>(val value: T, val label: String, val icon: String)

/**
 * The tab bar: four tabs around the raised Add button (56×52, radius 18, ink). Glass ground with a hairline
 * on top, over the navigation bar.
 */
@Composable
fun <T> TabBar(
    tabs: List<Tab<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    addLabel: String,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = KoshaTheme.colors
    Column(modifier.fillMaxWidth().background(c.glass)) {
        Hairline()
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp).selectableGroup(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val half = tabs.size / 2
            tabs.forEachIndexed { i, tab ->
                if (i == half) {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Box(
                            Modifier
                                .size(56.dp, 52.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(c.ink)
                                .clickable(role = Role.Button, onClickLabel = addLabel, onClick = onAdd),
                            contentAlignment = Alignment.Center,
                        ) { PhIcon("plus", size = 26.dp, tint = c.onInk, contentDescription = addLabel) }
                    }
                }
                val on = tab.value == selected
                Column(
                    Modifier
                        .weight(1f)
                        .height(52.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .selectable(on, role = Role.Tab) { onSelect(tab.value) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
                ) {
                    PhIcon(tab.icon, size = 24.dp, tint = if (on) c.selection else c.ink3, fill = on)
                    KText(
                        tab.label,
                        style = KoshaTheme.type.xs.copy(fontSize = 11.sp, fontWeight = if (on) FontWeight.Medium else FontWeight.Normal),
                        color = if (on) c.ink else c.ink3,
                        maxLines = 1,
                        align = TextAlign.Center,
                    )
                }
            }
        }
    }
}
