package io.github.ratmux.sdhelper.agent.core

import io.github.ratmux.sdhelper.core.protocol.Channels
import io.github.ratmux.sdhelper.core.protocol.ControlTypes
import io.github.ratmux.sdhelper.core.protocol.Envelope
import io.github.ratmux.sdhelper.core.protocol.Hello
import io.github.ratmux.sdhelper.core.protocol.Protocol
import io.github.ratmux.sdhelper.core.protocol.controlMessage
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SessionLifecycleTest {
    private class RecordingHandler : ChannelHandler {
        override val channel = Channels.DECK
        val started = mutableListOf<SessionHandle>()
        val ended = mutableListOf<SessionHandle>()

        override suspend fun handle(
            device: PairedDevice,
            message: Envelope,
        ) = emptyList<Envelope>()

        override fun onSessionStarted(session: SessionHandle) {
            started += session
        }

        override fun onSessionEnded(session: SessionHandle) {
            ended += session
        }
    }

    private val clock = MutableClock()
    private val registry = InMemoryDeviceRegistry()
    private val pairing = PairingService(registry, TEST_FINGERPRINT, { true }, clock)
    private val authenticator = ChallengeAuthenticator(registry, TEST_FINGERPRINT)
    private val handler = RecordingHandler()
    private val channels = ChannelRegistry().also { it.register(handler) }
    private val sink = RecordingSink()
    private val phone = FakePhone()

    private fun newSession() =
        ControlSession(
            AgentIdentity("a", "PC", "1"),
            pairing,
            authenticator,
            channels,
            RateLimiter(clock = clock),
            sink,
        )

    private suspend fun ControlSession.pair() {
        onMessage(controlMessage(ControlTypes.HELLO, Hello(listOf(Protocol.VERSION), "t", "p")))
        onMessage(controlMessage(ControlTypes.PAIR_REQUEST, phone.pairRequestWithToken(pairing.newOffer().token)))
    }

    @Test
    fun `handlers learn about authenticated sessions and their end exactly once`() =
        runTest {
            val session = newSession()
            assertTrue(handler.started.isEmpty())

            session.pair()
            assertEquals(1, handler.started.size)
            assertSame(sink, handler.started.single().sink)
            assertEquals(session.device, handler.started.single().device)

            session.onClosed()
            session.onClosed()
            assertEquals(handler.started, handler.ended)
        }

    @Test
    fun `an anonymous connection never reaches handlers`() =
        runTest {
            val session = newSession()
            session.onMessage(controlMessage(ControlTypes.HELLO, Hello(listOf(Protocol.VERSION), "t", "p")))
            session.onClosed()
            assertTrue(handler.started.isEmpty())
            assertTrue(handler.ended.isEmpty())
        }

    @Test
    fun `revoking a device ends its session for the handlers`() =
        runTest {
            val session = newSession()
            session.pair()
            registry.revoke(session.device!!.deviceId)

            session.onMessage(controlMessage(ControlTypes.PING, JsonObject(emptyMap())))

            assertEquals(1, handler.ended.size)
        }
}
