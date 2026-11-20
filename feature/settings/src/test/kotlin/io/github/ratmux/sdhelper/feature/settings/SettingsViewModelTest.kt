package io.github.ratmux.sdhelper.feature.settings

import io.github.ratmux.sdhelper.core.model.AppSettings
import io.github.ratmux.sdhelper.core.model.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private class FakeSettings : SettingsStore {
        val current = MutableStateFlow(AppSettings())
        override val settings: Flow<AppSettings> get() = current

        override suspend fun update(transform: (AppSettings) -> AppSettings) {
            current.value = transform(current.value)
        }
    }

    private val settings = FakeSettings()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `the two switches change the stored settings and nothing else`() =
        runTest {
            val vm = SettingsViewModel(settings)
            vm.setHapticFeedback(false)
            assertEquals(AppSettings(hapticFeedback = false, keepScreenOn = true), settings.current.value)
            vm.setKeepScreenOn(false)
            assertEquals(AppSettings(hapticFeedback = false, keepScreenOn = false), settings.current.value)
            vm.setHapticFeedback(true)
            assertEquals(AppSettings(hapticFeedback = true, keepScreenOn = false), settings.current.value)
        }
}
