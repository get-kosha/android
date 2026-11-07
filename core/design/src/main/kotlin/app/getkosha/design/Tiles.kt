package app.getkosha.design

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** A dashed rounded outline, for empty tiles and guesses. */
fun Modifier.dashedBorder(width: Dp, color: Color, radius: Dp) = drawWithContent {
    drawContent()
    val w = width.toPx()
    drawRoundRect(
        color = color,
        topLeft = Offset(w / 2, w / 2),
        size = Size(size.width - w, size.height - w),
        cornerRadius = CornerRadius(radius.toPx()),
        style = Stroke(w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))),
    )
}

/** The AI sparkle, in the accent colour. Only drawn where AI is on. */
@Composable
fun Sparkle(modifier: Modifier = Modifier, size: Dp = 14.dp) {
    PhIcon("sparkle", modifier, size = size, tint = KoshaTheme.colors.brand, fill = true)
}

/** How sure a picked value is. */
enum class Pick {
    /** Nothing yet: dashed, in ink3. */
    Empty,
    /** Kosha's guess from history (or the AI's, with [aiGuess]): dashed tile or ink2 label. One tap accepts. */
    Guess,
    /** Picked or confirmed. */
    Picked,
}

/**
 * One tile of the Add screen's tile row (account, category, date, note): 58 high, radius 14, icon 18 over a
 * 12.5px label. [leading] replaces the icon, for an account's colour square.
 */
@Composable
fun FieldTile(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: String? = null,
    pick: Pick = Pick.Picked,
    aiGuess: Boolean = false,
    description: String = label,
    leading: (@Composable () -> Unit)? = null,
) {
    val c = KoshaTheme.colors
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier
            .heightIn(min = 58.dp)
            .clip(shape)
            .background(c.surface)
            .then(
                if (pick == Pick.Empty) Modifier.dashedBorder(1.dp, c.line, 14.dp) else Modifier.border(1.dp, c.line, shape),
            )
            .clickable(role = Role.Button, onClickLabel = description, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
            when {
                leading != null -> leading()
                icon != null -> PhIcon(icon, size = 18.dp, tint = if (pick == Pick.Empty) c.ink3 else c.ink2)
            }
            KText(
                label,
                style = KoshaTheme.type.xs.copy(fontSize = 12.5.sp),
                color = when (pick) {
                    Pick.Empty -> c.ink3
                    Pick.Guess -> c.ink2
                    Pick.Picked -> c.ink
                },
                maxLines = 1,
                align = TextAlign.Center,
            )
        }
        // The corner sits 5dp from the top and 7dp from the right edge of the tile.
        if (aiGuess) Sparkle(Modifier.align(Alignment.TopEnd).offset(x = (-1).dp, y = (-3).dp), size = 10.dp)
    }
}

/** Tiles in equal columns with a 6dp gap: four on Add, two in Transfer mode. */
@Composable
fun TileRow(modifier: Modifier = Modifier, content: @Composable TileRowScope.() -> Unit) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        TileRowScope(this).content()
    }
}

class TileRowScope(private val row: androidx.compose.foundation.layout.RowScope) {
    val cell: Modifier get() = with(row) { Modifier.weight(1f) }
}

/** A category for the picker: its icon name (see [categoryIconName]) and label. */
data class CategoryTile(val id: String, val icon: String, val label: String)

/**
 * The category grid: five a row, gap 4, tiles 70 high. Picked: an outline in the selection colour. Guess:
 * dashed, with the sparkle when AI made it. Labels wrap at large font sizes rather than vanish.
 */
@Composable
fun CategoryGrid(
    categories: List<CategoryTile>,
    picked: String?,
    onPick: (CategoryTile) -> Unit,
    modifier: Modifier = Modifier,
    guess: String? = null,
    aiGuess: Boolean = false,
    guessLabel: String = "",
) {
    val c = KoshaTheme.colors
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        categories.chunked(5).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEach { cat ->
                    val on = cat.id == picked
                    val guessed = !on && cat.id == guess
                    val shape = RoundedCornerShape(14.dp)
                    Box(
                        Modifier
                            .weight(1f)
                            .heightIn(min = 70.dp)
                            .clip(shape)
                            .then(
                                when {
                                    on -> Modifier.border(1.5.dp, c.selection, shape)
                                    guessed -> Modifier.dashedBorder(1.5.dp, c.line, 14.dp)
                                    else -> Modifier
                                },
                            )
                            .clickable(role = Role.RadioButton) { onPick(cat) }
                            .semantics { if (guessed) stateDescription = guessLabel }
                            .padding(horizontal = 2.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            PhIcon(cat.icon, size = 24.dp, tint = if (on) c.selection else c.ink2)
                            KText(
                                cat.label,
                                style = KoshaTheme.type.xs.let { if (on) it.copy(fontWeight = FontWeight.Medium) else it },
                                color = if (on) c.ink else c.ink2,
                                maxLines = 1,
                                align = TextAlign.Center,
                            )
                        }
                        if (guessed && aiGuess) Sparkle(Modifier.align(Alignment.TopEnd).offset(x = (-5).dp, y = (-3).dp), size = 11.dp)
                    }
                }
                repeat(5 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
