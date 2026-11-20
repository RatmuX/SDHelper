package io.github.ratmux.sdhelper.data.connection

import io.github.ratmux.sdhelper.agent.core.platform.AudioDevice
import io.github.ratmux.sdhelper.core.model.ConnectionState
import io.github.ratmux.sdhelper.core.model.DeckActionTone
import io.github.ratmux.sdhelper.core.model.InvokeOutcome
import io.github.ratmux.sdhelper.core.model.PairedComputer
import io.github.ratmux.sdhelper.core.model.PairingOutcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** The phone's view of the deck channel against the real agent: catalog, state, invoke, push. */
class DeckActionsIntegrationTest {
    private val agent = AgentFixture()
    private val keys = JvmDeviceKeyStore()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val manager =
        DefaultConnectionManager(keys, {
            "Test Phone"
        }, scope, Backoff(10, 50), pingIntervalMillis = 50, silenceTimeoutMillis = 1_000)

    @AfterTest
    fun tearDown() {
        manager.disconnect()
        scope.cancel()
        agent.stop()
    }

    private fun run(block: suspend () -> Unit) = runBlocking { withTimeout(30_000) { block() } }

    private fun deck(language: String = "en") =
        DefaultDeckActions(manager, manager, scope, language = {
            language
        }, timeoutMillis = 5_000)

    private suspend fun connect(deck: DefaultDeckActions): PairedComputer {
        val outcome =
            DefaultPairingClient(keys) {
                "Test Phone"
            }.pairWithQr("127.0.0.1", agent.port, agent.tls.fingerprint, agent.newOffer().token)
        val computer = assertIs<PairingOutcome.Success>(outcome).computer
        manager.connect(computer)
        withTimeout(15_000) {
            manager.state.first { it is ConnectionState.Connected }
            deck.actions.first { it.isNotEmpty() }
            deck.states.first { it.size >= 7 }
        }
        return computer
    }

    @Test
    fun `actions and their state arrive after connecting, in the phone's language`() =
        run {
            val deck = deck("tr")
            connect(deck)

            assertEquals(7, deck.actions.value.size)
            val mic = deck.actions.value.first { it.id == "builtin.system-audio/mute-microphone" }
            assertEquals("Mikrofonu sustur", mic.title)
            assertEquals("mic_off", mic.icon)
            assertTrue(mic.toggle)
            assertEquals(
                "50%",
                deck.states.value
                    .getValue("builtin.system-audio/volume-up")
                    .label,
            )
        }

    @Test
    fun `an unknown language falls back to english`() =
        run {
            val deck = deck("de")
            connect(deck)
            assertEquals(
                "Mute microphone",
                deck.actions.value
                    .first { it.id.endsWith("mute-microphone") }
                    .title,
            )
        }

    @Test
    fun `invoking an action changes the computer and the new state comes back`() =
        run {
            val deck = deck()
            connect(deck)

            assertEquals(InvokeOutcome.Success, deck.invoke("builtin.system-audio/mute-microphone"))

            val muted =
                withTimeout(10_000) {
                    deck.states.first {
                        it["builtin.system-audio/mute-microphone"]?.toggled ==
                            true
                    }
                }
            assertEquals(DeckActionTone.ALERT, muted.getValue("builtin.system-audio/mute-microphone").tone)
            assertTrue(
                agent.audio.state
                    .first()
                    .input!!
                    .muted,
            )
        }

    @Test
    fun `changes made on the computer itself are pushed to the phone`() =
        run {
            val deck = deck()
            connect(deck)

            agent.audio.changeVolume(+20)

            withTimeout(10_000) { deck.states.first { it["builtin.system-audio/volume-up"]?.label == "70%" } }
        }

    @Test
    fun `media actions report what is playing`() =
        run {
            val deck = deck()
            connect(deck)
            deck.invoke("builtin.media/play-pause")
            val state = withTimeout(10_000) { deck.states.first { it["builtin.media/play-pause"]?.toggled == true } }
            assertEquals("Demo Artist – Demo Track One", state.getValue("builtin.media/play-pause").label)
        }

    @Test
    fun `a refused action reports the reason, and nothing is invoked while disconnected`() =
        run {
            val deck = deck()
            assertEquals(InvokeOutcome.NotConnected, deck.invoke("builtin.media/next"))

            connect(deck)
            assertEquals(InvokeOutcome.Failure("unknown action"), deck.invoke("builtin.media/explode"))

            agent.audio.setMuted(AudioDevice.INPUT, null) // sanity: the agent side is still healthy
            assertEquals(InvokeOutcome.Success, deck.invoke("builtin.media/next"))
        }

    @Test
    fun `everything is cleared when the connection ends`() =
        run {
            val deck = deck()
            connect(deck)

            manager.disconnect()

            withTimeout(10_000) {
                deck.actions.first { it.isEmpty() }
                deck.states.first { it.isEmpty() }
            }
            assertEquals(InvokeOutcome.NotConnected, deck.invoke("builtin.media/next"))
        }

    @Test
    fun `after reconnecting the catalog and state are fetched again`() =
        run {
            val deck = deck()
            val computer = connect(deck)
            manager.disconnect()
            withTimeout(10_000) { deck.actions.first { it.isEmpty() } }

            manager.connect(computer)

            withTimeout(15_000) {
                deck.actions.first { it.size == 7 }
                deck.states.first { it.size >= 7 }
            }
        }
}
