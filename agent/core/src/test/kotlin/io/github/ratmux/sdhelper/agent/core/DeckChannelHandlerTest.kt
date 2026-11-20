package io.github.ratmux.sdhelper.agent.core

import io.github.ratmux.sdhelper.core.pluginapi.ActionResult
import io.github.ratmux.sdhelper.core.pluginapi.ActionState
import io.github.ratmux.sdhelper.core.pluginapi.ActionTone
import io.github.ratmux.sdhelper.core.protocol.Catalog
import io.github.ratmux.sdhelper.core.protocol.Channels
import io.github.ratmux.sdhelper.core.protocol.ControlTypes
import io.github.ratmux.sdhelper.core.protocol.DeckTypes
import io.github.ratmux.sdhelper.core.protocol.Envelope
import io.github.ratmux.sdhelper.core.protocol.Invoke
import io.github.ratmux.sdhelper.core.protocol.InvokeResult
import io.github.ratmux.sdhelper.core.protocol.Protocol
import io.github.ratmux.sdhelper.core.protocol.StateUpdate
import io.github.ratmux.sdhelper.core.protocol.deckMessage
import io.github.ratmux.sdhelper.core.protocol.payloadAs
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DeckChannelHandlerTest {
    private val provider = FakeProvider()
    private val device = PairedDevice("d1", "Phone", "key", 0)

    private fun handlerFor(
        scope: kotlinx.coroutines.CoroutineScope,
        timeout: Long = 10_000,
    ) = DeckChannelHandler(ProviderRegistry(listOf(provider)), scope, timeout)

    private fun get(type: String) = Envelope(Protocol.VERSION, Channels.DECK, type, id = "r1")

    private suspend fun DeckChannelHandler.invoke(
        actionId: String,
        params: Map<String, String> = emptyMap(),
    ): InvokeResult {
        val reply = handle(device, deckMessage(DeckTypes.INVOKE, Invoke(actionId, params), id = "i1")).single()
        assertEquals(DeckTypes.RESULT, reply.type)
        assertEquals("i1", reply.id)
        return reply.payloadAs()
    }

    @Test
    fun `catalog lists qualified actions and the current state`() =
        runTest(UnconfinedTestDispatcher()) {
            provider.state.value = mapOf("go" to ActionState(toggled = true, label = "on", tone = ActionTone.ALERT))
            val handler = handlerFor(backgroundScope)

            val replies = handler.handle(device, get(DeckTypes.CATALOG_GET))

            val catalog = replies.first().payloadAs<Catalog>()
            assertEquals(DeckTypes.CATALOG, replies.first().type)
            assertEquals("r1", replies.first().id)
            assertEquals(setOf("fake/go", "fake/stop"), catalog.actions.map { it.id }.toSet())
            val go = catalog.actions.single { it.id == "fake/go" }
            assertEquals(mapOf("en" to "Go", "tr" to "Git"), go.title)
            assertEquals("icon_go", go.icon)
            assertTrue(go.toggle)

            val state = replies.drop(1).single().payloadAs<StateUpdate>()
            assertEquals("fake/go", state.actionId)
            assertEquals(true, state.state.toggled)
            assertEquals("ALERT", state.state.tone)
        }

    @Test
    fun `invoke runs the provider with the local id and parameters`() =
        runTest(UnconfinedTestDispatcher()) {
            val handler = handlerFor(backgroundScope)

            assertEquals(InvokeResult(true), handler.invoke("fake/go", mapOf("a" to "b")))
            assertEquals(listOf("go" to mapOf("a" to "b")), provider.invoked)
        }

    @Test
    fun `a failing action reports its message`() =
        runTest(UnconfinedTestDispatcher()) {
            provider.behaviour = { _, _ -> ActionResult.Failure("no microphone") }
            assertEquals(InvokeResult(false, "no microphone"), handlerFor(backgroundScope).invoke("fake/go"))
        }

    @Test
    fun `an exception inside an action does not leak details and keeps the handler alive`() =
        runTest(UnconfinedTestDispatcher()) {
            val handler = handlerFor(backgroundScope)
            provider.behaviour = { _, _ -> error("secret path C:\\Users\\x") }

            assertEquals(InvokeResult(false, "action failed"), handler.invoke("fake/go"))

            provider.behaviour = { _, _ -> ActionResult.Success }
            assertEquals(InvokeResult(true), handler.invoke("fake/go"))
        }

    @Test
    fun `an action that hangs times out`() =
        runTest(UnconfinedTestDispatcher()) {
            provider.behaviour = { _, _ ->
                delay(60_000)
                ActionResult.Success
            }
            assertEquals(
                InvokeResult(false, "timed out"),
                handlerFor(backgroundScope, timeout = 1_000).invoke("fake/go"),
            )
        }

    @Test
    fun `unknown actions and bad requests are refused without running anything`() =
        runTest(UnconfinedTestDispatcher()) {
            val handler = handlerFor(backgroundScope)

            assertEquals(InvokeResult(false, "unknown action"), handler.invoke("fake/missing"))
            assertEquals(InvokeResult(false, "unknown action"), handler.invoke("other/go"))
            assertEquals(
                InvokeResult(false, "invalid parameters"),
                handler.invoke(
                    "fake/go",
                    (1..17).associate {
                        "k$it" to
                            "v"
                    },
                ),
            )
            assertEquals(
                InvokeResult(false, "invalid parameters"),
                handler.invoke(
                    "fake/go",
                    mapOf("k" to "x".repeat(257)),
                ),
            )

            val malformed = handler.handle(device, Envelope(Protocol.VERSION, Channels.DECK, DeckTypes.INVOKE)).single()
            assertEquals(InvokeResult(false, "malformed request"), malformed.payloadAs())
            assertTrue(provider.invoked.isEmpty())
        }

    @Test
    fun `unknown deck message types are answered with an error`() =
        runTest(UnconfinedTestDispatcher()) {
            val reply = handlerFor(backgroundScope).handle(device, get("nonsense")).single()
            assertEquals(ControlTypes.ERROR, reply.type)
        }

    @Test
    fun `state changes are pushed to connected sessions only once and not after they end`() =
        runTest(UnconfinedTestDispatcher()) {
            val handler = handlerFor(backgroundScope)
            val sink = RecordingSink()
            val session = SessionHandle(device, sink)
            handler.onSessionStarted(session)

            provider.state.value = mapOf("go" to ActionState(toggled = true))
            assertEquals(listOf("fake/go"), sink.received.map { it.payloadAs<StateUpdate>().actionId })

            provider.state.value = mapOf("go" to ActionState(toggled = true), "stop" to ActionState(available = false))
            // "go" is unchanged and is not repeated; only the new state of "stop" arrives.
            assertEquals(listOf("fake/go", "fake/stop"), sink.received.map { it.payloadAs<StateUpdate>().actionId })

            handler.onSessionEnded(session)
            provider.state.value = mapOf("go" to ActionState(toggled = false))
            assertEquals(2, sink.received.size)
        }

    @Test
    fun `states of actions that are not published are ignored`() =
        runTest(UnconfinedTestDispatcher()) {
            val handler = handlerFor(backgroundScope)
            val sink = RecordingSink()
            handler.onSessionStarted(SessionHandle(device, sink))

            provider.state.value = mapOf("ghost" to ActionState())

            assertFalse(sink.received.any { it.payloadAs<StateUpdate>().actionId.endsWith("ghost") })
        }
}
