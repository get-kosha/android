package app.getkosha.statements

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.getkosha.data.Kosha
import app.getkosha.data.Loaded
import app.getkosha.model.Dates
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** A statement file read for a reconciliation. A CSV keeps its text, so a changed column choice re-reads it. */
data class Statement(
    val name: String,
    val source: String,
    val lines: List<Line>,
    val withdrawn: List<String> = emptyList(),
    val file: Picked? = null,
    val rows: List<List<String>> = emptyList(),
    val layout: Layout? = null,
    val signature: String? = null,
    val remembered: Boolean = false,
    val byAi: Boolean = false,
    val confident: Boolean = true,
    /** The opening balance as printed, when the file gives one. */
    val opening: Long? = null,
    /** For a PDF read by AI: how far its lines miss its closing balance. */
    val off: Long? = null,
)

/** What the person chose for a line missing from Kosha. `category` is a category id or "transfer:<account id>". */
data class LinePick(val payee: String? = null, val category: String? = null, val skip: Boolean = false)

/** A reconciliation just finished, for the Done step. */
data class Done(val id: String, val entries: Int, val adjustment: Long, val day: String)

/**
 * One reconciliation, from picking the account to Done (the web's reconcile.tsx Session). It keeps what the
 * person typed and ticked across rotation; the account's entries come from GET /api/app/reconcile through the
 * cache, and read again after every change.
 * ponytail: a half-done reconciliation lives as long as this screen; the web also keeps a draft in the browser.
 */
class ReconcileModel(start: String?) : ViewModel() {
    enum class Step { Start, Tick, Done }

    private val kosha = Kosha.get()
    private var job: Job? = null

    var step by mutableStateOf(Step.Start)
    var accountId by mutableStateOf<String?>(null)
        private set
    var data by mutableStateOf<Loaded<ReconcileData>?>(null)
        private set

    var day by mutableStateOf(Dates.text(Dates.today(kosha.tz)))
    /** The statement's closing balance as printed: for a card, what is owed. */
    var balance by mutableStateOf<Long?>(null)
    var ticks by mutableStateOf(mapOf<String, Boolean>())
    var statement by mutableStateOf<Statement?>(null)
    var reading by mutableStateOf<String?>(null)
    var picks by mutableStateOf(mapOf<String, LinePick>())
    var tidy by mutableStateOf(mapOf<String, Tidy>())
    var tidyAsked: String? = null
    var notSame by mutableStateOf(setOf<String>())
    var openGroups by mutableStateOf(setOf<String>())
    var focus by mutableStateOf(listOf<String>())
    var busy by mutableStateOf<String?>(null)
    var ai by mutableStateOf<Explained?>(null)
    var done by mutableStateOf<Done?>(null)

    /** The ids chosen for lines added to Kosha, by bank ref, so adding them again after a failure saves each once. */
    val added = mutableMapOf<String, String>()

    init {
        val first = start ?: kosha.meta.value?.wallets?.firstOrNull { !it.isArchived && it.type != app.getkosha.model.AccountType.Investment }?.id
        first?.let(::select)
    }

    fun select(id: String) {
        if (id == accountId) return
        accountId = id
        balance = null
        ticks = emptyMap()
        statement = null
        picks = emptyMap()
        tidy = emptyMap()
        notSame = emptySet()
        ai = null
        done = null
        job?.cancel()
        data = null
        job = viewModelScope.launch {
            kosha.version.collectLatest {
                kosha.load<ReconcileData>("reconcile/$id") { get("/api/app/reconcile", mapOf("walletId" to id)) }
                    .collect { data = it }
            }
        }
    }

    /** A new statement replaces what the last one decided. */
    fun loaded(s: Statement?) {
        statement = s
        picks = emptyMap()
        notSame = emptySet()
        ai = null
    }
}
