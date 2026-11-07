package app.getkosha.design

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em

private val Phosphor = FontFamily(Font(R.font.phosphor))
private val PhosphorFill = FontFamily(Font(R.font.phosphor_fill))

/** Phosphor's icon names and codepoints (res/raw/phosphor.txt, from scripts/phosphor-codes.py). */
private object PhosphorCodes {
    @Volatile private var codes: Map<String, String>? = null

    fun get(context: Context, name: String): String? {
        val map = codes ?: context.resources.openRawResource(R.raw.phosphor).bufferedReader().useLines { lines ->
            lines.associate { line ->
                val (n, hex) = line.split(' ')
                n to String(Character.toChars(hex.toInt(16)))
            }
        }.also { codes = it }
        return map[name]
    }
}

/**
 * A Phosphor icon by name, such as "shopping-cart": regular weight, or fill for selected tabs and the AI
 * sparkle. Decorative unless it has a [contentDescription]. Icons keep their size at every font scale.
 */
@Composable
fun PhIcon(
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
    tint: Color = KoshaTheme.colors.ink2,
    fill: Boolean = false,
    contentDescription: String? = null,
) {
    val glyph = PhosphorCodes.get(LocalContext.current, name) ?: PhosphorCodes.get(LocalContext.current, "tag")!!
    val fontSize = with(LocalDensity.current) { size.toSp() }
    Box(
        modifier
            .size(size)
            .clearAndSetSemantics { if (contentDescription != null) this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            glyph,
            style = TextStyle(
                fontFamily = if (fill) PhosphorFill else Phosphor,
                fontSize = fontSize,
                lineHeight = 1.em,
                color = tint,
                platformStyle = PlatformTextStyle(includeFontPadding = false),
            ),
        )
    }
}

/**
 * A category's icon. The server stores an emoji, or `ph:<name>` for one picked from Phosphor; each emoji maps
 * to the closest Phosphor icon, as on the web (src/components/category-icon.tsx). Unknown ones show a tag.
 */
fun categoryIconName(icon: String?): String {
    if (icon == null) return "tag"
    if (icon.startsWith("ph:")) return icon.removePrefix("ph:")
    return BY_EMOJI[icon.replace("️", "")] ?: "tag"
}

private val BY_EMOJI = mapOf(
    "↔" to "arrows-left-right", "💱" to "currency-circle-dollar", "🍽" to "fork-knife", "🛒" to "shopping-cart",
    "🥤" to "martini", "📊" to "chart-line", "🛍" to "shopping-bag", "🚋" to "tram", "🌐" to "globe-simple",
    "⚡" to "lightning", "💊" to "pill", "👕" to "t-shirt", "✈" to "airplane-tilt", "🛋" to "couch",
    "🎮" to "game-controller", "📄" to "file-text", "📱" to "device-mobile", "🎁" to "gift", "↩" to "arrow-u-up-left",
    "🏧" to "money", "🍿" to "popcorn", "🏠" to "house", "🪙" to "coins", "💸" to "hand-coins", "🏛" to "bank",
    "💵" to "money", "🐷" to "piggy-bank", "🌸" to "flower", "🍼" to "baby", "🎣" to "fish-simple",
    "⚽" to "soccer-ball", "🧾" to "receipt", "💼" to "briefcase", "🚗" to "car", "⚖" to "scales", "☕" to "coffee",
    "💻" to "laptop",
)
