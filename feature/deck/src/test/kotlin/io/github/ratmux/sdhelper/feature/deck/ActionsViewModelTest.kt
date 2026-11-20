package io.github.ratmux.sdhelper.feature.deck

import io.github.ratmux.sdhelper.core.model.ConnectionState
import io.github.ratmux.sdhelper.core.model.ConnectionStatus
import io.github.ratmux.sdhelper.core.model.DeckAction
import io.github.ratmux.sdhelper.core.model.DeckActionState
import io.github.ratmux.sdhelper.core.model.DeckActionTone
import io.github.ratmux.sdhelper.core.model.DeckActions
import io.github.ratmux.sdhelper.core.model.InvokeOutcome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ActionsViewModelTest {
    private class FakeDeck : DeckActions {
        override val actions = MutableStateFlow<List<DeckAction>>(emptyList())
        override val states = MutableStateFlow<Map<String, DeckActionState>>(emptyMap())
        val invoked = mutableListOf<String>()
        var outcome: InvokeOutcome = InvokeOutcome.Success

        override suspend fun invoke(actionId: String): InvokeOutcome {
            invoked += actionId
            return outcome
        }
    }

    private class FakeStatus(
        initial: ConnectionState = ConnectionState.Connected("PC"),
    ) : ConnectionStatus {
        override val state: StateFlow<ConnectionState> = MutableStateFlow(initial)
    }

    private val deck = FakeDeck()
    private val mute = DeckAction("p/mute", "Mute", "mic_off", toggle = true)
    private val next = DeckAction("p/next", "Next", null, toggle = false)

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(status: ConnectionStatus = FakeStatus()) = ActionsViewModel(deck, status)

    @Test
    fun `rows pair every action with its state, available until told otherwise`() =
        runTest {
            deck.actions.value = listOf(mute, next)
            deck.states.value = mapOf("p/mute" to DeckActionState(toggled = true, tone = DeckActionTone.ALERT))
            val vm = viewModel()

            val job = launch { vm.state.collect {} }
            val rows = vm.state.first { it.rows.size == 2 }.rows
            job.cancel()

            assertEquals(true, rows[0].state.toggled)
            assertEquals(DeckActionState(), rows[1].state)
        }

    @Test
    fun `tapping an available action runs it and clears the error`() =
        runTest {
            val vm = viewModel()
            vm.invoke(ActionRow(mute, DeckActionState()))
            assertEquals(listOf("p/mute"), deck.invoked)
        }

    @Test
    fun `an unavailable action is not run`() =
        runTest {
            val vm = viewModel()
            vm.invoke(ActionRow(mute, DeckActionState(available = false)))
            assertTrue(deck.invoked.isEmpty())
        }

    @Test
    fun `a refusal is shown and can be dismissed`() =
        runTest {
            deck.outcome = InvokeOutcome.Failure("No microphone")
            val vm = viewModel()
            val job = launch { vm.state.collect {} }

            vm.invoke(ActionRow(mute, DeckActionState()))
            assertEquals(ActionError.Refused("No microphone"), vm.state.first { it.error != null }.error)

            vm.dismissError()
            assertEquals(null, vm.state.first { it.error == null }.error)
            job.cancel()
        }

    @Test
    fun `losing the connection is reported`() =
        runTest {
            deck.outcome = InvokeOutcome.NotConnected
            val vm = viewModel()
            val job = launch { vm.state.collect {} }

            vm.invoke(ActionRow(next, DeckActionState()))

            assertEquals(ActionError.NotConnected, vm.state.first { it.error != null }.error)
            job.cancel()
        }

    @Test
    fun `the connection state is passed through`() =
        runTest {
            val vm = viewModel(FakeStatus(ConnectionState.Connecting(3)))
            val job = launch { vm.state.collect {} }
            assertEquals(
                ConnectionState.Connecting(3),
                vm.state.first { it.connection is ConnectionState.Connecting }.connection,
            )
            job.cancel()
        }
}
