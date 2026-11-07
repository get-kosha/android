package app.getkosha.data

import android.content.Context
import android.util.AtomicFile
import app.getkosha.model.KoshaJson
import app.getkosha.model.NewTransaction
import app.getkosha.model.Transactions
import app.getkosha.network.ApiException
import app.getkosha.network.KoshaApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.io.File
import java.io.IOException

/** A new entry waiting for a connection. Lists show it with a "waiting" mark. */
@Serializable
data class Queued(val entry: NewTransaction, val currency: String, val accountName: String, val categoryName: String)

/**
 * New transactions added without a connection, as on the web (src/lib/offline.ts). Only new entries queue;
 * edits, transfers and deletes need a connection. Each carries its own id, so one the server saved before the
 * reply was lost isn't saved twice. Entries go in the order they were added; one the server rejects (say, its
 * account was deleted meanwhile) is dropped and reported in [rejected].
 */
class Outbox(context: Context, private val api: KoshaApi) {
    private val file = AtomicFile(File(context.filesDir, "outbox.json"))
    private val lock = Mutex()
    private val _items = MutableStateFlow(load())
    val items: StateFlow<List<Queued>> = _items.asStateFlow()

    private val _rejected = MutableSharedFlow<String>(extraBufferCapacity = 8)
    /** The server's message for each entry it refused. */
    val rejected: SharedFlow<String> = _rejected

    private fun load(): List<Queued> = runCatching {
        KoshaJson.decodeFromString(ListSerializer(Queued.serializer()), String(file.readFully()))
    }.getOrDefault(emptyList())

    private fun save(list: List<Queued>) {
        val out = file.startWrite()
        runCatching {
            out.write(KoshaJson.encodeToString(ListSerializer(Queued.serializer()), list).toByteArray())
            file.finishWrite(out)
        }.onFailure { file.failWrite(out) }
        _items.value = list
    }

    suspend fun add(item: Queued) = lock.withLock { save(_items.value + item) }

    /** Undo for an entry that hasn't been sent yet. */
    suspend fun remove(id: String) = lock.withLock { save(_items.value.filterNot { it.entry.id == id }) }

    /** Sends what's waiting. Returns how many were sent; stops at the first sign of no connection. */
    suspend fun send(): Int = lock.withLock {
        var sent = 0
        for (item in _items.value) {
            try {
                api.post<Map<String, List<NewTransaction>>, Transactions>("/api/v1/transactions", mapOf("transactions" to listOf(item.entry)))
                sent++
            } catch (e: IOException) {
                return@withLock sent
            } catch (e: ApiException) {
                // The server refused it for good (400, 404, 409). A 403 means read-only: keep nothing either.
                _rejected.tryEmit(e.message ?: "")
            }
            save(_items.value.filterNot { it.entry.id == item.entry.id })
        }
        sent
    }

    fun clear() {
        file.delete()
        _items.value = emptyList()
    }
}
