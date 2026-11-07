package app.getkosha.design

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

val Geist = FontFamily(
    Font(R.font.geist_regular, FontWeight.Normal),
    Font(R.font.geist_medium, FontWeight.Medium),
    Font(R.font.geist_semibold, FontWeight.SemiBold),
)
val GeistMono = FontFamily(
    Font(R.font.geist_mono_regular, FontWeight.Normal),
    Font(R.font.geist_mono_medium, FontWeight.Medium),
    // Geist Mono has no 600 here; the hero uses Geist.
    Font(R.font.geist_mono_medium, FontWeight.SemiBold),
)

private val base = TextStyle(
    fontFamily = Geist,
    fontSize = 15.sp,
    lineHeight = 1.35.em,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
)

/** Kosha's type scale (k.css). `num` and the amount styles follow Settings › Appearance › Number font. */
@Immutable
data class KoshaType(
    /** Big money: 40/600, cents in ink3. */
    val hero: TextStyle,
    val page: TextStyle,
    val title: TextStyle,
    /** Sheet titles. */
    val sheetTitle: TextStyle,
    val head: TextStyle,
    val body: TextStyle,
    val small: TextStyle,
    val xs: TextStyle,
    /** Uppercase section captions in ink3. */
    val caption: TextStyle,
    val button: TextStyle,
    /** Figures in lists and cards. */
    val num: TextStyle,
    /** The amount on Add: 60, dropping to 46 for long amounts. */
    val amount: TextStyle,
    /** The keypad's digits. */
    val key: TextStyle,
    /** The expression above the amount. */
    val expr: TextStyle,
) {
    companion object {
        fun of(figures: Figures): KoshaType {
            val figure = if (figures == Figures.Mono) {
                base.copy(fontFamily = GeistMono, letterSpacing = (-0.02).em, fontFeatureSettings = "tnum")
            } else {
                base.copy(fontFamily = Geist, letterSpacing = (-0.01).em, fontFeatureSettings = "tnum")
            }
            return KoshaType(
                hero = base.copy(fontSize = 40.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.035).em, lineHeight = 1.em, fontFeatureSettings = "tnum"),
                page = base.copy(fontSize = 34.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.03).em, lineHeight = 1.05.em),
                title = base.copy(fontSize = 22.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.02).em, lineHeight = 1.15.em),
                sheetTitle = base.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.01).em),
                head = base.copy(fontWeight = FontWeight.Medium),
                body = base,
                small = base.copy(fontSize = 13.sp),
                xs = base.copy(fontSize = 12.sp),
                caption = base.copy(fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.06.em),
                button = base.copy(fontSize = 16.sp, fontWeight = FontWeight.Medium),
                num = figure,
                amount = figure.copy(fontSize = 60.sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.04).em, lineHeight = 1.em),
                key = figure.copy(fontSize = 28.sp),
                expr = figure.copy(fontSize = 16.sp, letterSpacing = (-0.01).em),
            )
        }
    }
}

/** Text in a Kosha style. Defaults to body in ink. */
@Composable
fun KText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = KoshaTheme.type.body,
    color: Color = KoshaTheme.colors.ink,
    align: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = if (align != null) style.copy(color = color, textAlign = align) else style.copy(color = color),
        maxLines = maxLines,
        overflow = if (maxLines == Int.MAX_VALUE) TextOverflow.Clip else TextOverflow.Ellipsis,
    )
}
