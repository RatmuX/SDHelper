package io.github.ratmux.sdhelper.feature.deckeditor

import io.github.ratmux.sdhelper.core.model.DeckProfile
import io.github.ratmux.sdhelper.core.model.LayoutLimits
import io.github.ratmux.sdhelper.core.model.LayoutPresets
import io.github.ratmux.sdhelper.core.model.ProfileStore
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
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class LayoutsViewModelTest {
    private class FakeProfiles(
        initial: List<DeckProfile>,
    ) : ProfileStore {
        val list = MutableStateFlow(initial)
        val active = MutableStateFlow<String?>(initial.firstOrNull()?.id)
        override val profiles: Flow<List<DeckProfile>> get() = list
        override val activeProfileId: Flow<String?> get() = active

        override suspend fun save(profile: DeckProfile) {
            list.value =
                if (list.value.any { it.id == profile.id }) {
                    list.value.map {
                        if (it.id ==
                            profile.id
                        ) {
                            profile
                        } else {
                            it
                        }
                    }
                } else {
                    list.value + profile
                }
        }

        override suspend fun delete(profileId: String): Boolean {
            if (list.value.size <= 1) return false
            list.value = list.value.filterNot { it.id == profileId }
            if (active.value == profileId) active.value = list.value.first().id
            return true
        }

        override suspend fun setActive(profileId: String) {
            active.value = profileId
        }
    }

    private var counter = 0
    private val newId = { "id-${counter++}" }
    private val everyday = LayoutPresets.EVERYDAY.create("en", newId = { "e-${counter++}" })
    private val profiles = FakeProfiles(listOf(everyday))

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(language: String = "en") = LayoutsViewModel(profiles, { language }, { "Page $it" }, newId)

    private suspend fun LayoutsViewModel.collected(block: suspend (StateFlow<LayoutsUiState>) -> Unit) =
        coroutineScope {
            val job = launch { state.collect {} }
            block(state)
            job.cancel()
        }

    @Test
    fun `the state lists layouts and marks the active one`() =
        runTest {
            viewModel().collected { state ->
                val ui = state.first { it.profiles.isNotEmpty() }
                assertEquals(listOf(everyday.name), ui.profiles.map { it.name })
                assertEquals(everyday.id, ui.activeProfileId)
            }
        }

    @Test
    fun `a ready-made layout is added in the phone's language and becomes active`() =
        runTest {
            viewModel(language = "tr").addFromPreset("streaming", "")

            val added = profiles.list.value.last()
            assertEquals("Yayın", added.name)
            assertEquals("Canlı", added.pages.first().name)
            assertEquals(added.id, profiles.active.value)
        }

    @Test
    fun `adding the same preset twice gives distinct names`() =
        runTest {
            val vm = viewModel()
            vm.addFromPreset("gaming", "")
            vm.addFromPreset("gaming", "")
            vm.addFromPreset("nonexistent", "")

            assertEquals(listOf("Everyday", "Gaming", "Gaming (2)"), profiles.list.value.map { it.name })
        }

    @Test
    fun `duplicating copies the layout with new ids and a new name`() =
        runTest {
            val vm = viewModel()
            vm.duplicate(everyday.id)

            val copy = profiles.list.value.last()
            assertEquals("Everyday (2)", copy.name)
            assertNotEquals(everyday.id, copy.id)
            assertEquals(everyday.pages.map { it.slots }, copy.pages.map { it.slots }, "same buttons")
            assertTrue(
                (everyday.pages.map { it.id } + copy.pages.map { it.id }).toSet().size == everyday.pages.size * 2,
            )
        }

    @Test
    fun `copies of long names stay within the length limit`() =
        runTest {
            val long = everyday.copy(name = "n".repeat(LayoutLimits.MAX_NAME_LENGTH))
            val store = FakeProfiles(listOf(long))
            val vm = LayoutsViewModel(store, { "en" }, { "Page $it" }, newId)
            vm.duplicate(long.id)
            val copy = store.list.value.last()
            assertTrue(copy.name.length <= LayoutLimits.MAX_NAME_LENGTH)
            assertTrue(copy.name.endsWith(" (2)"))
        }

    @Test
    fun `renaming cleans the name and ignores empty ones`() =
        runTest {
            val vm = viewModel()
            vm.rename(everyday.id, "  My layout ")
            assertEquals(
                "My layout",
                profiles.list.value
                    .single()
                    .name,
            )
            vm.rename(everyday.id, "   ")
            assertEquals(
                "My layout",
                profiles.list.value
                    .single()
                    .name,
            )
            vm.rename("missing", "x")
            assertEquals(1, profiles.list.value.size)
        }

    @Test
    fun `choosing a layout makes it active`() =
        runTest {
            val vm = viewModel()
            vm.addFromPreset("gaming", "")
            val gaming = profiles.list.value.last()
            vm.activate(gaming.id)
            assertEquals(gaming.id, profiles.active.value)
        }

    @Test
    fun `the last layout cannot be deleted and the person is told`() =
        runTest {
            val vm = viewModel()
            vm.collected { state ->
                vm.delete(everyday.id)

                assertEquals(1, profiles.list.value.size)
                assertEquals(LayoutsMessage.LAST_LAYOUT_CANNOT_BE_DELETED, state.first { it.message != null }.message)
                vm.dismissMessage()
                assertNull(state.first { it.message == null }.message)
            }
        }

    @Test
    fun `deleting the active layout activates another`() =
        runTest {
            val vm = viewModel()
            vm.addFromPreset("gaming", "")
            vm.delete(everyday.id)

            assertEquals(1, profiles.list.value.size)
            assertEquals(
                profiles.list.value
                    .single()
                    .id,
                profiles.active.value,
            )
            assertFalse(profiles.list.value.any { it.id == everyday.id })
        }

    @Test
    fun `a ready-made layout can get the name the person chose`() =
        runTest {
            viewModel().addFromPreset("streaming", "  Friday stream ")
            assertEquals(
                "Friday stream",
                profiles.list.value
                    .last()
                    .name,
            )
        }

    @Test
    fun `an empty layout has one empty page, the chosen name, and becomes active`() =
        runTest {
            viewModel().createEmpty("My deck")

            val created = profiles.list.value.last()
            assertEquals("My deck", created.name)
            assertEquals(1, created.pages.size)
            assertEquals(0, created.buttonCount)
            assertEquals(created.id, profiles.active.value)
        }

    @Test
    fun `a blank name falls back to a readable one`() =
        runTest {
            viewModel().createEmpty("   ")
            assertEquals(
                "New layout",
                profiles.list.value
                    .last()
                    .name,
            )
        }

    @Test
    fun `saving a copy keeps the edited layout active and names the copy`() =
        runTest {
            val vm = viewModel()
            vm.saveCopy(everyday.id, "  Evening ")

            assertEquals(listOf("Everyday", "Evening"), profiles.list.value.map { it.name })
            assertEquals(everyday.id, profiles.active.value)
            assertEquals(
                everyday.pages.map { it.slots },
                profiles.list.value
                    .last()
                    .pages
                    .map { it.slots },
            )
        }
}
