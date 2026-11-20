package io.github.ratmux.sdhelper.core.protocol

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class MessagesTest {
    @Test
    fun `pair request survives an envelope round trip`() {
        val request = PairRequest(deviceName = "Pixel", publicKey = "MFkw", token = "t")
        val wire = Protocol.encode(controlMessage(ControlTypes.PAIR_REQUEST, request, id = "1"))
        val decoded = Protocol.decode(wire)

        assertEquals(Channels.CONTROL, decoded.channel)
        assertEquals(ControlTypes.PAIR_REQUEST, decoded.type)
        assertEquals(request, decoded.payloadAs<PairRequest>())
    }

    @Test
    fun `payload with missing required fields is rejected`() {
        val envelope = Protocol.decode("""{"v":1,"ch":"control","type":"pair.request","payload":{}}""")
        assertFailsWith<Exception> { envelope.payloadAs<PairRequest>() }
    }

    @Test
    fun `signed bytes are bound to nonce and certificate fingerprint`() {
        val nonce = ByteArray(32) { it.toByte() }
        val a = AuthSigning.signedBytes(nonce, "aa".repeat(32))
        assertContentEquals(a, AuthSigning.signedBytes(nonce, "aa".repeat(32)))
        assertNotEquals(a.toList(), AuthSigning.signedBytes(nonce, "bb".repeat(32)).toList())
        assertNotEquals(a.toList(), AuthSigning.signedBytes(ByteArray(32), "aa".repeat(32)).toList())
    }
}
