package io.github.ratmux.sdhelper.agent.server

import io.github.ratmux.sdhelper.core.protocol.Channels
import io.github.ratmux.sdhelper.core.protocol.ControlTypes
import io.github.ratmux.sdhelper.core.protocol.Envelope
import io.github.ratmux.sdhelper.core.protocol.ErrorCodes
import io.github.ratmux.sdhelper.core.protocol.ErrorPayload
import io.github.ratmux.sdhelper.core.protocol.Fingerprint
import io.github.ratmux.sdhelper.core.protocol.HelloReply
import io.github.ratmux.sdhelper.core.protocol.PairAccepted
import io.github.ratmux.sdhelper.core.protocol.PairRejected
import io.github.ratmux.sdhelper.core.protocol.Protocol
import io.github.ratmux.sdhelper.core.protocol.controlMessage
import io.github.ratmux.sdhelper.core.protocol.payloadAs
import io.github.ratmux.sdhelper.core.protocol.tls.CapturingTrustManager
import io.github.ratmux.sdhelper.core.protocol.tls.PinnedTrustManager
import kotlinx.serialization.json.JsonObject
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Real TLS, real WebSocket, real certificates and keys; only the phone is simulated. */
class AgentServerIntegrationTest {
    private val fixture = ServerFixture()
    private val phone = PhoneIdentity()
    private val clients = mutableListOf<WsClient>()

    @AfterTest
    fun tearDown() {
        clients.forEach { it.close() }
        fixture.stop()
    }

    private fun connectPinned(fingerprint: String = fixture.tls.fingerprint): WsClient =
        WsClient.connect(fixture.port, PinnedTrustManager(fingerprint)).also(clients::add)

    /** hello, then returns the auth challenge the agent sent. */
    private fun greet(client: WsClient): Envelope {
        client.send(phone.hello())
        val reply = client.next()
        assertEquals(ControlTypes.HELLO, reply.type)
        assertEquals("Test PC", reply.payloadAs<HelloReply>().agentName)
        return client.next().also { assertEquals(ControlTypes.AUTH_CHALLENGE, it.type) }
    }

    private fun pairWithToken(): String {
        val client = connectPinned()
        greet(client)
        client.send(phone.pairWithToken(fixture.newOffer().token))
        val accepted = client.next()
        assertEquals(ControlTypes.PAIR_ACCEPTED, accepted.type)
        return accepted.payloadAs<PairAccepted>().deviceId
    }

    @Test
    fun `phone pairs with the qr token and is authenticated afterwards`() {
        val client = connectPinned()
        greet(client)
        client.send(phone.pairWithToken(fixture.newOffer().token))
        val accepted = client.next()

        assertEquals(ControlTypes.PAIR_ACCEPTED, accepted.type)
        val deviceId = accepted.payloadAs<PairAccepted>().deviceId
        assertNotNull(fixture.registry.find(deviceId))

        client.send(controlMessage(ControlTypes.PING, JsonObject(emptyMap()), id = "p"))
        assertEquals(ControlTypes.PONG, client.next().type)
    }

    @Test
    fun `a paired phone reconnects and authenticates with its key`() {
        val deviceId = pairWithToken()

        val client = connectPinned()
        val challenge = greet(client)
        client.send(phone.authResponse(deviceId, challenge, fixture.tls.fingerprint))
        assertEquals(ControlTypes.AUTH_OK, client.next().type)

        client.send(Envelope(Protocol.VERSION, Channels.DECK, "invoke"))
        val reply = client.next()
        // authenticated, but no deck handler is registered in this fixture
        assertEquals(ErrorCodes.UNSUPPORTED_TYPE, reply.payloadAs<ErrorPayload>().code)
    }

    @Test
    fun `a phone that trusts a different fingerprint cannot connect`() {
        assertFailsWith<Exception> { connectPinned(fingerprint = "00".repeat(32)) }
    }

    @Test
    fun `an unauthenticated connection cannot send deck messages`() {
        val client = connectPinned()
        greet(client)
        client.send(Envelope(Protocol.VERSION, Channels.DECK, "invoke"))
        assertEquals(ErrorCodes.UNAUTHENTICATED, client.next().payloadAs<ErrorPayload>().code)
    }

    @Test
    fun `a qr token works only once`() {
        val offer = fixture.newOffer()
        val first = connectPinned()
        greet(first)
        first.send(phone.pairWithToken(offer.token))
        assertEquals(ControlTypes.PAIR_ACCEPTED, first.next().type)

        val second = connectPinned()
        greet(second)
        second.send(PhoneIdentity("Other").pairWithToken(offer.token))
        assertEquals(ControlTypes.PAIR_REJECTED, second.next().type)
        second.awaitClose()
        assertEquals(1, fixture.registry.devices.value.size)
    }

    @Test
    fun `a revoked phone can no longer authenticate`() {
        val deviceId = pairWithToken()
        fixture.registry.revoke(deviceId)

        val client = connectPinned()
        val challenge = greet(client)
        client.send(phone.authResponse(deviceId, challenge, fixture.tls.fingerprint))
        assertEquals(ErrorCodes.UNAUTHENTICATED, client.next().payloadAs<ErrorPayload>().code)
        client.awaitClose()
    }

    @Test
    fun `pin pairing shows the same short code on both sides and needs approval`() {
        val capture = CapturingTrustManager()
        val client = WsClient.connect(fixture.port, capture).also(clients::add)
        greet(client)

        val phoneCode = Fingerprint.shortCode(assertNotNull(capture.observedFingerprint))
        client.send(phone.pairWithPin(fixture.newOffer().pin))
        assertEquals(ControlTypes.PAIR_ACCEPTED, client.next().type)

        assertEquals(phoneCode, fixture.approvals.single().shortCode)
        assertEquals(fixture.tls.fingerprint, capture.observedFingerprint)
    }

    @Test
    fun `a denied pin pairing registers nothing`() {
        fixture.approve = false
        val client = WsClient.connect(fixture.port, CapturingTrustManager()).also(clients::add)
        greet(client)
        client.send(phone.pairWithPin(fixture.newOffer().pin))

        assertEquals("denied", client.next().payloadAs<PairRejected>().reason)
        assertTrue(
            fixture.registry.devices.value
                .isEmpty(),
        )
    }

    @Test
    fun `oversized frames close the connection`() {
        val client = connectPinned()
        greet(client)
        client.sendRaw("x".repeat(70 * 1024))
        client.awaitClose()
    }

    @Test
    fun `repeated garbage closes the connection`() {
        val client = connectPinned()
        greet(client)
        repeat(5) {
            client.sendRaw("not json")
            if (it < 4) assertEquals(ErrorCodes.MALFORMED, client.next().payloadAs<ErrorPayload>().code)
        }
        client.awaitClose()
    }
}
