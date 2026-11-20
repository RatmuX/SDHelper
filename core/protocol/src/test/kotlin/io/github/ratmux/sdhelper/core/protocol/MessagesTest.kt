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

    @Test
    fun `deck messages round trip with maps and optional fields`() {
        val catalog =
            Catalog(
                listOf(
                    DeckActionInfo(
                        "builtin.media/play-pause",
                        mapOf("en" to "Play", "tr" to "Oynat"),
                        "play_pause",
                        true,
                    ),
                    DeckActionInfo("builtin.media/next", mapOf("en" to "Next")),
                ),
            )
        val decoded = Protocol.decode(Protocol.encode(deckMessage(DeckTypes.CATALOG, catalog, id = "c1")))

        assertEquals(Channels.DECK, decoded.channel)
        assertEquals(catalog, decoded.payloadAs<Catalog>())
    }

    @Test
    fun `state update keeps tone and tolerates missing optional fields`() {
        val update = StateUpdate("a/b", WireActionState(toggled = true, label = "42%", tone = "ALERT"))
        assertEquals(update, deckMessage(DeckTypes.STATE, update).payloadAs<StateUpdate>())

        val minimal = Protocol.decode("""{"v":1,"ch":"deck","type":"state","payload":{"actionId":"a/b","state":{}}}""")
        assertEquals(WireActionState(), minimal.payloadAs<StateUpdate>().state)
    }

    @Test
    fun `invoke defaults to no parameters`() {
        val invoke = Protocol.decode("""{"v":1,"ch":"deck","type":"invoke","payload":{"actionId":"a/b"}}""")
        assertEquals(emptyMap(), invoke.payloadAs<Invoke>().params)
    }
}
