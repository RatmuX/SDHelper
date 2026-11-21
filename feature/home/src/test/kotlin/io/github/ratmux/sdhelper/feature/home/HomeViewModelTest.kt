package io.github.ratmux.sdhelper.feature.home

import io.github.ratmux.sdhelper.core.model.AppMode
import io.github.ratmux.sdhelper.core.model.AppSettings
import io.github.ratmux.sdhelper.core.model.ConnectionState
import io.github.ratmux.sdhelper.core.model.ConnectionStatus
import io.github.ratmux.sdhelper.core.model.PairedComputer
import io.github.ratmux.sdhelper.core.model.PairedComputerStore
import io.github.ratmux.sdhelper.core.model.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private class FakeSettings : SettingsStore {
        val current = MutableStateFlow(AppSettings())
        override val settings: Flow<AppSettings> get() = current

        override suspend fun update(transform: (AppSettings) -> AppSettings) {
            current.value = transform(current.value)
        }
    }

    private class FakeStatus : ConnectionStatus {
        override val state = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    }

    private class FakeComputers(
        list: List<PairedComputer>,
        active: String?,
    ) : PairedComputerStore {
        override val computers = MutableStateFlow(list)
        override val activeAgentId = MutableStateFlow(active)

        override suspend fun save(computer: PairedComputer) = Unit

        override suspend fun remove(agentId: String) = Unit

        override suspend fun setActive(agentId: String?) = Unit
    }

    private val work = PairedComputer("a1", "Work PC", "192.168.1.42", 4242, "fp", "dev", "alias")
    private val home = PairedComputer("a2", "Home PC", "192.168.1.7", 4242, "fp", "dev", "alias2")
    private val settings = FakeSettings()
    private val status = FakeStatus()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(computers: FakeComputers = FakeComputers(listOf(work, home), "a1")) =
        HomeViewModel(settings, status, computers)

    private suspend fun HomeViewModel.collected(block: suspend (StateFlow<HomeUiState>) -> Unit) =
        coroutineScope {
            val job = launch { state.collect {} }
            block(state)
            job.cancel()
        }

    @Test
    fun `without a connection the drawer offers to connect a computer`() =
        runTest {
            viewModel().collected { state ->
                val ui = state.first()
                assertNull(ui.computerName)
                assertNull(ui.computerAddress)
            }
        }

    @Test
    fun `a connected computer shows its name and address`() =
        runTest {
            status.state.value = ConnectionState.Connected("Work PC")
            viewModel().collected { state ->
                val ui = state.first { it.computerName != null }
                assertEquals("Work PC", ui.computerName)
                assertEquals("192.168.1.42", ui.computerAddress)
            }
        }

    @Test
    fun `the address belongs to the computer that is connected, found by name if none is active`() =
        runTest {
            status.state.value = ConnectionState.Connected("Home PC")
            viewModel(FakeComputers(listOf(work, home), active = null)).collected { state ->
                assertEquals("192.168.1.7", state.first { it.computerName != null }.computerAddress)
            }
        }

    @Test
    fun `the last used mode is restored, an unavailable one opens the stream deck`() =
        runTest {
            viewModel().collected { state ->
                assertEquals(AppMode.STREAM_DECK, state.first().mode)
                settings.current.value = AppSettings(lastMode = AppMode.KEYBOARD)
                assertEquals(AppMode.STREAM_DECK, state.value.mode)
            }
        }

    @Test
    fun `selecting a mode remembers it, but modes that are not available are ignored`() =
        runTest {
            val vm = viewModel()
            vm.selectMode(AppMode.TOUCH_PAD)
            vm.selectMode(AppMode.KEYBOARD)
            assertEquals(AppMode.STREAM_DECK, settings.current.value.lastMode)

            vm.selectMode(AppMode.STREAM_DECK)
            assertEquals(AppMode.STREAM_DECK, settings.current.value.lastMode)
        }
}
