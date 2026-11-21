package io.github.ratmux.sdhelper.feature.settings

import io.github.ratmux.sdhelper.core.model.AppSettings
import io.github.ratmux.sdhelper.core.model.DeckProfile
import io.github.ratmux.sdhelper.core.model.LayoutPresets
import io.github.ratmux.sdhelper.core.model.ProfileStore
import io.github.ratmux.sdhelper.core.model.SettingsStore
import io.github.ratmux.sdhelper.core.model.ThemeMode
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
class SettingsViewModelTest {
    private class FakeSettings : SettingsStore {
        val current = MutableStateFlow(AppSettings())
        override val settings: Flow<AppSettings> get() = current

        override suspend fun update(transform: (AppSettings) -> AppSettings) {
            current.value = transform(current.value)
        }
    }

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
                    list.value.map { if (it.id == profile.id) profile else it }
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
    private val settings = FakeSettings()
    private val everyday = LayoutPresets.EVERYDAY.create("en", newId = { "e-${counter++}" })
    private val gaming = LayoutPresets.GAMING.create("en", newId = { "g-${counter++}" })
    private val profiles = FakeProfiles(listOf(everyday, gaming))

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = SettingsViewModel(settings, profiles) { "id-${counter++}" }

    private suspend fun SettingsViewModel.collected(block: suspend (StateFlow<SettingsUiState>) -> Unit) =
        coroutineScope {
            val job = launch { state.collect {} }
            block(state)
            job.cancel()
        }

    @Test
    fun `the switches change the stored settings and nothing else`() =
        runTest {
            val vm = viewModel()
            vm.setHapticFeedback(false)
            assertEquals(AppSettings(hapticFeedback = false, keepScreenOn = true), settings.current.value)
            vm.setKeepScreenOn(false)
            assertEquals(AppSettings(hapticFeedback = false, keepScreenOn = false), settings.current.value)
            vm.setHapticFeedback(true)
            assertEquals(AppSettings(hapticFeedback = true, keepScreenOn = false), settings.current.value)
        }

    @Test
    fun `choosing light or dark is remembered and no longer follows the phone`() =
        runTest {
            val vm = viewModel()
            assertEquals(ThemeMode.SYSTEM, settings.current.value.themeMode)
            vm.setDarkTheme(true)
            assertEquals(ThemeMode.DARK, settings.current.value.themeMode)
            vm.setDarkTheme(false)
            assertEquals(ThemeMode.LIGHT, settings.current.value.themeMode)
            assertEquals(true, settings.current.value.hapticFeedback, "other settings untouched")
        }

    @Test
    fun `the state lists the layouts and marks the active one`() =
        runTest {
            viewModel().collected { state ->
                val ui = state.first { it.profiles.isNotEmpty() }
                assertEquals(listOf(everyday.name, gaming.name), ui.profiles.map { it.name })
                assertEquals(everyday.id, ui.activeProfileId)
            }
        }

    @Test
    fun `a layout can be activated, renamed and copied`() =
        runTest {
            val vm = viewModel()
            vm.activate(gaming.id)
            assertEquals(gaming.id, profiles.active.value)

            vm.rename(gaming.id, "  Night games ")
            assertEquals(
                "Night games",
                profiles.list.value
                    .first { it.id == gaming.id }
                    .name,
            )

            vm.duplicate(everyday.id)
            assertEquals(
                "Default (2)",
                profiles.list.value
                    .last()
                    .name,
            )
            assertEquals(3, profiles.list.value.size)
        }

    @Test
    fun `deleting works, but the last layout stays and the person is told`() =
        runTest {
            val vm = viewModel()
            vm.collected { state ->
                vm.delete(gaming.id)
                assertEquals(listOf(everyday.id), profiles.list.value.map { it.id })

                vm.delete(everyday.id)
                assertEquals(1, profiles.list.value.size)
                assertEquals(SettingsMessage.LAST_LAYOUT_CANNOT_BE_DELETED, state.first { it.message != null }.message)
                vm.dismissMessage()
                assertNull(state.first { it.message == null }.message)
            }
        }
}
