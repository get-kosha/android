package app.getkosha.settings

import android.icu.text.ListFormatter
import android.icu.text.RelativeDateTimeFormatter
import android.icu.util.ULocale
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.foundation.text.BasicText
import app.getkosha.design.Button
import app.getkosha.design.ButtonSize
import app.getkosha.design.ButtonStyle
import app.getkosha.design.Card
import app.getkosha.design.Field
import app.getkosha.design.Hairline
import app.getkosha.design.IconButton
import app.getkosha.design.KText
import app.getkosha.design.KoshaTheme
import app.getkosha.design.ListOption
import app.getkosha.design.ListPicker
import app.getkosha.design.MonthGrid
import app.getkosha.design.PhIcon
import app.getkosha.design.Pill
import app.getkosha.design.R
import app.getkosha.design.Segmented
import app.getkosha.design.Sheet
import app.getkosha.design.categoryIconName
import app.getkosha.model.Categories as Cats
import app.getkosha.model.Category
import app.getkosha.model.Dates
import app.getkosha.model.Money
import app.getkosha.ui.LocalNav
import app.getkosha.ui.LoadingNote
import app.getkosha.ui.Page
import app.getkosha.ui.Route
import app.getkosha.ui.meta
import app.getkosha.ui.rememberKosha
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Currency
import kotlin.math.roundToInt

// Currencies, Categories (and one category), Events and Payee rules.

// ---------- Currencies ----------

/** "3 hours ago" in the app's language, or "just now". */
@Composable
fun ago(unixSeconds: Long): String {
    val locale = KoshaTheme.locale
    val s = System.currentTimeMillis() / 1000 - unixSeconds
    if (s < 90) return stringResource(R.string.settings_currencies_just_now)
    val f = RelativeDateTimeFormatter.getInstance(ULocale.forLocale(locale))
    return when {
        s < 3600 -> f.format((s / 60.0).roundToInt().toDouble(), RelativeDateTimeFormatter.Direction.LAST, RelativeDateTimeFormatter.RelativeUnit.MINUTES)
        s < 86400 * 1.5 -> f.format((s / 3600.0).roundToInt().toDouble(), RelativeDateTimeFormatter.Direction.LAST, RelativeDateTimeFormatter.RelativeUnit.HOURS)
        else -> f.format((s / 86400.0).roundToInt().toDouble(), RelativeDateTimeFormatter.Direction.LAST, RelativeDateTimeFormatter.RelativeUnit.DAYS)
    }
}

/** A currency's name in the app's language, or its code when Java doesn't know it. */
fun currencyName(code: String, locale: java.util.Locale): String =
    runCatching { Currency.getInstance(code.uppercase()).getDisplayName(locale) }.getOrDefault(code.uppercase())

/**
 * Currencies: the person's currencies in order, held and dragged to reorder (the one on top is the default, so
 * net worth and totals add up in it), add and remove, and the exchange rates with Refresh.
 */
@Composable
fun CurrenciesPage() {
    val nav = LocalNav.current
    val kosha = rememberKosha()
    val m = meta() ?: return
    val c = KoshaTheme.colors
    val w = rememberWriter()
    val locale = KoshaTheme.locale
    val used = m.wallets.filterNot { it.isArchived }.map { it.currency.lowercase() }
    val saved = currencyOrder(m.primary, m.order, used)
    var items by remember(saved) { mutableStateOf(saved) }
    var adding by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    val save = { next: List<String> ->
        items = next
        w.run({
            kosha.api.send(
                "PATCH",
                "/api/v1/settings",
                buildJsonObject {
                    put("primaryCurrency", JsonPrimitive(next.first()))
                    put("currencyOrder", JsonArray(next.map(::JsonPrimitive)))
                },
            )
        })
    }
    val (prefix, suffix) = Money.affixes(m.primary, locale)
    val one = prefix + "1" + suffix

    Page(stringResource(R.string.settings_section_currencies), onBack = { nav.popBackStack() }) {
        GroupCaption(stringResource(R.string.settings_row_currencies))
        Card(Modifier.fillMaxWidth(), tight = true) {
            var dragging by remember { mutableIntStateOf(-1) }
            var offset by remember { mutableFloatStateOf(0f) }
            var rowHeight by remember { mutableFloatStateOf(1f) }
            val target = if (dragging < 0) -1 else (dragging + (offset / rowHeight).roundToInt()).coerceIn(0, items.lastIndex)
            val moveUp = stringResource(R.string.app_set_move_up)
            val moveDown = stringResource(R.string.app_set_move_down)
            val line = c.line
            items.forEachIndexed { i, code ->
                // While a row is dragged, the rows it passes make room for it.
                val shift = when {
                    dragging < 0 || i == dragging -> 0f
                    i in (dragging + 1)..target -> -rowHeight
                    i in target until dragging -> rowHeight
                    else -> 0f
                }
                val accounts = used.count { it == code }
                val r = if (i == 0) null else rate(m.primary, code, m.rates)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .zIndex(if (i == dragging) 1f else 0f)
                        .graphicsLayer { translationY = if (i == dragging) offset else shift }
                        .background(c.surface)
                        .drawBehind { if (i > 0 && i != dragging) drawLine(line, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx()) }
                        .onSizeChanged { rowHeight = it.height.toFloat() }
                        .pointerInput(items) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { dragging = i; offset = 0f },
                                onDrag = { change, amount -> change.consume(); offset += amount.y },
                                onDragEnd = {
                                    val to = (dragging + (offset / rowHeight).roundToInt()).coerceIn(0, items.lastIndex)
                                    val from = dragging
                                    dragging = -1
                                    offset = 0f
                                    if (to != from) save(items.moved(from, to))
                                },
                                onDragCancel = { dragging = -1; offset = 0f },
                            )
                        }
                        .semantics {
                            customActions = listOfNotNull(
                                if (i > 0) CustomAccessibilityAction(moveUp) { save(items.moved(i, i - 1)); true } else null,
                                if (i < items.lastIndex) CustomAccessibilityAction(moveDown) { save(items.moved(i, i + 1)); true } else null,
                            )
                        }
                        .heightIn(min = KoshaTheme.dims.settingsRow)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (items.size > 1) PhIcon("dots-six-vertical", size = 18.dp, tint = c.ink3, contentDescription = stringResource(R.string.settings_currencies_reorder, code.uppercase()))
                    KText(code.uppercase(), Modifier.width(44.dp), style = KoshaTheme.type.num.copy(fontWeight = KoshaTheme.type.head.fontWeight))
                    Column(Modifier.weight(1f)) {
                        KText(currencyName(code, locale), style = KoshaTheme.type.head)
                        val detail = listOfNotNull(
                            if (accounts > 0) pluralStringResource(R.plurals.accounts_count, accounts, accounts) else null,
                            r?.let { "${fmtRate(it)} = $one" },
                        ).joinToString(" · ")
                        if (detail.isNotEmpty()) KText(detail, style = KoshaTheme.type.small, color = c.ink3)
                    }
                    if (i == 0) Pill(stringResource(R.string.settings_currencies_default), color = c.brand)
                    else if (accounts == 0) {
                        IconButton("x", stringResource(R.string.common_remove) + " " + code.uppercase(), { save(items.filterNot { it == code }) })
                    }
                }
            }
            Hairline()
            Row(
                Modifier.fillMaxWidth().clickable(role = Role.Button) { adding = true }.heightIn(min = KoshaTheme.dims.settingsRow).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PhIcon("plus", size = 18.dp, tint = c.ink2)
                KText(stringResource(R.string.app_set_add_currency), color = c.ink2)
            }
        }
        Hint(stringResource(R.string.app_set_currencies_note))

        GroupCaption(stringResource(R.string.settings_currencies_rates))
        Card(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val fresh = m.ratesUpdated > 0 && System.currentTimeMillis() / 1000 - m.ratesUpdated < 86400 * 1.5
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PhIcon(if (fresh) "check-circle" else "warning-circle", size = 18.dp, tint = if (fresh) c.pos else c.warn)
                    KText(stringResource(if (fresh) R.string.settings_currencies_up_to_date else R.string.settings_row_rates_source), Modifier.weight(1f), style = KoshaTheme.type.head)
                    Button(
                        stringResource(if (refreshing) R.string.settings_currencies_refreshing else R.string.settings_currencies_refresh),
                        {
                            refreshing = true
                            w.run({ kosha.api.send("POST", "/api/v1/exchange-rates/refresh", JsonObject(emptyMap())) }, always = { refreshing = false })
                        },
                        style = ButtonStyle.Secondary,
                        size = ButtonSize.Small,
                        icon = "arrow-clockwise",
                        enabled = !refreshing,
                    )
                }
                KText(
                    if (m.ratesUpdated > 0) stringResource(R.string.settings_currencies_updated, ago(m.ratesUpdated)) else stringResource(R.string.settings_currencies_never_updated),
                    style = KoshaTheme.type.small,
                    color = c.ink2,
                )
                val others = items.drop(1).filter { m.rates[it] != null }
                if (others.isNotEmpty()) {
                    Hairline()
                    others.forEach { code ->
                        Row {
                            KText(stringResource(R.string.app_set_rate_in, m.primary.uppercase(), code.uppercase()), Modifier.weight(1f), style = KoshaTheme.type.small, color = c.ink3)
                            KText(rate(m.primary, code, m.rates)?.let(::fmtRate) ?: "", style = KoshaTheme.type.num.copy(fontSize = KoshaTheme.type.small.fontSize))
                        }
                    }
                }
            }
        }
    }

    if (adding) {
        Sheet(stringResource(R.string.app_set_add_currency), { adding = false }) {
            val options = remember(m.rates, items) {
                m.rates.keys.filter { it.length == 3 && it !in items && runCatching { Currency.getInstance(it.uppercase()) }.isSuccess }
                    .map { ListOption(it, currencyName(it, locale), it.uppercase()) }
                    .sortedBy { it.label }
            }
            ListPicker(options, null, { o -> adding = false; save(items + o.value) })
        }
    }
}

// ---------- Categories ----------

/** "124 transactions · 1 recurring · 3 rules", or "Not used yet". */
@Composable
fun usage(cat: Category): String = listOfNotNull(
    if (cat.transactionCount > 0) pluralStringResource(R.plurals.settings_cat_count_tx, cat.transactionCount, cat.transactionCount) else null,
    if (cat.recurringCount > 0) pluralStringResource(R.plurals.settings_cat_count_recurring, cat.recurringCount, cat.recurringCount) else null,
    if (cat.ruleCount > 0) pluralStringResource(R.plurals.settings_cat_count_rules, cat.ruleCount, cat.ruleCount) else null,
).joinToString(" · ").ifEmpty { stringResource(R.string.settings_cat_unused) }

/** Top-level categories, each followed by its subcategories (categories.tsx tree). */
fun tree(cats: List<Category>): List<Category> {
    val tops = cats.filter { c -> c.parentId == null || cats.none { it.id == c.parentId } }
    return tops.flatMap { t -> listOf(t) + cats.filter { it.parentId == t.id } }
}

@Composable
private fun loadCategories() = loaded("categories-all") { get<CategoryList>("/api/v1/categories", mapOf("includeArchived" to true)) }

private const val SHOWN = 10

/** Categories: Spending and Income, with subcategories under their parent, Kosha's own categories, and Archived. */
@Composable
fun CategoriesPage() {
    val nav = LocalNav.current
    val open = go()
    val m = meta()
    val c = KoshaTheme.colors
    val w = rememberWriter()
    val kosha = rememberKosha()
    val load = loadCategories()
    val all = load.value?.categories ?: emptyList()
    var tab by rememberSaveable { mutableStateOf("expense") }
    var showAll by rememberSaveable { mutableStateOf(false) }
    var archivedOpen by rememberSaveable { mutableStateOf(false) }
    val active = all.filter { !it.archived && !Cats.isSystem(it.id) }
    val spending = active.filter { !it.income }
    val income = active.filter { it.income }

    Page(
        stringResource(R.string.settings_section_categories),
        onBack = { nav.popBackStack() },
        actions = { IconButton("plus", stringResource(R.string.settings_cat_new), { open("category", if (tab == "income") "new-income" else "new") }, solid = true) },
    ) {
        Segmented(
            listOf(
                "expense" to stringResource(R.string.settings_cat_spending) + " · " + spending.size,
                "income" to stringResource(R.string.settings_cat_income) + " · " + income.size,
            ),
            tab,
            { tab = it; showAll = false },
            Modifier.fillMaxWidth(),
            small = true,
        )
        if (load.value == null) LoadingNote()
        val rows = tree(if (tab == "income") income else spending)
        if (load.value != null && rows.isEmpty()) {
            KText(stringResource(if (tab == "income") R.string.settings_cat_empty_income else R.string.settings_cat_empty_spending), color = c.ink3)
        }
        val visible = if (showAll || rows.size <= SHOWN + 1) rows else rows.take(SHOWN)
        Rows(
            *visible.map { cat ->
                @Composable { CategoryRow(cat, sub = cat.parentId != null && rows.any { it.id == cat.parentId }) { open("category", cat.id) } }
            }.toTypedArray(),
            if (visible.size < rows.size) {
                {
                    Row(
                        Modifier.fillMaxWidth().clickable(role = Role.Button) { showAll = true }.heightIn(min = 48.dp).padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        KText(stringResource(R.string.settings_cat_show_all, rows.size.toString()), Modifier.weight(1f), style = KoshaTheme.type.small, color = c.ink2)
                        PhIcon("caret-down", size = 16.dp, tint = c.ink3)
                    }
                }
            } else null,
        )

        // Kosha's own categories: transfer fees count as spending; balance corrections are neutral.
        if (tab == "expense") {
            val fees = all.filter { it.id == Cats.TRANSFER_FEE }
            val correction = m?.categories?.firstOrNull { it.id == Cats.CORRECTION }
            Rows(
                *fees.map { cat -> @Composable { CategoryRow(cat, sub = false, system = true) { open("category", cat.id) } } }.toTypedArray(),
                if (correction != null) {
                    {
                        val used = load.value?.correctionCount ?: 0
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = KoshaTheme.dims.row).padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            app.getkosha.design.IconTile(categoryIconName(correction.icon ?: "⚖"))
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    KText(correction.name, Modifier.weight(1f, fill = false))
                                    Pill(stringResource(R.string.settings_cat_system))
                                }
                                KText(
                                    if (used > 0) pluralStringResource(R.plurals.settings_cat_count_tx, used, used) else stringResource(R.string.settings_cat_unused),
                                    style = KoshaTheme.type.xs,
                                    color = c.ink3,
                                )
                            }
                        }
                    }
                } else null,
            )
        }

        val archived = all.filter { it.archived }
        if (archived.isNotEmpty()) {
            Card(Modifier.fillMaxWidth(), tight = true) {
                Row(
                    Modifier.fillMaxWidth().clickable(role = Role.Button) { archivedOpen = !archivedOpen }.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    PhIcon("archive", tint = c.ink3)
                    Column(Modifier.weight(1f)) {
                        KText(stringResource(R.string.settings_cat_archived_count, archived.size.toString()), color = c.ink2)
                        KText(stringResource(R.string.settings_cat_archived_hint), style = KoshaTheme.type.xs, color = c.ink3)
                    }
                    PhIcon(if (archivedOpen) "caret-up" else "caret-down", size = 16.dp, tint = c.ink3)
                }
                if (archivedOpen) {
                    val restored = stringResource(R.string.settings_cat_restored, "%s")
                    tree(archived).forEach { cat ->
                        Hairline()
                        SRow(cat.name, description = usage(cat), icon = categoryIconName(cat.icon)) {
                            Button(
                                stringResource(R.string.settings_cat_restore),
                                {
                                    w.run({ kosha.api.send("PATCH", "/api/v1/categories/${cat.id}", buildJsonObject { put("archived", JsonPrimitive(false)) }) }) {
                                        w.toast(restored.replace("%s", cat.name))
                                    }
                                },
                                style = ButtonStyle.Secondary,
                                size = ButtonSize.Small,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryRow(cat: Category, sub: Boolean, system: Boolean = false, onClick: () -> Unit) {
    val c = KoshaTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = KoshaTheme.dims.row)
            .padding(start = if (sub) 44.dp else 16.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(if (sub) 30.dp else 36.dp).clip(RoundedCornerShape(if (sub) 8.dp else 10.dp)).background(c.soft),
            contentAlignment = Alignment.Center,
        ) { PhIcon(categoryIconName(cat.icon), size = if (sub) 16.dp else 18.dp) }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                KText(cat.name, Modifier.weight(1f, fill = false))
                if (system) Pill(stringResource(R.string.settings_cat_system))
            }
            KText(usage(cat), style = KoshaTheme.type.xs, color = c.ink3)
        }
        PhIcon("caret-right", size = 16.dp, tint = c.ink3)
    }
}

/** Every Phosphor icon a category can take, in the web's order (category-icon.tsx CATEGORY_ICONS). */
val CATEGORY_ICONS = listOf(
    "shopping-cart", "basket", "storefront", "fork-knife", "coffee", "hamburger", "pizza", "cookie", "cake", "wine",
    "beer-stein", "martini", "shopping-bag", "t-shirt", "sneaker", "package", "house", "house-line", "couch", "bed",
    "lightbulb", "lightning", "drop", "flame", "wifi-high", "globe-simple", "device-mobile", "laptop", "television",
    "game-controller", "music-notes", "film-slate", "popcorn", "ticket", "book-open", "graduation-cap", "baby", "dog",
    "paw-print", "heartbeat", "pill", "first-aid", "tooth", "barbell", "soccer-ball", "bicycle", "car", "gas-pump",
    "taxi", "bus", "tram", "train", "airplane-tilt", "suitcase", "map-pin", "umbrella", "gift", "heart", "flower",
    "plant", "tree", "sparkle", "scissors", "paint-brush", "camera", "fish-simple", "church", "cigarette", "wrench",
    "hammer", "shield-check", "receipt", "file-text", "scales", "bank", "credit-card", "piggy-bank", "coins", "money",
    "hand-coins", "briefcase", "chart-line", "currency-circle-dollar", "arrows-left-right", "arrow-u-up-left", "tag",
)

/**
 * One category: name, the category it sits inside, type, icon (with search), its subcategories, and Merge into…
 * (or Delete when nothing uses it) and Archive. Every change saves as it's made; a new one saves once it has a name.
 * [routeId] is a category id, "new" for a new spending category or "new-income" for income.
 */
@Composable
fun CategoryEditPage(routeId: String) {
    val nav = LocalNav.current
    val open = go()
    val kosha = rememberKosha()
    val c = KoshaTheme.colors
    val w = rememberWriter()
    val all = loadCategories().value?.categories ?: emptyList()
    var id by rememberSaveable { mutableStateOf(routeId.takeUnless { it.startsWith("new") }) }
    val cat = all.firstOrNull { it.id == id }
    val system = id != null && Cats.isSystem(id!!)
    var name by rememberSaveable { mutableStateOf("") }
    var icon by rememberSaveable { mutableStateOf("ph:tag") }
    var income by rememberSaveable { mutableStateOf(routeId == "new-income") }
    var parentId by rememberSaveable { mutableStateOf<String?>(null) }
    var filled by rememberSaveable { mutableStateOf(false) }
    if (cat != null && !filled) {
        // The editor starts from the saved category once it has loaded.
        filled = true
        name = cat.name
        icon = cat.icon ?: "ph:tag"
        income = cat.income
        parentId = cat.parentId
    }
    var iconQuery by remember { mutableStateOf("") }
    var sheet by remember { mutableStateOf<String?>(null) }
    val children = if (cat == null) emptyList() else all.filter { it.parentId == cat.id }
    val parents = all.filter { !it.archived && it.parentId == null && it.income == income && it.id != id && !Cats.isSystem(it.id) }
    val added = stringResource(R.string.settings_cat_added, "%s")

    fun commit(patch: JsonObject) {
        val current = id
        if (current == null) {
            if (name.isBlank()) return
            val body = buildJsonObject {
                put("name", JsonPrimitive(name.trim()))
                put("icon", JsonPrimitive(icon))
                put("type", JsonPrimitive(if (income) "income" else "expense"))
                parentId?.let { put("parentId", JsonPrimitive(it)) }
            }
            w.run({ kosha.api.post<JsonObject, Category>("/api/v1/categories", body) }) { made ->
                id = made.id
                w.toast(added.replace("%s", made.name))
            }
        } else {
            w.run({ kosha.api.send("PATCH", "/api/v1/categories/$current", patch) })
        }
    }

    // The name saves itself a moment after typing stops.
    LaunchedEffect(name) {
        val next = name.trim()
        if (next.isEmpty() || (cat != null && next == cat.name) || (id != null && cat == null)) return@LaunchedEffect
        delay(800)
        commit(buildJsonObject { put("name", JsonPrimitive(next)) })
    }

    Page(
        name.ifBlank { stringResource(R.string.settings_cat_new) },
        onBack = { nav.popBackStack() },
        subtitle = cat?.let { usage(it) },
    ) {
        Column {
            FieldLabel(stringResource(R.string.settings_cat_name))
            Field(name, { name = it }, placeholder = stringResource(R.string.settings_cat_name_placeholder))
        }
        if (!system) {
            Rows(
                {
                    Row(
                        Modifier.fillMaxWidth().clickable(role = Role.Button) { sheet = "parent" }.heightIn(min = KoshaTheme.dims.settingsRow).padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        KText(stringResource(R.string.settings_cat_inside), Modifier.width(72.dp), style = KoshaTheme.type.small, color = c.ink3)
                        KText(parents.firstOrNull { it.id == parentId }?.name ?: stringResource(R.string.settings_cat_top_level), Modifier.weight(1f))
                        PhIcon("caret-right", size = 16.dp, tint = c.ink3)
                    }
                },
                if (parentId == null) {
                    {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                KText(stringResource(R.string.settings_cat_type), Modifier.width(72.dp), style = KoshaTheme.type.small, color = c.ink3)
                                Segmented(
                                    listOf("expense" to stringResource(R.string.settings_cat_spending), "income" to stringResource(R.string.settings_cat_income)),
                                    if (income) "income" else "expense",
                                    { v ->
                                        income = v == "income"
                                        commit(buildJsonObject { put("type", JsonPrimitive(v)); put("parentId", JsonNull) })
                                    },
                                    Modifier.weight(1f),
                                    small = true,
                                )
                            }
                            if (cat != null && cat.income != income && cat.transactionCount > 0) {
                                KText(
                                    pluralStringResource(if (income) R.plurals.settings_cat_now_income else R.plurals.settings_cat_now_spending, cat.transactionCount, cat.transactionCount),
                                    style = KoshaTheme.type.xs,
                                    color = c.warn,
                                )
                            }
                        }
                    }
                } else null,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            FieldLabel(stringResource(R.string.settings_cat_icon))
            Field(iconQuery, { iconQuery = it }, placeholder = stringResource(R.string.settings_cat_find_icon), icon = "magnifying-glass")
            val q = iconQuery.trim().lowercase().replace(Regex("\\s+"), "-")
            val icons = CATEGORY_ICONS.filter { q in it }
            val picked = categoryIconName(icon)
            if (icons.isEmpty()) KText(stringResource(R.string.settings_cat_no_icon, iconQuery), style = KoshaTheme.type.small, color = c.ink3)
            icons.chunked(6).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { n ->
                        val on = n == picked
                        Box(
                            Modifier
                                .weight(1f)
                                .height(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (on) c.surface else c.soft)
                                .then(if (on) Modifier.border(1.5.dp, c.selection, RoundedCornerShape(12.dp)) else Modifier)
                                .clickable(role = Role.RadioButton) {
                                    icon = "ph:$n"
                                    commit(buildJsonObject { put("icon", JsonPrimitive("ph:$n")) })
                                }
                                .described(n.replace('-', ' ')),
                            contentAlignment = Alignment.Center,
                        ) { PhIcon(n, size = 22.dp, tint = if (on) c.selection else c.ink2) }
                    }
                    repeat(6 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }

        if (children.isNotEmpty()) {
            GroupCaption(stringResource(R.string.app_set_subcategories))
            Rows(*children.map { sub ->
                @Composable {
                    SRow(sub.name, icon = categoryIconName(sub.icon), onClick = { open("category", sub.id) }) {
                        KText(sub.transactionCount.toString(), style = KoshaTheme.type.xs, color = c.ink3)
                    }
                }
            }.toTypedArray())
        }

        if (cat != null && !system) {
            val used = cat.transactionCount > 0 || cat.recurringCount > 0 || children.isNotEmpty()
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    stringResource(if (used) R.string.settings_cat_merge_into_ellipsis else R.string.common_delete),
                    { if (!w.refused()) sheet = if (used) "merge" else "delete" },
                    style = ButtonStyle.Secondary,
                    size = ButtonSize.Medium,
                    icon = if (used) "arrow-elbow-down-right" else "trash",
                )
                val archivedToast = stringResource(R.string.settings_cat_archived_toast, cat.name)
                Button(
                    stringResource(R.string.settings_cat_archive),
                    { w.run({ kosha.api.send("PATCH", "/api/v1/categories/${cat.id}", buildJsonObject { put("archived", JsonPrimitive(true)) }) }) { w.toast(archivedToast); nav.popBackStack() } },
                    style = ButtonStyle.Secondary,
                    size = ButtonSize.Medium,
                    icon = "archive",
                )
            }
        }
        KText(
            stringResource(if (id != null) R.string.settings_cat_autosave else R.string.settings_cat_new_hint),
            Modifier.fillMaxWidth(),
            style = KoshaTheme.type.xs,
            color = c.ink3,
            align = TextAlign.Center,
        )
    }

    when (sheet) {
        "parent" -> Sheet(stringResource(R.string.settings_cat_inside), { sheet = null }) {
            val top = ListOption("", stringResource(R.string.settings_cat_top_level))
            // A category with subcategories stays top level: categories nest one deep.
            ListPicker(listOf(top) + if (children.isEmpty()) parents.map { ListOption(it.id, it.name) } else emptyList(), parentId ?: "", { o ->
                sheet = null
                parentId = o.value.ifEmpty { null }
                commit(buildJsonObject { put("parentId", o.value.ifEmpty { null }?.let(::JsonPrimitive) ?: JsonNull) })
            })
        }
        "merge" -> if (cat != null) {
            val res = LocalResources.current
            MergeSheet(cat, all, children.isNotEmpty(), { sheet = null }) { into ->
                sheet = null
                w.run({ kosha.api.send("POST", "/api/v1/categories/${cat.id}/merge", buildJsonObject { put("intoId", JsonPrimitive(into.id)) }) }) {
                    w.toast(res.getString(R.string.settings_cat_merged, cat.name, into.name))
                    nav.popBackStack()
                }
            }
        }
        "delete" -> if (cat != null) Sheet(stringResource(R.string.settings_cat_delete_title, cat.name), { sheet = null }) {
            KText(stringResource(R.string.settings_cat_delete_body), color = c.ink2)
            Spacer(Modifier.height(16.dp))
            val deleted = stringResource(R.string.settings_cat_deleted, cat.name)
            Button(
                stringResource(R.string.common_delete),
                { sheet = null; w.run({ kosha.api.send("DELETE", "/api/v1/categories/${cat.id}") }) { w.toast(deleted); nav.popBackStack() } },
                Modifier.fillMaxWidth(),
                style = ButtonStyle.Danger,
            )
        }
    }
}

/**
 * Merge a category into another of the same type, then delete it: what moves, the target, and the button that
 * names it. A category with subcategories merges only into a top-level one (categories.tsx MergeDialog).
 */
@Composable
private fun MergeSheet(cat: Category, all: List<Category>, hasChildren: Boolean, onDismiss: () -> Unit, onMerge: (Category) -> Unit) {
    val c = KoshaTheme.colors
    val locale = KoshaTheme.locale
    var into by remember { mutableStateOf<Category?>(null) }
    val targets = tree(all.filter { !it.archived && it.id != cat.id && it.parentId != cat.id && it.income == cat.income && !(hasChildren && it.parentId != null) && !Cats.isNeutral(it.id) })
    val items = listOfNotNull(
        if (cat.transactionCount > 0) pluralStringResource(R.plurals.settings_cat_count_tx, cat.transactionCount, cat.transactionCount) else null,
        if (cat.recurringCount > 0) pluralStringResource(R.plurals.settings_cat_merge_item_recurring, cat.recurringCount, cat.recurringCount) else null,
        if (cat.ruleCount > 0) pluralStringResource(R.plurals.settings_cat_merge_item_rules, cat.ruleCount, cat.ruleCount) else null,
        if (hasChildren) stringResource(R.string.settings_cat_merge_item_subcategories) else null,
    )
    Sheet(stringResource(R.string.settings_cat_merge_title, cat.name), onDismiss) {
        KText(stringResource(R.string.settings_cat_merge_body, ListFormatter.getInstance(locale).format(items), cat.name), style = KoshaTheme.type.small, color = c.ink2)
        Spacer(Modifier.height(12.dp))
        ListPicker(targets.map { ListOption(it.id, it.name, all.firstOrNull { p -> p.id == it.parentId }?.name) }, into?.id, { o -> into = targets.first { it.id == o.value } })
        Spacer(Modifier.height(12.dp))
        val target = into
        Button(
            if (target != null) stringResource(R.string.settings_cat_merge_into, target.name) else stringResource(R.string.settings_cat_merge),
            { target?.let(onMerge) },
            Modifier.fillMaxWidth(),
            style = ButtonStyle.Danger,
            enabled = target != null,
        )
    }
}

// ---------- Events ----------

/** "12 Oct 2026 – 18 Oct 2026", one end only, or "No dates". */
@Composable
private fun eventDates(e: KEvent): String {
    val f = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(KoshaTheme.locale)
    val s = e.startDay?.let { LocalDate.parse(it).format(f) }
    val t = e.endDay?.let { LocalDate.parse(it).format(f) }
    return when {
        s != null && t != null -> "$s – $t"
        s != null -> "$s –"
        t != null -> "– $t"
        else -> stringResource(R.string.settings_events_no_dates)
    }
}

/** Events: trips and occasions with their dates and counts; edit the name and dates, see the entries, delete. */
@Composable
fun EventsPage() {
    val nav = LocalNav.current
    val kosha = rememberKosha()
    val c = KoshaTheme.colors
    val w = rememberWriter()
    val load = loaded("events") { get<Events>("/api/v1/events") }
    val events = load.value?.events ?: emptyList()
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    var newName by rememberSaveable { mutableStateOf<String?>(null) }

    Page(
        stringResource(R.string.settings_section_events),
        onBack = { nav.popBackStack() },
        actions = { IconButton("plus", stringResource(R.string.settings_events_new), { if (!w.refused()) { newName = ""; openId = null } }, solid = true) },
    ) {
        KText(stringResource(R.string.settings_events_note), style = KoshaTheme.type.small, color = c.ink3)
        val typed = newName
        if (typed != null) {
            Card(Modifier.fillMaxWidth().border(1.dp, c.ink, RoundedCornerShape(16.dp))) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    KText(stringResource(R.string.settings_events_new), style = KoshaTheme.type.head)
                    Field(typed, { newName = it }, placeholder = stringResource(R.string.settings_cat_name))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(stringResource(R.string.common_cancel), { newName = null }, Modifier.weight(1f), style = ButtonStyle.Secondary, size = ButtonSize.Small)
                        Button(
                            stringResource(R.string.common_add),
                            {
                                w.run({ kosha.api.post<JsonObject, KEvent>("/api/v1/events", buildJsonObject { put("name", JsonPrimitive(typed.trim())) }) }) {
                                    newName = null
                                    openId = it.id
                                }
                            },
                            Modifier.weight(1f),
                            size = ButtonSize.Small,
                            enabled = typed.isNotBlank(),
                        )
                    }
                }
            }
        }
        events.firstOrNull { it.id == openId }?.let { e -> EventEditor(e, onClosed = { openId = null }) }
        if (load.value == null) LoadingNote()
        else if (events.isEmpty()) KText(stringResource(R.string.settings_events_empty), color = c.ink3)
        Rows(*events.map { e ->
            @Composable {
                SRow(e.name, description = eventDates(e), icon = "calendar-star", onClick = { openId = if (openId == e.id) null else e.id }) {
                    KText(e.transactionCount.toString(), style = KoshaTheme.type.num.copy(fontSize = KoshaTheme.type.xs.fontSize), color = c.ink3)
                    PhIcon(if (openId == e.id) "caret-up" else "caret-right", size = 16.dp, tint = c.ink3)
                }
            }
        }.toTypedArray())
    }
}

@Composable
private fun EventEditor(e: KEvent, onClosed: () -> Unit) {
    val nav = LocalNav.current
    val kosha = rememberKosha()
    val c = KoshaTheme.colors
    val w = rememberWriter()
    val tz = kosha.tz
    var name by remember(e.id) { mutableStateOf(e.name) }
    var picking by remember { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf(false) }
    val patch = { body: JsonObject -> w.run({ kosha.api.send("PATCH", "/api/v1/events/${e.id}", body) }) }
    LaunchedEffect(name) {
        if (name.isBlank() || name.trim() == e.name) return@LaunchedEffect
        delay(800)
        patch(buildJsonObject { put("name", JsonPrimitive(name.trim())) })
    }
    val f = DateTimeFormatter.ofPattern(android.text.format.DateFormat.getBestDateTimePattern(KoshaTheme.locale, "EEEdMMM"), KoshaTheme.locale)
    Card(Modifier.fillMaxWidth().border(1.dp, c.ink, RoundedCornerShape(16.dp))) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                KText(e.name, Modifier.weight(1f), style = KoshaTheme.type.head)
                KText(pluralStringResource(R.plurals.settings_events_transactions, e.transactionCount, e.transactionCount), style = KoshaTheme.type.xs, color = c.ink3)
            }
            Field(name, { name = it })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("start" to (R.string.tx_from to e.startDay), "end" to (R.string.tx_to to e.endDay)).forEach { (which, v) ->
                    Column(Modifier.weight(1f)) {
                        FieldLabel(stringResource(v.first))
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(14.dp)).background(c.surface)
                                .border(1.dp, c.line, RoundedCornerShape(14.dp)).clickable(role = Role.Button) { picking = which }.padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            PhIcon("calendar-blank", size = 18.dp, tint = c.ink3)
                            KText(v.second?.let { LocalDate.parse(it).format(f) } ?: "–", maxLines = 1)
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(stringResource(R.string.settings_events_see_transactions), { nav.navigate(Route.TagView(eventId = e.id)) }, Modifier.weight(1f), style = ButtonStyle.Secondary, size = ButtonSize.Small)
                Button(stringResource(R.string.common_delete), { if (!w.refused()) confirm = true }, style = ButtonStyle.Danger, size = ButtonSize.Small)
            }
        }
    }
    picking?.let { which ->
        val current = (if (which == "start") e.startDay else e.endDay)?.let(LocalDate::parse)
        var month by remember(which) { mutableStateOf(YearMonth.from(current ?: Dates.today(tz))) }
        val key = if (which == "start") "startDay" else "endDay"
        Sheet(stringResource(if (which == "start") R.string.tx_from else R.string.tx_to), { picking = null }) {
            MonthGrid(month, current, Dates.today(tz), { d -> picking = null; patch(buildJsonObject { put(key, JsonPrimitive(d.toString())) }) }, { month = it })
            if (current != null) {
                Spacer(Modifier.height(8.dp))
                Button(stringResource(R.string.tx_clear), { picking = null; patch(buildJsonObject { put(key, JsonNull) }) }, Modifier.fillMaxWidth(), style = ButtonStyle.Secondary)
            }
        }
    }
    if (confirm) {
        val deleted = stringResource(R.string.settings_events_deleted, e.name)
        Sheet(stringResource(R.string.settings_events_delete_title, e.name), { confirm = false }) {
            KText(pluralStringResource(R.plurals.settings_events_delete_body, e.transactionCount, e.transactionCount), color = c.ink2)
            Spacer(Modifier.height(16.dp))
            Button(
                stringResource(R.string.common_delete),
                { confirm = false; w.run({ kosha.api.send("DELETE", "/api/v1/events/${e.id}") }) { w.toast(deleted); onClosed() } },
                Modifier.fillMaxWidth(),
                style = ButtonStyle.Danger,
            )
        }
    }
}

// ---------- Payee rules ----------

/** A message with one argument set in [style], such as "Payee is ⟨REWE⟩". */
@Composable
fun styledArg(res: Int, arg: String, style: SpanStyle): AnnotatedString {
    val raw = LocalResources.current.getString(res)
    val at = raw.indexOf("%1\$s")
    if (at < 0) return AnnotatedString(raw)
    return buildAnnotatedString {
        append(raw.substring(0, at))
        withStyle(style) { append(arg) }
        append(raw.substring(at + 4))
    }
}

/** Categories a rule can set: not archived and not one of Kosha's own. */
@Composable
private fun categoryOptions(): List<ListOption> {
    val m = meta()
    val cats = m?.categories?.filter { !it.isArchived && !Cats.isSystem(it.id) } ?: emptyList()
    return cats.sortedBy { it.name.lowercase() }.map { ListOption(it.id, it.name, cats.firstOrNull { p -> p.id == it.parentId }?.name) }
}

/** Payee rules: Learn from my edits, a new rule (payee contains or is exactly → category), search, and the list. */
@Composable
fun RulesPage() {
    val nav = LocalNav.current
    val kosha = rememberKosha()
    val m = meta()
    val c = KoshaTheme.colors
    val w = rememberWriter()
    var query by rememberSaveable { mutableStateOf("") }
    var searched by remember { mutableStateOf("") }
    LaunchedEffect(query) { delay(300); searched = query.trim() }
    val first = loaded("rules?$searched") { get<PayeeRules>("/api/v1/payee-rules", mapOf("query" to searched, "limit" to 50)) }
    var more by remember(first.value) { mutableStateOf<List<PayeeRule>>(emptyList()) }
    var next by remember(first.value) { mutableStateOf(first.value?.nextOffset) }
    val rules = (first.value?.rules ?: emptyList()) + more
    var newPayee by rememberSaveable { mutableStateOf("") }
    var newExact by rememberSaveable { mutableStateOf(false) }
    var newCategory by rememberSaveable { mutableStateOf<String?>(null) }
    var picking by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<PayeeRule?>(null) }
    val saved = stringResource(R.string.common_saved)
    val saveNew = {
        val body = buildJsonObject {
            put("rules", buildJsonArray {
                add(buildJsonObject {
                    put("payee", JsonPrimitive(newPayee.trim()))
                    put("categoryId", JsonPrimitive(newCategory!!))
                    put("match", JsonPrimitive(if (newExact) "exact" else "contains"))
                })
            })
        }
        w.run({ kosha.api.send("PUT", "/api/v1/payee-rules", body) }) {
            newPayee = ""
            newCategory = null
            w.toast(saved)
        }
    }
    // A new rule saves once it has both a payee and a category.
    LaunchedEffect(newPayee, newCategory, newExact) {
        if (newPayee.isBlank() || newCategory == null) return@LaunchedEffect
        delay(1200)
        saveNew()
    }
    val catName = { id: String -> m?.categories?.firstOrNull { it.id == id } }
    val deletedCategory = stringResource(R.string.settings_rules_deleted_category)
    val code = SpanStyle(fontFamily = KoshaTheme.type.num.fontFamily, background = c.soft)

    Page(stringResource(R.string.settings_section_rules), onBack = { nav.popBackStack() }) {
        KText(stringResource(R.string.settings_section_rules_desc), style = KoshaTheme.type.small, color = c.ink3)
        Rows({
            SwitchRow(
                stringResource(R.string.settings_rules_learn_title),
                stringResource(R.string.settings_rules_learn_description),
                m.prefOf("learn_rules") == "1",
                { w.pref("learn_rules", if (it) "1" else "0") },
            )
        })

        Card(Modifier.fillMaxWidth().border(1.dp, c.ink, RoundedCornerShape(16.dp))) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                KText(stringResource(R.string.settings_rules_new), style = KoshaTheme.type.head)
                Column {
                    FieldLabel(stringResource(R.string.settings_rules_payee))
                    Field(newPayee, { newPayee = it }, placeholder = stringResource(R.string.settings_rules_payee_placeholder))
                }
                Segmented(
                    listOf(false to stringResource(R.string.settings_rules_contains), true to stringResource(R.string.settings_rules_is_exactly)),
                    newExact,
                    { newExact = it },
                    Modifier.fillMaxWidth(),
                    small = true,
                )
                PickField(
                    "squares-four",
                    newCategory?.let { catName(it)?.name } ?: stringResource(R.string.settings_rules_pick_category),
                    placeholder = newCategory == null,
                ) { if (!w.refused()) picking = true }
                KText(stringResource(R.string.settings_rules_new_hint), style = KoshaTheme.type.xs, color = c.ink3)
            }
        }

        Field(query, { query = it }, placeholder = stringResource(R.string.common_search), icon = "magnifying-glass")
        if (first.value == null) LoadingNote()
        else if (rules.isEmpty()) {
            KText(
                if (searched.isEmpty()) stringResource(R.string.settings_rules_empty) else stringResource(R.string.settings_rules_no_match, searched),
                color = c.ink3,
            )
        }
        Rows(
            *rules.map { r ->
                @Composable {
                    val cat = catName(r.categoryId)
                    Row(
                        Modifier.fillMaxWidth().clickable(role = Role.Button) { editing = r }.heightIn(min = KoshaTheme.dims.row).padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            BasicText(
                                styledArg(if (r.match == "exact") R.string.settings_rules_payee_is else R.string.settings_rules_payee_contains, r.payee, code),
                                style = KoshaTheme.type.small.copy(color = c.ink),
                            )
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                PhIcon(categoryIconName(cat?.icon), size = 14.dp, tint = c.ink3)
                                KText(
                                    listOf(
                                        cat?.name ?: deletedCategory,
                                        pluralStringResource(R.plurals.app_set_rule_used, r.matches, r.matches),
                                        stringResource(if (r.source == "learned") R.string.settings_rules_source_learned else R.string.settings_rules_source_manual),
                                    ).joinToString(" · "),
                                    style = KoshaTheme.type.xs,
                                    color = c.ink3,
                                )
                            }
                        }
                        PhIcon("caret-right", size = 16.dp, tint = c.ink3)
                    }
                }
            }.toTypedArray(),
            next?.let { offset ->
                {
                    Row(
                        Modifier.fillMaxWidth().clickable(role = Role.Button) {
                            w.run({ kosha.api.get<PayeeRules>("/api/v1/payee-rules", mapOf("query" to searched, "limit" to 50, "offset" to offset)) }) {
                                more = more + it.rules
                                next = it.nextOffset
                            }
                        }.heightIn(min = 48.dp).padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        KText(stringResource(R.string.settings_rules_show_more), Modifier.weight(1f), style = KoshaTheme.type.small, color = c.ink2)
                        PhIcon("caret-down", size = 16.dp, tint = c.ink3)
                    }
                }
            },
        )
    }

    if (picking) {
        Sheet(stringResource(R.string.settings_rules_pick_category), { picking = false }) {
            ListPicker(categoryOptions(), newCategory, { o -> picking = false; newCategory = o.value })
        }
    }
    editing?.let { r -> RuleSheet(r, { editing = null }) }
}

/** A field-like button that opens a picker. */
@Composable
fun PickField(icon: String?, text: String, placeholder: Boolean = false, onClick: () -> Unit) {
    val c = KoshaTheme.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(14.dp)).background(c.surface)
            .border(1.dp, c.line, RoundedCornerShape(14.dp)).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (icon != null) PhIcon(icon, size = 18.dp, tint = c.ink3)
        KText(text, Modifier.weight(1f), color = if (placeholder) c.ink3 else c.ink, maxLines = 1)
        PhIcon("caret-right", size = 16.dp, tint = c.ink3)
    }
}

/** Change one rule's text, match or category, or delete it. */
@Composable
private fun RuleSheet(rule: PayeeRule, onDismiss: () -> Unit) {
    val kosha = rememberKosha()
    val m = meta()
    val c = KoshaTheme.colors
    val w = rememberWriter()
    var payee by remember { mutableStateOf(rule.payee) }
    var exact by remember { mutableStateOf(rule.match == "exact") }
    var category by remember { mutableStateOf(rule.categoryId) }
    var picking by remember { mutableStateOf(false) }
    val deleted = stringResource(R.string.settings_rules_deleted)
    Sheet(stringResource(R.string.settings_section_rules), onDismiss) {
        if (picking) {
            ListPicker(categoryOptions(), category, { o -> picking = false; category = o.value })
            return@Sheet
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            FieldLabel(stringResource(R.string.settings_rules_payee))
            Field(payee, { payee = it })
            Segmented(
                listOf(false to stringResource(R.string.settings_rules_contains), true to stringResource(R.string.settings_rules_is_exactly)),
                exact,
                { exact = it },
                Modifier.fillMaxWidth(),
                small = true,
            )
            PickField("squares-four", m?.categories?.firstOrNull { it.id == category }?.name ?: stringResource(R.string.settings_rules_deleted_category)) { picking = true }
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    stringResource(R.string.common_delete),
                    {
                        w.run({ kosha.api.send("POST", "/api/v1/payee-rules/delete", buildJsonObject { put("payees", JsonArray(listOf(JsonPrimitive(rule.payee)))) }) }) {
                            w.toast(deleted)
                            onDismiss()
                        }
                    },
                    style = ButtonStyle.Danger,
                )
                Button(
                    stringResource(R.string.common_save),
                    {
                        val body = buildJsonObject {
                            put("rules", buildJsonArray {
                                add(buildJsonObject {
                                    put("payee", JsonPrimitive(payee.trim()))
                                    put("categoryId", JsonPrimitive(category))
                                    put("match", JsonPrimitive(if (exact) "exact" else "contains"))
                                    if (payee.trim() != rule.payee) put("previousPayee", JsonPrimitive(rule.payee))
                                })
                            })
                        }
                        w.run({ kosha.api.send("PUT", "/api/v1/payee-rules", body) }) { onDismiss() }
                    },
                    Modifier.weight(1f),
                    enabled = payee.isNotBlank(),
                )
            }
        }
    }
}
