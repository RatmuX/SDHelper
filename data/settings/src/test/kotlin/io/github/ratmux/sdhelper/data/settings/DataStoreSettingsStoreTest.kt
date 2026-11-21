package io.github.ratmux.sdhelper.data.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.ratmux.sdhelper.core.model.AppMode
import io.github.ratmux.sdhelper.core.model.AppSettings
import io.github.ratmux.sdhelper.core.model.ThemeMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals

class DataStoreSettingsStoreTest {
    private val file = createTempDirectory("sdhelper-settings").resolve("settings.preferences_pb").toFile()

    // DataStore allows one instance per file, so the tests share it and wrap it in new stores.
    private val dataStore = PreferenceDataStoreFactory.create { file }

    @Test
    fun `defaults apply until something is changed`() =
        runTest {
            assertEquals(AppSettings(), DataStoreSettingsStore(dataStore).settings.first())
        }

    @Test
    fun `changes persist and only touch what was asked`() =
        runTest {
            val store = DataStoreSettingsStore(dataStore)
            store.update { it.copy(hapticFeedback = false) }

            val reloaded = DataStoreSettingsStore(dataStore).settings.first()
            assertEquals(AppSettings(hapticFeedback = false, keepScreenOn = true), reloaded)

            store.update { it.copy(keepScreenOn = false) }
            assertEquals(AppSettings(hapticFeedback = false, keepScreenOn = false), store.settings.first())
        }

    @Test
    fun `theme and last mode persist, unknown stored values read as defaults`() =
        runTest {
            val store = DataStoreSettingsStore(dataStore)
            store.update { it.copy(themeMode = ThemeMode.DARK, lastMode = AppMode.KEYBOARD) }
            val reloaded = DataStoreSettingsStore(dataStore).settings.first()
            assertEquals(ThemeMode.DARK, reloaded.themeMode)
            assertEquals(AppMode.KEYBOARD, reloaded.lastMode)
            assertEquals(true, reloaded.hapticFeedback, "other settings untouched")

            dataStore.edit {
                it[stringPreferencesKey("theme_mode")] = "SEPIA"
                it[stringPreferencesKey("last_mode")] = "HOLOGRAM"
            }
            assertEquals(AppSettings(), store.settings.first())
        }
}
