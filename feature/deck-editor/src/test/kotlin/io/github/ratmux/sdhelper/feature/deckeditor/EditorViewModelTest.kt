package io.github.ratmux.sdhelper.feature.deckeditor

import io.github.ratmux.sdhelper.core.model.ActionCatalogCache
import io.github.ratmux.sdhelper.core.model.BuiltinActions
import io.github.ratmux.sdhelper.core.model.ConnectionState
import io.github.ratmux.sdhelper.core.model.ConnectionStatus
import io.github.ratmux.sdhelper.core.model.DeckAction
import io.github.ratmux.sdhelper.core.model.DeckActionState
import io.github.ratmux.sdhelper.core.model.DeckActions
import io.github.ratmux.sdhelper.core.model.DeckPage
import io.github.ratmux.sdhelper.core.model.DeckProfile
import io.github.ratmux.sdhelper.core.model.DeckSlot
import io.github.ratmux.sdhelper.core.model.GridSize
import io.github.ratmux.sdhelper.core.model.InvokeOutcome
import io.github.ratmux.sdhelper.core.model.LayoutLimits
import io.github.ratmux.sdhelper.core.model.LayoutOps
import io.github.ratmux.sdhelper.core.model.LayoutPresets
import io.github.ratmux.sdhelper.core.model.ProfileStore
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
class EditorViewModelTest {
    private class FakeProfiles(
        initial: DeckProfile,
    ) : ProfileStore {
        val list = MutableStateFlow(listOf(initial))
        var saves = 0
        override val profiles: Flow<List<DeckProfile>> get() = list
        override val activeProfileId: Flow<String?> = MutableStateFlow(initial.id)

        override suspend fun save(profile: DeckProfile) {
            saves++
            list.value = list.value.map { if (it.id == profile.id) profile else it }
        }

        override suspend fun delete(profileId: String) = false

        override suspend fun setActive(profileId: String) = Unit
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

        override suspend fun invoke(actionId: String): InvokeOutcome = InvokeOutcome.Success
    }

    private class FakeStatus : ConnectionStatus {
        override val state = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    }

    private val start = LayoutPresets.EVERYDAY.create("en")
    private val profiles = FakeProfiles(start)
    private val known =
        listOf(
            DeckAction(BuiltinActions.MUTE_MICROPHONE, "Mute microphone", "mic_off", true),
            DeckAction(BuiltinActions.NEXT, "Next track", "skip_next", false),
        )

    // created on first use, i.e. after setUp installed the test dispatcher that viewModelScope must pick up
    private val vm by lazy { EditorViewModel(profiles, FakeCache(known), FakeDeck(), FakeStatus()) }

    private val saved: DeckProfile get() = profiles.list.value.single()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private suspend fun collected(block: suspend (StateFlow<EditorUiState>) -> Unit) =
        coroutineScope {
            val job = launch { vm.state.collect {} }
            block(vm.state)
            job.cancel()
        }

    @Test
    fun `nothing can be changed while edit mode is off`() =
        runTest {
            collected {
                vm.assign(SlotPosition(0, 7), BuiltinActions.NEXT)
                vm.clear(SlotPosition(0, 0))
                vm.move(SlotPosition(0, 0), SlotPosition(0, 5))
                vm.addPage()
                vm.removePage(0)
                vm.renamePage(0, "Hacked")
                vm.reorderPage(0, 1)
                vm.requestGrid(GridSize(6, 3))
                vm.confirmResize()

                assertEquals(start, saved)
                assertEquals(0, profiles.saves, "not even a save happened")
            }
        }

    @Test
    fun `with edit mode on, changes are applied and saved at once`() =
        runTest {
            collected { state ->
                vm.setEditMode(true)
                vm.assign(SlotPosition(0, 7), BuiltinActions.MUTE_MICROPHONE)
                assertEquals(DeckSlot.Action(BuiltinActions.MUTE_MICROPHONE), saved.slotAt(SlotPosition(0, 7)))

                vm.clear(SlotPosition(0, 7))
                assertNull(saved.slotAt(SlotPosition(0, 7)))

                vm.move(SlotPosition(0, 1), SlotPosition(1, 6))
                assertEquals(start.slotAt(SlotPosition(0, 1)), saved.slotAt(SlotPosition(1, 6)))
                assertTrue(state.first { it.editMode }.editMode)
            }
        }

    @Test
    fun `turning edit mode off locks the editor again`() =
        runTest {
            collected {
                vm.setEditMode(true)
                vm.setEditMode(false)
                vm.clear(SlotPosition(0, 1))
                assertEquals(start, saved)
            }
        }

    @Test
    fun `a button can be sent to another page without dragging`() =
        runTest {
            collected {
                vm.setEditMode(true)
                vm.moveToPage(SlotPosition(0, 0), page = 1)

                assertNull(saved.slotAt(SlotPosition(0, 0)))
                val onPageTwo = saved.pages[1].slots.indexOf(start.slotAt(SlotPosition(0, 0)))
                assertTrue(onPageTwo >= 0, "it arrived on page 2")
                assertEquals(start.buttonCount, saved.buttonCount, "nothing lost")
            }
        }

    @Test
    fun `sending a button to a full page is refused with a message`() =
        runTest {
            collected { state ->
                vm.setEditMode(true)
                repeat(8) { vm.assign(SlotPosition(1, it), BuiltinActions.NEXT) }

                vm.moveToPage(SlotPosition(0, 0), page = 1)

                assertEquals(EditorMessage.PAGE_FULL, state.first { it.message != null }.message)
                assertEquals(start.slotAt(SlotPosition(0, 0)), saved.slotAt(SlotPosition(0, 0)))
                vm.dismissMessage()
                assertNull(state.first { it.message == null }.message)
            }
        }

    @Test
    fun `pages can be added up to the limit, renamed, reordered and removed`() =
        runTest {
            collected { state ->
                vm.setEditMode(true)
                vm.addPage()
                assertEquals(3, saved.pages.size)
                assertEquals("Page 3", saved.pages[2].name)

                vm.renamePage(2, "Games")
                vm.reorderPage(2, 0)
                assertEquals("Games", saved.pages[0].name)

                vm.removePage(0)
                assertEquals(2, saved.pages.size)

                repeat(LayoutLimits.MAX_PAGES) { vm.addPage() }
                assertEquals(LayoutLimits.MAX_PAGES, saved.pages.size)
                assertEquals(EditorMessage.PAGE_LIMIT, state.first { it.message != null }.message)
            }
        }

    @Test
    fun `a grid change that keeps every button in place is applied directly`() =
        runTest {
            collected { state ->
                vm.setEditMode(true)
                vm.requestGrid(GridSize(6, 3))

                assertEquals(GridSize(6, 3), saved.grid)
                assertNull(state.value.pendingResize)
                assertEquals(start.buttonCount, saved.buttonCount)
            }
        }

    @Test
    fun `a grid change that moves buttons waits for confirmation and can be cancelled`() =
        runTest {
            collected { state ->
                vm.setEditMode(true)
                vm.assign(SlotPosition(0, 3), BuiltinActions.NEXT) // column 3 disappears in a 3-column grid

                vm.requestGrid(GridSize(3, 2))
                val pending = state.first { it.pendingResize != null }.pendingResize
                assertEquals(GridSize(3, 2), pending?.grid)
                assertTrue(pending!!.movedButtons >= 1)
                assertEquals(GridSize(4, 2), saved.grid, "nothing changed yet")

                vm.cancelResize()
                assertNull(state.first { it.pendingResize == null }.pendingResize)
                assertEquals(GridSize(4, 2), saved.grid)

                vm.requestGrid(GridSize(3, 2))
                vm.confirmResize()
                assertEquals(GridSize(3, 2), saved.grid)
                assertTrue(saved.pages.all { it.slots.size == 6 })
            }
        }

    @Test
    fun `leaving edit mode drops a pending grid change`() =
        runTest {
            collected { state ->
                vm.setEditMode(true)
                vm.assign(SlotPosition(0, 3), BuiltinActions.NEXT)
                vm.requestGrid(GridSize(3, 2))
                state.first { it.pendingResize != null }

                vm.setEditMode(false)

                assertNull(state.first { it.pendingResize == null }.pendingResize)
                vm.confirmResize()
                assertEquals(GridSize(4, 2), saved.grid)
            }
        }

    @Test
    fun `a grid change that cannot fit is refused and changes nothing`() =
        runTest {
            val full =
                DeckProfile(
                    "big",
                    "Big",
                    GridSize(6, 3),
                    List(LayoutLimits.MAX_PAGES) { page ->
                        DeckPage("p$page", "P$page", List(18) { DeckSlot.Action("x/$page-$it") })
                    },
                )
            val bigProfiles = FakeProfiles(full)
            val bigVm = EditorViewModel(bigProfiles, FakeCache(emptyList()), FakeDeck(), FakeStatus())
            coroutineScope {
                val job = launch { bigVm.state.collect {} }
                bigVm.setEditMode(true)

                bigVm.requestGrid(GridSize(3, 2))

                assertEquals(EditorMessage.GRID_DOES_NOT_FIT, bigVm.state.first { it.message != null }.message)
                assertEquals(
                    GridSize(6, 3),
                    bigProfiles.list.value
                        .single()
                        .grid,
                )
                job.cancel()
            }
        }

    @Test
    fun `the action picker lists every known action by title, also while disconnected`() =
        runTest {
            collected { state ->
                val ui = state.first { it.profile != null }
                assertEquals(listOf("Mute microphone", "Next track"), ui.actions.map { it.title })
                assertFalse(ui.connected)
            }
        }

    @Test
    fun `buttons keep their cached titles in the editor while offline`() =
        runTest {
            collected { state ->
                val ui = state.first { it.profile != null }
                assertEquals("Mute microphone", ui.buttons.getValue(BuiltinActions.MUTE_MICROPHONE).title)
                assertEquals(
                    "Volume up",
                    ui.buttons.getValue(BuiltinActions.VOLUME_UP).title,
                    "an action never seen gets a readable fallback title",
                )
                assertEquals(
                    LayoutOps.emptySlots(GridSize(4, 2)).size,
                    ui.profile
                        ?.pages
                        ?.first()
                        ?.slots
                        ?.size,
                )
            }
        }
}
