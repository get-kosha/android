package app.getkosha.add

import kotlinx.serialization.Serializable

// The shapes Add sends and reads beyond core:model, with only the fields it needs (../kosha/src/lib/api-contract.ts).

/** delete_transactions: deleting either half of a transfer deletes both. */
@Serializable
internal data class DeleteIds(val ids: List<String>)

/** create_transfers: `sent` leaves From and includes the fee; `received` is in To's currency. */
@Serializable
internal data class NewTransfer(
    val fromAccountId: String,
    val toAccountId: String,
    val sent: Long,
    val received: Long,
    val fee: Long,
    val provider: String,
    val day: String,
    val note: String,
)

@Serializable
internal data class NewTransfers(val transfers: List<NewTransfer>)

@Serializable
internal data class SavedTransfer(val id: String, val day: String, val fromAccountId: String)

@Serializable
internal data class SavedTransfers(val transfers: List<SavedTransfer>)

/** POST /api/app/ai/category: auto-categorise for a payee Kosha hasn't seen. */
@Serializable
internal data class CategoryAsk(val payee: String, val kind: String)

@Serializable
internal data class CategoryGuess(val categoryId: String? = null)

/** read_transaction with save false: one sentence or one receipt photo read into an entry. */
@Serializable
internal data class ReadAsk(val text: String? = null, val photo: Photo? = null, val accountId: String? = null, val save: Boolean = false) {
    @Serializable
    data class Photo(val data: String, val mimeType: String = "image/jpeg")
}

@Serializable
internal data class ReadReply(val entry: ReadEntry)

/** What AI read. Amounts are signed minor units in the account's currency; fields it couldn't read are null. */
@Serializable
internal data class ReadEntry(
    val payee: String = "",
    val amount: Long? = null,
    val accountId: String? = null,
    val categoryId: String? = null,
    val day: String? = null,
    val time: String? = null,
    val note: String = "",
    val tags: List<String> = emptyList(),
)

/** create_event. */
@Serializable
internal data class NewEvent(val name: String, val startDay: String?, val endDay: String? = null)

@Serializable
internal data class SavedEvent(val id: String)
