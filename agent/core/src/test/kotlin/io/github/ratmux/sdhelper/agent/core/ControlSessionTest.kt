package io.github.ratmux.sdhelper.agent.core

import io.github.ratmux.sdhelper.core.protocol.AuthChallenge
import io.github.ratmux.sdhelper.core.protocol.Channels
import io.github.ratmux.sdhelper.core.protocol.ControlTypes
import io.github.ratmux.sdhelper.core.protocol.Envelope
import io.github.ratmux.sdhelper.core.protocol.ErrorCodes
import io.github.ratmux.sdhelper.core.protocol.ErrorPayload
import io.github.ratmux.sdhelper.core.protocol.Hello
import io.github.ratmux.sdhelper.core.protocol.HelloReply
import io.github.ratmux.sdhelper.core.protocol.PairAccepted
import io.github.ratmux.sdhelper.core.protocol.PairRejected
import io.github.ratmux.sdhelper.core.protocol.Protocol
import io.github.ratmux.sdhelper.core.protocol.controlMessage
import io.github.ratmux.sdhelper.core.protocol.payloadAs
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ControlSessionTest {
    private val clock = MutableClock()
    private val registry = InMemoryDeviceRegistry()
    private val pairing = PairingService(registry, TEST_FINGERPRINT, { true }, clock)
    private val authenticator = ChallengeAuthenticator(registry, TEST_FINGERPRINT)
    private val channels = ChannelRegistry()
    private val identity = AgentIdentity("agent-1", "Desk PC", "0.1.0")
    private val phone = FakePhone()

    private fun newSession(limiter: RateLimiter = RateLimiter(clock = clock)) =
        ControlSession(identity, pairing, authenticator, channels, limiter)

    private fun hello(versions: List<Int> = listOf(Protocol.VERSION)) =
        controlMessage(ControlTypes.HELLO, Hello(versions, "test", phone.name), id = "h")

    private fun errorCode(reaction: SessionReaction) =
        reaction.replies
            .single()
            .payloadAs<ErrorPayload>()
            .code

    /** Runs hello and returns the nonce the agent issued. */
    private suspend fun greet(session: ControlSession): ByteArray {
        val reaction = session.onMessage(hello())
        assertEquals(listOf(ControlTypes.HELLO, ControlTypes.AUTH_CHALLENGE), reaction.replies.map { it.type })
        assertEquals("agent-1", reaction.replies[0].payloadAs<HelloReply>().agentId)
        assertEquals("h", reaction.replies[0].id)
        return Base64.getUrlDecoder().decode(reaction.replies[1].payloadAs<AuthChallenge>().nonce)
    }

    private fun pairedDevice(): PairedDevice =
        PairedDevice("dev-1", "Phone", phone.publicKeyBase64, 0).also(registry::add)

    @Test
    fun `hello must come first`() =
        runTest {
            val reaction = newSession().onMessage(controlMessage(ControlTypes.PING, JsonObject(emptyMap())))
            assertEquals(ErrorCodes.UNAUTHENTICATED, errorCode(reaction))
            assertTrue(reaction.close)
        }

    @Test
    fun `an unsupported protocol version is refused`() =
        runTest {
            val reaction = newSession().onMessage(hello(versions = listOf(2, 3)))
            assertEquals(ErrorCodes.UNSUPPORTED_VERSION, errorCode(reaction))
            assertTrue(reaction.close)
        }

    @Test
    fun `an envelope with another major version is refused`() =
        runTest {
            val reaction = newSession().onMessage(Envelope(version = 9, channel = Channels.CONTROL, type = "hello"))
            assertEquals(ErrorCodes.UNSUPPORTED_VERSION, errorCode(reaction))
            assertTrue(reaction.close)
        }

    @Test
    fun `a malformed hello closes the connection`() =
        runTest {
            val reaction = newSession().onMessage(Envelope(Protocol.VERSION, Channels.CONTROL, ControlTypes.HELLO))
            assertEquals(ErrorCodes.MALFORMED, errorCode(reaction))
            assertTrue(reaction.close)
        }

    @Test
    fun `an anonymous connection cannot use the deck channel`() =
        runTest {
            val session = newSession()
            greet(session)

            val reaction = session.onMessage(Envelope(Protocol.VERSION, Channels.DECK, "invoke"))

            assertEquals(ErrorCodes.UNAUTHENTICATED, errorCode(reaction))
            assertNull(session.device)
        }

    @Test
    fun `repeated violations close the connection`() =
        runTest {
            val session = newSession()
            greet(session)
            val attempt = Envelope(Protocol.VERSION, Channels.DECK, "invoke")
            val reactions = (1..5).map { session.onMessage(attempt) }
            assertFalse(reactions.first().close)
            assertTrue(reactions.last().close)
        }

    @Test
    fun `pairing with a token authenticates the connection`() =
        runTest {
            val session = newSession()
            greet(session)
            val offer = pairing.newOffer()

            val reaction =
                session.onMessage(controlMessage(ControlTypes.PAIR_REQUEST, phone.pairRequestWithToken(offer.token)))

            val accepted = reaction.replies.single().payloadAs<PairAccepted>()
            assertEquals(ControlTypes.PAIR_ACCEPTED, reaction.replies.single().type)
            assertEquals("Desk PC", accepted.agentName)
            assertEquals(accepted.deviceId, session.device?.deviceId)
            assertNotNull(registry.find(accepted.deviceId))
        }

    @Test
    fun `a rejected pairing closes the connection`() =
        runTest {
            val session = newSession()
            greet(session)
            pairing.newOffer()

            val reaction =
                session.onMessage(
                    controlMessage(ControlTypes.PAIR_REQUEST, phone.pairRequestWithToken("wrong")),
                )

            assertEquals(ControlTypes.PAIR_REJECTED, reaction.replies.single().type)
            assertEquals(
                "invalid_credentials",
                reaction.replies
                    .single()
                    .payloadAs<PairRejected>()
                    .reason,
            )
            assertTrue(reaction.close)
            assertNull(session.device)
        }

    @Test
    fun `a paired phone authenticates with a signed challenge`() =
        runTest {
            val device = pairedDevice()
            val session = newSession()
            val nonce = greet(session)

            val reaction =
                session.onMessage(
                    controlMessage(ControlTypes.AUTH_RESPONSE, phone.authResponse(device.deviceId, nonce)),
                )

            assertEquals(ControlTypes.AUTH_OK, reaction.replies.single().type)
            assertEquals(device, session.device)
        }

    @Test
    fun `a wrong signature is refused and closes the connection`() =
        runTest {
            val device = pairedDevice()
            val session = newSession()
            val nonce = greet(session)
            val forged = FakePhone().authResponse(device.deviceId, nonce)

            val reaction = session.onMessage(controlMessage(ControlTypes.AUTH_RESPONSE, forged))

            assertEquals(ErrorCodes.UNAUTHENTICATED, errorCode(reaction))
            assertTrue(reaction.close)
            assertNull(session.device)
        }

    @Test
    fun `a challenge can be answered only once`() =
        runTest {
            val device = pairedDevice()
            val session = newSession()
            val nonce = greet(session)
            val answer = controlMessage(ControlTypes.AUTH_RESPONSE, phone.authResponse(device.deviceId, nonce))

            assertEquals(
                ControlTypes.AUTH_OK,
                session
                    .onMessage(answer)
                    .replies
                    .single()
                    .type,
            )
            assertEquals(ErrorCodes.UNAUTHENTICATED, errorCode(session.onMessage(answer)))
        }

    @Test
    fun `an authenticated device reaches registered channel handlers`() =
        runTest {
            channels.register(
                object : ChannelHandler {
                    override val channel = Channels.DECK

                    override suspend fun handle(
                        device: PairedDevice,
                        message: Envelope,
                    ) = listOf(message.copy(type = "echo:${device.deviceId}"))
                },
            )
            val device = pairedDevice()
            val session = newSession()
            val nonce = greet(session)
            session.onMessage(controlMessage(ControlTypes.AUTH_RESPONSE, phone.authResponse(device.deviceId, nonce)))

            val reaction = session.onMessage(Envelope(Protocol.VERSION, Channels.DECK, "invoke"))

            assertEquals("echo:dev-1", reaction.replies.single().type)
            assertEquals(
                ErrorCodes.UNSUPPORTED_TYPE,
                errorCode(session.onMessage(Envelope(Protocol.VERSION, "nope", "x"))),
            )
        }

    @Test
    fun `revoking a device ends its open session`() =
        runTest {
            val device = pairedDevice()
            val session = newSession()
            val nonce = greet(session)
            session.onMessage(controlMessage(ControlTypes.AUTH_RESPONSE, phone.authResponse(device.deviceId, nonce)))

            registry.revoke(device.deviceId)
            val reaction = session.onMessage(controlMessage(ControlTypes.PING, JsonObject(emptyMap())))

            assertEquals(ErrorCodes.UNAUTHENTICATED, errorCode(reaction))
            assertTrue(reaction.close)
            assertNull(session.device)
        }

    @Test
    fun `ping is answered with the same id`() =
        runTest {
            val session = newSession()
            greet(session)
            val reaction = session.onMessage(controlMessage(ControlTypes.PING, JsonObject(emptyMap()), id = "p1"))
            assertEquals(ControlTypes.PONG, reaction.replies.single().type)
            assertEquals("p1", reaction.replies.single().id)
        }

    @Test
    fun `flooding is rate limited`() =
        runTest {
            val session = newSession(RateLimiter(capacity = 3, refillPerSecond = 0.0, clock = clock))
            greet(session)
            val ping = controlMessage(ControlTypes.PING, JsonObject(emptyMap()))
            val codes = (1..4).map { session.onMessage(ping) }
            assertEquals(ControlTypes.PONG, codes[0].replies.single().type)
            assertEquals(ErrorCodes.RATE_LIMITED, errorCode(codes[3]))
        }

    @Test
    fun `registering two handlers for a channel is a programming error`() {
        val handler =
            object : ChannelHandler {
                override val channel = Channels.DECK

                override suspend fun handle(
                    device: PairedDevice,
                    message: Envelope,
                ) = emptyList<Envelope>()
            }
        channels.register(handler)
        val error = runCatching { channels.register(handler) }.exceptionOrNull()
        assertTrue(error is IllegalStateException)
    }
}
