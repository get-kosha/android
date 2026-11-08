package app.getkosha.data

import app.getkosha.model.NewTransaction
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** "No event" survives the queue as an explicit null; otherwise the server picks the event by date. */
class OutboxTest {
    private val entry = NewTransaction(id = "t1", accountId = "a1", payee = "Tesco", amount = -1250, categoryId = "c1")

    private fun tx(noEvent: Boolean) = createTransactions(entry, noEvent)["transactions"]!!.jsonArray[0].jsonObject

    @Test fun noEventIsSentAsNull() = assertEquals(JsonNull, tx(noEvent = true)["eventId"])

    @Test fun otherwiseTheEventIsLeftOut() = assertFalse("eventId" in tx(noEvent = false))
}
