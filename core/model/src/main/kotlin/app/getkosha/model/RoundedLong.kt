package app.getkosha.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.roundToLong

/**
 * Reads a JSON number as whole minor units, rounding a fraction. The web's own reads (dashboard, accounts
 * overview, subscriptions) send amounts converted to the display currency unrounded, as the web rounds when it
 * formats. Every API model file applies it with `@file:UseSerializers(RoundedLong::class)`.
 */
object RoundedLong : KSerializer<Long> {
    override val descriptor = PrimitiveSerialDescriptor("app.getkosha.RoundedLong", PrimitiveKind.LONG)

    override fun deserialize(decoder: Decoder): Long {
        if (decoder !is JsonDecoder) return decoder.decodeLong()
        val p = decoder.decodeJsonElement().jsonPrimitive
        return p.content.toLongOrNull() ?: p.content.toDouble().roundToLong()
    }

    override fun serialize(encoder: Encoder, value: Long) = encoder.encodeLong(value)
}
