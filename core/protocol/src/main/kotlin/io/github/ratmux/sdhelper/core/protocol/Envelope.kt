package io.github.ratmux.sdhelper.core.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/** Wire message envelope, see docs/PROTOCOL.md section 1. */
@Serializable
data class Envelope(
    @SerialName("v") val version: Int,
    @SerialName("ch") val channel: String,
    val type: String,
    val id: String? = null,
    val payload: JsonObject = JsonObject(emptyMap()),
)

/** Protocol channels, see docs/PROTOCOL.md section 2. */
object Channels {
    const val CONTROL = "control"
    const val DECK = "deck"
    const val PLUGIN = "plugin"

    /** Reserved for the keyboard surface (v2). */
    const val INPUT_KEYBOARD = "input.keyboard"

    /** Reserved for the touchpad surface (v3). */
    const val INPUT_POINTER = "input.pointer"
}

object Protocol {
    const val VERSION = 1

    /** Tolerant reader: additive protocol changes must not break older peers. */
    val json: Json =
        Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }

    fun encode(envelope: Envelope): String = json.encodeToString(Envelope.serializer(), envelope)

    fun decode(text: String): Envelope = json.decodeFromString(Envelope.serializer(), text)
}
