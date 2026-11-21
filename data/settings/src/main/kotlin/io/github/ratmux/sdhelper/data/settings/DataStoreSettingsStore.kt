package io.github.ratmux.sdhelper.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import io.github.ratmux.sdhelper.core.model.AppSettings
import io.github.ratmux.sdhelper.core.model.SettingsStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DataStoreSettingsStore(
    private val dataStore: DataStore<Preferences>,
) : SettingsStore {
    override val settings: Flow<AppSettings> = dataStore.data.map { read(it) }

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        dataStore.edit { prefs ->
            val next = transform(read(prefs))
            prefs[HAPTIC] = next.hapticFeedback
            prefs[KEEP_SCREEN_ON] = next.keepScreenOn
            prefs[THEME] = next.themeMode.name
            prefs[LAST_MODE] = next.lastMode.name
        }
    }

    /** A value this version does not know (written by another version) reads as its default. */
    private fun read(prefs: Preferences): AppSettings {
        val defaults = AppSettings()
        return AppSettings(
            hapticFeedback = prefs[HAPTIC] ?: defaults.hapticFeedback,
            keepScreenOn = prefs[KEEP_SCREEN_ON] ?: defaults.keepScreenOn,
            themeMode = enumOrDefault(prefs[THEME], defaults.themeMode),
            lastMode = enumOrDefault(prefs[LAST_MODE], defaults.lastMode),
        )
    }

    private inline fun <reified T : Enum<T>> enumOrDefault(
        name: String?,
        default: T,
    ): T = enumValues<T>().firstOrNull { it.name == name } ?: default

    companion object {
        private val HAPTIC = booleanPreferencesKey("haptic_feedback")
        private val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        private val THEME = stringPreferencesKey("theme_mode")
        private val LAST_MODE = stringPreferencesKey("last_mode")

        fun create(context: Context): SettingsStore =
            DataStoreSettingsStore(
                PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("app_settings") },
            )
    }
}
