package io.github.ratmux.sdhelper.core.protocol

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull

class EnvelopeTest {
    @Test
    fun `round trip keeps all fields`() {
        val original =
            Envelope(
                version = Protocol.VERSION,
                channel = Channels.DECK,
                type = "invoke",
                id = "abc",
                payload = buildJsonObject { put("actionId", JsonPrimitive("audio.mute")) },
            )
        assertEquals(original, Protocol.decode(Protocol.encode(original)))
    }

    @Test
    fun `wire format uses short field names and omits null id`() {
        val text = Protocol.encode(Envelope(version = 1, channel = Channels.CONTROL, type = "ping"))
        assertEquals("""{"v":1,"ch":"control","type":"ping"}""", text)
        assertFalse(text.contains("\"id\""))
    }

    @Test
    fun `unknown fields are ignored so additive changes stay compatible`() {
        val decoded = Protocol.decode("""{"v":1,"ch":"deck","type":"state","future":true}""")
        assertEquals("state", decoded.type)
        assertNull(decoded.id)
    }

    @Test
    fun `malformed input is rejected with an exception`() {
        assertFailsWith<Exception> { Protocol.decode("not json") }
    }
}
