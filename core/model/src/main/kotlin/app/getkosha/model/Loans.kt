package app.getkosha.model

import kotlinx.serialization.Serializable

@Serializable
data class Loan(
    val id: String, val direction: String, val person: String, val purpose: String,
    val note: String, val currency: String, val principal: Long, val repaid: Long,
    val remaining: Long, val day: String, val dueDay: String? = null, val opening: Boolean,
)

@Serializable
data class LoanPayment(
    val transactionId: String, val kind: String, val principal: Long, val amount: Long,
    val currency: String, val accountId: String, val accountName: String,
    val day: String, val note: String, val reconciled: Boolean,
)

@Serializable
data class Loans(val loans: List<Loan>)

@Serializable
data class LoanDetail(val loan: Loan, val payments: List<LoanPayment>)

@Serializable
data class LoanLink(val id: String, val kind: String, val principal: Long, val person: String, val direction: String)

/** Matches the service's movement eligibility; the server checks it again atomically. */
fun Transaction.canLinkLoan(positive: Boolean): Boolean =
    amount != 0L && (amount > 0) == positive && status == "paid" && transfer == null &&
        recurringId == null && refundOf == null && refunds.isNullOrEmpty() && loan == null &&
        !Categories.isNeutral(categoryId)

fun validRepayment(amount: Long?, remaining: Long): Boolean = amount != null && amount > 0 && amount <= remaining

/** Only explicitly changed terms are sent; a refresh must not overwrite unrelated edits. */
fun loanMetadataChanges(original: Loan, person: String, purpose: String, note: String, dueDay: String?): kotlinx.serialization.json.JsonObject =
    kotlinx.serialization.json.buildJsonObject {
        if (person.trim() != original.person) put("person", kotlinx.serialization.json.JsonPrimitive(person.trim()))
        if (purpose.trim() != original.purpose) put("purpose", kotlinx.serialization.json.JsonPrimitive(purpose.trim()))
        if (note.trim() != original.note) put("note", kotlinx.serialization.json.JsonPrimitive(note.trim()))
        if (dueDay != original.dueDay) put("dueDay", dueDay?.let { kotlinx.serialization.json.JsonPrimitive(it) } ?: kotlinx.serialization.json.JsonNull)
    }
