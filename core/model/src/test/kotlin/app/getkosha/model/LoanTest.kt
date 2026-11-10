package app.getkosha.model

import org.junit.Assert.*
import org.junit.Test

class LoanTest {
    private val tx = Transaction("t", "2026-11-01", payee = "Sam", amount = -10000, currency = "eur", accountId = "bank", categoryId = "food", refunds = emptyList())

    @Test fun principalIsNeutralAndCannotBeRefundedOrLinkedTwice() {
        val linked = tx.copy(categoryId = Categories.LOAN_PRINCIPAL, loan = LoanLink("loan", "funding", 10000, "Sam", "lent"))
        assertTrue(Categories.isNeutral(linked.categoryId))
        assertTrue(Categories.isSystem(linked.categoryId))
        assertFalse(linked.canRefund)
        assertFalse(linked.canLinkLoan(false))
        assertTrue(tx.canLinkLoan(false))
        assertFalse(tx.canLinkLoan(true))
        for (invalid in listOf(tx.copy(status = "upcoming"), tx.copy(transfer = Transaction.TransferLink("transfer")), tx.copy(recurringId = "schedule"), tx.copy(refundOf = "purchase"), tx.copy(refunds = listOf(Transaction.Refund("r", "bank", 10, "eur", tx.day))), tx.copy(categoryId = Categories.CORRECTION))) {
            assertFalse(invalid.canLinkLoan(false))
        }
    }

    @Test fun metadataEditsOmitUnspecifiedTermsAndCanClearDueDate() {
        val loan = Loan("l", "lent", "Sam", "Deposit", "Retain", "eur", 10000, 0, 10000, "2026-10-01", "2026-12-01", true)
        val patch = loanMetadataChanges(loan, "Alex", loan.purpose, loan.note, loan.dueDay)
        assertEquals(setOf("person"), patch.keys)
        val cleared = loanMetadataChanges(loan, loan.person, loan.purpose, loan.note, null)
        assertEquals(setOf("dueDay"), cleared.keys)
        assertEquals(kotlinx.serialization.json.JsonNull, cleared["dueDay"])
        assertTrue(loanMetadataChanges(loan, " Sam ", loan.purpose, loan.note, loan.dueDay).isEmpty())
    }

    @Test fun repaymentBoundsUseIntegerPrincipal() {
        assertFalse(validRepayment(null, 60000))
        assertFalse(validRepayment(0, 60000))
        assertFalse(validRepayment(-1, 60000))
        assertFalse(validRepayment(60001, 60000))
        assertTrue(validRepayment(1, 60000))
        assertTrue(validRepayment(60000, 60000))
        assertFalse(validRepayment(1, 0))
    }

    @Test fun pinnedDetailKeepsPrincipalAndCashCurrenciesSeparate() {
        val detail = KoshaJson.decodeFromString<LoanDetail>("""{"loan":{"id":"l","direction":"lent","person":"Sam","purpose":"Deposit","note":"","currency":"eur","principal":100000,"repaid":40000,"remaining":60000,"day":"2026-10-01","dueDay":null,"opening":true},"payments":[{"transactionId":"p","kind":"repayment","principal":40000,"amount":44000,"currency":"usd","accountId":"bank","accountName":"Demo bank","day":"2026-11-01","note":"","reconciled":true}]}""")
        assertEquals(60000L, detail.loan.remaining)
        assertEquals("eur", detail.loan.currency)
        assertEquals(44000L, detail.payments.single().amount)
        assertEquals(40000L, detail.payments.single().principal)
        assertTrue(detail.payments.single().reconciled)
        assertEquals(detail, KoshaJson.decodeFromString<LoanDetail>(KoshaJson.encodeToString(LoanDetail.serializer(), detail)))
        assertNull(KoshaJson.decodeFromString<Transaction>(KoshaJson.encodeToString(Transaction.serializer(), tx)).loan)
    }
}
