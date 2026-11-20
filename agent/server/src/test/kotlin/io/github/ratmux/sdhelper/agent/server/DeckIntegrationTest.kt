package io.github.ratmux.sdhelper.agent.server

import io.github.ratmux.sdhelper.agent.core.DeckChannelHandler
import io.github.ratmux.sdhelper.agent.core.ProviderRegistry
import io.github.ratmux.sdhelper.agent.core.platform.InMemoryAudioController
import io.github.ratmux.sdhelper.agent.core.platform.InMemoryMediaController
import io.github.ratmux.sdhelper.core.protocol.Catalog
import io.github.ratmux.sdhelper.core.protocol.ControlTypes
import io.github.ratmux.sdhelper.core.protocol.DeckTypes
import io.github.ratmux.sdhelper.core.protocol.Envelope
import io.github.ratmux.sdhelper.core.protocol.ErrorCodes
import io.github.ratmux.sdhelper.core.protocol.ErrorPayload
import io.github.ratmux.sdhelper.core.protocol.Invoke
import io.github.ratmux.sdhelper.core.protocol.InvokeResult
import io.github.ratmux.sdhelper.core.protocol.Protocol
import io.github.ratmux.sdhelper.core.protocol.StateUpdate
import io.github.ratmux.sdhelper.core.protocol.deckMessage
import io.github.ratmux.sdhelper.core.protocol.payloadAs
import io.github.ratmux.sdhelper.core.protocol.tls.PinnedTrustManager
import io.github.ratmux.sdhelper.plugins.builtinmedia.MediaProvider
import io.github.ratmux.sdhelper.plugins.builtinsystemaudio.SystemAudioProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** The deck channel on the real stack: TLS, WebSocket, authentication, built-in providers on in-memory controllers. */
class DeckIntegrationTest {
    private val fixture = ServerFixture()
    private val audio = InMemoryAudioController(initialVolume = 50)
    private val media = InMemoryMediaController()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val clients = mutableListOf<WsClient>()

    init {
        fixture.channels.register(
            DeckChannelHandler(ProviderRegistry(listOf(SystemAudioProvider(audio), MediaProvider(media))), scope),
        )
    }

    @AfterTest
    fun tearDown() {
        clients.forEach { it.close() }
        fixture.stop()
        scope.cancel()
    }

    /** A paired, authenticated connection. */
    private fun connectedPhone(name: String = "Phone"): WsClient {
        val phone = PhoneIdentity(name)
        val client = WsClient.connect(fixture.port, PinnedTrustManager(fixture.tls.fingerprint)).also(clients::add)
        client.send(phone.hello())
        client.next() // hello reply
        client.next() // auth challenge
        client.send(phone.pairWithToken(fixture.newOffer().token))
        assertEquals(ControlTypes.PAIR_ACCEPTED, client.next().type)
        return client
    }

    private fun WsClient.get(
        type: String,
        id: String = "r",
    ) = send(Envelope(Protocol.VERSION, "deck", type, id = id))

    /** Reads messages until [done] is satisfied by what has been collected, returns everything read. */
    private fun WsClient.collectUntil(done: (List<Envelope>) -> Boolean): List<Envelope> {
        val seen = mutableListOf<Envelope>()
        while (!done(seen)) seen += next()
        return seen
    }

    private fun List<Envelope>.states(): Map<String, StateUpdate> =
        filter { it.type == DeckTypes.STATE }.map { it.payloadAs<StateUpdate>() }.associateBy { it.actionId }

    @Test
    fun `the catalog lists all built-in actions with their current state`() {
        val phone = connectedPhone()
        phone.get(DeckTypes.CATALOG_GET)

        val messages = phone.collectUntil { it.states().size >= 7 }

        val catalog = messages.first { it.type == DeckTypes.CATALOG }.payloadAs<Catalog>()
        assertEquals(
            setOf(
                "builtin.system-audio/volume-up",
                "builtin.system-audio/volume-down",
                "builtin.system-audio/mute-output",
                "builtin.system-audio/mute-microphone",
                "builtin.media/play-pause",
                "builtin.media/next",
                "builtin.media/previous",
            ),
            catalog.actions.map { it.id }.toSet(),
        )
        assertEquals("Mute microphone", catalog.actions.first { it.id.endsWith("mute-microphone") }.title["en"])
        assertEquals(
            "50%",
            messages
                .states()
                .getValue("builtin.system-audio/volume-up")
                .state.label,
        )
    }

    @Test
    fun `invoking an action changes the system and the phone sees the new state`() {
        val phone = connectedPhone()
        phone.send(deckMessage(DeckTypes.INVOKE, Invoke("builtin.system-audio/mute-microphone"), id = "i1"))

        val messages =
            phone.collectUntil { seen ->
                seen.any { it.type == DeckTypes.RESULT } &&
                    seen.states()["builtin.system-audio/mute-microphone"]?.state?.toggled == true
            }

        val result = messages.first { it.type == DeckTypes.RESULT }
        assertEquals("i1", result.id)
        assertEquals(InvokeResult(true), result.payloadAs())
        assertEquals(
            "ALERT",
            messages
                .states()
                .getValue("builtin.system-audio/mute-microphone")
                .state.tone,
        )
        assertTrue(audio.stateValue().input!!.muted)
    }

    @Test
    fun `a change made by one phone is pushed to every connected phone`() {
        val first = connectedPhone("First")
        val second = connectedPhone("Second")

        first.send(deckMessage(DeckTypes.INVOKE, Invoke("builtin.system-audio/volume-up"), id = "v"))

        // Both volume actions share the level as their label, so both are pushed.
        val pushed =
            second.collectUntil { seen ->
                listOf("builtin.system-audio/volume-up", "builtin.system-audio/volume-down")
                    .all { seen.states()[it]?.state?.label == "55%" }
            }
        assertEquals(2, pushed.states().size)
        assertEquals(55, audio.stateValue().output?.volumePercent)
    }

    @Test
    fun `media actions run and report what is playing`() {
        val phone = connectedPhone()
        phone.send(deckMessage(DeckTypes.INVOKE, Invoke("builtin.media/play-pause"), id = "m"))

        val messages =
            phone.collectUntil {
                it.states()["builtin.media/play-pause"]?.state?.label == "Demo Artist – Demo Track One"
            }

        assertEquals(
            true,
            messages
                .states()
                .getValue("builtin.media/play-pause")
                .state.toggled,
        )
    }

    @Test
    fun `unknown actions and malformed requests are refused over the wire`() {
        val phone = connectedPhone()

        phone.send(deckMessage(DeckTypes.INVOKE, Invoke("builtin.media/explode"), id = "x"))
        assertEquals(
            InvokeResult(false, "unknown action"),
            phone
                .collectUntil { s -> s.any { it.id == "x" } }
                .first {
                    it.id ==
                        "x"
                }.payloadAs(),
        )

        phone.send(Envelope(Protocol.VERSION, "deck", DeckTypes.INVOKE, id = "y"))
        assertEquals(
            InvokeResult(false, "malformed request"),
            phone
                .collectUntil { s -> s.any { it.id == "y" } }
                .first {
                    it.id ==
                        "y"
                }.payloadAs(),
        )
    }

    @Test
    fun `an unauthenticated connection cannot invoke anything`() {
        val phone = PhoneIdentity()
        val client = WsClient.connect(fixture.port, PinnedTrustManager(fixture.tls.fingerprint)).also(clients::add)
        client.send(phone.hello())
        client.next()
        client.next()

        client.send(deckMessage(DeckTypes.INVOKE, Invoke("builtin.system-audio/volume-up")))

        assertEquals(ErrorCodes.UNAUTHENTICATED, client.next().payloadAs<ErrorPayload>().code)
        assertEquals(50, audio.stateValue().output?.volumePercent)
        assertNotNull(client)
    }

    private fun InMemoryAudioController.stateValue() = runBlocking { state.first() }
}
