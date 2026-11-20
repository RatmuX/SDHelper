package io.github.ratmux.sdhelper.feature.connection

import io.github.ratmux.sdhelper.core.model.ComputerDiscovery
import io.github.ratmux.sdhelper.core.model.ConnectionState
import io.github.ratmux.sdhelper.core.model.ConnectionStatus
import io.github.ratmux.sdhelper.core.model.DiscoveredComputer
import io.github.ratmux.sdhelper.core.model.LocalNetworkAccess
import io.github.ratmux.sdhelper.core.model.PairedComputer
import io.github.ratmux.sdhelper.core.model.PairedComputerStore
import io.github.ratmux.sdhelper.core.model.PairingClient
import io.github.ratmux.sdhelper.core.model.PairingFailure
import io.github.ratmux.sdhelper.core.model.PairingOutcome
import io.github.ratmux.sdhelper.core.protocol.PairingUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ViewModelsTest {
    private val computer = PairedComputer("agent-1", "Desk PC", "192.168.1.5", 47821, "ab".repeat(32), "dev-1", "key-1")

    private class FakeStore : PairedComputerStore {
        val list = MutableStateFlow<List<PairedComputer>>(emptyList())
        val active = MutableStateFlow<String?>(null)
        override val computers: Flow<List<PairedComputer>> get() = list
        override val activeAgentId: Flow<String?> get() = active

        override suspend fun save(computer: PairedComputer) {
            list.value = list.value.filterNot { it.agentId == computer.agentId } + computer
        }

        override suspend fun remove(agentId: String) {
            list.value = list.value.filterNot { it.agentId == agentId }
        }

        override suspend fun setActive(agentId: String?) {
            active.value = agentId
        }
    }

    private class FakePairing(
        var outcome: PairingOutcome,
    ) : PairingClient {
        var qr: List<Any>? = null
        var pin: List<Any>? = null
        val forgotten = mutableListOf<PairedComputer>()

        override suspend fun pairWithQr(
            host: String,
            port: Int,
            fingerprint: String,
            token: String,
        ): PairingOutcome {
            qr = listOf(host, port, fingerprint, token)
            return outcome
        }

        override suspend fun pairWithPin(
            host: String,
            port: Int,
            pin: String,
            onVerificationCode: (String) -> Unit,
        ): PairingOutcome {
            this.pin = listOf(host, port, pin)
            onVerificationCode("AB12-CD34")
            return outcome
        }

        override fun forget(computer: PairedComputer) {
            forgotten += computer
        }
    }

    private class FakeNetwork(
        initial: Boolean = true,
    ) : LocalNetworkAccess {
        private val flow = MutableStateFlow(initial)
        override val granted: StateFlow<Boolean> get() = flow
        var refreshed = 0

        override fun refresh() {
            refreshed++
        }
    }

    private class FakeStatus(
        initial: ConnectionState = ConnectionState.Disconnected,
    ) : ConnectionStatus {
        override val state = MutableStateFlow(initial)
    }

    private val store = FakeStore()
    private val network = FakeNetwork()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun addViewModel(outcome: PairingOutcome = PairingOutcome.Success(computer)) =
        AddComputerViewModel(FakePairing(outcome).also { pairing = it }, store, network)

    private lateinit var pairing: FakePairing

    @Test
    fun `a valid qr code pairs, saves and selects the computer`() =
        runTest {
            val vm = addViewModel()
            val uri = PairingUri("192.168.1.5", 47821, "ab".repeat(32), "tok", "Desk PC").toUriString()

            vm.onQrScanned(uri)

            assertEquals(listOf("192.168.1.5", 47821, "ab".repeat(32), "tok"), pairing.qr)
            assertEquals(listOf(computer), store.list.value)
            assertEquals("agent-1", store.active.value)
            assertEquals(AddStatus.Done, vm.state.first { it.status == AddStatus.Done }.status)
        }

    @Test
    fun `something that is not a pairing code is reported and pairs nothing`() =
        runTest {
            val vm = addViewModel()
            vm.onQrScanned("https://example.com")
            assertEquals(AddStatus.InvalidCode, vm.state.first { it.status == AddStatus.InvalidCode }.status)
            assertEquals(null, pairing.qr)
        }

    @Test
    fun `pin form is validated and only then submitted`() =
        runTest {
            val vm = addViewModel()
            vm.onHostChange(" 192.168.1.5 ")
            vm.onPortChange("47a821")
            vm.onPinChange("12 34ab56")

            val form = vm.state.first { it.pin == "123456" }
            assertEquals("192.168.1.5", form.host)
            assertEquals("47821", form.port)
            assertTrue(form.canSubmitPin)

            vm.submitPin()

            assertEquals(listOf("192.168.1.5", 47821, "123456"), pairing.pin)
            assertEquals(listOf(computer), store.list.value)
        }

    @Test
    fun `incomplete pin or bad port cannot be submitted`() =
        runTest {
            val vm = addViewModel()
            vm.onHostChange("192.168.1.5")
            vm.onPinChange("123")
            vm.submitPin()
            assertEquals(null, pairing.pin)

            vm.onPinChange("123456")
            vm.onPortChange("99999")
            vm.submitPin()
            assertEquals(null, pairing.pin)
            assertFalse(vm.state.first { it.pin == "123456" }.canSubmitPin)
        }

    @Test
    fun `a failure is shown and nothing is saved`() =
        runTest {
            val vm = addViewModel(PairingOutcome.Failure(PairingFailure.DENIED))
            vm.onHostChange("h")
            vm.onPinChange("123456")
            vm.submitPin()

            assertEquals(
                AddStatus.Failed(PairingFailure.DENIED),
                vm.state.first { it.status is AddStatus.Failed }.status,
            )
            assertTrue(store.list.value.isEmpty())
        }

    @Test
    fun `discovered computer prefills the form once`() =
        runTest {
            val vm = addViewModel()
            vm.prefill("10.0.0.2", 5000)
            vm.prefill("10.9.9.9", 1)
            val form = vm.state.first { it.host.isNotEmpty() }
            assertEquals("10.0.0.2", form.host)
            assertEquals("5000", form.port)
        }

    @Test
    fun `permission is required before pairing`() =
        runTest {
            val denied = FakeNetwork(initial = false)
            val vm =
                AddComputerViewModel(FakePairing(PairingOutcome.Success(computer)).also { pairing = it }, store, denied)
            vm.onHostChange("h")
            vm.onPinChange("123456")
            vm.submitPin()
            assertEquals(null, pairing.pin)
            vm.onPermissionResult()
            assertEquals(1, denied.refreshed)
        }

    private fun computersViewModel(
        status: ConnectionStatus = FakeStatus(),
        found: List<DiscoveredComputer> = emptyList(),
        net: LocalNetworkAccess = network,
    ): ComputersViewModel {
        val discovery =
            object : ComputerDiscovery {
                override fun discover(): Flow<List<DiscoveredComputer>> = flowOf(found)
            }
        pairing = FakePairing(PairingOutcome.Success(computer))
        return ComputersViewModel(store, pairing, net, status, discovery)
    }

    @Test
    fun `lists paired computers and hides discovered ones that are already paired`() =
        runTest {
            store.save(computer)
            store.setActive("agent-1")
            val seen =
                listOf(
                    DiscoveredComputer("agent-1", "Desk PC", "1.1.1.1", 1),
                    DiscoveredComputer("agent-2", "Laptop", "2.2.2.2", 2),
                )
            val vm = computersViewModel(found = seen, status = FakeStatus(ConnectionState.Connected("Desk PC")))

            val job = launch { vm.state.collect {} }
            val state = vm.state.first { it.computers.isNotEmpty() && it.discovered.isNotEmpty() }
            job.cancel()

            assertEquals(listOf("agent-2"), state.discovered.map { it.agentId })
            assertEquals(computer, state.active)
            assertEquals(ConnectionState.Connected("Desk PC"), state.connection)
        }

    @Test
    fun `network scanning waits for the local network permission`() =
        runTest {
            val vm =
                computersViewModel(
                    found = listOf(DiscoveredComputer("a", "n", "h", 1)),
                    net = FakeNetwork(initial = false),
                )
            val job = launch { vm.state.collect {} }
            val state = vm.state.first { !it.localNetworkGranted } // waits for the first real emission
            job.cancel()
            assertTrue(state.discovered.isEmpty())
        }

    @Test
    fun `selecting and removing update the store and the key`() =
        runTest {
            store.save(computer)
            val vm = computersViewModel()
            vm.select("agent-1")
            assertEquals("agent-1", store.active.value)

            vm.remove(computer)

            assertTrue(store.list.value.isEmpty())
            assertEquals(listOf(computer), pairing.forgotten)
        }
}
