package io.github.ratmux.sdhelper.feature.deck

import io.github.ratmux.sdhelper.core.model.ActionCatalogCache
import io.github.ratmux.sdhelper.core.model.AppSettings
import io.github.ratmux.sdhelper.core.model.BuiltinActions
import io.github.ratmux.sdhelper.core.model.ConnectionState
import io.github.ratmux.sdhelper.core.model.ConnectionStatus
import io.github.ratmux.sdhelper.core.model.DeckAction
import io.github.ratmux.sdhelper.core.model.DeckActionState
import io.github.ratmux.sdhelper.core.model.DeckActionTone
import io.github.ratmux.sdhelper.core.model.DeckActions
import io.github.ratmux.sdhelper.core.model.DeckProfile
import io.github.ratmux.sdhelper.core.model.DeckSlot
import io.github.ratmux.sdhelper.core.model.InvokeOutcome
import io.github.ratmux.sdhelper.core.model.LayoutOps
import io.github.ratmux.sdhelper.core.model.LayoutPresets
import io.github.ratmux.sdhelper.core.model.ProfileStore
import io.github.ratmux.sdhelper.core.model.SettingsStore
import io.github.ratmux.sdhelper.core.model.SlotPosition
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
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DeckViewModelTest {
    private class FakeProfiles(
        initial: List<DeckProfile>,
        active: String?,
    ) : ProfileStore {
        val list = MutableStateFlow(initial)
        val active = MutableStateFlow(active)
        override val profiles: Flow<List<DeckProfile>> get() = list
        override val activeProfileId: Flow<String?> get() = active

        override suspend fun save(profile: DeckProfile) {
            list.value = list.value.filterNot { it.id == profile.id } + profile
        }

        override suspend fun delete(profileId: String) = false

        override suspend fun setActive(profileId: String) {
            active.value = profileId
        }
    }

    private class FakeCache(
        initial: List<DeckAction>,
    ) : ActionCatalogCache {
        override val actions = MutableStateFlow(initial)

        override suspend fun update(actions: List<DeckAction>) = Unit
    }

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
        initial: ConnectionState,
    ) : ConnectionStatus {
        override val state = MutableStateFlow(initial)
    }

    private class FakeSettings : SettingsStore {
        val current = MutableStateFlow(AppSettings())
        override val settings: Flow<AppSettings> get() = current

        override suspend fun update(transform: (AppSettings) -> AppSettings) {
            current.value = transform(current.value)
        }
    }

    private val everyday = LayoutPresets.EVERYDAY.create("en")
    private val gaming = LayoutPresets.GAMING.create("en")
    private val mic = DeckAction(BuiltinActions.MUTE_MICROPHONE, "Mute microphone", "mic_off", toggle = true)
    private val deck = FakeDeck()
    private val status = FakeStatus(ConnectionState.Connected("PC"))

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(
        profiles: FakeProfiles = FakeProfiles(listOf(everyday, gaming), everyday.id),
        cached: List<DeckAction> = listOf(mic),
        settings: FakeSettings = FakeSettings(),
    ) = DeckViewModel(profiles, FakeCache(cached), deck, status, settings)

    private suspend fun DeckViewModel.collected(block: suspend (StateFlow<DeckUiState>) -> Unit) =
        coroutineScope {
            val job = launch { state.collect {} }
            block(state)
            job.cancel()
        }

    @Test
    fun `the active profile and the buttons it uses are shown`() =
        runTest {
            viewModel().collected { state ->
                val ui = state.first { it.profile != null }
                assertEquals(everyday.id, ui.profile?.id)
                assertEquals(2, ui.profiles.size)
                assertEquals(everyday.buttonCount, ui.buttons.size)
            }
        }

    @Test
    fun `without a connection the buttons keep their cached titles but are not available`() =
        runTest {
            status.state.value = ConnectionState.Disconnected
            viewModel().collected { state ->
                val button = state.first { it.profile != null }.buttons.getValue(BuiltinActions.MUTE_MICROPHONE)
                assertEquals("Mute microphone", button.title)
                assertFalse(button.available)
            }
        }

    @Test
    fun `when connected the live catalog and state decide`() =
        runTest {
            deck.actions.value = listOf(mic)
            deck.states.value = mapOf(mic.id to DeckActionState(toggled = true, tone = DeckActionTone.ALERT))
            viewModel().collected { state ->
                val button = state.first { it.buttons[mic.id]?.available == true }.buttons.getValue(mic.id)
                assertEquals(true, button.state.toggled)
                assertEquals(DeckActionTone.ALERT, button.state.tone)
                // an action the computer does not offer stays unavailable although the layout uses it
                assertFalse(
                    state.value.buttons
                        .getValue(BuiltinActions.PLAY_PAUSE)
                        .available,
                )
            }
        }

    @Test
    fun `a button for an action the phone has never seen still reads sensibly`() =
        runTest {
            viewModel(cached = emptyList()).collected { state ->
                val button = state.first { it.profile != null }.buttons.getValue(BuiltinActions.PLAY_PAUSE)
                assertEquals("Play pause", button.title)
                assertFalse(button.known)
            }
        }

    @Test
    fun `an unknown active id falls back to the first profile, and selecting switches`() =
        runTest {
            val profiles = FakeProfiles(listOf(everyday, gaming), active = "gone")
            val vm = viewModel(profiles)
            vm.collected { state ->
                assertEquals(everyday.id, state.first { it.profile != null }.profile?.id)

                vm.selectProfile(gaming.id)
                assertEquals(gaming.id, state.first { it.profile?.id == gaming.id }.profile?.id)
            }
            assertEquals(gaming.id, profiles.active.value)
        }

    @Test
    fun `pressing a button runs its action and reports problems briefly`() =
        runTest {
            val vm = viewModel()
            vm.collected { state ->
                vm.press(BuiltinActions.MUTE_MICROPHONE)
                assertEquals(listOf(BuiltinActions.MUTE_MICROPHONE), deck.invoked)
                assertNull(state.value.message)

                deck.outcome = InvokeOutcome.Failure("No microphone")
                vm.press(BuiltinActions.MUTE_MICROPHONE)
                assertEquals(DeckMessage.Refused("No microphone"), state.first { it.message != null }.message)

                vm.dismissMessage()
                assertNull(state.first { it.message == null }.message)

                deck.outcome = InvokeOutcome.NotConnected
                vm.press(BuiltinActions.MUTE_MICROPHONE)
                assertEquals(DeckMessage.NotConnected, state.first { it.message != null }.message)
            }
        }

    @Test
    fun `settings reach the screen`() =
        runTest {
            val settings = FakeSettings()
            settings.current.value = AppSettings(hapticFeedback = false, keepScreenOn = false)
            viewModel(settings = settings).collected { state ->
                val ui = state.first { it.profile != null }
                assertFalse(ui.settings.hapticFeedback)
                assertFalse(ui.settings.keepScreenOn)
                assertTrue(ui.connection is ConnectionState.Connected)
            }
        }

    @Test
    fun `buttons inside folders are resolved like the others`() =
        runTest {
            val folderAt = SlotPosition(0, 7)
            val withFolder =
                LayoutOps.addToFolder(
                    LayoutOps.createFolder(everyday, folderAt, "Mics"),
                    folderAt,
                    BuiltinActions.MUTE_MICROPHONE,
                )
            val only =
                everyday.copy(
                    pages =
                        withFolder.pages.map { page ->
                            page.copy(slots = page.slots.map { if (it is DeckSlot.Folder) it else null })
                        },
                )
            viewModel(FakeProfiles(listOf(only), only.id)).collected { state ->
                val ui = state.first { it.profile != null }
                assertEquals(setOf(BuiltinActions.MUTE_MICROPHONE), ui.buttons.keys)
                assertEquals("Mute microphone", ui.buttons.getValue(BuiltinActions.MUTE_MICROPHONE).title)
            }
        }
}
