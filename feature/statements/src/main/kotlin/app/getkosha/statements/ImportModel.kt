package app.getkosha.statements

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.setValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.getkosha.data.Kosha
import app.getkosha.model.AccountType
import app.getkosha.model.Categories
import app.getkosha.model.Dates
import app.getkosha.model.Wallet
import app.getkosha.model.guessCategory
import app.getkosha.network.ApiException
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.io.IOException
import java.util.UUID
import kotlin.math.abs

/** A statement read from a file. Balances are as the bank prints them: for a card, what is owed. */
data class Stmt(
    val id: String,
    val file: String,
    val source: String,
    val lines: List<Line>,
    val withdrawn: List<String> = emptyList(),
    /** A CSV's text and first rows, so a changed column choice re-reads it. */
    val picked: Picked? = null,
    val rows: List<List<String>> = emptyList(),
    val layout: Layout? = null,
    val signature: String? = null,
    val remembered: Boolean = false,
    val byAi: Boolean = false,
    val confident: Boolean = true,
    val opening: Long? = null,
    val closing: Long? = null,
    val closingDay: String? = null,
    /** For a PDF read by AI: how far its lines miss its closing balance. */
    val off: Long? = null,
    val currency: String? = null,
    val type: AccountType? = null,
    val last4: String? = null,
    /** Which account it is from: statements with the same key go to the same account. */
    val key: String,
    /** A name for that account. */
    val title: String,
    val acct: String = "",
)

/** Where a group of statements goes: "new", or the id of an account the person has. */
data class Acct(
    val id: String,
    val target: String,
    val name: String,
    val type: AccountType,
    val currency: String,
    val limit: Long? = null,
    /** The closing balance as typed, once the person changed it; null clears it. */
    val closing: Long? = null,
    val closingEdited: Boolean = false,
)

/** An account's details: from the wallet when it goes into one the person has. */
data class Info(val name: String, val type: AccountType, val currency: String, val limit: Long, val wallet: Wallet?)

/** A statement line in the review, keyed by its account and bank id. */
data class Row(val line: Line, val key: String, val acct: String)

/** Lines grouped under one payee: a change applies to all of them. */
data class Group(
    val id: String,
    val payee: String,
    val income: Boolean,
    val raws: MutableList<String>,
    val lines: MutableList<Row>,
    var category: String,
    var aiPayee: Boolean,
    var aiCategory: Boolean,
)

data class Review(val rows: List<Row>, val pairs: List<Pair<Row, Row>>, val list: List<Group>)

data class Reading(val id: String, val name: String, val pdf: Boolean)

enum class Fail { Pdf, Empty, Offline, Unreadable, Server }

data class Failed(val id: String, val name: String, val fail: Fail, val message: String? = null)

/** What the save is doing, for its progress line. */
sealed interface Doing {
    data class Setup(val name: String) : Doing
    data class Adding(val count: Int, val name: String) : Doing
    data object Transfers : Doing
    data class Checking(val name: String) : Doing
    data object Finished : Doing
}

data class DoneAccount(val name: String, val walletId: String, val lines: Int, val reconciled: String?, val isNew: Boolean, val checkPdf: Boolean)

data class ImportResult(val accounts: List<DoneAccount>, val lines: Int, val transfers: Int, val recurring: List<String>)

/** The words a statement's account name is made of, from the screen. */
data class Labels(val bank: String, val card: String, val newAccount: String)

private val GENERIC = Regex("^(account|current|checking|cheque|savings?|credit|card|bank|joint|main|personal|everyday)$")
private fun words(s: String) = descriptorKey(s).split(" ").filter { it.isNotEmpty() && !GENERIC.matches(it) }
private val CARD_FILE = Regex("card|credit|visa|amex|master", RegexOption.IGNORE_CASE)

/** A file's name as an account name: "n26-statement-2026-10.csv" is "N26". */
internal fun fileTitle(name: String) = name
    .replace(Regex("""\.[a-z0-9]+$""", RegexOption.IGNORE_CASE), "")
    .replace(Regex("""[\d_\-.()\[\]]+"""), " ")
    .replace(Regex("""\b(statement|export|transactions?|download|account)\b""", RegexOption.IGNORE_CASE), " ")
    .trim()
    .replace(Regex("""\s+"""), " ")
    .replace(Regex("""\b\p{L}""")) { it.value.uppercase() }

// Raw payee groups are the bank text's merchant words and the direction of the money.
private fun rawKey(l: Line) =
    "${descriptorKey(l.description).ifEmpty { l.description.lowercase().trim().take(60) }}|${if (l.amount < 0) "out" else "in"}"

/**
 * Importing many statements at once, from choosing files to Done (the web's import.tsx): read each file on the
 * server, put statements with their accounts, check the payees and transfers, then save account by account.
 * What is saved is remembered, so trying again after a failure carries on rather than starting over.
 */
class ImportModel : ViewModel() {
    enum class Step { Files, Accounts, Review, Saving, Done }

    private val kosha = Kosha.get()
    var labels = Labels("", "", "")

    var step by mutableStateOf(Step.Files)
    private var memory by mutableStateOf(Memory())
    var reading by mutableStateOf(listOf<Reading>())
        private set
    /** Files read in this round, for "4 of 6 done". */
    var batch by mutableIntStateOf(0)
        private set
    var failed by mutableStateOf(listOf<Failed>())
    var stmts by mutableStateOf(listOf<Stmt>())
    var accts by mutableStateOf(listOf<Acct>())
    private var already by mutableStateOf(mapOf<String, Set<String>>())
    var busy by mutableStateOf(false)
        private set
    var checkError by mutableStateOf<String?>(null)

    /** Payee and category chosen per raw payee group. */
    private var edits by mutableStateOf(mapOf<String, Pair<String?, String?>>())
    /** Transfer pairs the person said aren't transfers, by the money-out line. */
    private var unpaired by mutableStateOf(setOf<String>())
    /** Lines left out of the import. */
    private var left by mutableStateOf(setOf<String>())
    private var tidy by mutableStateOf(mapOf<String, Tidy>())
    var naming by mutableStateOf(false)
        private set
    private var tidyAsked = false

    var doing by mutableStateOf<Doing?>(null)
        private set
    var progress by mutableFloatStateOf(0f)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var result by mutableStateOf<ImportResult?>(null)
        private set

    private val meta get() = kosha.meta.value

    init {
        viewModelScope.launch { runCatching { memory = kosha.api.get<Memory>("/api/app/import/memory") } }
    }

    fun wallets(): List<Wallet> = meta?.wallets.orEmpty().filter { !it.isArchived && it.type != AccountType.Investment }

    fun info(a: Acct): Info {
        val w = wallets().firstOrNull { it.id == a.target }
        return if (w != null) Info(w.name, w.type, w.currency, w.creditLimit ?: 0, w)
        else Info(a.name.trim().ifEmpty { labels.newAccount }, a.type, a.currency, a.limit ?: 0, null)
    }

    /** Accounts that still have a statement. */
    val live: List<Acct> get() = accts.filter { a -> stmts.any { it.acct == a.id } }

    fun linesOf(a: Acct) = mergeStatements(stmts.filter { it.acct == a.id }.map { StatementLines(it.lines, it.withdrawn) })

    /** The closing balance from the latest statement that gives one, as the bank prints it. */
    data class Closing(val day: String, val balance: Long?, val fromFile: Boolean)

    fun closingOf(a: Acct): Closing {
        val latest = stmts.filter { it.acct == a.id && it.closing != null && it.closingDay != null }.maxByOrNull { it.closingDay!! }
        val day = latest?.closingDay ?: span(linesOf(a))?.second ?: Dates.text(Dates.today(kosha.tz))
        return Closing(day, if (a.closingEdited) a.closing else latest?.closing, !a.closingEdited && latest != null)
    }

    // ---------- reading files ----------

    /** Reads files three at a time: a PDF read by AI takes a while, and a year of them shouldn't queue. */
    fun add(context: Context, uris: List<Uri>) {
        val queue = uris.map { Reading(UUID.randomUUID().toString(), fileName(context, it), false) to it }
            .map { (r, uri) -> r.copy(pdf = isPdf(context, uri, r.name)) to uri }
        if (reading.isEmpty()) batch = 0
        batch += queue.size
        reading = reading + queue.map { it.first }
        val gate = Semaphore(3)
        queue.forEach { (r, uri) ->
            viewModelScope.launch {
                gate.withPermit {
                    try {
                        place(read(context, uri, r.id))
                    } catch (e: Failure) {
                        failed = failed + Failed(r.id, r.name, e.fail)
                    } catch (e: ApiException) {
                        failed = failed + Failed(r.id, r.name, Fail.Server, e.message)
                    } catch (e: IOException) {
                        failed = failed + Failed(r.id, r.name, Fail.Offline)
                    } catch (e: Exception) {
                        failed = failed + Failed(r.id, r.name, Fail.Unreadable)
                    } finally {
                        reading = reading.filter { it.id != r.id }
                    }
                }
            }
        }
    }

    private class Failure(val fail: Fail) : Exception()

    private suspend fun read(context: Context, uri: Uri, id: String): Stmt {
        val ai = meta?.ai?.statements == true
        val file = readFile(context, uri)
        if (file.pdf && !ai) throw Failure(Fail.Pdf)
        val card = !file.pdf && CARD_FILE.containsMatchIn(file.name)
        val r = kosha.api.read(file, card)
        fun typeLabel(credit: Boolean) = if (credit) labels.card else labels.bank
        fun named(base: String, last4: String?) = base + (last4?.let { " ••$it" } ?: "")
        return when (r.format) {
            "pdf" -> {
                val credit = r.account?.type == "credit"
                val last4 = r.account?.last4
                Stmt(
                    id, file.name, "pdf", r.lines, byAi = true, opening = r.opening, closing = r.closing,
                    closingDay = r.to ?: span(r.lines)?.second, off = r.off, currency = r.currency,
                    type = if (credit) AccountType.Credit else AccountType.Bank, last4 = last4,
                    key = when {
                        last4 != null -> "last4:$last4"
                        r.account?.name != null -> "name:${r.account.name.lowercase()}"
                        else -> "file:$id"
                    },
                    // The last four digits stay in the name, so next month's statement finds this account.
                    title = named(r.account?.name ?: typeLabel(credit), last4),
                )
            }
            "ofx", "qfx" -> {
                if (r.lines.isEmpty()) throw Failure(Fail.Empty)
                val credit = r.account?.type == "credit"
                val last4 = r.account?.id?.takeLast(4)
                Stmt(
                    id, file.name, "ofx", r.lines, r.withdrawn,
                    // Cards report what's owed as a negative balance.
                    closing = r.closing?.let { if (credit) -it else it },
                    closingDay = r.closingDay ?: span(r.lines)?.second,
                    currency = r.currency, type = if (credit) AccountType.Credit else AccountType.Bank, last4 = last4,
                    key = r.account?.id?.let { "ofx:$it" } ?: "file:$id",
                    title = named(typeLabel(credit), last4),
                )
            }
            else -> {
                val remembered = r.signature != null && r.signature in memory.layouts
                var csv = r
                var byAi = false
                if (!remembered && !r.confident && ai) {
                    kosha.api.aiLayout(r)?.let { l ->
                        csv = kosha.api.read(file, card, l)
                        byAi = true
                    }
                }
                val layout = csv.layout
                // Some exports (Monzo, Revolut) have a currency column; its first value is the account's currency.
                val currency = layout?.takeIf { it.header >= 0 }?.let { l ->
                    val ci = csv.rows.getOrNull(l.header)?.indexOfFirst { it.trim().equals("currency", ignoreCase = true) } ?: -1
                    if (ci >= 0) csv.rows.getOrNull(l.header + 1)?.getOrNull(ci)?.trim()?.lowercase() else null
                }?.takeIf { Regex("^[a-z]{3}$").matches(it) }
                val b = csvBalances(csv.lines, card)
                val last4 = csv.accountHint
                Stmt(
                    id, file.name, "csv", csv.lines, picked = file, rows = csv.rows, layout = layout, signature = csv.signature,
                    remembered = remembered, byAi = byAi, confident = csv.confident || byAi,
                    opening = b.opening, closing = b.closing, closingDay = b.closingDay, currency = currency,
                    type = if (card) AccountType.Credit else null, last4 = last4,
                    key = last4?.let { "last4:$it" } ?: "sig:${csv.signature}",
                    title = named(fileTitle(file.name).ifEmpty { typeLabel(card) }, last4),
                )
            }
        }
    }

    /** Two statements are of one account when they share a key and, for a CSV, overlap or share a line. */
    private fun sameAccount(a: Stmt, b: Stmt): Boolean {
        if (a.key != b.key) return false
        if (a.key.startsWith("ofx:") || a.key.startsWith("last4:")) return true
        // Two exports in the same format that cover the same days without sharing a line are two accounts.
        val x = span(a.lines) ?: return true
        val y = span(b.lines) ?: return true
        if (x.second < y.first || y.second < x.first) return true
        val refs = a.lines.map { it.ref }.toSet()
        return b.lines.any { it.ref in refs }
    }

    /** Puts a statement with its account: one it shares a key with, else a new one, matched to a wallet if one fits. */
    private fun place(s: Stmt) {
        val same = stmts.firstOrNull { sameAccount(it, s) }
        if (same != null) {
            stmts = stmts + s.copy(acct = same.acct)
            return
        }
        val acct = UUID.randomUUID().toString()
        val taken = accts.map { it.target }.toSet()
        val fits = wallets().filter { w ->
            w.id !in taken && ((s.last4 != null && w.name.contains(s.last4)) ||
                (words(w.name).isNotEmpty() && words(s.title).containsAll(words(w.name))))
        }
        stmts = stmts + s.copy(acct = acct)
        accts = accts + Acct(acct, fits.singleOrNull()?.id ?: "new", s.title, s.type ?: AccountType.Bank, s.currency ?: meta?.primary ?: "eur")
    }

    fun dismiss(f: Failed) {
        failed = failed - f
    }

    // ---------- accounts ----------

    fun patch(id: String, change: (Acct) -> Acct) {
        accts = accts.map { if (it.id == id) change(it) else it }
    }

    /** A card's CSV often shows spending as positive: read it again as a card's, unless its columns were set. */
    fun setType(a: Acct, type: AccountType) {
        patch(a.id) { it.copy(type = type) }
        stmts.filter { it.acct == a.id && it.source == "csv" && !it.remembered && !it.byAi && it.layout?.balance == null && it.picked != null }
            .forEach { s ->
                viewModelScope.launch {
                    runCatching { kosha.api.read(s.picked!!, type == AccountType.Credit) }.onSuccess { r -> replaced(s, r, r.layout ?: s.layout, type == AccountType.Credit) }
                }
            }
    }

    /** A CSV read again with columns the person set. */
    fun columns(s: Stmt, layout: Layout) {
        val card = accts.firstOrNull { it.id == s.acct }?.let { info(it).type } == AccountType.Credit
        viewModelScope.launch {
            runCatching { kosha.api.read(s.picked ?: return@launch, card, layout) }
                .onSuccess { r -> replaced(s, r, layout, card, edited = true) }
                .onFailure { checkError = (it as? ApiException)?.message ?: "" }
        }
    }

    private fun replaced(s: Stmt, r: Read, layout: Layout?, card: Boolean, edited: Boolean = false) {
        val b = csvBalances(r.lines, card)
        stmts = stmts.map {
            if (it.id != s.id) it
            else it.copy(lines = r.lines, rows = r.rows, layout = layout, opening = b.opening, closing = b.closing, closingDay = b.closingDay,
                remembered = if (edited) false else it.remembered, byAi = if (edited) false else it.byAi)
        }
    }

    fun move(s: Stmt, to: String) {
        if (to == "new") {
            val id = UUID.randomUUID().toString()
            accts = accts + Acct(id, "new", s.title, s.type ?: AccountType.Bank, s.currency ?: meta?.primary ?: "eur")
            stmts = stmts.map { if (it.id == s.id) it.copy(acct = id) else it }
        } else {
            stmts = stmts.map { if (it.id == s.id) it.copy(acct = to) else it }
        }
    }

    fun remove(s: Stmt) {
        stmts = stmts - s
    }

    /** Lines already in an account Kosha has are left out: by bank id, or the same amount within a few days. */
    fun toReview() {
        viewModelScope.launch {
            busy = true
            checkError = null
            try {
                val found = mutableMapOf<String, Set<String>>()
                for (a in live) {
                    val w = wallets().firstOrNull { it.id == a.target }
                    if (w == null || w.entryCount == 0) continue
                    val lines = linesOf(a)
                    val r = span(lines) ?: continue
                    // An entry the bank withdrew stays as it is, but matches nothing.
                    val gone = stmts.filter { it.acct == a.id }.flatMap { it.withdrawn }.toSet()
                    val entries = kosha.api.get<List<KnownEntry>>("/api/app/import/entries", mapOf("walletId" to w.id, "from" to r.first, "to" to r.second))
                        .filter { (it.ref ?: "") !in gone }
                        .map { Entry(it.id, it.day, it.amount, it.name, it.ref) }
                    val m = matchLines(lines, entries)
                    found[a.id] = m.pairs.filter { it.kind != Kind.Close }.mapNotNull { lines[it.line].ref }.toSet()
                }
                already = found
                step = Step.Review
                askTidy()
            } catch (e: ApiException) {
                checkError = e.message
            } catch (e: IOException) {
                checkError = ""
            } finally {
                busy = false
            }
        }
    }

    // ---------- review: payees, categories and transfers ----------

    private val categories get() = meta?.categories.orEmpty().filter { !it.isArchived && it.parentId == null && !Categories.isNeutral(it.id) }
    private val guessing get() = meta?.pref("suggest_category", "0") == "1"

    data class Guess(val payee: String, val category: String, val aiPayee: Boolean, val aiCategory: Boolean)

    /** What Kosha would pick for a raw group by itself: remembered names, rules, AI, then well-known merchants. */
    private fun guess(l: Line): Guess {
        val m = meta
        val key = descriptorKey(l.description)
        val income = l.amount > 0
        val ai = tidy[key]
        val own = memory.aliases[key] ?: l.payee?.takeIf { it.isNotBlank() }
        val payee = own ?: ai?.payee ?: payeeFor(l.description, m?.titles.orEmpty(), memory.aliases)
        val cats = categories
        fun fits(id: String?) = id?.takeIf { x -> cats.any { it.id == x && it.isIncome == income } }
        val ruled = if (guessing) fits(guessCategory(payee, m?.rules.orEmpty())) ?: fits(guessCategory(l.description, m?.rules.orEmpty())) else null
        val byAi = if (guessing && ruled == null) fits(ai?.categoryId) else null
        val category = ruled ?: byAi ?: (if (guessing) starterCategory("$payee ${l.description}", income, cats.map { it.id }.toSet()) else null) ?: ""
        return Guess(payee, category, own == null && ai != null, byAi != null)
    }

    /** The review: lines not yet in Kosha, transfers between the accounts, and payee groups, most lines first. */
    val review: Review? by derivedStateOf {
        if (step == Step.Files || step == Step.Accounts) return@derivedStateOf null
        val liveAccts = live
        val rows = liveAccts.flatMap { a ->
            linesOf(a).filter { it.ref !in already[a.id].orEmpty() }.map { Row(it, "${a.id}:${it.ref}", a.id) }
        }.filter { it.key !in left }
        val byKey = rows.associateBy { it.key }
        // Money out of one account and into another on the same days is a transfer between them.
        val curOf = liveAccts.associate { it.id to info(it).currency }
        val pairs = curOf.values.distinct()
            .flatMap { cur -> pairTransfers(rows.filter { curOf[it.acct] == cur }.map { Leg(it.key, it.acct, it.line.day, it.line.amount) }) }
            .filter { it.first !in unpaired }
            .map { byKey.getValue(it.first) to byKey.getValue(it.second) }
        val paired = pairs.flatMap { listOf(it.first.key, it.second.key) }.toSet()
        val raws = LinkedHashMap<String, MutableList<Row>>()
        for (r in rows) if (r.key !in paired) raws.getOrPut(rawKey(r.line)) { mutableListOf() } += r
        val groups = LinkedHashMap<String, Group>()
        for ((raw, lines) in raws.entries.sortedByDescending { it.value.size }) {
            val g = guess(lines[0].line)
            val edit = edits[raw]
            val payee = (edit?.first ?: g.payee).trim().ifEmpty { g.payee }
            val income = lines[0].line.amount > 0
            val id = "${payee.lowercase()}|$income"
            val group = groups[id]
            if (group != null) {
                group.raws += raw
                group.lines += lines
                if (group.category.isEmpty()) group.category = edit?.second ?: g.category
            } else {
                groups[id] = Group(id, payee, income, mutableListOf(raw), lines.toMutableList(), edit?.second ?: g.category,
                    edit?.first == null && g.aiPayee, edit?.second == null && g.aiCategory)
            }
        }
        // A category chosen for any part of a merged group goes for all of it.
        for (g in groups.values) {
            g.raws.firstNotNullOfOrNull { edits[it]?.second }?.let { g.category = it; g.aiCategory = false }
        }
        Review(rows, pairs, groups.values.sortedByDescending { it.lines.size })
    }

    fun edit(g: Group, payee: String? = null, category: String? = null) {
        edits = edits + g.raws.associateWith { raw ->
            val was = edits[raw]
            (payee ?: was?.first) to (category ?: was?.second)
        }
    }

    fun unpair(pairs: List<Pair<Row, Row>>) {
        unpaired = unpaired + pairs.map { it.first.key }
    }

    fun leaveOut(pairs: List<Pair<Row, Row>>) {
        left = left + pairs.flatMap { listOf(it.first.key, it.second.key) }
    }

    /** AI names payees and sorts what Kosha can't place itself, once, in chunks a model answers well. */
    private fun askTidy() {
        val m = meta ?: return
        if (tidyAsked || !m.ai.statements || m.pref("ai_share_payee", "0") != "1") return
        tidyAsked = true
        val r = review ?: return
        val todo = LinkedHashMap<String, TidyItem>()
        for (g in r.list) for (raw in g.raws) {
            val l = g.lines.first { rawKey(it.line) == raw }.line
            val key = descriptorKey(l.description)
            if (key.isEmpty() || memory.aliases[key] != null || key in todo) continue
            if (l.payee == null || guess(l).category.isEmpty()) todo[key] = TidyItem(key, l.description, l.amount)
        }
        if (todo.isEmpty()) return
        naming = true
        viewModelScope.launch {
            for (chunk in todo.values.chunked(150)) {
                // Without it, Kosha's own names stand.
                val got = runCatching { kosha.api.post<TidyBody, Map<String, Tidy>>("/api/app/import/tidy", TidyBody(chunk)) }.getOrNull() ?: break
                tidy = tidy + got
            }
            naming = false
        }
    }

    // ---------- saving ----------

    private val savedIds = mutableMapOf<String, String>()
    private val savedLines = mutableMapOf<String, Int>()
    private var savedPairs = 0
    private var remembered = false
    private val finished = mutableMapOf<String, ImportFinished>()

    fun save() {
        val plan = review ?: return
        step = Step.Saving
        error = null
        viewModelScope.launch {
            val liveAccts = live
            val groupOf = plan.list.flatMap { g -> g.lines.map { it.key to g } }.toMap()
            fun payload(a: Acct) = plan.rows.filter { it.acct == a.id && groupOf[it.key]?.category.let { c -> c != null && c != "skip" } }.map { l ->
                val g = groupOf.getValue(l.key)
                val to = when {
                    g.category.startsWith("t:") -> savedIds[g.category.removePrefix("t:")]
                    g.category.startsWith("w:") -> g.category.removePrefix("w:")
                    else -> null
                }
                NewLine(
                    l.line.day, l.line.amount,
                    name = if (to != null) "" else g.payee,
                    note = if (to != null) g.payee else "",
                    categoryId = if (to == null) g.category else null,
                    transferWith = to,
                    ref = l.line.ref!!,
                )
            }
            val total = liveAccts.size * 2 + plan.rows.size - plan.pairs.size * 2 + plan.pairs.size + 1
            var done = 0
            fun tick(n: Int, what: Doing) {
                done += n
                doing = what
                progress = (done.toFloat() / total).coerceAtMost(1f)
            }
            try {
                for (a in liveAccts) {
                    val i = info(a)
                    tick(0, Doing.Setup(i.name))
                    if (savedIds[a.id] == null) {
                        savedIds[a.id] = if (a.target != "new") a.target
                        else kosha.api.post<NewAccount, Created>(
                            "/api/v1/accounts",
                            NewAccount(i.name, if (a.type == AccountType.Credit) "credit" else "bank", a.currency, if (a.type == AccountType.Credit) a.limit ?: 0 else null),
                        ).id
                    }
                    tick(1, Doing.Setup(i.name))
                }
                // Kosha remembers each bank's columns and the payee names, so the next statement reads straight away.
                val layouts: List<JsonElement> = stmts.filter { it.source == "csv" && it.signature != null && it.layout != null }
                    .distinctBy { it.signature }
                    .take(50)
                    .map { s -> buildJsonObject { put("signature", JsonPrimitive(s.signature)); put("layout", s.layout!!.json()) } }
                val aliases = plan.list.flatMap { g -> g.raws.map { raw -> Alias(raw.substringBeforeLast('|'), g.payee) } }
                    .distinctBy { it.key }
                    .filter { it.key.isNotEmpty() && it.payee.isNotEmpty() && it.key == descriptorKey(it.key) }
                    .take(2000)
                for (a in liveAccts) {
                    val list = payload(a)
                    val name = info(a).name
                    var i = savedLines[a.id] ?: 0
                    while (i < list.size) {
                        tick(0, Doing.Adding(list.size, name))
                        kosha.api.post<LinesBody, JsonElement>(
                            "/api/app/import/lines",
                            LinesBody(savedIds.getValue(a.id), if (remembered) emptyList() else layouts, if (remembered) emptyList() else aliases, list.subList(i, minOf(i + 500, list.size))),
                        )
                        remembered = true
                        savedLines[a.id] = i + 500
                        tick(minOf(500, list.size - i), Doing.Adding(list.size, name))
                        i += 500
                    }
                }
                while (savedPairs < plan.pairs.size) {
                    tick(0, Doing.Transfers)
                    val chunk = plan.pairs.subList(savedPairs, minOf(savedPairs + 200, plan.pairs.size))
                    kosha.api.post<PairBody, JsonElement>(
                        "/api/app/import/transfers",
                        PairBody(chunk.map { (o, n) ->
                            TransferPair(savedIds.getValue(o.acct), savedIds.getValue(n.acct), -o.line.amount, n.line.amount, o.line.day, o.line.ref!!, n.line.ref!!)
                        }),
                    )
                    savedPairs += 200
                    tick(chunk.size, Doing.Transfers)
                }
                for (a in liveAccts) {
                    val i = info(a)
                    tick(0, Doing.Checking(i.name))
                    if (finished[a.id] == null) {
                        val own = plan.rows.filter { it.acct == a.id }.map { it.line }
                        val c = closingOf(a)
                        val credit = i.type == AccountType.Credit
                        finished[a.id] = kosha.api.postExact(
                            "/api/app/import/finish",
                            ExactJson.encodeToJsonElement(
                                FinishImport.serializer(),
                                FinishImport(
                                    savedIds.getValue(a.id),
                                    span(own)?.first ?: c.day,
                                    c.balance?.let { FinishImport.Closing(c.day, if (credit) i.limit - abs(it) else it) },
                                    if (a.target == "new") null else i.wallet?.balance ?: 0,
                                ),
                            ),
                        )
                    }
                    tick(1, Doing.Checking(i.name))
                }
                kosha.changed()
                val spotted = runCatching {
                    kosha.api.get<RecurringSuggestions>("/api/v1/recurring", mapOf("includeSuggestions" to true)).suggestions.map { it.payee }
                }.getOrDefault(emptyList())
                tick(1, Doing.Finished)
                result = ImportResult(
                    liveAccts.map { a ->
                        val i = info(a)
                        DoneAccount(
                            i.name, savedIds.getValue(a.id), plan.rows.count { it.acct == a.id },
                            if (finished[a.id]?.reconciled == true) closingOf(a).day else null,
                            a.target == "new",
                            stmts.any { it.acct == a.id && it.off != null && it.off != 0L },
                        )
                    },
                    plan.rows.size, plan.pairs.size, spotted,
                )
                step = Step.Done
            } catch (e: ApiException) {
                error = e.message.orEmpty()
            } catch (e: IOException) {
                error = ""
            }
        }
    }
}
