package app.getkosha.add

import android.content.Context
import android.graphics.BitmapFactory
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import app.getkosha.data.Kosha
import app.getkosha.data.Queued
import app.getkosha.design.R
import app.getkosha.design.Toast
import app.getkosha.design.ToastState
import app.getkosha.model.Categories
import app.getkosha.model.Dates
import app.getkosha.model.EntryKind
import app.getkosha.model.Event
import app.getkosha.model.Keypad
import app.getkosha.model.KoshaJson
import app.getkosha.model.Meta
import app.getkosha.model.MetaCategory
import app.getkosha.model.Money
import app.getkosha.model.NewTransaction
import app.getkosha.model.Payee
import app.getkosha.model.Payees
import app.getkosha.model.Sign
import app.getkosha.model.Transaction
import app.getkosha.model.TransactionList
import app.getkosha.model.Wallet
import app.getkosha.model.convert
import app.getkosha.model.guessCategory
import app.getkosha.network.ApiException
import app.getkosha.ui.Route
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import java.io.IOException
import java.time.LocalDate
import java.time.LocalTime
import java.util.Base64
import java.util.UUID
import kotlin.math.abs

internal enum class AddSheet { Account, To, Date, Time, Note, Category, Fee, Describe }

/** Where AI filled the form from. */
internal enum class Filled { Text, Receipt }

/** A file to attach once the entry is saved. */
internal class Staged(val name: String, val type: String, val bytes: ByteArray)

/** The largest file Kosha stores (core.ts ATTACHMENT_LIMIT). */
internal const val ATTACHMENT_LIMIT = 10 * 1024 * 1024

/** Toasts, haptics and navigation for the model, from the screen that holds it. */
internal class AddUi(private val context: Context, private val view: View, private val toasts: ToastState, val nav: NavController) {
    fun s(@StringRes id: Int, vararg args: Any): String = context.getString(id, *args)

    fun toast(message: String, undo: (() -> Unit)? = null) =
        toasts.show(Toast(message, if (undo != null) s(R.string.common_undo) else null, undo))

    /** A refused save or read: the warning haptic and the message. */
    fun error(message: String) {
        view.performHapticFeedback(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS)
        toast(message)
    }

    fun success() {
        view.performHapticFeedback(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY)
    }

    /** Back to where Add was opened from. */
    fun close() {
        nav.popBackStack()
    }
}

/**
 * Everything the Add screen holds, as on the web's phone add (../kosha/src/components/phone-add.tsx): the amount
 * as keypad input, the kind, account, category (picked, or guessed from history, rules or AI), day and time, note,
 * tags, event, files, and the transfer's other account and fee. It lives as long as the Add destination, so
 * opening another screen from a sheet and coming back keeps the form.
 */
internal class AddModel(route: Route.Add) : ViewModel() {
    val kosha = Kosha.get()
    var meta by mutableStateOf(kosha.meta.value ?: Meta())
        private set

    init {
        viewModelScope.launch { kosha.meta.collect { if (it != null) meta = it } }
    }

    val today: LocalDate get() = Dates.today(meta.tz)

    var kind by mutableStateOf(kindOf(route.kind ?: meta.pref("add_kind", "expense")))
        private set
    var input by mutableStateOf("")
    var payee by mutableStateOf("")
        private set
    /** Payees matching what's typed, most used first. */
    var suggestions by mutableStateOf<List<Payee>>(emptyList())
        private set
    private var picked by mutableStateOf<Payee?>(null)

    /** The category picked (always a top-level one) and its subcategory. Null until the person picks. */
    var categoryId by mutableStateOf<String?>(null)
        private set
    var subcategoryId by mutableStateOf<String?>(null)
        private set
    /** The category AI read from a sentence or receipt: a guess until confirmed. */
    private var aiCategoryId by mutableStateOf<String?>(null)

    var accountId by mutableStateOf(startAccount(meta.pref("default_wallet", "last"), active, kosha.local.lastAccount))
        private set
    /** The account came from the payee's history or AI, not from the person. */
    var accountGuessed by mutableStateOf(false)
        private set
    private var accountPicked = false
    var toAccountId by mutableStateOf<String?>(null)

    var day by mutableStateOf(today)
    /** Null until changed: then today's entries get the moment they're saved, as on the web. */
    var time by mutableStateOf<LocalTime?>(null)
    var note by mutableStateOf("")
    var tags by mutableStateOf<List<String>>(emptyList())
    /** Until an event is picked, the entry joins the event its day falls in. */
    var eventPicked by mutableStateOf(false)
        private set
    private var eventChoice by mutableStateOf<String?>(null)
    /** An event made here, until meta brings it back. */
    var newEvent by mutableStateOf<Event?>(null)
        private set
    val files = mutableStateListOf<Staged>()
    var fee by mutableStateOf(FeeInput())

    var sheet by mutableStateOf<AddSheet?>(null)
    /** The category sheet was opened by Next: picking a tile saves. */
    var viaNext by mutableStateOf(false)
    var filled by mutableStateOf<Filled?>(null)
        private set
    var receipt by mutableStateOf<ImageBitmap?>(null)
        private set
    var reading by mutableStateOf<Filled?>(null)
        private set
    var payeeByAi by mutableStateOf(false)
        private set
    var savedOnce by mutableStateOf(false)
        private set
    var camera by mutableStateOf(route.scan && meta.ai.receipts)
    var described by mutableStateOf("")
    private var saving = false

    private var searchJob: Job? = null
    private var aiJob: Job? = null

    init {
        route.duplicateOf?.let { id -> viewModelScope.launch { duplicate(id) } }
    }

    // ---------- what the form shows ----------

    val active: List<Wallet> get() = meta.wallets.filterNot { it.isArchived }
    val account: Wallet? get() = active.firstOrNull { it.id == accountId } ?: active.firstOrNull()
    val toAccount: Wallet? get() = active.firstOrNull { it.id == toAccountId && it.id != account?.id }
    val currency: String get() = account?.currency ?: meta.primary
    val decimals: Int get() = Money.decimals(currency)
    /** The amount to save, positive minor units, or null while it isn't above zero. */
    val minor: Long? get() = Keypad.minorUnits(input, decimals)
    val transfer get() = kind == EntryKind.Transfer

    /** The account's colour: its place in the account order. */
    fun colorIndex(id: String?) = meta.wallets.indexOfFirst { it.id == id }.coerceAtLeast(0)

    /** Categories the kind can take: not archived, not Kosha's own, income or spending to match. */
    val candidates: List<MetaCategory>
        get() = meta.categories.filter {
            !it.isArchived && !Categories.isSystem(it.id) && it.isIncome == (kind == EntryKind.Income)
        }
    val tops: List<MetaCategory> get() = candidates.filter { c -> c.parentId == null || candidates.none { it.id == c.parentId } }

    /** The payee as Kosha knows it: the suggestion picked, or one whose name is exactly what's typed. */
    val match: Payee? get() = picked ?: suggestions.firstOrNull { it.payee.equals(payee.trim(), ignoreCase = true) }

    private val guessing get() = meta.pref("suggest_category", "1") == "1"
    private fun fits(id: String?) = id != null && tops.any { it.id == id }

    /** The category history gives: the payee's last one, else the payee rules (phone-add.tsx fromHistory). */
    val fromHistory: String?
        get() = if (!guessing) null
        else match?.categoryId?.takeIf(::fits) ?: guessCategory(payee, meta.rules)?.takeIf(::fits)

    /** AI's guess: what it read from a sentence or receipt, or auto-categorise for a payee Kosha hasn't seen. */
    private val aiGuess: String?
        get() = if (!guessing) null else listOf(aiCategoryId, aiGuesses[aiKey(payee)]).firstOrNull(::fits)

    /** The category this entry gets: picked, else guessed. */
    val chosen: String? get() = categoryId?.takeIf(::fits) ?: fromHistory ?: aiGuess
    val chosenIsAi: Boolean get() = categoryId == null && fromHistory == null && chosen != null
    /** What the category tile names: the subcategory when one is picked. */
    val chosenCategory: MetaCategory?
        get() = meta.categories.firstOrNull { it.id == (subcategoryId.takeIf { categoryId != null } ?: chosen) }

    val needsCategory get() = !transfer && chosen == null
    val ready get() = minor != null && !needsCategory

    val eventId: String? get() = if (eventPicked) eventChoice else eventFor(day.toString(), meta.events)
    val event: Event? get() = eventId?.let { id -> meta.events.firstOrNull { it.id == id } ?: newEvent?.takeIf { it.id == id } }

    val sides: TransferSides
        get() = transferSides(minor ?: 0, fee, currency, toAccount?.currency, decimals, Money.decimals(toAccount?.currency ?: currency), meta.rates)

    /** The amount in the main currency, for the hint under it, when the account's currency differs. */
    val converted: Long?
        get() = minor?.takeIf { !transfer && currency != meta.primary }?.let { convert(it, currency, meta.primary, meta.rates) }

    // ---------- changes ----------

    fun pickKind(k: EntryKind) {
        if (k == kind) return
        kind = k
        // A category only fits one kind, so switching starts the pick again.
        categoryId = null
        subcategoryId = null
        askAi()
    }

    fun typePayee(text: String) {
        payee = text
        payeeByAi = false
        if (picked?.payee != text) picked = null
        // A category read with an earlier payee doesn't speak for the new one.
        aiCategoryId = null
        searchJob?.cancel()
        val q = text.trim()
        if (q.isEmpty()) {
            suggestions = emptyList()
        } else {
            searchJob = viewModelScope.launch {
                delay(150)
                suggestions = try {
                    kosha.api.get<Payees>("/api/v1/payees", mapOf("query" to q, "limit" to 5)).payees
                } catch (e: IOException) {
                    // Offline: the payee names this phone saved, without their history.
                    meta.titles.filter { it.contains(q, ignoreCase = true) }.take(5).map { Payee(it, 0) }
                } catch (e: Exception) {
                    emptyList()
                }
            }
        }
        askAi()
    }

    /** Picking a suggestion fills the guesses: its category and its account, in ink2 until confirmed. */
    fun pickPayee(p: Payee) {
        payee = p.payee
        picked = p
        payeeByAi = false
        aiCategoryId = null
        suggestions = emptyList()
        val usual = active.firstOrNull { it.id == p.accountId }
        if (usual != null && !accountPicked && usual.id != account?.id) {
            accountId = usual.id
            accountGuessed = true
        }
        askAi()
    }

    fun pickAccount(id: String, to: Boolean) {
        if (to) {
            toAccountId = id
        } else {
            if (toAccountId == id) toAccountId = account?.id
            accountId = id
            accountPicked = true
            accountGuessed = false
        }
        sheet = null
    }

    fun swap() {
        val from = account ?: return
        val to = toAccount ?: return
        accountId = to.id
        toAccountId = from.id
        accountPicked = true
        fee = FeeInput(provider = fee.provider)
    }

    fun pickCategory(top: String, sub: String?) {
        categoryId = top
        subcategoryId = sub
        sheet = null
    }

    fun pickEvent(id: String?) {
        eventPicked = true
        eventChoice = id
    }

    /**
     * Auto-categorise, asked once per payee and kind, only for a payee history and rules can't place, after typing
     * pauses (ai.tsx useAiCategory). AI spends the person's tokens, so nothing is asked speculatively per keystroke.
     */
    private fun askAi() {
        aiJob?.cancel()
        aiJob = viewModelScope.launch {
            delay(700)
            val name = payee.trim()
            val key = aiKey(name)
            if (!meta.ai.categorise || transfer || name.length < 3 || categoryId != null || aiCategoryId != null ||
                fromHistory != null || aiGuesses.containsKey(key)
            ) return@launch
            // A failure (offline, a bad key) isn't remembered, so the same payee can ask again later.
            runCatching {
                kosha.api.post<CategoryAsk, CategoryGuess>("/api/app/ai/category", CategoryAsk(name, if (kind == EntryKind.Income) "income" else "expense"))
            }.onSuccess { aiGuesses[key] = it.categoryId }
        }
    }

    private fun aiKey(name: String) = "${if (kind == EntryKind.Income) "income" else "expense"}:${name.trim().lowercase()}"

    // ---------- saving ----------

    /** The primary button: Next opens the category sheet when Kosha can't guess one; otherwise it adds and closes. */
    fun primary(ui: AddUi) {
        if (minor == null) return
        if (needsCategory) {
            viaNext = true
            sheet = AddSheet.Category
            return
        }
        save(ui, again = false)
    }

    /**
     * Saves the entry. `again` keeps Add open with the account and date (Add another); otherwise Add closes. A
     * category picked after Next comes in [top] and [sub].
     */
    fun save(ui: AddUi, again: Boolean, top: String? = null, sub: String? = null) {
        if (saving) return
        val amount = minor ?: return ui.error(ui.s(R.string.add_error_amount))
        val from = account ?: return ui.error(ui.s(R.string.add_error_no_account))
        if (meta.demo) return ui.error(ui.s(R.string.common_demo_read_only))
        if (transfer) return saveTransfer(ui, again, amount, from)
        val cat = top ?: chosen ?: return ui.error(ui.s(R.string.add_error_category))
        val subId = if (top != null) sub else subcategoryId.takeIf { categoryId == cat }
        val signed = if (kind == EntryKind.Income) amount else -amount
        val catName = meta.categories.firstOrNull { it.id == (subId ?: cat) }?.name ?: ""
        val entry = NewTransaction(
            id = UUID.randomUUID().toString(),
            accountId = from.id,
            payee = payee.trim(),
            amount = signed,
            categoryId = cat,
            subcategoryId = subId,
            day = day.toString(),
            time = (time ?: Dates.now(meta.tz).takeIf { day != today })?.let(Dates::text),
            note = note.trim(),
            tags = tags,
            eventId = eventId,
        )
        val staged = files.toList()
        saving = true
        sheet = null
        viewModelScope.launch {
            val queued = try {
                kosha.api.send("POST", "/api/v1/transactions", body(entry))
                false
            } catch (e: IOException) {
                // Offline: only new entries queue, each with its id, so a retry is saved once.
                kosha.outbox.add(Queued(entry, from.currency, from.name, catName))
                kosha.sendOutbox()
                true
            } catch (e: Exception) {
                saving = false
                return@launch ui.error((e as? ApiException)?.message?.ifBlank { null } ?: ui.s(R.string.add_error_add))
            }
            saving = false
            kosha.local.lastAccount = from.id
            ui.success()
            val shown = Money.format(signed, from.currency, kosha.language, Sign.Always)
            val name = entry.payee.ifEmpty { catName }
            val message = if (queued) {
                ui.s(R.string.add_toast_saved_offline, shown, name) + if (staged.isNotEmpty()) " " + ui.s(R.string.add_toast_offline_files) else ""
            } else ui.s(R.string.add_toast_added, shown, name)
            ui.toast(message) {
                kosha.scope.launch {
                    if (queued) kosha.outbox.remove(entry.id)
                    else runCatching { kosha.api.send("POST", "/api/v1/transactions/delete", KoshaJson.encodeToJsonElement(DeleteIds(listOf(entry.id)))) }
                        .onFailure { ui.error(it.message ?: ui.s(R.string.tx_delete_failed)) }
                    kosha.changed()
                }
            }
            if (!queued) {
                kosha.changed()
                if (staged.isNotEmpty() && meta.attachments) upload(ui, entry.id, staged)
            }
            if (again) next() else ui.close()
        }
    }

    /** A new transaction as create_transactions takes it. "No event" is sent as null; left out, the server picks by date. */
    private fun body(entry: NewTransaction): JsonObject {
        var tx = KoshaJson.encodeToJsonElement(entry).jsonObject
        if (eventPicked && eventChoice == null) tx = JsonObject(tx + ("eventId" to JsonNull))
        return buildJsonObject { put("transactions", JsonArray(listOf(tx))) }
    }

    /** Files go up after the entry is saved, and keep going once Add has closed. */
    private fun upload(ui: AddUi, id: String, staged: List<Staged>) {
        kosha.scope.launch {
            for (f in staged) {
                runCatching { kosha.api.upload("/api/app/transactions/$id/attachments", f.name, f.type, f.bytes) }
                    .onFailure { ui.error("${f.name}: ${(it as? ApiException)?.message ?: ui.s(R.string.tx_attach_failed)}") }
            }
            kosha.changed()
        }
    }

    /** Transfers need a connection: they're two linked rows the server writes together. */
    private fun saveTransfer(ui: AddUi, again: Boolean, sent: Long, from: Wallet) {
        val to = toAccount ?: run {
            sheet = AddSheet.To
            return ui.error(ui.s(R.string.add_error_to_account))
        }
        val s = sides
        if (s.converted <= 0) return ui.error(ui.s(R.string.add_error_fee))
        if (s.received <= 0) return ui.error(ui.s(R.string.add_error_received))
        saving = true
        sheet = null
        viewModelScope.launch {
            val saved = try {
                kosha.api.post<NewTransfers, SavedTransfers>(
                    "/api/v1/transfers",
                    NewTransfers(listOf(NewTransfer(from.id, to.id, sent, s.received, s.fee, fee.provider.trim(), day.toString(), time?.let(Dates::text), note.trim()))),
                ).transfers.first()
            } catch (e: IOException) {
                saving = false
                return@launch ui.error(ui.s(R.string.auth_error_offline))
            } catch (e: Exception) {
                saving = false
                return@launch ui.error((e as? ApiException)?.message?.ifBlank { null } ?: ui.s(R.string.add_error_add))
            }
            saving = false
            kosha.local.lastAccount = from.id
            ui.success()
            kosha.changed()
            ui.toast(ui.s(R.string.add_toast_moved, Money.format(sent, from.currency, kosha.language, Sign.None), from.name, to.name)) {
                kosha.scope.launch { undoTransfer(ui, saved) }
            }
            if (again) next() else ui.close()
        }
    }

    /** delete_transactions takes a half's id, so Undo finds the outgoing half by the transfer's id. */
    private suspend fun undoTransfer(ui: AddUi, t: SavedTransfer) {
        runCatching {
            val half = kosha.api.get<TransactionList>(
                "/api/v1/transactions",
                mapOf("accountId" to t.fromAccountId, "from" to t.day, "to" to t.day, "type" to "transfer", "limit" to 100),
            ).transactions.first { it.transfer?.id == t.id }
            kosha.api.send("POST", "/api/v1/transactions/delete", KoshaJson.encodeToJsonElement(DeleteIds(listOf(half.id))))
        }.onFailure { ui.error((it as? ApiException)?.message ?: ui.s(R.string.tx_delete_failed)) }
        kosha.changed()
    }

    /** Ready for the next entry: account and date stay (AddSaved). */
    private fun next() {
        input = ""
        payee = ""
        picked = null
        suggestions = emptyList()
        categoryId = null
        subcategoryId = null
        aiCategoryId = null
        filled = null
        receipt = null
        described = ""
        note = ""
        tags = emptyList()
        files.clear()
        eventPicked = false
        eventChoice = null
        time = null
        fee = FeeInput()
        payeeByAi = false
        accountGuessed = false
        viaNext = false
        savedOnce = true
    }

    // ---------- events ----------

    /** New event from the note sheet: it starts on the entry's day and runs until ended. */
    fun createEvent(ui: AddUi, name: String, done: () -> Unit) {
        if (name.isBlank()) return ui.error(ui.s(R.string.tx_event_name_required))
        if (meta.demo) return ui.error(ui.s(R.string.common_demo_read_only))
        viewModelScope.launch {
            try {
                val saved = kosha.api.post<NewEvent, SavedEvent>("/api/v1/events", NewEvent(name.trim(), day.toString()))
                newEvent = Event(saved.id, name.trim(), startDay = day.toString())
                pickEvent(saved.id)
                kosha.changed()
                done()
            } catch (e: IOException) {
                ui.error(ui.s(R.string.auth_error_offline))
            } catch (e: Exception) {
                ui.error((e as? ApiException)?.message?.ifBlank { null } ?: ui.s(R.string.tx_event_save_failed))
            }
        }
    }

    // ---------- AI ----------

    /** Describe it: one sentence read into the form, not saved. */
    fun describe(ui: AddUi) {
        val text = described.trim()
        if (text.isEmpty() || reading != null) return
        sheet = null
        read(ui, Filled.Text, ReadAsk(text = text, accountId = account?.id), null)
    }

    /** A receipt photo (JPEG, at most 1600px): read into the form, and attached once the entry is saved. */
    fun scan(ui: AddUi, jpeg: ByteArray) {
        if (reading != null) return
        read(ui, Filled.Receipt, ReadAsk(photo = ReadAsk.Photo(Base64.getEncoder().encodeToString(jpeg)), accountId = account?.id), jpeg)
    }

    private fun read(ui: AddUi, from: Filled, ask: ReadAsk, photo: ByteArray?) {
        reading = from
        viewModelScope.launch {
            try {
                fill(kosha.api.post<ReadAsk, ReadReply>("/api/v1/ai/read-transaction", ask).entry, from)
                if (photo != null) {
                    camera = false
                    receipt = BitmapFactory.decodeByteArray(photo, 0, photo.size)?.asImageBitmap()
                    if (meta.attachments && photo.size <= ATTACHMENT_LIMIT) files.add(Staged("receipt.jpg", "image/jpeg", photo))
                }
            } catch (e: IOException) {
                ui.error(ui.s(R.string.auth_error_offline))
            } catch (e: Exception) {
                val fallback = if (from == Filled.Receipt) R.string.add_error_receipt else R.string.add_error_read
                ui.error((e as? ApiException)?.message?.ifBlank { null } ?: ui.s(fallback))
            } finally {
                reading = null
            }
        }
    }

    /** What AI read goes into the form; fields it couldn't read keep what they had (phone-add.tsx fill). */
    private fun fill(e: ReadEntry, from: Filled) {
        active.firstOrNull { it.id == e.accountId }?.let {
            if (it.id != account?.id) accountGuessed = true
            accountId = it.id
        }
        val category = meta.categories.firstOrNull { it.id == e.categoryId }
        kind = when {
            (e.amount ?: 0) > 0 -> EntryKind.Income
            (e.amount ?: 0) < 0 -> EntryKind.Expense
            category != null -> if (category.isIncome) EntryKind.Income else EntryKind.Expense
            transfer -> EntryKind.Expense
            else -> kind
        }
        e.amount?.takeIf { it != 0L }?.let { input = keypadInput(abs(it), decimals) }
        if (e.payee.isNotBlank()) {
            payee = e.payee
            picked = null
            payeeByAi = true
        }
        categoryId = null
        subcategoryId = null
        aiCategoryId = category?.let { it.parentId ?: it.id }
        e.day?.let { runCatching { LocalDate.parse(it) }.getOrNull() }?.let { day = it }
        e.time?.let { runCatching { LocalTime.parse(it) }.getOrNull() }?.let { time = it }
        if (e.note.isNotBlank()) note = e.note
        if (e.tags.isNotEmpty()) tags = (tags + e.tags).distinct()
        filled = from
    }

    // ---------- duplicate ----------

    /** Starts from an existing entry (GET /api/app/transactions/{id}) with today's date. */
    private suspend fun duplicate(id: String) {
        val found = runCatching { kosha.api.get<Transaction>("/api/app/transactions/$id") }.getOrNull()
        val tx = found ?: return
        val link = tx.transfer
        kind = when {
            link != null -> EntryKind.Transfer
            tx.amount > 0 -> EntryKind.Income
            else -> EntryKind.Expense
        }
        active.firstOrNull { it.id == tx.accountId }?.let { accountId = it.id; accountPicked = true }
        if (link != null) {
            // A transfer is listed as its outgoing half; what was sent includes the fee.
            toAccountId = link.counterAccountId
            input = keypadInput(abs(tx.amount) + link.fee, Money.decimals(tx.currency))
            val toCurrency = active.firstOrNull { it.id == link.counterAccountId }?.currency ?: tx.currency
            fee = FeeInput(
                fee = keypadInput(link.fee, Money.decimals(tx.currency)),
                received = link.counterAmount?.let { keypadInput(abs(it), Money.decimals(toCurrency)) } ?: "",
                typed = if (link.counterAmount != null && toCurrency != tx.currency) FeeInput.Typed.Received else null,
            )
        } else {
            input = keypadInput(abs(tx.amount), Money.decimals(tx.currency))
            payee = tx.payee
            if (!Categories.isSystem(tx.categoryId)) {
                categoryId = tx.categoryId
                subcategoryId = tx.subcategoryId
            }
            tags = tx.tags
        }
        note = tx.note
        day = today
    }

    companion object {
        /** Auto-categorise answers per kind and payee, for as long as the app runs (ai.tsx guesses). */
        private val aiGuesses = mutableStateMapOf<String, String?>()

        fun kindOf(name: String) = when (name) {
            "income" -> EntryKind.Income
            "transfer" -> EntryKind.Transfer
            else -> EntryKind.Expense
        }
    }
}
