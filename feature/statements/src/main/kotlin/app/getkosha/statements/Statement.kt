package app.getkosha.statements

import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sign

// Bank statements on the phone: matching statement lines to Kosha's entries, payee names from bank text, transfer
// pairs between accounts, the reconcile plan and the hints for a difference. Ported line for line from the web's
// src/lib/statement.ts, so the phone ticks, matches and hints exactly as the web does; core.test.ts's asserts are
// in StatementTest. Parsing CSV, OFX and PDF stays on the server (POST /api/app/import/read).

/**
 * One line of a statement, in the account's currency. Negative is money out. `replaces` is the ref of an earlier
 * line the bank corrected with this one (OFX's CORRECTFITID). A PDF read by AI also names the `payee`.
 */
@Serializable
data class Line(
    val day: String,
    val amount: Long,
    val description: String = "",
    val balance: Long? = null,
    val ref: String? = null,
    val replaces: String? = null,
    val payee: String? = null,
)

/** A Kosha entry a statement line can match. `paid` is 1 for paid, 0 for a bill still due. */
data class Entry(val id: String, val day: String, val amount: Long, val name: String, val ref: String?, val paid: Int = 1)

enum class Kind { Ref, Exact, Split, Close, Corrected }

/** A statement line matched to an entry (statement.ts Pair). */
data class Matched(val line: Int, val entry: String, val kind: Kind)

/** Lines and entries whose amounts add up, but which aren't matched on their own since another way could. */
data class Split(val lines: List<Int>, val entries: List<String>)

data class Matching(val pairs: List<Matched>, val missing: List<Int>, val extra: List<String>, val splits: List<Split>)

fun addDays(day: String, n: Int): String = LocalDate.parse(day).plusDays(n.toLong()).toString()

internal fun dayDiff(a: String, b: String): Int = ChronoUnit.DAYS.between(LocalDate.parse(a), LocalDate.parse(b)).toInt()

// ---------- dates in a CSV (for the column editor) ----------

private val MONTHS = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")
private fun fullYear(y: Int) = if (y < 100) 2000 + y else y
private fun valid(y: Int, m: Int, d: Int): String? {
    if (!(y in 1990..2100 && m in 1..12 && d in 1..31)) return null
    return runCatching { LocalDate.of(y, m, d).toString() }.getOrNull()
}
private val TEXT_A = Regex("""^(\d{1,2})[\s\-./]*([a-z]{3})[a-z]*\.?[\s\-./,]*(\d{2,4})""")
private val TEXT_B = Regex("""^([a-z]{3})[a-z]*\.?[\s\-./]*(\d{1,2})(?:st|nd|rd|th)?,?[\s\-./]*(\d{2,4})""")
private val PARTS = Regex("""^(\d{1,4})[-./ ](\d{1,2})[-./ ](\d{1,4})""")

const val YMD = "ymd"
const val DMY = "dmy"
const val MDY = "mdy"
const val TEXT = "text"
val DATE_FORMATS = listOf(YMD, DMY, MDY, TEXT)

/** Reads a statement date in the given format, ignoring any time after it. Returns YYYY-MM-DD or null. */
fun parseDay(raw: String, format: String): String? {
    val s = raw.trim().lowercase()
    if (format == TEXT) {
        val a = TEXT_A.find(s)
        val b = TEXT_B.find(s)
        val (d, mon, y) = when {
            a != null -> Triple(a.groupValues[1], a.groupValues[2], a.groupValues[3])
            b != null -> Triple(b.groupValues[2], b.groupValues[1], b.groupValues[3])
            else -> return null
        }
        val m = MONTHS.indexOf(mon) + 1
        return if (m > 0) valid(fullYear(y.toInt()), m, d.toInt()) else null
    }
    if (format == YMD && s.length >= 8 && s.take(8).all(Char::isDigit)) {
        return valid(s.substring(0, 4).toInt(), s.substring(4, 6).toInt(), s.substring(6, 8).toInt())
    }
    val p = PARTS.find(s) ?: return null
    val (p1, p2, p3) = p.destructured
    val (x, y, z) = Triple(p1.toInt(), p2.toInt(), p3.toInt())
    if (format == YMD) return if (p1.length == 4) valid(x, y, z) else null
    if (p3.length != 4 && p3.length != 2) return null
    return if (format == DMY) valid(fullYear(z), y, x) else valid(fullYear(z), x, y)
}

/**
 * The date format that reads every value. Between day-first and month-first, a part over 12 decides; when
 * nothing does, the one that keeps the rows in date order wins, then day-first.
 */
fun guessDateFormat(values: List<String>): String? {
    val list = values.filter { it.isNotBlank() }.take(200)
    if (list.isEmpty()) return null
    val ok = DATE_FORMATS.filter { f -> list.all { parseDay(it, f) != null } }
    if (ok.size < 2) return ok.firstOrNull()
    fun ordered(f: String): Int {
        val days = list.map { parseDay(it, f)!! }
        var up = 0
        var down = 0
        for (i in 1 until days.size) {
            if (days[i] > days[i - 1]) up++
            if (days[i] < days[i - 1]) down++
        }
        return min(up, down)
    }
    return ok.sortedBy(::ordered).first()
}

// ---------- bank text to payees ----------

// Card processors and wallets that prefix the merchant: "PAYPAL *SPOTIFY", "SQ *BLUE BOTTLE".
private val PROCESSOR =
    Regex("""^(paypal|pp|sq|sqr|tst|sp|sumup|zettle|iz|izettle|google|gpay|apple pay|amzn mktp|crv|wpy|pay)\s?\*\s?""", RegexOption.IGNORE_CASE)

// Words banks add around the merchant, in English, German, French, Spanish and Dutch.
private val NOISE = listOf(
    "card payment purchase pos debit credit visa mastercard maestro contactless cnp direct dd so standing order",
    "faster payments fps fpi fpo bacs transfer trf tfr to from on at via ref reference gb gbr uk usa us ie de fr nl",
    "es it eur usd gbp ltd limited inc plc llc gmbh bv sarl www com net org co the and",
    "kartenzahlung lastschrift basislastschrift gutschrift überweisung ueberweisung dauerauftrag sepa",
    "paiement carte cb prelevement prélèvement virement achat compra pago tarjeta transferencia recibo",
    "betaalautomaat pinbetaling incasso overboeking",
).joinToString(" ").split(" ").toSet()

private val ON_DATE = Regex("""\b(on|date)\s+\d{1,2}\s*[a-z]{3}\b.*$""")
private val WITH_DIGIT = Regex("""\S*\d\S*""")
private val NOT_LETTER = Regex("""[^\p{L}\s]+""")
private val SPACE = Regex("""\s+""")

/** Bank text reduced to the words that name the merchant, for matching and for remembering payees. */
fun descriptorKey(text: String): String = text.lowercase()
    .replace(PROCESSOR, "")
    .replace(ON_DATE, "") // "… ON 03 NOV …" to the end
    .replace(WITH_DIGIT, " ") // card numbers, references and amounts: any word with a digit in it
    .replace(NOT_LETTER, " ")
    .split(SPACE)
    .filter { it.isNotEmpty() && it !in NOISE }
    .take(3)
    .joinToString(" ")

private val WORD = Regex("""\p{L}[\p{L}']*""")
private fun title(s: String) = WORD.replace(s) { it.value.take(1).uppercase() + it.value.drop(1).lowercase() }

/**
 * The payee for a statement line: a remembered alias first, then a past payee the text names, then the text
 * itself tidied ("CARD PAYMENT TO TESCO STORES 3211 ON 03 NOV" is "Tesco Stores").
 */
fun payeeFor(description: String, known: List<String>, aliases: Map<String, String>): String {
    val key = descriptorKey(description)
    aliases[key]?.let { return it }
    val words = " $key "
    var best = ""
    for (name in known) {
        val k = descriptorKey(name)
        if (k.isNotEmpty() && words.contains(" $k ") && k.length > descriptorKey(best).length) best = name
    }
    if (best.isNotEmpty()) return best
    // "Boots · BOOTS 1123" names the merchant twice.
    return title(key.split(" ").distinct().joinToString(" ")).ifEmpty { description.trim().take(60) }
}

/** How alike two payee texts are, from 0 to 1: the share of the shorter one's words found in the other. */
fun similarity(a: String, b: String): Double {
    val x = descriptorKey(a).split(" ").filter { it.isNotEmpty() }.toSet()
    val y = descriptorKey(b).split(" ").filter { it.isNotEmpty() }.toSet()
    if (x.isEmpty() || y.isEmpty()) return 0.0
    val common = x.count { w -> w in y || y.any { v -> v.length > 3 && (v.startsWith(w) || w.startsWith(v)) } }
    return common.toDouble() / min(x.size, y.size)
}

// ---------- matching ----------

/** Groups of 2 or 3 items whose values add up to `total`. */
private fun <T> sums(items: List<T>, total: Long, value: (T) -> Long): List<List<T>> {
    val out = mutableListOf<List<T>>()
    for (a in items.indices) for (b in a + 1 until items.size) {
        val ab = value(items[a]) + value(items[b])
        if (ab == total) out += listOf(items[a], items[b])
        for (c in b + 1 until items.size) if (ab + value(items[c]) == total) out += listOf(items[a], items[b], items[c])
    }
    return out
}

/**
 * Pairs statement lines with Kosha entries, each used once. A bank id that Kosha stored matches first, also
 * through a bank's correction of it. Then the same amount within `before` days before or `after` days after the
 * entry, closest and most alike first. Then splits: a line that is 2 or 3 entries together, or the other way
 * round, matched only when it is the one way to make that sum and the names agree; other ways come back in
 * `splits`. Lines left over that look like an entry with a different amount become "close" pairs to confirm.
 */
fun matchLines(lines: List<Line>, entries: List<Entry>, before: Int = 3, after: Int = 7): Matching {
    val pairs = mutableListOf<Matched>()
    val usedLine = mutableSetOf<Int>()
    val usedEntry = mutableSetOf<String>()
    fun take(line: Int, entry: String, kind: Kind) {
        pairs += Matched(line, entry, kind)
        usedLine += line
        usedEntry += entry
    }
    val byRef = entries.filter { it.ref != null }.associateBy { it.ref!! }
    lines.forEachIndexed { i, l ->
        val own = l.ref?.let(byRef::get)
        val e = own ?: l.replaces?.let(byRef::get)
        if (e == null || e.id in usedEntry) return@forEachIndexed
        take(i, e.id, if (own != null || l.amount == e.amount) Kind.Ref else Kind.Corrected)
    }
    fun near(l: Line, e: Entry): Boolean = dayDiff(e.day, l.day).let { it >= -before && it <= after }
    class Candidate(val line: Int, val entry: String, val score: Double)
    fun scored(kind: Kind) {
        val list = mutableListOf<Candidate>()
        lines.forEachIndexed { i, l ->
            if (i in usedLine) return@forEachIndexed
            for (e in entries) {
                if (e.id in usedEntry || !near(l, e)) continue
                val d = dayDiff(e.day, l.day)
                val sim = similarity(l.description, e.name)
                if (kind == Kind.Exact) {
                    if (l.amount != e.amount) continue
                    list += Candidate(i, e.id, abs(d) + (1 - sim) * 4)
                } else {
                    val off = abs(l.amount - e.amount)
                    val same = l.amount.sign == e.amount.sign
                    if (!same || sim < 0.5 || abs(d) > 3 || off > maxOf(abs(e.amount) * 0.25, 500.0)) continue
                    list += Candidate(i, e.id, abs(d) + off.toDouble() / abs(e.amount))
                }
            }
        }
        for (c in list.sortedBy { it.score }) if (c.line !in usedLine && c.entry !in usedEntry) take(c.line, c.entry, kind)
    }
    scored(Kind.Exact)

    // Parts have the same sign and are smaller than their whole. ponytail: only the 12 nearest parts are tried,
    // so a split with a part further off stays unmatched.
    fun <T> parts(whole: Long, list: List<T>, amount: (T) -> Long, gap: (T) -> Int) = list
        .filter { amount(it).sign == whole.sign && abs(amount(it)) < abs(whole) }
        .sortedBy(gap)
        .take(12)
    class Group(val lines: List<Int>, val entries: List<String>, val alike: Boolean)
    val open = entries.filter { it.id !in usedEntry }
    val free = lines.withIndex().filter { it.index !in usedLine }
    val groups = mutableListOf<Group>()
    for ((i, l) in free) {
        val list = parts(l.amount, open.filter { near(l, it) }, { it.amount }, { abs(dayDiff(it.day, l.day)) })
        for (g in sums(list, l.amount) { it.amount }) {
            groups += Group(listOf(i), g.map { it.id }, g.all { similarity(l.description, it.name) >= 0.5 })
        }
    }
    for (e in open) {
        val list = parts(e.amount, free.filter { near(it.value, e) }, { it.value.amount }, { abs(dayDiff(e.day, it.value.day)) })
        for (g in sums(list, e.amount) { it.value.amount }) {
            groups += Group(g.map { it.index }, listOf(e.id), g.all { similarity(it.value.description, e.name) >= 0.5 })
        }
    }
    // A group is sure when nothing it holds could be part of another group.
    fun keys(g: Group) = g.lines.map { "l$it" } + g.entries.map { "e$it" }
    val uses = mutableMapOf<String, Int>()
    for (g in groups) for (k in keys(g)) uses[k] = (uses[k] ?: 0) + 1
    val sure = groups.filter { g -> g.alike && keys(g).all { uses[it] == 1 } }
    for (g in sure) for (i in g.lines) for (id in g.entries) take(i, id, Kind.Split)

    scored(Kind.Close)
    return Matching(
        pairs = pairs,
        missing = lines.indices.filter { it !in usedLine },
        extra = entries.filter { it.id !in usedEntry }.map { it.id },
        splits = groups
            .filter { g -> sure.none { it === g } && g.lines.all { it !in usedLine } }
            .filter { g -> g.entries.all { it !in usedEntry } }
            .map { Split(it.lines, it.entries) },
    )
}

// ---------- finding a difference ----------

/** A hint: what to check, and the entries it is about. */
data class Hint(val text: String, val ids: List<String>)

/** One entry, for the difference checks. */
data class Item(val id: String, val amount: Long, val day: String, val name: String)

/** The words of the difference hints (the reconcile_hint_* messages), so the checks stay pure. */
interface HintWords {
    fun anEntry(): String
    fun exactUnticked(name: String, amount: String): String
    fun exactTicked(name: String, amount: String): String
    fun wrongWayIn(name: String): String
    fun wrongWayOut(name: String): String
    fun swapped(name: String, meant: String, amount: String): String
    fun decimal(name: String, meant: String, amount: String): String
    fun pair(first: String, second: String): String
    fun duplicate(name: String): String
    fun late(): String
    fun lookOut(amount: String): String
    fun lookIn(amount: String): String
}

/** Every amount made by swapping two neighbouring digits of `cents`, e.g. 5400 gives 4500 and 5040. */
private fun swaps(cents: Long): List<Long> {
    val s = abs(cents).toString().padStart(3, '0')
    val out = LinkedHashSet<Long>()
    for (i in 0 until s.length - 1) {
        if (s[i] == s[i + 1]) continue
        out += (s.substring(0, i) + s[i + 1] + s[i] + s.substring(i + 2)).toLong() * cents.sign
    }
    return out.toList()
}

/**
 * Bookkeepers' checks for a reconciliation that is out by `diff` (statement minus what's ticked), most
 * specific first. Each hint names the entries it is about. `money` formats cents for the text.
 */
fun differenceHints(
    diff: Long,
    ticked: List<Item>,
    unticked: List<Item>,
    statementDay: String,
    money: (Long) -> String,
    words: HintWords,
): List<Hint> {
    if (diff == 0L) return emptyList()
    val out = mutableListOf<Hint>()
    fun name(e: Item) = e.name.ifEmpty { words.anEntry() }
    // Ticking one more entry, or unticking one, closes it.
    for (e in unticked.filter { it.amount == diff }) out += Hint(words.exactUnticked(name(e), money(e.amount)), listOf(e.id))
    for (e in ticked.filter { it.amount == -diff }) out += Hint(words.exactTicked(name(e), money(e.amount)), listOf(e.id))
    // An entry with the wrong sign is off by twice its amount.
    if (diff % 2 == 0L) {
        for (e in ticked.filter { it.amount == -diff / 2 }) {
            out += Hint(if (e.amount < 0) words.wrongWayIn(name(e)) else words.wrongWayOut(name(e)), listOf(e.id))
        }
    }
    // Two digits swapped while typing always leave a difference divisible by 9.
    if (abs(diff) % 9 == 0L) {
        for (e in ticked) {
            val meant = swaps(e.amount).firstOrNull { it - e.amount == diff }
            if (meant != null) out += Hint(words.swapped(name(e), money(meant), money(e.amount)), listOf(e.id))
        }
    }
    // A misplaced decimal point (164.67 for 1,646.70) is off by the entry times 9, 99 or 999.
    for (e in ticked) for (k in listOf(10L, 100L, 1000L)) {
        if (diff == e.amount * (k - 1)) {
            out += Hint(words.decimal(name(e), money(e.amount * k), money(e.amount)), listOf(e.id))
        } else if (diff * k == -e.amount * (k - 1) && e.amount % k == 0L) {
            out += Hint(words.decimal(name(e), money(e.amount / k), money(e.amount)), listOf(e.id))
        }
    }
    // Two unticked entries that together make the difference.
    val small = unticked.take(80)
    var i = 0
    while (i < small.size && out.size < 6) {
        for (j in i + 1 until small.size) {
            if (small[i].amount + small[j].amount == diff) {
                out += Hint(words.pair(name(small[i]), name(small[j])), listOf(small[i].id, small[j].id))
                break
            }
        }
        i++
    }
    // The same payment entered twice.
    for (e in ticked) for (f in ticked) {
        if (e.id < f.id && e.amount == f.amount && e.amount == -diff && abs(dayDiff(e.day, f.day)) <= 3 &&
            similarity(e.name, f.name) >= 0.5
        ) out += Hint(words.duplicate(name(e)), listOf(e.id, f.id))
    }
    // Entries at the very end of the period often reach the bank on the next statement.
    val from = addDays(statementDay, -3)
    val late = ticked.filter { it.day > from && it.day <= statementDay }
    if (late.isNotEmpty() && out.isEmpty()) out += Hint(words.late(), late.map { it.id })
    if (out.isEmpty()) out += Hint(if (diff < 0) words.lookOut(money(abs(diff))) else words.lookIn(money(abs(diff))), emptyList())
    return out.take(6)
}

// ---------- planning a reconciliation ----------

/**
 * What a reconciliation starts from (statement.ts planReconciliation): which statement lines match Kosha's
 * entries, which are missing, and which entries count as ticked. With a statement, its period starts at its
 * first line; an entry from before then that matches no line was on an earlier statement, so it counts as ticked,
 * and in the period only matched entries are. A close match or a bank's correction to another amount is never
 * ticked until settled. Without a statement, every paid entry up to the statement day is ticked.
 */
class Plan(
    val pairs: List<Matched>,
    val missing: List<Int>,
    val matched: Set<String>,
    val close: List<Matched>,
    val splits: List<Split>,
    val withdrawn: List<String>,
    val periodStart: String,
    private val statementDay: String,
    private val hasLines: Boolean,
) {
    fun inPeriod(e: Entry) = e.day <= statementDay && (e.day >= periodStart || e.id in matched || e.id in withdrawn)

    fun ticks(e: Entry) = e.day <= statementDay && (if (hasLines) !inPeriod(e) || e.id in matched else e.paid == 1)
}

fun planReconciliation(
    lines: List<Line>,
    open: List<Entry>,
    done: List<Entry>,
    statementDay: String,
    lastDay: String?,
    rejected: Set<String> = emptySet(),
    withdrawn: List<String> = emptyList(),
): Plan {
    val gone = withdrawn.toSet()
    val entries = (open + done).filter { it.ref == null || it.ref !in gone }
    val match = if (lines.isNotEmpty()) matchLines(lines, entries) else null
    val openIds = open.map { it.id }.toSet()
    fun settle(p: Matched) = p.kind == Kind.Close || p.kind == Kind.Corrected
    val pairs = (match?.pairs ?: emptyList()).filter { !(settle(it) && (lines[it.line].ref ?: "") in rejected) }
    val paired = pairs.map { it.line }.toSet()
    val matched = pairs.filter { !settle(it) && it.entry in openIds }.map { it.entry }.toSet()
    val close = pairs.filter { settle(it) && it.entry in openIds }
    val out = open.filter { it.ref != null && it.ref in gone }.map { it.id }
    val firstLine = lines.minOfOrNull { it.day } ?: "9999-12-31"
    val periodStart = when {
        lines.isNotEmpty() -> firstLine
        lastDay != null -> addDays(lastDay, 1)
        else -> addDays(statementDay, -45)
    }
    return Plan(
        pairs = pairs,
        missing = lines.indices.filter { it !in paired },
        matched = matched,
        close = close,
        splits = (match?.splits ?: emptyList()).filter { s -> s.entries.all { it in openIds } },
        withdrawn = out,
        periodStart = periodStart,
        statementDay = statementDay,
        hasLines = lines.isNotEmpty(),
    )
}

// ---------- getting started: many statements at once ----------

// Common merchants and words, mapped to Kosha's starting categories, so a first import sorts much of itself
// even without AI or any rules yet. Only categories the person still has are used.
private val STARTER: List<Triple<Regex, String, Boolean>> = listOf(
    Triple(Regex("""\b(tesco|sainsbury|asda|aldi|lidl|morrisons|waitrose|co ?op|ocado|rewe|edeka|kaufland|netto|penny|carrefour|auchan|leclerc|intermarche|mercadona|albert heijn|jumbo|whole foods|trader joe|safeway|kroger|publix|supermarket|grocer)"""), "groceries", false),
    Triple(Regex("""\b(restaurant|cafe|caffe|coffee|starbucks|costa|pret|mcdonald|burger king|kfc|subway|nando|pizza|domino|deliveroo|uber ?eats|just ?eat|doordash|grubhub|wolt|bistro|sushi|trattoria|bakery|pub)"""), "dining", false),
    Triple(Regex("""\b(amazon|amzn|ebay|etsy|zara|primark|uniqlo|ikea|argos|john lewis|decathlon|zalando|asos|apple store|best buy|target)"""), "shopping", false),
    Triple(Regex("""\b(uber|lyft|bolt|free ?now|taxi|tfl|transport for london|trainline|railway|deutsche bahn|sncf|renfe|metro|bus|shell|esso|texaco|fuel|petrol|parking|citymapper)"""), "transit", false),
    Triple(Regex("""\b(rent|landlord|mortgage|letting)"""), "home", false),
    Triple(Regex("""\b(electric|energy|octopus|british gas|edf|eon|water|council tax|broadband|vodafone|o2|ee|three|verizon|t mobile|insurance|account fee|monthly fee|service charge|overdraft)"""), "bills", false),
    Triple(Regex("""\b(netflix|spotify|disney|hbo|prime video|youtube|apple tv|audible|cinema|odeon|steam|playstation|xbox|nintendo|ticketmaster)"""), "entertainment", false),
    Triple(Regex("""\b(pharmacy|boots|chemist|apotheke|doctor|dentist|hospital|clinic|optician|gym|puregym|fitness)"""), "health", false),
    Triple(Regex("""\b(airline|airways|ryanair|easyjet|wizz|lufthansa|klm|air france|vueling|hotel|airbnb|booking com|expedia|hostel)"""), "travel", false),
    Triple(Regex("""\b(salary|payroll|wages|gehalt|salaire)"""), "salary", true),
    Triple(Regex("""\b(interest|refund|cashback|dividend|reimburse)"""), "other_income", true),
)
private val NOT_WORD = Regex("""[^\p{L}\p{N}&]+""")

/** A starting category for a payee from a few well-known names and words, or null. */
fun starterCategory(text: String, income: Boolean, have: Set<String>): String? {
    val t = " ${text.lowercase().replace(NOT_WORD, " ")} "
    return STARTER.firstOrNull { (re, id, way) -> way == income && id in have && re.containsMatchIn(t) }?.second
}

/** A line of one account, for finding transfers between accounts. */
data class Leg(val key: String, val account: String, val day: String, val amount: Long)

/**
 * Lines of different accounts that look like one transfer: money out of one and the same amount into the
 * other within `days` days, closest first, each line used once. Returns pairs of line keys, out first.
 */
fun pairTransfers(lines: List<Leg>, days: Int = 3): List<Pair<String, String>> {
    class Candidate(val o: String, val i: String, val d: Int)
    val outs = lines.filter { it.amount < 0 }
    val ins = lines.filter { it.amount > 0 }
    val cands = mutableListOf<Candidate>()
    for (o in outs) for (i in ins) {
        if (i.account == o.account || i.amount != -o.amount) continue
        val d = abs(dayDiff(o.day, i.day))
        if (d <= days) cands += Candidate(o.key, i.key, d)
    }
    val used = mutableSetOf<String>()
    val out = mutableListOf<Pair<String, String>>()
    for (c in cands.sortedBy { it.d }) {
        if (c.o in used || c.i in used) continue
        used += c.o
        used += c.i
        out += c.o to c.i
    }
    return out
}

/** One statement of an account, for [statementGaps]. Balances are as printed. */
data class Span(val name: String, val from: String, val to: String, val opening: Long?, val closing: Long?)

sealed interface Gap {
    /** No statement covers these days. */
    data class Uncovered(val from: String, val to: String) : Gap

    /** [next] doesn't open where [previous] closed. */
    data class Opening(val next: String, val opening: Long, val previous: String, val closing: Long) : Gap
}

/**
 * Checks one account's statements join up: a gap of more than a few days between one and the next, or a
 * statement that doesn't open where the one before closed, means one is likely missing.
 */
fun statementGaps(files: List<Span>): List<Gap> {
    val sorted = files.sortedBy { it.from }
    val out = mutableListOf<Gap>()
    for (i in 1 until sorted.size) {
        val a = sorted[i - 1]
        val b = sorted[i]
        if (dayDiff(a.to, b.from) > 5) out += Gap.Uncovered(addDays(a.to, 1), addDays(b.from, -1))
        else if (a.closing != null && b.opening != null && a.closing != b.opening) out += Gap.Opening(b.name, b.opening, a.name, a.closing)
    }
    return out
}

/** A statement's lines and the refs a bank's correction in it withdrew. */
data class StatementLines(val lines: List<Line>, val withdrawn: List<String> = emptyList())

/**
 * One account's lines from several statements, each line once. Statements that overlap share lines: the same
 * bank id, or in days an earlier statement covers, the same day and amount. Within one statement, two equal
 * lines are two payments. A line another statement's bank correction replaced or withdrew is left out.
 */
fun mergeStatements(files: List<StatementLines>): List<Line> {
    val corrected = files.flatMap { f -> f.withdrawn + f.lines.mapNotNull { it.replaces } }.toSet()
    fun num(l: Line) = l.day.replace("-", "").toInt()
    fun span(f: StatementLines) = f.lines.minOf(::num) to f.lines.maxOf(::num)
    val sorted = files.filter { it.lines.isNotEmpty() }.sortedBy { span(it).first }
    val refs = mutableSetOf<String>()
    val counts = mutableMapOf<String, Int>()
    val covered = mutableListOf<Pair<Int, Int>>()
    val out = mutableListOf<Line>()
    for (f in sorted) {
        val kept = mutableListOf<Line>()
        for (l in f.lines) {
            val k = "${l.day}|${l.amount}"
            val d = num(l)
            val seen = if (covered.any { (a, b) -> d in a..b }) counts[k] ?: 0 else 0
            if (seen > 0) counts[k] = seen - 1
            if (seen > 0 || (l.ref != null && (l.ref in refs || l.ref in corrected))) continue
            kept += l
        }
        for (l in kept) {
            l.ref?.let(refs::add)
            val k = "${l.day}|${l.amount}"
            counts[k] = (counts[k] ?: 0) + 1
        }
        covered += span(f)
        out += kept
    }
    return out
}
