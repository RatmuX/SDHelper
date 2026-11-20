package io.github.ratmux.sdhelper.data.connection

import io.github.ratmux.sdhelper.core.model.ConnectionState
import io.github.ratmux.sdhelper.core.model.FailureReason
import io.github.ratmux.sdhelper.core.model.PairedComputer
import io.github.ratmux.sdhelper.core.model.PairingFailure
import io.github.ratmux.sdhelper.core.model.PairingOutcome
import io.github.ratmux.sdhelper.core.protocol.Fingerprint
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
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** The phone side against the real agent: real TLS, real WebSocket, real keys; only the Keystore is simulated. */
class ConnectionIntegrationTest {
    private val agent = AgentFixture()
    private val keys = JvmDeviceKeyStore()
    private val pairingClient = DefaultPairingClient(keys) { "Test Phone" }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val manager =
        DefaultConnectionManager(
            keys = keys,
            deviceName = { "Test Phone" },
            scope = scope,
            backoff = Backoff(initialMillis = 10, maxMillis = 50),
            pingIntervalMillis = 50,
            silenceTimeoutMillis = 1_000,
        )

    @AfterTest
    fun tearDown() {
        manager.disconnect()
        scope.cancel()
        agent.stop()
    }

    private fun run(block: suspend () -> Unit) = runBlocking { withTimeout(30_000) { block() } }

    private suspend fun awaitState(predicate: (ConnectionState) -> Boolean): ConnectionState =
        withTimeout(15_000) {
            manager.state.first(predicate)
        }

    private suspend fun pairWithQr(): PairedComputer {
        val outcome = pairingClient.pairWithQr("127.0.0.1", agent.port, agent.tls.fingerprint, agent.newOffer().token)
        return assertIs<PairingOutcome.Success>(outcome).computer
    }

    @Test
    fun `qr pairing stores the key at the agent and returns the computer`() =
        run {
            val computer = pairWithQr()

            assertEquals(agent.tls.fingerprint, computer.fingerprint)
            assertEquals("Desk PC", computer.name)
            assertEquals("agent-it", computer.agentId)
            val stored = assertNotNull(agent.registry.find(computer.deviceId))
            assertEquals("Test Phone", stored.name)
            assertTrue(computer.keyAlias in keys.aliases)
        }

    @Test
    fun `qr pairing with a different fingerprint is refused and leaves no key behind`() =
        run {
            val outcome = pairingClient.pairWithQr("127.0.0.1", agent.port, "00".repeat(32), agent.newOffer().token)

            assertEquals(PairingOutcome.Failure(PairingFailure.CERTIFICATE_MISMATCH), outcome)
            assertTrue(keys.aliases.isEmpty())
            assertTrue(
                agent.registry.devices.value
                    .isEmpty(),
            )
        }

    @Test
    fun `a wrong token is rejected and leaves no key behind`() =
        run {
            agent.newOffer()
            val outcome = pairingClient.pairWithQr("127.0.0.1", agent.port, agent.tls.fingerprint, "wrong")

            assertEquals(PairingOutcome.Failure(PairingFailure.REJECTED), outcome)
            assertTrue(keys.aliases.isEmpty())
        }

    @Test
    fun `pin pairing shows the same code the pc shows and pins the observed certificate`() =
        run {
            var phoneCode: String? = null
            val outcome = pairingClient.pairWithPin("127.0.0.1", agent.port, agent.newOffer().pin) { phoneCode = it }

            val computer = assertIs<PairingOutcome.Success>(outcome).computer
            assertEquals(agent.tls.fingerprint, computer.fingerprint)
            assertEquals(Fingerprint.shortCode(agent.tls.fingerprint), phoneCode)
            assertEquals(phoneCode, agent.approvals.single().shortCode)
        }

    @Test
    fun `a denied pin pairing reports denial`() =
        run {
            agent.approve = false
            val outcome = pairingClient.pairWithPin("127.0.0.1", agent.port, agent.newOffer().pin) {}
            assertEquals(PairingOutcome.Failure(PairingFailure.DENIED), outcome)
            assertTrue(keys.aliases.isEmpty())
        }

    @Test
    fun `pairing with a computer that is not there reports unreachable`() =
        run {
            val closed = AgentFixture().also { it.stop() }
            val outcome = pairingClient.pairWithQr("127.0.0.1", closed.port, closed.tls.fingerprint, "t")
            assertEquals(PairingOutcome.Failure(PairingFailure.UNREACHABLE), outcome)
        }

    @Test
    fun `a paired phone connects, authenticates and stays connected`() =
        run {
            val computer = pairWithQr()
            manager.connect(computer)

            assertEquals(ConnectionState.Connected("Desk PC"), awaitState { it is ConnectionState.Connected })
            delay(400) // several heartbeat rounds
            assertEquals(ConnectionState.Connected("Desk PC"), manager.state.value)
        }

    @Test
    fun `removing the phone on the pc ends the connection for good`() =
        run {
            val computer = pairWithQr()
            manager.connect(computer)
            awaitState { it is ConnectionState.Connected }

            agent.registry.revoke(computer.deviceId)

            assertEquals(ConnectionState.Failed(FailureReason.REVOKED), awaitState { it is ConnectionState.Failed })
        }

    @Test
    fun `a certificate that does not match the pin is never accepted`() =
        run {
            val computer = pairWithQr().copy(fingerprint = "11".repeat(32))
            manager.connect(computer)
            assertEquals(
                ConnectionState.Failed(FailureReason.CERTIFICATE_MISMATCH),
                awaitState { it is ConnectionState.Failed },
            )
        }

    @Test
    fun `an unreachable computer keeps being retried`() =
        run {
            val computer = pairWithQr()
            agent.stop()
            manager.connect(computer)

            val state = awaitState { it is ConnectionState.Connecting && it.attempt >= 3 }
            assertIs<ConnectionState.Connecting>(state)
        }

    @Test
    fun `losing the connection triggers reconnecting`() =
        run {
            val computer = pairWithQr()
            manager.connect(computer)
            awaitState { it is ConnectionState.Connected }

            agent.stop()

            assertIs<ConnectionState.Connecting>(awaitState { it is ConnectionState.Connecting })
        }

    @Test
    fun `disconnect returns to the disconnected state`() =
        run {
            val computer = pairWithQr()
            manager.connect(computer)
            awaitState { it is ConnectionState.Connected }

            manager.disconnect()

            assertEquals(ConnectionState.Disconnected, manager.state.value)
        }
}
