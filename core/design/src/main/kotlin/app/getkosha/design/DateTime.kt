package app.getkosha.design

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * A month of days: weeks start on the language's first weekday, today is outlined, the picked day is filled
 * in the selection colour, and a dot marks days that already have entries. Future days stay pickable for
 * planned payments. Days are plain dates in the user's time zone; `today` comes from that zone, not the phone's.
 */
@Composable
fun MonthGrid(
    month: YearMonth,
    selected: LocalDate?,
    today: LocalDate,
    onPick: (LocalDate) -> Unit,
    onMonth: (YearMonth) -> Unit,
    modifier: Modifier = Modifier,
    hasEntries: (LocalDate) -> Boolean = { false },
) {
    val c = KoshaTheme.colors
    val locale = KoshaTheme.locale
    val first = WeekFields.of(locale).firstDayOfWeek
    val days = (0 until 7).map { first.plus(it.toLong()) }
    val lead = Math.floorMod(month.atDay(1).dayOfWeek.value - first.value, 7)
    val start = month.atDay(1).minusDays(lead.toLong())
    val weeks = (lead + month.lengthOfMonth() + 6) / 7
    Column(modifier) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            KText(
                month.format(DateTimeFormatter.ofPattern("LLLL yyyy", locale)),
                Modifier.weight(1f),
                style = KoshaTheme.type.head.copy(fontSize = 17.sp),
            )
            IconButton("caret-left", stringResource(R.string.shell_previous_month), { onMonth(month.minusMonths(1)) })
            IconButton("caret-right", stringResource(R.string.shell_next_month), { onMonth(month.plusMonths(1)) })
        }
        Row(Modifier.fillMaxWidth().height(28.dp)) {
            days.forEach { d ->
                KText(
                    d.getDisplayName(TextStyle.NARROW_STANDALONE, locale),
                    Modifier.weight(1f).clearAndSetSemantics { },
                    style = KoshaTheme.type.xs,
                    color = c.ink3,
                    align = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
        repeat(weeks) { w ->
            Row(Modifier.fillMaxWidth()) {
                repeat(7) { i ->
                    val day = start.plusDays((w * 7 + i).toLong())
                    DayCell(day, Modifier.weight(1f), inMonth = YearMonth.from(day) == month, today = today,
                        selected = day == selected, has = YearMonth.from(day) == month && hasEntries(day)) { onPick(day) }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    day: LocalDate,
    modifier: Modifier,
    inMonth: Boolean,
    today: LocalDate,
    selected: Boolean,
    has: Boolean,
    onClick: () -> Unit,
) {
    val c = KoshaTheme.colors
    val locale = KoshaTheme.locale
    Box(
        modifier
            .height(44.dp)
            .alpha(if (inMonth) 1f else 0.5f)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics {
                contentDescription = day.format(DateTimeFormatter.ofPattern("EEEE d MMMM", locale))
                this.selected = selected
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(38.dp)
                .clip(CircleShape)
                .then(
                    when {
                        selected -> Modifier.background(c.selection)
                        day == today -> Modifier.border(1.dp, c.line, CircleShape)
                        else -> Modifier
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            KText(
                day.dayOfMonth.toString(),
                style = KoshaTheme.type.num.let { if (selected) it.copy(fontWeight = FontWeight.Medium) else it },
                color = when {
                    selected -> c.onSelection
                    !inMonth || day.isAfter(today) -> c.ink3
                    else -> c.ink
                },
            )
        }
        if (has && !selected) {
            Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 3.dp).size(4.dp).clip(CircleShape).background(c.ink3))
        }
    }
}

/** A short label for a day: Today, Yesterday, or the weekday and date ("Fri 6"). */
@Composable
fun dayLabel(day: LocalDate, today: LocalDate): String = when (day) {
    today -> stringResource(R.string.add_today)
    today.minusDays(1) -> stringResource(R.string.add_yesterday)
    else -> day.format(DateTimeFormatter.ofPattern(if (day.year == today.year) "EEE d" else "d MMM yyyy", KoshaTheme.locale))
}

private const val ROW = 44
private const val VISIBLE = 5
// The wheels go round: a long list of the same values, started in the middle.
private const val LAPS = 200

/**
 * One wheel of the time picker, in the style of iOS: the value under the band is picked; rows fade and tilt
 * away from it. Flick, drag, or tap a row. TalkBack can step it up and down like a slider.
 */
@Composable
private fun Wheel(
    values: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    loop: Boolean = true,
) {
    val c = KoshaTheme.colors
    val count = if (loop) values.size * LAPS else values.size
    val offset = if (loop) values.size * (LAPS / 2) else 0
    val state = rememberLazyListState(initialFirstVisibleItemIndex = offset + selected)
    val scope = rememberCoroutineScope()
    val rowPx = with(LocalDensity.current) { ROW.dp.toPx() }
    val centre by remember {
        derivedStateOf { state.firstVisibleItemIndex + (state.firstVisibleItemScrollOffset / rowPx).roundToInt() }
    }
    // Report the value once the wheel comes to rest.
    LaunchedEffect(state) {
        snapshotFlow { state.isScrollInProgress to centre }
            .distinctUntilChanged()
            .collect { (moving, index) -> if (!moving) onSelect(Math.floorMod(index, values.size)) }
    }
    // A value set from outside (Now) turns the wheel to it.
    LaunchedEffect(selected) {
        if (Math.floorMod(centre, values.size) != selected && !state.isScrollInProgress) {
            state.animateScrollToItem(centre - Math.floorMod(centre, values.size) + selected)
        }
    }
    LazyColumn(
        modifier
            .width(96.dp)
            .height((ROW * VISIBLE).dp)
            .semantics {
                contentDescription = label
                stateDescription = values[selected]
                progressBarRangeInfo = ProgressBarRangeInfo(selected.toFloat(), 0f..(values.size - 1).toFloat(), values.size - 2)
                setProgress { v ->
                    scope.launch { state.animateScrollToItem(centre - Math.floorMod(centre, values.size) + v.roundToInt()) }
                    true
                }
            },
        state = state,
        flingBehavior = rememberSnapFlingBehavior(state),
        contentPadding = PaddingValues(vertical = (ROW * (VISIBLE / 2)).dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        items(count) { i ->
            val distance = abs(i - centre).coerceAtMost(3)
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(ROW.dp)
                    .clearAndSetSemantics { }
                    .clickable(interactionSource = null, indication = null) { scope.launch { state.animateScrollToItem(i) } }
                    .graphicsLayer {
                        val d = i - centre
                        alpha = when (distance) { 0 -> 1f; 1 -> 0.5f; 2 -> 0.22f; else -> 0f }
                        rotationX = (-d * 18f).coerceIn(-54f, 54f)
                        cameraDistance = 8 * density
                    },
                contentAlignment = Alignment.Center,
            ) {
                KText(
                    values[Math.floorMod(i, values.size)],
                    style = KoshaTheme.type.num.copy(
                        fontSize = when (distance) { 0 -> 26.sp; 1 -> 20.sp; else -> 18.sp },
                        fontWeight = if (distance == 0) FontWeight.Medium else FontWeight.Normal,
                    ),
                    color = c.ink,
                )
            }
        }
    }
}

/**
 * Hours and minutes on two wheels, with an AM/PM wheel when the language writes 12-hour time. A highlighted
 * band marks the picked time, and Now jumps to the current time in the user's time zone.
 */
@Composable
fun TimeWheel(
    hour: Int,
    minute: Int,
    onChange: (hour: Int, minute: Int) -> Unit,
    onNow: () -> Unit,
    modifier: Modifier = Modifier,
    twelveHour: Boolean = false,
) {
    val c = KoshaTheme.colors
    val minutes = remember { (0..59).map { "%02d".format(it) } }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(Modifier.fillMaxWidth().height((ROW * VISIBLE).dp), contentAlignment = Alignment.Center) {
            Box(Modifier.fillMaxWidth().height(ROW.dp).clip(RoundedCornerShape(12.dp)).background(c.soft))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                if (twelveHour) {
                    val hours12 = remember { (1..12).map { it.toString() } }
                    // Index 0 is 1 o'clock and 11 is 12; the AM/PM wheel keeps its half of the day.
                    Wheel(hours12, (hour + 11) % 12, { h -> onChange((h + 1) % 12 + (if (hour >= 12) 12 else 0), minute) },
                        stringResource(R.string.app_time_hours))
                } else {
                    val hours = remember { (0..23).map { "%02d".format(it) } }
                    Wheel(hours, hour, { onChange(it, minute) }, stringResource(R.string.app_time_hours))
                }
                KText(":", Modifier.clearAndSetSemantics { }, style = KoshaTheme.type.num.copy(fontSize = 26.sp), color = c.ink3)
                Wheel(minutes, minute, { onChange(hour, it) }, stringResource(R.string.app_time_minutes))
                if (twelveHour) {
                    val locale = KoshaTheme.locale
                    val periods = remember(locale) { java.text.DateFormatSymbols.getInstance(locale).amPmStrings.toList() }
                    Wheel(periods, if (hour >= 12) 1 else 0, { p -> onChange(hour % 12 + p * 12, minute) },
                        stringResource(R.string.app_time_period), loop = false)
                }
            }
            // Rows fade into the sheet towards the edges.
            Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().height(60.dp).background(Brush.verticalGradient(listOf(c.surface, Color.Transparent))))
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(60.dp).background(Brush.verticalGradient(listOf(Color.Transparent, c.surface))))
        }
        Button(stringResource(R.string.app_time_now), onNow, Modifier.fillMaxWidth(), style = ButtonStyle.Soft)
    }
}
