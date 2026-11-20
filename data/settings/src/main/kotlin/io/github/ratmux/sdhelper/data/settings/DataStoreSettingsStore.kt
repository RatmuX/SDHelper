package io.github.ratmux.sdhelper.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStoreFile
import io.github.ratmux.sdhelper.core.model.AppSettings
import io.github.ratmux.sdhelper.core.model.SettingsStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DataStoreSettingsStore(
    private val dataStore: DataStore<Preferences>,
) : SettingsStore {
    override val settings: Flow<AppSettings> =
        dataStore.data.map { prefs ->
            val defaults = AppSettings()
            AppSettings(
                hapticFeedback = prefs[HAPTIC] ?: defaults.hapticFeedback,
                keepScreenOn = prefs[KEEP_SCREEN_ON] ?: defaults.keepScreenOn,
            )
        }

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        dataStore.edit { prefs ->
            val defaults = AppSettings()
            val current =
                AppSettings(prefs[HAPTIC] ?: defaults.hapticFeedback, prefs[KEEP_SCREEN_ON] ?: defaults.keepScreenOn)
            val next = transform(current)
            prefs[HAPTIC] = next.hapticFeedback
            prefs[KEEP_SCREEN_ON] = next.keepScreenOn
        }
    }

    companion object {
        private val HAPTIC = booleanPreferencesKey("haptic_feedback")
        private val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")

        fun create(context: Context): SettingsStore =
            DataStoreSettingsStore(
                PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("app_settings") },
            )
    }
}
