package io.github.ratmux.sdhelper.data.connection

import io.github.ratmux.sdhelper.core.model.ConnectionState
import io.github.ratmux.sdhelper.core.model.DiscoveredComputer
import io.github.ratmux.sdhelper.core.model.PairedComputer
import io.github.ratmux.sdhelper.core.model.PairingOutcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ConnectionSupervisorTest {
    private val agent = AgentFixture()
    private val keys = JvmDeviceKeyStore()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val manager =
        DefaultConnectionManager(keys, {
            "Test Phone"
        }, scope, Backoff(10, 50), pingIntervalMillis = 50, silenceTimeoutMillis = 1_000)
    private val store = FakeStore()
    private val network = FakeLocalNetwork(initial = false)
    private val discovery = FakeDiscovery()
    private val supervisor = ConnectionSupervisor(scope, store, manager, discovery, network)

    @AfterTest
    fun tearDown() {
        manager.disconnect()
        scope.cancel()
        agent.stop()
    }

    private fun run(block: suspend () -> Unit) = runBlocking { withTimeout(30_000) { block() } }

    private suspend fun awaitState(predicate: (ConnectionState) -> Boolean) =
        withTimeout(15_000) {
            manager.state.first(predicate)
        }

    private suspend fun pair(): PairedComputer {
        val outcome =
            DefaultPairingClient(keys) {
                "Test Phone"
            }.pairWithQr("127.0.0.1", agent.port, agent.tls.fingerprint, agent.newOffer().token)
        return assertIs<PairingOutcome.Success>(outcome).computer.also { store.save(it) }
    }

    @Test
    fun `connects only in the foreground and with the local network permission`() =
        run {
            pair()
            delay(300)
            assertEquals(ConnectionState.Disconnected, manager.state.value) // background, no permission

            supervisor.setForeground(true)
            delay(300)
            assertEquals(ConnectionState.Disconnected, manager.state.value) // foreground, still no permission

            network.set(true)
            awaitState { it is ConnectionState.Connected }

            supervisor.setForeground(false)
            awaitState { it == ConnectionState.Disconnected }
        }

    @Test
    fun `revoked permission disconnects`() =
        run {
            pair()
            network.set(true)
            supervisor.setForeground(true)
            awaitState { it is ConnectionState.Connected }

            network.set(false)

            awaitState { it == ConnectionState.Disconnected }
        }

    @Test
    fun `a paired computer that appears at a new address is followed`() =
        run {
            val computer = pair()
            network.set(true)
            supervisor.setForeground(true)

            discovery.announcements.emit(
                listOf(
                    DiscoveredComputer(
                        computer.agentId,
                        "Desk PC",
                        "127.0.0.1",
                        agent.port + 1,
                    ),
                ),
            )

            withTimeout(10_000) {
                while (store.current.single().port != agent.port + 1) delay(20)
            }
            assertEquals(agent.port + 1, store.current.single().port)
        }
}
