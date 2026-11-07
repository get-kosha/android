package app.getkosha.design

import android.app.Activity
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import app.getkosha.model.Money
import java.util.Locale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Every colour, size and type style Kosha uses. Values come from design/source/k.css; CSS px are used as dp 1:1.

enum class Accent { Teal, Blue, Amber, Rose, Graphite }

enum class Density { Comfortable, Compact }

/** Settings › Appearance › Number font: Geist Mono, or Geist with tabular figures. */
enum class Figures { Mono, Proportional }

@Immutable
data class KoshaColors(
    val bg: Color,
    val surface: Color,
    val soft: Color,
    val sel: Color,
    val line: Color,
    val ink: Color,
    val ink2: Color,
    val ink3: Color,
    val onInk: Color,
    val pos: Color,
    val warn: Color,
    val bad: Color,
    val brand: Color,
    val onBrand: Color,
    val bar: Color,
    val bar2: Color,
    val scrim: Color,
    val glass: Color,
    /** Account colours, s1 to s6, given out in account order. */
    val series: List<Color>,
    val dark: Boolean,
    /** Account colours off: selections take ink instead of the accent, and account squares hide. */
    val neutral: Boolean,
) {
    /** What a picked tile, day, radio or tab takes. */
    val selection get() = if (neutral) ink else brand
    val onSelection get() = if (neutral) onInk else onBrand
    val accountColours get() = !neutral

    /** The colour of the account at this position in the user's account order. */
    fun account(index: Int) = series[Math.floorMod(index, series.size)]

    companion object {
        fun of(dark: Boolean, accent: Accent = Accent.Teal, neutral: Boolean = false): KoshaColors {
            val base = if (dark) Dark else Light
            return base.copy(brand = accentColor(accent, dark), neutral = neutral)
        }

        private fun accentColor(accent: Accent, dark: Boolean) = when (accent) {
            Accent.Teal -> if (dark) Color(0xFF2DD4BF) else Color(0xFF0F766E)
            Accent.Blue -> if (dark) Color(0xFF60A5FA) else Color(0xFF2563EB)
            Accent.Amber -> if (dark) Color(0xFFFBBF24) else Color(0xFFB45309)
            Accent.Rose -> if (dark) Color(0xFFFB7185) else Color(0xFFBE123C)
            Accent.Graphite -> if (dark) Color(0xFFE4E4E7) else Color(0xFF27272A)
        }

        private val Light = KoshaColors(
            bg = Color(0xFFF6F6F4), surface = Color(0xFFFFFFFF), soft = Color(0xFFF0F0ED), sel = Color(0xFFEBEBE7),
            line = Color(0xFFE4E4E0), ink = Color(0xFF111113), ink2 = Color(0xFF46464D), ink3 = Color(0xFF6B6B73),
            onInk = Color(0xFFF6F6F4), pos = Color(0xFF0F766E), warn = Color(0xFFB45309), bad = Color(0xFFB42318),
            brand = Color(0xFF0F766E), onBrand = Color(0xFFFFFFFF), bar = Color(0xFF1C1C1F), bar2 = Color(0xFFCFCFCA),
            scrim = Color(0x57111113), glass = Color(0xF0FFFFFF),
            series = listOf(
                Color(0xFF2A78D6), Color(0xFFE87BA4), Color(0xFFEDA100),
                Color(0xFF6250D6), Color(0xFFEB6834), Color(0xFF1BAF7A),
            ),
            dark = false, neutral = false,
        )

        private val Dark = KoshaColors(
            bg = Color(0xFF0B0B0C), surface = Color(0xFF131315), soft = Color(0xFF1C1C1F), sel = Color(0xFF232327),
            line = Color(0xFF27272B), ink = Color(0xFFF3F3F4), ink2 = Color(0xFFBDBDC4), ink3 = Color(0xFF90909A),
            onInk = Color(0xFF0B0B0C), pos = Color(0xFF2DD4BF), warn = Color(0xFFF59E0B), bad = Color(0xFFF87171),
            brand = Color(0xFF2DD4BF), onBrand = Color(0xFF0B0B0C), bar = Color(0xFFECECEE), bar2 = Color(0xFF3B3B41),
            scrim = Color(0x8C000000), glass = Color(0xF0131315),
            series = listOf(
                Color(0xFF3987E5), Color(0xFFD55181), Color(0xFFC98500),
                Color(0xFF9085E9), Color(0xFFD95926), Color(0xFF199E70),
            ),
            dark = true, neutral = false,
        )
    }
}

/**
 * Sizes that change with Settings › Appearance › Density. Compact follows the web's compact density, except
 * that rows never go below the 48dp touch target.
 */
@Immutable
data class KoshaDims(val row: Dp, val settingsRow: Dp, val cardPadding: Dp) {
    companion object {
        fun of(density: Density) = when (density) {
            Density.Comfortable -> KoshaDims(row = 52.dp, settingsRow = 56.dp, cardPadding = 16.dp)
            Density.Compact -> KoshaDims(row = 48.dp, settingsRow = 48.dp, cardPadding = 14.dp)
        }
    }
}

private val LocalColors = staticCompositionLocalOf { KoshaColors.of(dark = false) }
private val LocalType = staticCompositionLocalOf { KoshaType.of(Figures.Mono) }
private val LocalDims = staticCompositionLocalOf { KoshaDims.of(Density.Comfortable) }
private val LocalLocale = staticCompositionLocalOf { Money.locale("en") }

@Composable
fun KoshaTheme(
    dark: Boolean = isSystemInDarkTheme(),
    accent: Accent = Accent.Teal,
    neutral: Boolean = false,
    density: Density = Density.Comfortable,
    figures: Figures = Figures.Mono,
    /** The app's language, which sets how money, dates and numbers are written. */
    language: String = LocalConfiguration.current.locales[0].language,
    content: @Composable () -> Unit,
) {
    val colors = KoshaColors.of(dark, accent, neutral)
    // Status and navigation bar icons follow Kosha's theme, which can differ from the phone's.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    CompositionLocalProvider(
        LocalColors provides colors,
        LocalType provides KoshaType.of(figures),
        LocalDims provides KoshaDims.of(density),
        LocalLocale provides Money.locale(language),
        LocalIndication provides ripple(color = colors.ink),
        content = content,
    )
}

object KoshaTheme {
    val colors: KoshaColors @Composable @ReadOnlyComposable get() = LocalColors.current
    val type: KoshaType @Composable @ReadOnlyComposable get() = LocalType.current
    val dims: KoshaDims @Composable @ReadOnlyComposable get() = LocalDims.current
    val locale: Locale @Composable @ReadOnlyComposable get() = LocalLocale.current
}
